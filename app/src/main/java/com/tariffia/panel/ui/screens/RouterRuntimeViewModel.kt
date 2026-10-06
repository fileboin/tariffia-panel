package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.providers.ProviderKeySyncRunner
import com.tariffia.panel.data.providers.summaryText
import com.tariffia.panel.data.router.RouterRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** PR1 temporary state for the embedded Router runtime. */
sealed interface RouterRuntimeStatus {
    data object Idle : RouterRuntimeStatus
    data object Starting : RouterRuntimeStatus
    data object Ready : RouterRuntimeStatus
    data class Error(val message: String) : RouterRuntimeStatus
}

/**
 * Temporary controller: starts the embedded Router, waits (bounded) for `/healthz`
 * to answer, and — once READY — pushes every locally stored provider key to the
 * Router (the Router's key store is in-memory, so this repeats on each start).
 */
class RouterRuntimeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<RouterRuntimeStatus>(RouterRuntimeStatus.Idle)
    val state: StateFlow<RouterRuntimeStatus> = _state.asStateFlow()

    private val _syncSummary = MutableStateFlow<String?>(null)
    val syncSummary: StateFlow<String?> = _syncSummary.asStateFlow()

    fun start() {
        val current = _state.value
        if (current is RouterRuntimeStatus.Starting || current is RouterRuntimeStatus.Ready) return
        _state.value = RouterRuntimeStatus.Starting
        _syncSummary.value = null
        viewModelScope.launch {
            // Guard the whole native bring-up: a missing/incompatible libnode must
            // surface as an ERROR, never as a crash.
            val app = getApplication<Application>()
            try {
                val ready = if (RouterRuntime.isHealthyNow(app)) {
                    // Already listening: do not start a second Node/Router process.
                    true
                } else {
                    val result = RouterRuntime.start(app)
                    if (!result.started) {
                        _state.value = RouterRuntimeStatus.Error(result.message)
                        return@launch
                    }
                    RouterRuntime.awaitHealthy(app)
                }

                if (!ready) {
                    _state.value = RouterRuntimeStatus.Error("Router did not answer /healthz on 127.0.0.1:8910.")
                    return@launch
                }

                _state.value = RouterRuntimeStatus.Ready
                syncProviderKeys(app)
            } catch (t: Throwable) {
                _state.value = RouterRuntimeStatus.Error("runtime init failed: ${t.message}")
            }
        }
    }

    /** After READY, push every locally stored provider key to the Router. */
    private suspend fun syncProviderKeys(app: Application) {
        _syncSummary.value = "Key sync: running…"
        val summary = try {
            val config = RouterRuntime.ensureLocalConfig(app)
            ProviderKeySyncRunner.syncToLocalRouter(app, config.url, config.token).summaryText()
        } catch (t: Throwable) {
            "Key sync failed: ${t.message}"
        }
        _syncSummary.value = summary
    }
}
