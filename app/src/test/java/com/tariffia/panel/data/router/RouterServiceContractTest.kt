package com.tariffia.panel.data.router

import kotlinx.coroutines.runBlocking
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
    fun tunnelFailureExposesReasonAndRedactsSessionPassword() {
        val password = "ssh-password-sentinel"
        assertEquals(
            "SSH tunnel failed: Auth fail for [redacted]",
            RouterServiceContract.appendSshTunnelFailure(null, "Auth fail for $password", password),
        )
        assertFalse(
            RouterServiceContract.appendSshTunnelFailure(null, "Auth fail for $password", password)
                .contains(password),
        )
        assertEquals(
            "Key sync: 1 synced\nSSH tunnel failed: Connection refused",
            RouterServiceContract.appendSshTunnelFailure("Key sync: 1 synced", "Connection refused", null),
        )
        assertEquals(
            RouterServiceContract.SSH_TUNNEL_FAILURE_NOTICE,
            RouterServiceContract.appendSshTunnelFailure(null, null, null),
        )
    }

    @Test
    fun tunnelReturningFalseStillStartsRouterAndSetsOllamaUnavailable() = runBlocking {
        var routerStarted = false
        val availability = mutableListOf<Boolean>()
        val tunnelStarted = startRouterAfterTunnel(
            tunnelStart = { false },
            routerStart = { routerStarted = true; true },
            setOllamaAvailable = { availability += it },
        )
        assertFalse(tunnelStarted)
        assertTrue(routerStarted)
        assertEquals(listOf(false), availability)
    }

    @Test
    fun tunnelExceptionStillStartsRouterAndSetsOllamaUnavailable() = runBlocking {
        var routerStarted = false
        val availability = mutableListOf<Boolean>()
        val tunnelStarted = startRouterAfterTunnel(
            tunnelStart = { throw IllegalStateException("tunnel failed") },
            routerStart = { routerStarted = true; true },
            setOllamaAvailable = { availability += it },
        )
        assertFalse(tunnelStarted)
        assertTrue(routerStarted)
        assertEquals(listOf(false), availability)
    }

    @Test
    fun successfulTunnelStartsRouterAndEnablesOllamaAfterRouterReady() = runBlocking {
        var routerStarted = false
        val events = mutableListOf<String>()
        val tunnelStarted = startRouterAfterTunnel(
            tunnelStart = { events += "tunnel"; true },
            routerStart = { events += "router"; routerStarted = true; true },
            setOllamaAvailable = { events += "available:$it" },
        )
        assertTrue(tunnelStarted)
        assertTrue(routerStarted)
        assertEquals(listOf("tunnel", "router", "available:true"), events)
    }

    @Test
    fun routerNotReadyKeepsOllamaUnavailable() = runBlocking {
        val availability = mutableListOf<Boolean>()
        startRouterAfterTunnel(
            tunnelStart = { true },
            routerStart = { false },
            setOllamaAvailable = { availability += it },
        )
        assertEquals(listOf(false), availability)
    }

    @Test
    fun stopDisablesOllamaBeforeReleasingTunnel() = runBlocking {
        val events = mutableListOf<String>()
        stopTunnelAfterDisablingOllama(
            setOllamaUnavailable = { events += "available:false" },
            stopTunnel = { events += "stop-tunnel" },
        )
        assertEquals(listOf("available:false", "stop-tunnel"), events)
    }

    @Test
    fun stopStillReleasesTunnelIfAvailabilityUpdateThrows() = runBlocking {
        var tunnelStopped = false
        stopTunnelAfterDisablingOllama(
            setOllamaUnavailable = { throw IllegalStateException("Router unavailable") },
            stopTunnel = { tunnelStopped = true },
        )
        assertTrue(tunnelStopped)
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
