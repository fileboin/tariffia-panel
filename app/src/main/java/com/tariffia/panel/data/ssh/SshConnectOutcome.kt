package com.tariffia.panel.data.ssh

/**
 * Outcome of a Test Connection attempt. Failures never carry key material or
 * response bodies that could contain a secret.
 */
sealed interface SshConnectOutcome {
    /** Handshake + public-key authentication succeeded (and the fixed check ran). */
    data object Connected : SshConnectOutcome

    /** The server's host key is not pinned yet; the user must confirm it. */
    data class HostKeyUnknown(val presented: HostKeyIdentity) : SshConnectOutcome

    /** The server presented a key different from the pinned one. */
    data class HostKeyChanged(
        val pinned: HostKeyPin,
        val presented: HostKeyIdentity,
    ) : SshConnectOutcome

    data object AuthenticationFailed : SshConnectOutcome

    data class Failed(val reason: String) : SshConnectOutcome
}

/**
 * Minimal SSH transport. Implementations must verify the host key against [pinned]
 * and must never auto-accept an unknown key.
 */
interface SshConnector {
    suspend fun connect(
        profile: SshProfile,
        privateKeyPem: String,
        passphrase: String?,
        pinned: HostKeyPin?,
    ): SshConnectOutcome
}
