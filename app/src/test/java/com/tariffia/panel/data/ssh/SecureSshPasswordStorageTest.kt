package com.tariffia.panel.data.ssh

import com.tariffia.panel.data.SecretCipher
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            hasCiphertext = { prefs.containsKey("password_enc") },
        )
        val password = "ssh-password-sentinel"

        storage.save(password)

        val storedValue = prefs.getValue("password_enc")
        assertFalse("plaintext password must not be stored", storedValue.contains(password))
        assertEquals(password, storage.read())
    }

    @Test
    fun savedPasswordPresenceSurvivesStoreRecreation() {
        val prefs = mutableMapOf<String, String>()
        val cipher = TestCipher()
        storage(prefs, cipher).save("saved-password")

        val reopenedStore = storage(prefs, cipher)
        assertTrue(reopenedStore.hasPassword())
        assertEquals("saved-password", reopenedStore.read())
    }

    @Test
    fun blankSavePreservesPreviouslySavedPassword() {
        val prefs = mutableMapOf<String, String>()
        val storage = storage(prefs, TestCipher())
        storage.save("saved-password")
        val ciphertext = prefs.getValue("password_enc")

        storage.save("")

        assertEquals(ciphertext, prefs["password_enc"])
        assertEquals("saved-password", storage.read())
    }

    @Test
    fun missingOrInvalidCiphertextRestoresNothingAndInvalidValueIsRemoved() {
        val prefs = mutableMapOf("password_enc" to "not-ciphertext")
        val storage = EncryptedSshPasswordStorage(
            cipher = TestCipher(),
            readCiphertext = { prefs["password_enc"] },
            writeCiphertext = { prefs["password_enc"] = it },
            removeCiphertext = { prefs.remove("password_enc") },
            hasCiphertext = { prefs.containsKey("password_enc") },
        )

        assertNull(storage.read())
        assertFalse(prefs.containsKey("password_enc"))
        assertNull(storage.read())
    }

    private fun storage(prefs: MutableMap<String, String>, cipher: SecretCipher) =
        EncryptedSshPasswordStorage(
            cipher = cipher,
            readCiphertext = { prefs["password_enc"] },
            writeCiphertext = { prefs["password_enc"] = it },
            removeCiphertext = { prefs.remove("password_enc") },
            hasCiphertext = { prefs.containsKey("password_enc") },
        )
}
