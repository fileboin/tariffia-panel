package com.tariffia.panel.data.router

import kotlinx.coroutines.delay

/**
 * Bounded readiness polling. The clock and sleep are injectable so the loop can be
 * unit tested without real waiting; production uses the defaults.
 */
object ReadinessWaiter {

    suspend fun awaitReady(
        timeoutMs: Long,
        pollIntervalMs: Long,
        nowMs: () -> Long = { System.currentTimeMillis() },
        sleep: suspend (Long) -> Unit = { delay(it) },
        probe: suspend () -> Boolean,
    ): Boolean {
        val deadline = nowMs() + timeoutMs
        while (true) {
            if (probe()) return true
            val remaining = deadline - nowMs()
            if (remaining <= 0L) return false
            sleep(minOf(pollIntervalMs, remaining))
        }
    }
}
