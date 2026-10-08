package com.tariffia.panel.data.ssh

import com.tariffia.panel.data.SecretCipher
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for the password storage helper used by SecureSshProfileStore. */
class SecureSshPasswordStorageTest {

    private class TestCipher : SecretCipher {
        override fun encrypt(plain: String): String =
            "ciphertext:" + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))

        override fun decrypt(encoded: String): String {
            require(encoded.startsWith("ciphertext:")) { "invalid test ciphertext" }
            val bytes = Base64.getDecoder().decode(encoded.removePrefix("ciphertext:"))
            return String(bytes, Charsets.UTF_8)
        }
    }

    @Test
    fun saveStoresCiphertextAndRestoreReturnsPassword() {
        val prefs = mutableMapOf<String, String>()
        val storage = EncryptedSshPasswordStorage(
            cipher = TestCipher(),
            readCiphertext = { prefs["password_enc"] },
            writeCiphertext = { prefs["password_enc"] = it },
            removeCiphertext = { prefs.remove("password_enc") },
        )
        val password = "ssh-password-sentinel"

        storage.save(password)

        val storedValue = prefs.getValue("password_enc")
        assertFalse("plaintext password must not be stored", storedValue.contains(password))
        assertEquals(password, storage.read())
    }

    @Test
    fun missingOrInvalidCiphertextRestoresNothingAndInvalidValueIsRemoved() {
        val prefs = mutableMapOf("password_enc" to "not-ciphertext")
        val storage = EncryptedSshPasswordStorage(
            cipher = TestCipher(),
            readCiphertext = { prefs["password_enc"] },
            writeCiphertext = { prefs["password_enc"] = it },
            removeCiphertext = { prefs.remove("password_enc") },
        )

        assertNull(storage.read())
        assertFalse(prefs.containsKey("password_enc"))
        assertNull(storage.read())
    }
}
