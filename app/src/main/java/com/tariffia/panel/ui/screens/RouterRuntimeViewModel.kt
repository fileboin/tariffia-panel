package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * Temporary PR1 controller: starts the embedded Router and waits (bounded) for
 * `/healthz` to answer. No persistence, no lifecycle ownership yet.
 */
class RouterRuntimeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<RouterRuntimeStatus>(RouterRuntimeStatus.Idle)
    val state: StateFlow<RouterRuntimeStatus> = _state.asStateFlow()

    fun start() {
        val current = _state.value
        if (current is RouterRuntimeStatus.Starting || current is RouterRuntimeStatus.Ready) return
        _state.value = RouterRuntimeStatus.Starting
        viewModelScope.launch {
            // Guard the whole native bring-up: a missing/incompatible libnode must
            // surface as an ERROR, never as a crash.
            val app = getApplication<Application>()
            val outcome = try {
                when {
                    // Already listening (e.g. Router still up): do not start a second process.
                    RouterRuntime.isHealthyNow(app) -> RouterRuntimeStatus.Ready
                    else -> {
                        val result = RouterRuntime.start(app)
                        when {
                            !result.started -> RouterRuntimeStatus.Error(result.message)
                            RouterRuntime.awaitHealthy(app) -> RouterRuntimeStatus.Ready
                            else -> RouterRuntimeStatus.Error("Router did not answer /healthz on 127.0.0.1:8910.")
                        }
                    }
                }
            } catch (t: Throwable) {
                RouterRuntimeStatus.Error("runtime init failed: ${t.message}")
            }
            _state.value = outcome
        }
    }
}
