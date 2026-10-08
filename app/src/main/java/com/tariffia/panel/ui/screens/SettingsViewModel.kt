package com.tariffia.panel.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.SettingsRules
import com.tariffia.panel.data.update.InstallOutcome
import com.tariffia.panel.data.update.InstalledApp
import com.tariffia.panel.data.update.InstalledAppIdentity
import com.tariffia.panel.data.update.UpdateChecker
import com.tariffia.panel.data.update.UpdateInstaller
import com.tariffia.panel.data.update.UpdateResult
import com.tariffia.panel.data.update.installedApp
import com.tariffia.panel.data.update.readInstalledAppIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the Settings screen. [tokenInput] holds only what the user is
 * currently typing (shown masked); the stored token is never loaded into it.
 */
/** Manual "Check for updates" status shown in the App section. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val version: String, val htmlUrl: String, val apkUrl: String) : UpdateCheckState
    data object Unable : UpdateCheckState
}

data class SettingsUiState(
    val routerUrl: String = "",
    val tokenInput: String = "",
    val tokenVisible: Boolean = false,
    val hasSavedToken: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val showClearConfirmation: Boolean = false,
    /** Installed app versionName, for the App section. */
    val appVersion: String = "",
    /** DIAGNOSTIC: installed versionCode and signing certificate SHA-256 (public metadata only). */
    val appIdentity: InstalledAppIdentity? = null,
    val updateCheck: UpdateCheckState = UpdateCheckState.Idle,
    /** Update install flow. */
    val showInstallConfirmation: Boolean = false,
    val isInstalling: Boolean = false,
    val installMessage: String? = null,
    val needsInstallPermission: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val updateChecker = UpdateChecker()
    private val installer = UpdateInstaller()
    private val installed: InstalledApp = installedApp(application)
    private var saveJob: Job? = null
    private var updateJob: Job? = null
    private var installJob: Job? = null

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(appVersion = installed.versionName, appIdentity = readInstalledAppIdentity(application))
        }
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { store.load() }
            _uiState.update {
                it.copy(
                    routerUrl = settings.routerUrl,
                    hasSavedToken = settings.hasToken,
                    isLoading = false,
                )
            }
        }
    }

    /** Best-effort manual update check. Failures show "Unable to check", never an error dialog. */
    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        _uiState.update {
            it.copy(
                updateCheck = UpdateCheckState.Checking,
                installMessage = null,
                needsInstallPermission = false,
            )
        }
        updateJob = viewModelScope.launch {
            val result = updateChecker.checkDetailed(installed)
            _uiState.update {
                it.copy(
                    updateCheck = when (result) {
                        is UpdateResult.Available -> UpdateCheckState.Available(
                            result.update.version,
                            result.update.htmlUrl,
                            result.update.apkUrl,
                        )
                        UpdateResult.UpToDate -> UpdateCheckState.UpToDate
                        UpdateResult.Failed -> UpdateCheckState.Unable
                    },
                )
            }
        }
    }

    /** Ask for confirmation before downloading and launching the installer. */
    fun requestInstall() {
        if (_uiState.value.updateCheck !is UpdateCheckState.Available) return
        _uiState.update { it.copy(showInstallConfirmation = true, installMessage = null) }
    }

    fun cancelInstall() {
        _uiState.update { it.copy(showInstallConfirmation = false) }
    }

    /** Downloads the APK, then hands it to the system installer. Never logs or shows the URL body. */
    fun confirmInstall() {
        if (installJob?.isActive == true) return
        val update = _uiState.value.updateCheck as? UpdateCheckState.Available ?: return
        _uiState.update {
            it.copy(showInstallConfirmation = false, isInstalling = true, installMessage = null)
        }
        installJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val file = installer.download(app, update.apkUrl, UpdateInstaller.DEFAULT_FILE_NAME)
            if (file == null) {
                _uiState.update {
                    it.copy(isInstalling = false, installMessage = "Download failed. Check your connection and try again.")
                }
                return@launch
            }
            val outcome = installer.install(app, file)
            _uiState.update {
                it.copy(
                    isInstalling = false,
                    needsInstallPermission = outcome is InstallOutcome.PermissionRequired,
                    installMessage = when (outcome) {
                        InstallOutcome.Launched -> "Installer opened. Confirm the install to finish updating."
                        InstallOutcome.PermissionRequired ->
                            "Allow \"Install unknown apps\" for Tariffia Panel, then try again."
                        is InstallOutcome.Failed -> "Could not start the installer: ${outcome.message}"
                    },
                )
            }
        }
    }

    /** Opens the system screen where the user allows this app to install packages. */
    fun openInstallPermissionSettings() {
        val app = getApplication<Application>()
        runCatching {
            app.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(routerUrl = value, statusMessage = null) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(tokenInput = value, statusMessage = null) }
    }

    fun onToggleTokenVisibility() {
        _uiState.update { it.copy(tokenVisible = !it.tokenVisible) }
    }

    fun save() {
        // Ignore a second save while one is already running.
        if (saveJob?.isActive == true) return
        val current = _uiState.value
        val normalized = SettingsRules.normalizeUrl(current.routerUrl)
        if (!SettingsRules.isValidUrl(normalized)) {
            _uiState.update { it.copy(statusMessage = "Enter a valid http(s) router URL.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, statusMessage = null) }
        saveJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(normalized, current.tokenInput) }
            _uiState.update {
                it.copy(
                    routerUrl = normalized,
                    // Never keep the typed secret in UI state after saving.
                    tokenInput = "",
                    tokenVisible = false,
                    hasSavedToken = SettingsRules.hasTokenAfterSave(it.hasSavedToken, current.tokenInput),
                    isSaving = false,
                    statusMessage = "Settings saved.",
                )
            }
        }
    }

    fun requestClearToken() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun cancelClearToken() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    fun confirmClearToken() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearToken() }
            _uiState.update {
                it.copy(
                    hasSavedToken = false,
                    tokenInput = "",
                    tokenVisible = false,
                    showClearConfirmation = false,
                    statusMessage = "Router token cleared.",
                )
            }
        }
    }
}
