package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.providers.ProviderCatalog
import com.tariffia.panel.data.providers.ProviderKeyRules
import com.tariffia.panel.data.providers.ProviderStatus
import com.tariffia.panel.data.providers.ProviderStatusResolver
import com.tariffia.panel.data.providers.SecureProviderKeyStore
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProviderDetailsUiState(
    val providerId: String = "",
    val displayName: String = "",
    val routerStatus: ProviderStatus = ProviderStatus.UNKNOWN,
    val routerNote: String? = null,
    val hasStoredKey: Boolean = false,
    val keyInput: String = "",
    val keyVisible: Boolean = false,
    val isLoading: Boolean = true,
    val statusMessage: String? = null,
    val showClearConfirmation: Boolean = false,
)

/**
 * Provider details: shows the router-reported status next to the locally stored API
 * key status, and manages that key in the Android Keystore. This PR is local only —
 * the key is never sent anywhere.
 */
class ProviderDetailsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val settings = SecureSettingsStore(application)
    private val keyStore = SecureProviderKeyStore(application)
    private val client = RouterClient()

    private val providerId: String = savedStateHandle.get<String>(ARG_PROVIDER_ID).orEmpty()

    private val _uiState = MutableStateFlow(
        ProviderDetailsUiState(
            providerId = providerId,
            displayName = ProviderCatalog.displayNameFor(providerId),
        ),
    )
    val uiState: StateFlow<ProviderDetailsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, statusMessage = null) }
        viewModelScope.launch {
            val hasKey = withContext(Dispatchers.IO) { keyStore.hasKey(providerId) }
            _uiState.update { it.copy(hasStoredKey = hasKey) }
            refreshRouterStatus()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshRouterStatus() {
        val settingsSnapshot = withContext(Dispatchers.IO) { settings.load() }
        val token = withContext(Dispatchers.IO) { settings.readToken() }
        val url = settingsSnapshot.routerUrl
        if (url.isBlank() || token.isNullOrBlank()) {
            _uiState.update { it.copy(routerStatus = ProviderStatus.UNKNOWN, routerNote = null) }
            return
        }
        when (val result = client.fetchHealth(url, token)) {
            is RouterResult.Success -> {
                val row = ProviderStatusResolver.resolve(
                    configuredIds = result.value.providers,
                    warnedReasons = result.value.warnings.associate { it.providerId to it.reason },
                ).firstOrNull { it.id.equals(providerId, ignoreCase = true) }
                _uiState.update {
                    it.copy(routerStatus = row?.status ?: ProviderStatus.UNKNOWN, routerNote = row?.note)
                }
            }
            else -> _uiState.update { it.copy(routerStatus = ProviderStatus.UNKNOWN, routerNote = null) }
        }
    }

    fun onKeyChange(value: String) {
        _uiState.update { it.copy(keyInput = value, statusMessage = null) }
    }

    fun onToggleKeyVisibility() {
        _uiState.update { it.copy(keyVisible = !it.keyVisible) }
    }

    fun saveKey() {
        val key = _uiState.value.keyInput
        if (!ProviderKeyRules.isValidKey(key)) {
            _uiState.update { it.copy(statusMessage = "Enter an API key.") }
            return
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { keyStore.saveKey(providerId, key) }
            _uiState.update {
                if (saved) {
                    // Never keep the typed secret in UI state after saving.
                    it.copy(
                        hasStoredKey = true,
                        keyInput = "",
                        keyVisible = false,
                        statusMessage = "API key saved.",
                    )
                } else {
                    it.copy(statusMessage = "Could not save the API key.")
                }
            }
        }
    }

    fun requestClear() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun cancelClear() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    fun confirmClear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { keyStore.clearKey(providerId) }
            _uiState.update {
                it.copy(
                    hasStoredKey = false,
                    keyInput = "",
                    keyVisible = false,
                    showClearConfirmation = false,
                    statusMessage = "API key cleared.",
                )
            }
        }
    }

    companion object {
        const val ARG_PROVIDER_ID = "providerId"
    }
}
