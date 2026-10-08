package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterServiceContractTest {

    @Test
    fun commandForMapsKnownActions() {
        assertEquals(
            RouterServiceContract.Command.START,
            RouterServiceContract.commandFor(RouterServiceContract.ACTION_START),
        )
        assertEquals(
            RouterServiceContract.Command.STOP,
            RouterServiceContract.commandFor(RouterServiceContract.ACTION_STOP),
        )
    }

    @Test
    fun commandForUnknownOrNullIsNone() {
        assertEquals(RouterServiceContract.Command.NONE, RouterServiceContract.commandFor(null))
        assertEquals(RouterServiceContract.Command.NONE, RouterServiceContract.commandFor("whatever"))
    }

    @Test
    fun tunnelFailureIsAppendedAsSafeRuntimeNotice() {
        assertEquals(
            RouterServiceContract.SSH_TUNNEL_FAILURE_NOTICE,
            RouterServiceContract.appendSshTunnelFailure(null),
        )
        assertEquals(
            "Key sync: 1 synced\n${RouterServiceContract.SSH_TUNNEL_FAILURE_NOTICE}",
            RouterServiceContract.appendSshTunnelFailure("Key sync: 1 synced"),
        )
        assertFalse(RouterServiceContract.SSH_TUNNEL_FAILURE_NOTICE.contains("password-value"))
    }

    @Test
    fun shouldStartIsFalseWhileStartingOrReady() {
        assertFalse(RouterStartGuard.shouldStart(RouterRuntime.State.Starting))
        assertFalse(RouterStartGuard.shouldStart(RouterRuntime.State.Ready))
    }

    @Test
    fun shouldStartIsTrueOtherwise() {
        assertTrue(RouterStartGuard.shouldStart(RouterRuntime.State.Idle))
        assertTrue(RouterStartGuard.shouldStart(RouterRuntime.State.Stopped))
        assertTrue(RouterStartGuard.shouldStart(RouterRuntime.State.Error("boom")))
    }

    @Test
    fun stateMappingReflectsOutcome() {
        assertEquals(
            RouterRuntime.State.Ready,
            RouterStateMapping.from(started = true, healthy = true, failureMessage = "x"),
        )
        assertEquals(
            RouterRuntime.State.Error("x"),
            RouterStateMapping.from(started = true, healthy = false, failureMessage = "x"),
        )
        assertEquals(
            RouterRuntime.State.Error("x"),
            RouterStateMapping.from(started = false, healthy = false, failureMessage = "x"),
        )
    }
}
