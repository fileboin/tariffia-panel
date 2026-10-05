package com.tariffia.panel.data

/**
 * Non-secret router configuration plus whether a token is currently stored.
 * The token value itself is never exposed here; callers can only overwrite or
 * clear it through [SecureSettingsStore].
 */
data class RouterSettings(
    val routerUrl: String,
    val hasToken: Boolean,
)

/**
 * Pure, dependency-free rules for normalising and validating the router URL.
 * Kept free of Android APIs so it can be unit tested on the JVM.
 */
object SettingsRules {

    /** Trims surrounding whitespace and a single trailing slash (keeps a bare "/"). */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length > 1 && trimmed.endsWith("/")) {
            trimmed.dropLast(1)
        } else {
            trimmed
        }
    }

    /** Minimal validity check: a non-blank http(s) URL with a host. */
    fun isValidUrl(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val host = normalized.substringAfter("://").substringBefore('/').substringBefore('?')
        return host.isNotBlank()
    }

    /**
     * Fixed-width mask shown when a token is stored. Deliberately independent of
     * the real token length so it does not leak any information about the secret.
     */
    fun maskedTokenHint(): String = "••••••••"
}
