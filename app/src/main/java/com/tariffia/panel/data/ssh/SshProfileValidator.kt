package com.tariffia.panel.data.ssh

/** Result of validating the VPS profile fields. */
sealed interface SshProfileValidation {
    data class Valid(val profile: SshProfile) : SshProfileValidation
    data class Invalid(val message: String) : SshProfileValidation
}

/**
 * Pure validation for Save/Test. Trims only safe text (host, username, port); it never
 * touches the private key, passphrase or password content.
 *
 * @param secretAvailable true when the secret the selected method needs is available:
 *   a private key for [SshAuthMethod.KEY], a password for [SshAuthMethod.PASSWORD].
 *   Saving a password-mode profile does not require the password (it is not persisted).
 */
object SshProfileValidator {

    fun validate(
        host: String,
        portText: String,
        username: String,
        authMethod: SshAuthMethod = SshAuthMethod.KEY,
        secretAvailable: Boolean,
    ): SshProfileValidation {
        val normalizedHost = SshProfileRules.normalizeHost(host)
        if (!SshProfileRules.isValidHost(normalizedHost)) {
            return SshProfileValidation.Invalid("Enter a valid host.")
        }

        val port = SshProfileRules.parsePort(portText)
        if (port == null || !SshProfileRules.isValidPort(port)) {
            return SshProfileValidation.Invalid("Port must be 1-65535.")
        }

        val normalizedUsername = username.trim()
        if (!SshProfileRules.isValidUsername(normalizedUsername)) {
            return SshProfileValidation.Invalid("Enter a valid username.")
        }

        if (!secretAvailable) {
            return SshProfileValidation.Invalid(
                if (authMethod == SshAuthMethod.PASSWORD) "Enter the SSH password." else "Paste an SSH private key.",
            )
        }

        return SshProfileValidation.Valid(
            SshProfile(
                host = normalizedHost,
                port = port,
                username = normalizedUsername,
                authMethod = authMethod,
            ),
        )
    }
}
