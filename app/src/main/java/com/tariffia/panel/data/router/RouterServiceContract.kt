package com.tariffia.panel.data.router

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure, JVM-testable pieces of the foreground-service contract: action parsing,
 * the single-start guard, and the outcome->state mapping. Kept free of Android APIs.
 */
object RouterServiceContract {

    const val ACTION_START = "com.tariffia.panel.action.ROUTER_START"
    const val ACTION_STOP = "com.tariffia.panel.action.ROUTER_STOP"

    enum class Command { START, STOP, NONE }

    fun commandFor(action: String?): Command = when (action) {
        ACTION_START -> Command.START
        ACTION_STOP -> Command.STOP
        else -> Command.NONE
    }

    /** Fixed fallback if the tunnel supplied no usable failure reason. */
    const val SSH_TUNNEL_FAILURE_NOTICE =
        "SSH tunnel unavailable. Check the VPS SSH profile, password/key, and pinned host key."

    /** Keeps the actual tunnel reason while redacting the current session password. */
    fun appendSshTunnelFailure(summary: String?, error: String?, password: String?): String {
        val safeError = error
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                val redacted = password?.takeIf { it.isNotEmpty() }
                    ?.let { message.replace(it, "[redacted]") }
                    ?: message
                redacted.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(160)
            }
            ?.takeIf { it.isNotBlank() }
        val notice = safeError?.let { "SSH tunnel failed: $it" } ?: SSH_TUNNEL_FAILURE_NOTICE
        return listOfNotNull(summary?.takeIf { it.isNotBlank() }, notice).joinToString("\n")
    }
}

/**
 * Runs the Router start even if the best-effort tunnel returns false or throws an exception.
 * Coroutine cancellation is preserved so an explicitly stopped/destroyed service does not
 * continue startup after cancellation.
 */
internal suspend fun startRouterAfterTunnel(
    tunnelStart: suspend () -> Boolean,
    routerStart: suspend () -> Boolean,
    setOllamaAvailable: suspend (Boolean) -> Unit,
): Boolean {
    val tunnelStarted = try {
        tunnelStart()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    val routerReady = routerStart()
    publishAvailability(setOllamaAvailable, tunnelStarted && routerReady)
    return tunnelStarted
}

/** Requests Ollama unavailable before releasing its local tunnel during explicit STOP. */
internal suspend fun stopTunnelAfterDisablingOllama(
    setOllamaUnavailable: suspend () -> Unit,
    stopTunnel: () -> Unit,
) {
    try {
        try {
            setOllamaUnavailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Still close the local tunnel if the embedded Router cannot be reached.
        }
    } finally {
        stopTunnel()
    }
}

private suspend fun publishAvailability(
    setOllamaAvailable: suspend (Boolean) -> Unit,
    available: Boolean,
) {
    try {
        setOllamaAvailable(available)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Availability signaling is best-effort; the Router defaults to unavailable.
    }
}

/**
 * Sends Ollama availability to the embedded Router only when it changes. A value is recorded
 * only after a successful send, so a failed update is retried by the next caller. Calls are
 * serialized so the tunnel watcher and an explicit STOP cannot interleave.
 */
internal class OllamaAvailabilityPublisher(
    private val send: suspend (Boolean) -> Unit,
) {
    private val mutex = Mutex()
    private var lastSent: Boolean? = null

    /** Returns true when the Router already reflects [available] or the send succeeded. */
    suspend fun publish(available: Boolean): Boolean = mutex.withLock {
        if (lastSent == available) return@withLock true
        try {
            send(available)
            lastSent = available
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Watches an ESTABLISHED SSH tunnel while the Router runs. When [isTunnelUp] reports the
 * session is gone, Ollama is marked unavailable once and the watcher exits. It never
 * reconnects and never stops or restarts the Router. [isTunnelUp] is a cheap local check
 * (JSch Session.isConnected), so polling does no network I/O.
 *
 * [stop] is terminal: it cancels any running watcher and makes later [start] calls no-ops.
 */
internal class OllamaTunnelWatch(
    private val scope: CoroutineScope,
    private val isTunnelUp: () -> Boolean,
    private val publisher: OllamaAvailabilityPublisher,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) {
    private var job: Job? = null
    private var disposed = false

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start() {
        if (disposed) return
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(pollIntervalMs)
                // A failed update is retried on the next poll; success ends the watch.
                if (!isTunnelUp() && publisher.publish(false)) return@launch
            }
        }
    }

    fun stop() {
        disposed = true
        job?.cancel()
        job = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}

/**
 * Decides whether a START request should actually launch the runtime. Starting or
 * already-ready states must NOT start again, so there is never a second Node/Router.
 */
object RouterStartGuard {
    fun shouldStart(state: RouterRuntime.State): Boolean = when (state) {
        RouterRuntime.State.Starting, RouterRuntime.State.Ready -> false
        else -> true
    }
}

/** Maps a bring-up outcome onto a runtime state. Carries no secret. */
object RouterStateMapping {
    fun from(started: Boolean, healthy: Boolean, failureMessage: String): RouterRuntime.State = when {
        !started -> RouterRuntime.State.Error(failureMessage)
        healthy -> RouterRuntime.State.Ready
        else -> RouterRuntime.State.Error(failureMessage)
    }
}
