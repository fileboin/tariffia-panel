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
 * - The private key, the passphrase, the SSH password and the pinned host keys are encrypted with
 *   [KeystoreCrypto] (AES/GCM key in the Android Keystore). Nothing secret is ever
 *   written in plain text or logged.
 * - The private key is never read back into the UI; [readPrivateKey] is only for the
 *   SSH transport and must not be displayed.
 */
class SecureSshProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto(KEY_ALIAS)
    private val passwordStorage = EncryptedSshPasswordStorage(
        cipher = crypto,
        readCiphertext = { prefs.getString(KEY_PASSWORD, null) },
        writeCiphertext = { prefs.edit().putString(KEY_PASSWORD, it).apply() },
        removeCiphertext = { prefs.edit().remove(KEY_PASSWORD).apply() },
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val pinsSerializer = ListSerializer(HostKeyPin.serializer())

    fun loadProfile(): SshProfile = SshProfile(
        host = prefs.getString(KEY_HOST, "").orEmpty(),
        port = prefs.getInt(KEY_PORT, SshProfile.DEFAULT_PORT),
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        authMethod = readAuthMethod(),
    )

    /** The selected auth method is non-secret. */
    private fun readAuthMethod(): SshAuthMethod =
        runCatching { SshAuthMethod.valueOf(prefs.getString(KEY_AUTH_METHOD, null).orEmpty()) }
            .getOrDefault(SshAuthMethod.KEY)

    fun hasPrivateKey(): Boolean = prefs.contains(KEY_PRIVATE_KEY)

    fun hasPassphrase(): Boolean = prefs.contains(KEY_PASSPHRASE)

    /**
     * Saves the profile. Non-blank key/passphrase/password values replace their encrypted
     * values; blank values leave them untouched. Switching away from PASSWORD clears its
     * encrypted password.
     */
    fun saveProfile(
        profile: SshProfile,
        privateKeyPem: String?,
        passphrase: String?,
        password: String? = null,
    ) {
        prefs.edit()
            .putString(KEY_HOST, SshProfileRules.normalizeHost(profile.host))
            .putInt(KEY_PORT, profile.port)
            .putString(KEY_USERNAME, profile.username.trim())
            .putString(KEY_AUTH_METHOD, profile.authMethod.name)
            .apply()
        if (!privateKeyPem.isNullOrBlank()) {
            prefs.edit().putString(KEY_PRIVATE_KEY, crypto.encrypt(privateKeyPem)).apply()
        }
        if (!passphrase.isNullOrEmpty()) {
            prefs.edit().putString(KEY_PASSPHRASE, crypto.encrypt(passphrase)).apply()
        }
        if (profile.authMethod == SshAuthMethod.PASSWORD) {
            passwordStorage.save(password)
        } else {
            passwordStorage.clear()
        }
    }

    fun readPrivateKey(): String? = readEncrypted(KEY_PRIVATE_KEY)

    fun readPassphrase(): String? = readEncrypted(KEY_PASSPHRASE)

    /** Decrypts the session password for SSH authentication; never expose or log the result. */
    fun readPassword(): String? = passwordStorage.read()

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
        const val KEY_AUTH_METHOD = "auth_method"
        const val KEY_PRIVATE_KEY = "private_key_enc"
        const val KEY_PASSPHRASE = "passphrase_enc"
        const val KEY_PASSWORD = "password_enc"
        const val KEY_PINS = "host_key_pins_enc"
    }
}

/** Small injectable seam so encrypted SSH password persistence can be JVM-tested. */
internal class EncryptedSshPasswordStorage(
    private val cipher: com.tariffia.panel.data.SecretCipher,
    private val readCiphertext: () -> String?,
    private val writeCiphertext: (String) -> Unit,
    private val removeCiphertext: () -> Unit,
) {
    fun save(password: String?) {
        if (!password.isNullOrEmpty()) writeCiphertext(cipher.encrypt(password))
    }

    fun read(): String? {
        val ciphertext = readCiphertext() ?: return null
        return try {
            cipher.decrypt(ciphertext)
        } catch (ignored: Exception) {
            removeCiphertext()
            null
        }
    }

    fun clear() = removeCiphertext()
}
