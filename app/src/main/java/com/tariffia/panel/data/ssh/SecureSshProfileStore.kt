package com.tariffia.panel.data.ssh

import android.content.Context
import android.content.SharedPreferences
import com.tariffia.panel.data.KeystoreCrypto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists the SSH profile.
 *
 * - Host/port/username are non-secret and stored in plain SharedPreferences.
 * - The private key, the passphrase and the pinned host keys are encrypted with
 *   [KeystoreCrypto] (AES/GCM key in the Android Keystore). Nothing secret is ever
 *   written in plain text or logged.
 * - The private key is never read back into the UI; [readPrivateKey] is only for the
 *   SSH transport and must not be displayed.
 */
class SecureSshProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto(KEY_ALIAS)
    private val json = Json { ignoreUnknownKeys = true }
    private val pinsSerializer = ListSerializer(HostKeyPin.serializer())

    fun loadProfile(): SshProfile = SshProfile(
        host = prefs.getString(KEY_HOST, "").orEmpty(),
        port = prefs.getInt(KEY_PORT, SshProfile.DEFAULT_PORT),
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
    )

    fun hasPrivateKey(): Boolean = prefs.contains(KEY_PRIVATE_KEY)

    fun hasPassphrase(): Boolean = prefs.contains(KEY_PASSPHRASE)

    /**
     * Saves the profile. A non-blank [privateKeyPem] replaces the stored key; a blank
     * one leaves it untouched. The same applies to [passphrase].
     */
    fun saveProfile(profile: SshProfile, privateKeyPem: String?, passphrase: String?) {
        prefs.edit()
            .putString(KEY_HOST, SshProfileRules.normalizeHost(profile.host))
            .putInt(KEY_PORT, profile.port)
            .putString(KEY_USERNAME, profile.username.trim())
            .apply()
        if (!privateKeyPem.isNullOrBlank()) {
            prefs.edit().putString(KEY_PRIVATE_KEY, crypto.encrypt(privateKeyPem)).apply()
        }
        if (!passphrase.isNullOrEmpty()) {
            prefs.edit().putString(KEY_PASSPHRASE, crypto.encrypt(passphrase)).apply()
        }
    }

    fun readPrivateKey(): String? = readEncrypted(KEY_PRIVATE_KEY)

    fun readPassphrase(): String? = readEncrypted(KEY_PASSPHRASE)

    fun getPin(host: String, port: Int): HostKeyPin? =
        readPins().firstOrNull { it.host == host && it.port == port }

    fun savePin(pin: HostKeyPin) {
        val updated = readPins().filterNot { it.host == pin.host && it.port == pin.port } + pin
        writePins(updated)
    }

    fun clearPin(host: String, port: Int) {
        writePins(readPins().filterNot { it.host == host && it.port == port })
    }

    private fun readEncrypted(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            crypto.decrypt(stored)
        } catch (ignored: Exception) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun readPins(): List<HostKeyPin> {
        val stored = prefs.getString(KEY_PINS, null) ?: return emptyList()
        return try {
            json.decodeFromString(pinsSerializer, crypto.decrypt(stored))
        } catch (ignored: Exception) {
            prefs.edit().remove(KEY_PINS).apply()
            emptyList()
        }
    }

    private fun writePins(pins: List<HostKeyPin>) {
        prefs.edit().putString(KEY_PINS, crypto.encrypt(json.encodeToString(pinsSerializer, pins))).apply()
    }

    private companion object {
        const val PREFS_NAME = "tariffia_ssh_profile"
        const val KEY_ALIAS = "tariffia_panel_ssh_secrets"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_PRIVATE_KEY = "private_key_enc"
        const val KEY_PASSPHRASE = "passphrase_enc"
        const val KEY_PINS = "host_key_pins_enc"
    }
}
