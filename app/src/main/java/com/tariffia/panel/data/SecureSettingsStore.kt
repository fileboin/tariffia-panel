package com.tariffia.panel.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the router URL in plain SharedPreferences and the router token encrypted
 * with an AES/GCM key held in the Android Keystore.
 *
 * Security properties:
 * - The token is never written in plain text and is never logged.
 * - A 256-bit AES key is created in the hardware-backed Android Keystore on first
 *   use; the Keystore key is not part of app data and cannot be backed up.
 * - GCM uses a fresh random IV for every encryption (platform default), and the
 *   IV is stored alongside the ciphertext.
 * - If the Keystore key becomes unreadable (e.g. the user resets the lock screen),
 *   the stale ciphertext is dropped so the user is asked for a new token.
 *
 * This uses the platform Keystore directly, so no extra security dependency is
 * required. [readToken] is intentionally the only way to obtain the plaintext and
 * must never be rendered in the UI or written to logs.
 */
class SecureSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
            prefs.edit().putString(KEY_TOKEN, encrypt(token)).apply()
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /**
     * Returns the decrypted token, or null when none is stored. For future use by
     * the router HTTP client; callers must never display or log the result.
     */
    fun readToken(): String? {
        val encoded = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            decrypt(encoded)
        } catch (ignored: Exception) {
            // Key invalidated or data corrupt: drop it and force re-entry.
            clearToken()
            null
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH) { "Malformed ciphertext" }
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val cipherText = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "tariffia_secure_settings"
        const val KEY_ROUTER_URL = "router_url"
        const val KEY_TOKEN = "router_token_enc"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tariffia_panel_router_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
