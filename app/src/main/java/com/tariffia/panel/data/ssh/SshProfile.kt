package com.tariffia.panel.data.ssh

/** How the SSH session authenticates. */
enum class SshAuthMethod {
    KEY,
    PASSWORD,
}

/** Non-secret SSH target. The private key, passphrase and password are handled separately. */
data class SshProfile(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val username: String = "",
    val authMethod: SshAuthMethod = SshAuthMethod.KEY,
) {
    companion object {
        const val DEFAULT_PORT = 22
    }
}

/**
 * Pure, dependency-free validation/normalisation for the SSH profile. Kept free of
 * Android APIs so it can be unit tested on the JVM.
 */
object SshProfileRules {

    fun normalizeHost(raw: String): String = raw.trim()

    fun isValidHost(host: String): Boolean {
        val h = host.trim()
        if (h.isEmpty() || h.length > 253) return false
        return h.none { it.isWhitespace() }
    }

    fun parsePort(raw: String): Int? = raw.trim().toIntOrNull()

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isValidUsername(username: String): Boolean {
        val u = username.trim()
        if (u.isEmpty() || u.length > 64) return false
        return u.none { it.isWhitespace() || it == ':' || it == '@' }
    }

    /** A loose check that the text looks like a PEM/OpenSSH private key. */
    fun looksLikePrivateKey(text: String): Boolean {
        val t = text.trim()
        val hasBegin = Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----").containsMatchIn(t)
        val hasEnd = Regex("-----END [A-Z0-9 ]*PRIVATE KEY-----").containsMatchIn(t)
        return hasBegin && hasEnd
    }
}
