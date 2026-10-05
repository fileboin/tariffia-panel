package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.ssh.HostKeyIdentity
import com.tariffia.panel.data.ssh.HostKeyPin
import com.tariffia.panel.data.ssh.SshConnectOutcome

/** Explicit Test Connection state so the UI can show one short, specific message. */
sealed interface VpsConnectionState {
    data object NotConfigured : VpsConnectionState
    data object Idle : VpsConnectionState
    data object Testing : VpsConnectionState
    data object Connected : VpsConnectionState
    data object AuthenticationFailed : VpsConnectionState
    data class ConnectionFailed(val message: String? = null) : VpsConnectionState
    data class HostKeyConfirmationRequired(val presented: HostKeyIdentity) : VpsConnectionState
    data class HostKeyChanged(val pinned: HostKeyPin, val presented: HostKeyIdentity) : VpsConnectionState
}

/** Pure mapping from an SSH outcome onto the UI state. JVM-testable. */
object VpsConnectionResolver {
    fun fromOutcome(outcome: SshConnectOutcome): VpsConnectionState = when (outcome) {
        SshConnectOutcome.Connected -> VpsConnectionState.Connected
        is SshConnectOutcome.HostKeyUnknown ->
            VpsConnectionState.HostKeyConfirmationRequired(outcome.presented)
        is SshConnectOutcome.HostKeyChanged ->
            VpsConnectionState.HostKeyChanged(outcome.pinned, outcome.presented)
        SshConnectOutcome.AuthenticationFailed -> VpsConnectionState.AuthenticationFailed
        is SshConnectOutcome.Failed -> VpsConnectionState.ConnectionFailed(outcome.reason)
    }
}
