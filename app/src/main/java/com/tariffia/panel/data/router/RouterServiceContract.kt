package com.tariffia.panel.data.router

import kotlinx.coroutines.CancellationException

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
    routerStart: suspend () -> Unit,
): Boolean {
    val tunnelStarted = try {
        tunnelStart()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    routerStart()
    return tunnelStarted
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
