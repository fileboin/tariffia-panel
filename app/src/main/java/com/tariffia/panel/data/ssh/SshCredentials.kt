package com.tariffia.panel.data.ssh

/**
 * The secret material for one SSH attempt. Handled in memory only; never logged and
 * never included in any error message.
 */
sealed interface SshCredentials {
    /** Public-key authentication: a PEM private key and an optional key passphrase. */
    data class Key(
        val privateKeyPem: String,
        val passphrase: String?,
    ) : SshCredentials

    /** Password authentication: the account password for this session only. */
    data class Password(val password: String) : SshCredentials
}
