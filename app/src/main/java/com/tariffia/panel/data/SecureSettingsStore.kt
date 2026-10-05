package com.tariffia.panel.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the router URL in plain SharedPreferences and the router token encrypted
 * via [KeystoreCrypto] (AES/GCM key in the Android Keystore).
 *
 * Security properties:
 * - The token is never written in plain text and is never logged.
 * - If the Keystore key becomes unreadable the stale ciphertext is dropped so the
 *   user is asked for a new token.
 * - [readToken] is the only way to obtain the plaintext and must never be rendered
 *   in the UI or written to logs.
 */
class SecureSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto(KEY_ALIAS)

    /** Loads non-secret config; only reports whether a token exists. */
    fun load(): RouterSettings = RouterSettings(
        routerUrl = prefs.getString(KEY_ROUTER_URL, "").orEmpty(),
        hasToken = prefs.contains(KEY_TOKEN),
    )

    /**
     * Persists the URL and, when [token] is non-blank, replaces the stored token.
     * A blank [token] leaves any existing token untouched.
     */
    fun save(routerUrl: String, token: String?) {
        prefs.edit()
            .putString(KEY_ROUTER_URL, SettingsRules.normalizeUrl(routerUrl))
            .apply()
        if (!token.isNullOrBlank()) {
            prefs.edit().putString(KEY_TOKEN, crypto.encrypt(token)).apply()
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /**
     * Returns the decrypted token, or null when none is stored. For the router HTTP
     * client; callers must never display or log the result.
     */
    fun readToken(): String? {
        val encoded = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            crypto.decrypt(encoded)
        } catch (ignored: Exception) {
            clearToken()
            null
        }
    }

    private companion object {
        const val PREFS_NAME = "tariffia_secure_settings"
        const val KEY_ROUTER_URL = "router_url"
        const val KEY_TOKEN = "router_token_enc"
        const val KEY_ALIAS = "tariffia_panel_router_token"
    }
}
