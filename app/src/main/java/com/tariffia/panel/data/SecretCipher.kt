package com.tariffia.panel.data

/**
 * Symmetric cipher for secrets. Implemented on device by [KeystoreCrypto]; a fake
 * implementation lets the storage logic be unit tested off-device.
 */
internal interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String
}
