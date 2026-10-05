package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.ssh.HostKeyIdentity
import com.tariffia.panel.data.ssh.HostKeyPin
import com.tariffia.panel.data.ssh.SshConnectOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for mapping SSH outcomes onto the VPS connection state. */
class VpsConnectionResolverTest {

    private val identity = HostKeyIdentity("ssh-ed25519", "AAAA", "SHA256:aaa")
    private val pin = HostKeyPin("vps.example.com", 22, identity)

    @Test
    fun connected_isConnected() {
        assertEquals(
            VpsConnectionState.Connected,
            VpsConnectionResolver.fromOutcome(SshConnectOutcome.Connected),
        )
    }

    @Test
    fun unknownHostKey_requiresConfirmation() {
        assertEquals(
            VpsConnectionState.HostKeyConfirmationRequired(identity),
            VpsConnectionResolver.fromOutcome(SshConnectOutcome.HostKeyUnknown(identity)),
        )
    }

    @Test
    fun changedHostKey_isHostKeyChanged() {
        assertEquals(
            VpsConnectionState.HostKeyChanged(pin, identity),
            VpsConnectionResolver.fromOutcome(SshConnectOutcome.HostKeyChanged(pin, identity)),
        )
    }

    @Test
    fun authenticationFailed_mapsThrough() {
        assertEquals(
            VpsConnectionState.AuthenticationFailed,
            VpsConnectionResolver.fromOutcome(SshConnectOutcome.AuthenticationFailed),
        )
    }

    @Test
    fun genericFailure_mapsToConnectionFailedWithMessage() {
        assertEquals(
            VpsConnectionState.ConnectionFailed("Connection failed."),
            VpsConnectionResolver.fromOutcome(SshConnectOutcome.Failed("Connection failed.")),
        )
    }
}
