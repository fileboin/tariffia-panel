package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.providers.ProviderRow
import com.tariffia.panel.data.providers.ProviderStatusResolver
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProvidersLoadState { LOADING, NOT_CONFIGURED, READY, ERROR }

data class ProvidersUiState(
    val loadState: ProvidersLoadState = ProvidersLoadState.LOADING,
    val errorMessage: String? = null,
    val rows: List<ProviderRow> = ProviderStatusResolver.unknown(),
)

/**
 * Reads the provider configuration the router exposes through the existing
 * `/healthz` status (loaded provider IDs and load-time warnings). No new endpoint is
 * used and no status is invented: anything the router does not mention is Unknown.
 */
class ProvidersViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val client = RouterClient()

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loadState = ProvidersLoadState.LOADING, errorMessage = null) }
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { store.load() }
            val token = withContext(Dispatchers.IO) { store.readToken() }
            val url = settings.routerUrl

            if (url.isBlank() || token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(loadState = ProvidersLoadState.NOT_CONFIGURED, rows = ProviderStatusResolver.unknown())
                }
                return@launch
            }

            when (val result = client.fetchHealth(url, token)) {
                is RouterResult.Success -> {
                    val health = result.value
                    val rows = ProviderStatusResolver.resolve(
                        configuredIds = health.providers,
                        warnedReasons = health.warnings.associate { it.providerId to it.reason },
                    )
                    _uiState.update {
                        it.copy(loadState = ProvidersLoadState.READY, errorMessage = null, rows = rows)
                    }
                }
                RouterResult.AuthenticationFailed -> fail("Authentication failed.")
                is RouterResult.HttpError -> fail("Router returned HTTP ${result.code}.")
                is RouterResult.InvalidResponse -> fail(result.reason)
                is RouterResult.ConnectionFailed -> fail("Connection failed.")
            }
        }
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(loadState = ProvidersLoadState.ERROR, errorMessage = message, rows = ProviderStatusResolver.unknown())
        }
    }
}
