package com.tariffia.panel.data.router

/**
 * Pure single-flight gate. Used to guarantee that at most one Router bring-up is in
 * flight at a time, so repeated START intents can never start a second Node instance.
 * Kept free of Android APIs so it can be unit tested on the JVM.
 */
class StartGate {

    private var inFlight = false

    val isInFlight: Boolean
        @Synchronized get() = inFlight

    /** Returns true and marks in-flight on the first call; false while already in flight. */
    @Synchronized
    fun tryEnter(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    @Synchronized
    fun exit() {
        inFlight = false
    }
}
