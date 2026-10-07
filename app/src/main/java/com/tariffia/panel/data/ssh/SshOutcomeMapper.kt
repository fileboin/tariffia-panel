package com.tariffia.panel.data.ssh

/**
 * Pure mapping from SSH failure messages and the post-auth phase onto outcomes.
 * Kept free of JSch/Android APIs so it can be unit tested on the JVM.
 *
 * Authentication failure is only concluded from known JSch auth markers, never from
 * an arbitrary message that merely happens to contain "auth".
 */
object SshOutcomeMapper {

    /** Concrete JSch authentication-failure messages (not a broad "auth" substring). */
    private val AUTH_FAILURE_MARKERS = listOf("Auth fail", "USERAUTH fail", "Auth cancel")

    /**
     * Maps a handshake-phase [message] (host-key or auth failure) to an outcome.
     * Anything unrecognised is a connection failure.
     */
    fun fromFailure(
        message: String,
        pinned: HostKeyPin?,
        presented: HostKeyIdentity?,
    ): SshConnectOutcome = when {
        // JSch emits "UnknownHostKey: ..." only when StrictHostKeyChecking != yes.
        // With StrictHostKeyChecking=yes (our connector) it instead throws
        // "reject HostKey: <host>" for an unpinned host. Both are the unknown-host-key case.
        message.contains("UnknownHostKey", ignoreCase = true) ||
            message.contains("reject HostKey", ignoreCase = true) ->
            presented?.let { SshConnectOutcome.HostKeyUnknown(it) }
                ?: SshConnectOutcome.Failed("Connection failed.")

        message.contains("HostKey has been changed", ignoreCase = true) ->
            if (pinned != null && presented != null) {
                SshConnectOutcome.HostKeyChanged(pinned, presented)
            } else {
                SshConnectOutcome.Failed("Connection failed.")
            }

        AUTH_FAILURE_MARKERS.any { message.contains(it, ignoreCase = true) } ->
            SshConnectOutcome.AuthenticationFailed

        else -> SshConnectOutcome.Failed("Connection failed.")
    }

    /**
     * Outcome once the handshake and authentication have succeeded. The optional fixed
     * exec check is best-effort: if the server forbids exec (ForceCommand/restricted
     * shell) the connection is still [SshConnectOutcome.Connected], never a failure.
     */
    fun afterAuthentication(execCheckSucceeded: Boolean): SshConnectOutcome = when (execCheckSucceeded) {
        true, false -> SshConnectOutcome.Connected
    }
}
