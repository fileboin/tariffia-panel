package com.tariffia.panel.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Ensures only one run of an action is in flight at a time. Used by the Providers
 * refresh so a second refresh while one is already running is ignored. The Job
 * reference is cleared when the run completes, so a later call can start again.
 *
 * The owner of the [CoroutineScope] keeps its own lifecycle; this guard never cancels
 * the scope or closes anything.
 */
internal class SingleFlightGuard {

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Runs [block] unless a previous run is still active. Returns true if started. */
    fun tryStart(scope: CoroutineScope, block: suspend () -> Unit): Boolean {
        if (isRunning) return false
        job = scope.launch {
            try {
                block()
            } finally {
                job = null
            }
        }
        return true
    }

    /** Waits for the current run (if any) to finish. For tests/observability. */
    suspend fun join() {
        job?.join()
    }
}
