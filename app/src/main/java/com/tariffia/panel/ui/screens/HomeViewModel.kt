package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult
import com.tariffia.panel.data.update.AvailableUpdate
import com.tariffia.panel.data.update.UpdateChecker
import com.tariffia.panel.data.update.installedVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Router status, kept distinct so the UI can show a specific, short message. */
sealed interface HomeStatus {
    data object Checking : HomeStatus
    data object Online : HomeStatus
    data object NotConfigured : HomeStatus
    data object AuthFailed : HomeStatus
    data object ConnectionFailed : HomeStatus
    data class HttpError(val code: Int) : HomeStatus
    data class InvalidResponse(val message: String) : HomeStatus
}

/** Short provider summary derived from `/healthz`. Never invented. */
data class HomeProviderSummary(
    val configured: Int,
    val warned: Int,
    val candidates: Int,
    val warnedProviderIds: List<String>,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val status: HomeStatus = HomeStatus.Checking,
    val models: List<String> = emptyList(),
    val modelsError: String? = null,
    val providerSummary: HomeProviderSummary? = null,
)

/** Best-effort update availability. Failure is silent (stays [None]). */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
}

/**
 * Drives the Home/Status dashboard: reads the router URL and token from secure
 * storage, checks `/healthz` (status + provider summary), then lists `/v1/models`.
 * One-shot on open plus manual refresh only; there is no background polling and no
 * parallel refresh.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val client = RouterClient()
    private val updateChecker = UpdateChecker()
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.None)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        refresh()
        checkForUpdateOnce()
    }

    /**
     * One-shot, best-effort update check (once per app process). It never affects the
     * Router/status flow: any failure simply leaves [UpdateState.None].
     */
    private fun checkForUpdateOnce() {
        if (updateCheckedThisProcess) return
        updateCheckedThisProcess = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val version = installedVersion(app)
            val update: AvailableUpdate? = updateChecker.check(version)
            if (update != null) {
                _updateState.value = UpdateState.Available(update.version, update.url)
            }
        }
    }

    fun refresh() {
        // Ignore a second refresh while one is already in flight.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, modelsError = null) }
            try {
                val settings = withContext(Dispatchers.IO) { store.load() }
                val token = withContext(Dispatchers.IO) { store.readToken() }
                val url = settings.routerUrl

                if (url.isBlank() || token.isNullOrBlank()) {
                    setError(HomeStatus.NotConfigured)
                    return@launch
                }

                when (val health = client.fetchHealth(url, token)) {
                    is RouterResult.Success -> {
                        _uiState.update {
                            it.copy(providerSummary = HomeStatusResolver.summaryOf(health.value))
                        }
                        loadModels(url, token)
                    }
                    else -> setError(HomeStatusResolver.fromHealthResult(health))
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadModels(url: String, token: String) {
        when (val models = client.fetchModels(url, token)) {
            is RouterResult.Success -> _uiState.update {
                it.copy(status = HomeStatus.Online, models = models.value, modelsError = null)
            }
            RouterResult.AuthenticationFailed -> setError(HomeStatus.AuthFailed)
            else -> _uiState.update {
                it.copy(
                    status = HomeStatus.Online,
                    models = emptyList(),
                    modelsError = HomeStatusResolver.modelsError(models),
                )
            }
        }
    }

    private fun setError(status: HomeStatus) {
        _uiState.update {
            it.copy(status = status, models = emptyList(), modelsError = null, providerSummary = null)
        }
    }

    private companion object {
        /** Guarantees at most one update check per app process. */
        @Volatile
        var updateCheckedThisProcess = false
    }
}
