package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for the SSH tunnel rules and lifecycle safety (no Android APIs, no real SSH). */
class SshTunnelRulesTest {

    @Test
    fun localListenerIsLoopbackOn11434() {
        assertEquals("127.0.0.1", SshTunnelRules.LOCAL_HOST)
        assertEquals(11434, SshTunnelRules.LOCAL_PORT)
        // Never bind to all interfaces / the LAN address.
        assertNotEquals("0.0.0.0", SshTunnelRules.LOCAL_HOST)
    }

    @Test
    fun forwardTargetIsVpsOllama() {
        assertEquals("127.0.0.1", SshTunnelRules.REMOTE_HOST)
        assertEquals(11434, SshTunnelRules.REMOTE_PORT)
    }

    @Test
    fun keyProfileWithStoredKeyStartsTheTunnel() {
        assertEquals(
            SshTunnelRules.Plan.START_KEY,
            SshTunnelRules.plan(SshAuthMethod.KEY, hasPrivateKey = true, hasPassword = false),
        )
    }

    @Test
    fun keyProfileWithoutStoredKeyIsRefused() {
        assertEquals(
            SshTunnelRules.Plan.NEEDS_KEY,
            SshTunnelRules.plan(SshAuthMethod.KEY, hasPrivateKey = false, hasPassword = false),
        )
    }

    @Test
    fun passwordProfileWithSessionPasswordStartsTheTunnel() {
        assertEquals(
            SshTunnelRules.Plan.START_PASSWORD,
            SshTunnelRules.plan(SshAuthMethod.PASSWORD, hasPrivateKey = false, hasPassword = true),
        )
    }

    @Test
    fun passwordProfileWithoutSessionPasswordIsRefused() {
        assertEquals(
            SshTunnelRules.Plan.NEEDS_PASSWORD,
            SshTunnelRules.plan(SshAuthMethod.PASSWORD, hasPrivateKey = false, hasPassword = false),
        )
        // A stored key is irrelevant for a password-mode profile.
        assertEquals(
            SshTunnelRules.Plan.NEEDS_PASSWORD,
            SshTunnelRules.plan(SshAuthMethod.PASSWORD, hasPrivateKey = true, hasPassword = false),
        )
    }

    @Test
    fun stopIsSafeAndIdempotentBeforeAnySessionExists() {
        // Simulates the Router STOP path with no tunnel up: must not throw.
        assertFalse(SshTunnel.isUp())
        SshTunnel.stop()
        SshTunnel.stop()
        assertFalse(SshTunnel.isUp())
        assertNull(SshTunnel.lastError())
    }
}
