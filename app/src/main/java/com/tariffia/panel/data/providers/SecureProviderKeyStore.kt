package com.tariffia.panel.data.providers

import android.content.Context
import android.content.SharedPreferences
import com.tariffia.panel.data.KeystoreCrypto
import com.tariffia.panel.data.SecretCipher

/** Minimal key/value backend so the store's logic can be tested off-device. */
internal interface ProviderKeyBackend {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun contains(key: String): Boolean
}

/**
 * Stores provider API keys locally, one encrypted entry per provider ID, using the
 * Android Keystore via [KeystoreCrypto].
 *
 * Security properties:
 * - Keys are never written in plain text, never logged, and never returned to the UI.
 * - Each key is namespaced by provider ID, so an OpenAI key cannot be read as a
 *   Together key (see [ProviderKeyRules.storageKey]).
 * - [hasKey] only checks presence; it never decrypts. [readKey] is for a future
 *   transport PR and must not be displayed.
 * - This store does NOT send anything anywhere; keys stay on the device.
 */
class SecureProviderKeyStore internal constructor(
    private val backend: ProviderKeyBackend,
    private val cipher: SecretCipher,
) {

    constructor(context: Context) : this(
        SharedPrefsProviderKeyBackend(context.applicationContext),
        KeystoreCrypto(KEY_ALIAS),
    )

    /** True when a key is stored for this provider. Never decrypts. */
    fun hasKey(providerId: String): Boolean {
        val id = ProviderKeyRules.normalizeProviderId(providerId)
        if (!ProviderKeyRules.isValidProviderId(id)) return false
        return backend.contains(ProviderKeyRules.storageKey(id))
    }

    /**
     * Stores a key for exactly one provider. Blank keys and invalid provider IDs are
     * rejected and nothing is written. Returns true on success.
     */
    fun saveKey(providerId: String, apiKey: String): Boolean {
        val id = ProviderKeyRules.normalizeProviderId(providerId)
        if (!ProviderKeyRules.isValidProviderId(id)) return false
        if (!ProviderKeyRules.isValidKey(apiKey)) return false
        val encrypted = cipher.encrypt(ProviderKeyRules.normalizeKey(apiKey))
        backend.put(ProviderKeyRules.storageKey(id), encrypted)
        return true
    }

    /**
     * The decrypted key for a provider, or null. For a future transport PR only; must
     * never be rendered in the UI or written to logs. Undecryptable entries are dropped.
     */
    fun readKey(providerId: String): String? {
        val id = ProviderKeyRules.normalizeProviderId(providerId)
        if (!ProviderKeyRules.isValidProviderId(id)) return null
        val storageKey = ProviderKeyRules.storageKey(id)
        val stored = backend.get(storageKey) ?: return null
        return try {
            cipher.decrypt(stored)
        } catch (ignored: Exception) {
            backend.remove(storageKey)
            null
        }
    }

    /** Removes the stored key for one provider. No-op for an invalid ID. */
    fun clearKey(providerId: String) {
        val id = ProviderKeyRules.normalizeProviderId(providerId)
        if (!ProviderKeyRules.isValidProviderId(id)) return
        backend.remove(ProviderKeyRules.storageKey(id))
    }

    private companion object {
        const val KEY_ALIAS = "tariffia_panel_provider_keys"
    }
}

private class SharedPrefsProviderKeyBackend(context: Context) : ProviderKeyBackend {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    private companion object {
        const val PREFS_NAME = "tariffia_provider_keys"
    }
}
