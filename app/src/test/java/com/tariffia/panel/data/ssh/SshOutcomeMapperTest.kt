package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the SSH failure/outcome mapping. No SSH server is used; the mapping is
 * pure so the connector's decision logic can be verified directly.
 */
class SshOutcomeMapperTest {

    private val identity = HostKeyIdentity("ssh-ed25519", "AAAA", "SHA256:aaa")
    private val pin = HostKeyPin("vps.example.com", 22, identity)

    @Test
    fun successfulAuth_execUnavailable_isConnected() {
        assertEquals(
            SshConnectOutcome.Connected,
            SshOutcomeMapper.afterAuthentication(execCheckSucceeded = false),
        )
    }

    @Test
    fun successfulAuth_execAvailable_isConnected() {
        assertEquals(
            SshConnectOutcome.Connected,
            SshOutcomeMapper.afterAuthentication(execCheckSucceeded = true),
        )
    }

    @Test
    fun knownAuthFailures_mapToAuthenticationFailed() {
        assertEquals(
            SshConnectOutcome.AuthenticationFailed,
            SshOutcomeMapper.fromFailure("Auth fail", pinned = null, presented = null),
        )
        assertEquals(
            SshConnectOutcome.AuthenticationFailed,
            SshOutcomeMapper.fromFailure("USERAUTH fail", pinned = null, presented = null),
        )
        assertEquals(
            SshConnectOutcome.AuthenticationFailed,
            SshOutcomeMapper.fromFailure("Auth cancel", pinned = null, presented = null),
        )
    }

    @Test
    fun unknownException_mapsToConnectionFailed() {
        assertEquals(
            SshConnectOutcome.Failed("Connection failed."),
            SshOutcomeMapper.fromFailure("java.net.ConnectException: Connection refused", null, null),
        )
        assertEquals(
            SshConnectOutcome.Failed("Connection failed."),
            SshOutcomeMapper.fromFailure("", null, null),
        )
    }

    @Test
    fun messageContainingAuthButNotAuthFailure_mapsToConnectionFailed() {
        // Contains "auth" (in the host name) but is a DNS/connection failure, not auth.
        assertEquals(
            SshConnectOutcome.Failed("Connection failed."),
            SshOutcomeMapper.fromFailure("java.net.UnknownHostException: auth.example.com", null, null),
        )
    }

    @Test
    fun unknownHostKey_mapsToConfirmationRequired() {
        assertEquals(
            SshConnectOutcome.HostKeyUnknown(identity),
            SshOutcomeMapper.fromFailure("UnknownHostKey: vps.example.com", pinned = null, presented = identity),
        )
    }

    @Test
    fun changedHostKey_mapsToHostKeyChanged() {
        assertEquals(
            SshConnectOutcome.HostKeyChanged(pin, identity),
            SshOutcomeMapper.fromFailure("HostKey has been changed: vps.example.com", pin, identity),
        )
    }
}
