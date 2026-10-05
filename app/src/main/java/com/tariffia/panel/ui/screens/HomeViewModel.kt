package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HomeStatus {
    data object Unknown : HomeStatus
    data object Online : HomeStatus
    data object AuthFailed : HomeStatus
    data object NotConfigured : HomeStatus
    data class Offline(val message: String) : HomeStatus
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val status: HomeStatus = HomeStatus.Unknown,
    val models: List<String> = emptyList(),
    val modelsError: String? = null,
)

/**
 * Drives the Home/Status screen: reads the router URL and token from secure
 * storage, checks `/healthz`, then lists `/v1/models`. One-shot on open plus
 * manual refresh only; there is no background polling.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val client = RouterClient()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, modelsError = null) }
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { store.load() }
            val token = withContext(Dispatchers.IO) { store.readToken() }
            val url = settings.routerUrl

            if (url.isBlank() || token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, status = HomeStatus.NotConfigured, models = emptyList())
                }
                return@launch
            }

            when (val health = client.checkHealth(url, token)) {
                is RouterResult.Success -> loadModels(url, token)
                RouterResult.AuthenticationFailed -> setAuthFailed()
                is RouterResult.HttpError -> setOffline("Router returned HTTP ${health.code}.")
                is RouterResult.InvalidResponse -> setOffline(health.reason)
                is RouterResult.ConnectionFailed -> setOffline(health.reason)
            }
        }
    }

    private suspend fun loadModels(url: String, token: String) {
        when (val models = client.fetchModels(url, token)) {
            is RouterResult.Success -> _uiState.update {
                it.copy(
                    isLoading = false,
                    status = HomeStatus.Online,
                    models = models.value,
                    modelsError = null,
                )
            }
            RouterResult.AuthenticationFailed -> setAuthFailed()
            is RouterResult.HttpError -> _uiState.update {
                it.copy(
                    isLoading = false,
                    status = HomeStatus.Online,
                    models = emptyList(),
                    modelsError = "Models unavailable (HTTP ${models.code}).",
                )
            }
            is RouterResult.InvalidResponse -> _uiState.update {
                it.copy(
                    isLoading = false,
                    status = HomeStatus.Online,
                    models = emptyList(),
                    modelsError = models.reason,
                )
            }
            is RouterResult.ConnectionFailed -> _uiState.update {
                it.copy(
                    isLoading = false,
                    status = HomeStatus.Online,
                    models = emptyList(),
                    modelsError = "Could not load models.",
                )
            }
        }
    }

    private fun setAuthFailed() {
        _uiState.update {
            it.copy(isLoading = false, status = HomeStatus.AuthFailed, models = emptyList())
        }
    }

    private fun setOffline(message: String) {
        _uiState.update {
            it.copy(isLoading = false, status = HomeStatus.Offline(message), models = emptyList())
        }
    }
}
