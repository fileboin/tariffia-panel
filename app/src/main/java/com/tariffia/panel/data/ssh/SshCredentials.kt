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

/**
 * Pure JSch authentication configuration. Kept free of JSch/Android APIs so it can be
 * unit tested on the JVM.
 *
 * Password mode offers `password` AND `keyboard-interactive`: many OpenSSH servers (e.g.
 * with PAM) advertise only keyboard-interactive for password logins, so advertising
 * `password` alone made a correct password fail with "Auth fail".
 */
object SshAuthConfig {
    fun preferredAuthentications(authMethod: SshAuthMethod): String = when (authMethod) {
        SshAuthMethod.KEY -> "publickey"
        SshAuthMethod.PASSWORD -> "password,keyboard-interactive"
    }
}
