package com.tariffia.panel.data.providers

/**
 * Pure rules for per-provider API-key storage: provider-ID validation/normalisation
 * and the SharedPreferences key each provider's encrypted key lives under. Kept free
 * of Android APIs so it can be unit tested on the JVM.
 *
 * Provider IDs are lowercased so the same provider always maps to one storage slot.
 */
object ProviderKeyRules {

    /** Lowercase, hyphen/underscore/dot and alphanumerics; must start alphanumeric. */
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")

    fun normalizeProviderId(raw: String): String = raw.trim().lowercase()

    fun isValidProviderId(raw: String): Boolean = ID_PATTERN.matches(normalizeProviderId(raw))

    /** A usable key is non-blank; surrounding whitespace is trimmed before storing. */
    fun normalizeKey(raw: String): String = raw.trim()

    fun isValidKey(raw: String): Boolean = normalizeKey(raw).isNotEmpty()

    /**
     * The SharedPreferences key for one provider's encrypted API key. Namespaced by
     * provider ID so one provider's key can never be read as another's.
     *
     * @throws IllegalArgumentException when [providerId] is not valid.
     */
    fun storageKey(providerId: String): String {
        val id = normalizeProviderId(providerId)
        require(isValidProviderId(id)) { "invalid provider id" }
        return "$STORAGE_PREFIX$id"
    }

    private const val STORAGE_PREFIX = "provider_key_enc_"
}
