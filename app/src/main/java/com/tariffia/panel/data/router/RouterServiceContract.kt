package com.tariffia.panel.data.router

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
