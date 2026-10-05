package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.providers.ProviderCatalog
import com.tariffia.panel.data.providers.ProviderKeyRules
import com.tariffia.panel.data.providers.ProviderStatus
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
    val routerStatusView: RouterStatusView = RouterStatusView.Loading,
    val hasStoredKey: Boolean = false,
    val keyInput: String = "",
    val keyVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val showClearConfirmation: Boolean = false,
)

/** Outcome of pushing the stored key to the Router. Never carries the key. */
private sealed interface SyncResult {
    data object Success : SyncResult
    data object NoLocalKey : SyncResult
    data object RouterNotConfigured : SyncResult
    data object AuthRejected : SyncResult
    data object ProviderNotAllowed : SyncResult
    data object Unreachable : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Provider details: shows the router-reported status (with explicit unavailable states)
 * next to the locally stored API key status, and manages that key in the Android
 * Keystore. The stored key is the source of truth; it can be pushed to the local Router
 * (PUT /v1/providers/{id}/key) without ever being logged or shown.
 *
 * Save, Clear and Sync share one [SingleFlightGuard], so a double tap or a race between
 * them is ignored. Secrets are never placed in status messages and are cleared from UI
 * state after saving.
 */
class ProviderDetailsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val settings = SecureSettingsStore(application)
    private val keyStore = SecureProviderKeyStore(application)
    private val client = RouterClient()
    private val keyGuard = SingleFlightGuard()

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
        _uiState.update { it.copy(isLoading = true, statusMessage = null, routerStatusView = RouterStatusView.Loading) }
        viewModelScope.launch {
            val hasKey = withContext(Dispatchers.IO) { keyStore.hasKey(providerId) }
            _uiState.update { it.copy(hasStoredKey = hasKey) }
            refreshRouterStatus()
            resyncIfRouterLacksKey()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshRouterStatus() {
        val settingsSnapshot = withContext(Dispatchers.IO) { settings.load() }
        val token = withContext(Dispatchers.IO) { settings.readToken() }
        val url = settingsSnapshot.routerUrl
        if (url.isBlank() || token.isNullOrBlank()) {
            _uiState.update { it.copy(routerStatusView = RouterStatusView.RouterNotConfigured) }
            return
        }
        val result = client.fetchHealth(url, token)
        _uiState.update {
            it.copy(routerStatusView = ProviderRouterStatusResolver.fromHealthResult(result, providerId))
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
        // Ignore a second Save (or a Clear) while a mutation is in flight.
        keyGuard.tryStart(viewModelScope) {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            try {
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
            } finally {
                _uiState.update { it.copy(isBusy = false) }
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
        // Shares the guard with Save so the two can never race.
        keyGuard.tryStart(viewModelScope) {
            _uiState.update { it.copy(isBusy = true) }
            try {
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
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    /** Pushes the locally stored key to the local Router's key-sync route. */
    fun syncKeyToRouter() {
        keyGuard.tryStart(viewModelScope) {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            try {
                val result = pushStoredKeyToRouter()
                _uiState.update { it.copy(statusMessage = syncMessage(result)) }
                if (result is SyncResult.Success) refreshRouterStatus()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    /**
     * Reads the stored key (in memory only) and PUTs it to the Router. The value is
     * never logged and never returned.
     */
    private suspend fun pushStoredKeyToRouter(): SyncResult {
        val key = withContext(Dispatchers.IO) { keyStore.readKey(providerId) }
            ?: return SyncResult.NoLocalKey
        val snapshot = withContext(Dispatchers.IO) { settings.load() }
        val token = withContext(Dispatchers.IO) { settings.readToken() }
        val url = snapshot.routerUrl
        if (url.isBlank() || token.isNullOrBlank()) return SyncResult.RouterNotConfigured
        return when (val result = client.syncProviderKey(url, token, providerId, key)) {
            is RouterResult.Success -> SyncResult.Success
            RouterResult.AuthenticationFailed -> SyncResult.AuthRejected
            is RouterResult.HttpError ->
                if (result.code == 404) SyncResult.ProviderNotAllowed else SyncResult.Failed("Router error (HTTP ${result.code}).")
            is RouterResult.InvalidResponse -> SyncResult.Failed("Router rejected the request.")
            is RouterResult.ConnectionFailed -> SyncResult.Unreachable
        }
    }

    private fun syncMessage(result: SyncResult): String = when (result) {
        SyncResult.Success -> "Key synced to router."
        SyncResult.NoLocalKey -> "No local key to sync."
        SyncResult.RouterNotConfigured -> "Router not configured (URL/token)."
        SyncResult.AuthRejected -> "Router rejected the credentials (auth or bind)."
        SyncResult.ProviderNotAllowed -> "Router does not allow this provider."
        SyncResult.Unreachable -> "Cannot reach the router."
        is SyncResult.Failed -> result.message
    }

    /**
     * When the Router reports this provider as not configured but a key is stored
     * locally, push it again (e.g. after a Router restart). Silent: it does not touch
     * the status message.
     */
    private suspend fun resyncIfRouterLacksKey() {
        val state = _uiState.value
        if (!state.hasStoredKey) return
        val view = state.routerStatusView
        if (view is RouterStatusView.Available && view.status == ProviderStatus.NOT_CONFIGURED) {
            if (pushStoredKeyToRouter() is SyncResult.Success) refreshRouterStatus()
        }
    }

    companion object {
        const val ARG_PROVIDER_ID = "providerId"
    }
}
