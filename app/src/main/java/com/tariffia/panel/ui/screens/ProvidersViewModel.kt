package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.providers.ProviderFacts
import com.tariffia.panel.data.providers.ProviderRow
import com.tariffia.panel.data.providers.ProviderStatusResolver
import com.tariffia.panel.data.providers.SecureProviderKeyStore
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterProvider
import com.tariffia.panel.data.router.RouterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class ProvidersLoadState { LOADING, NOT_CONFIGURED, READY, ERROR }

data class ProvidersUiState(
    val loadState: ProvidersLoadState = ProvidersLoadState.LOADING,
    val errorMessage: String? = null,
    val rows: List<ProviderRow> = emptyList(),
)

/**
 * Reads the provider list from the Router (`GET /v1/providers`) — the single source of
 * truth — and combines it with `/healthz` warnings and the local (device-only) key
 * status. The Panel never invents provider rows and never hard-codes a provider list.
 */
class ProvidersViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val keyStore = SecureProviderKeyStore(application)
    private val client = RouterClient()
    private val refreshGuard = SingleFlightGuard()

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        // Ignore a second refresh while one is already running.
        refreshGuard.tryStart(viewModelScope) {
            _uiState.update { it.copy(loadState = ProvidersLoadState.LOADING, errorMessage = null) }
            val settings = withContext(Dispatchers.IO) { store.load() }
            val token = withContext(Dispatchers.IO) { store.readToken() }
            val url = settings.routerUrl

            if (url.isBlank() || token.isNullOrBlank()) {
                _uiState.update { it.copy(loadState = ProvidersLoadState.NOT_CONFIGURED, rows = emptyList()) }
                return@tryStart
            }

            when (val providers = client.fetchProviders(url, token)) {
                is RouterResult.Success -> {
                    // /healthz supplies warning reasons only; the provider list itself
                    // (and `configured`) already came from /v1/providers.
                    val warnings = (client.fetchHealth(url, token) as? RouterResult.Success)
                        ?.value
                        ?.warnings
                        ?.associate { it.providerId to it.reason }
                        ?: emptyMap()
                    val rows = withContext(Dispatchers.IO) {
                        ProviderStatusResolver.resolve(
                            facts = providers.value.map { it.toFacts() },
                            warnedReasons = warnings,
                            hasLocalKey = { id -> keyStore.hasKey(id) },
                        )
                    }
                    _uiState.update {
                        it.copy(loadState = ProvidersLoadState.READY, errorMessage = null, rows = rows)
                    }
                }
                RouterResult.AuthenticationFailed -> fail("Authentication failed.")
                is RouterResult.HttpError -> fail("Router returned HTTP ${providers.code}.")
                is RouterResult.InvalidResponse -> fail(providers.reason)
                is RouterResult.ConnectionFailed -> fail("Connection failed.")
            }
        }
    }

    private fun RouterProvider.toFacts() = ProviderFacts(
        id = id,
        configured = configured,
        keyless = keyless,
        modelCount = models,
        summary = summary,
        freeTierNote = freeTierNote,
    )

    private suspend fun fail(message: String) {
        _uiState.update {
            it.copy(loadState = ProvidersLoadState.ERROR, errorMessage = message, rows = emptyList())
        }
    }
}
