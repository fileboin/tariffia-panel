package com.tariffia.panel.data.providers

import com.tariffia.panel.data.SecretCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the provider key store using a fake backend and cipher (the real
 * Android Keystore is device-only). Covers save/has/read/clear, per-provider
 * isolation, blank/invalid rejection and undecryptable entries.
 */
class SecureProviderKeyStoreTest {

    private class FakeBackend : ProviderKeyBackend {
        val entries = mutableMapOf<String, String>()
        override fun put(key: String, value: String) { entries[key] = value }
        override fun get(key: String): String? = entries[key]
        override fun remove(key: String) { entries.remove(key) }
        override fun contains(key: String): Boolean = entries.containsKey(key)
    }

    private class FakeCipher : SecretCipher {
        override fun encrypt(plain: String): String = "enc($plain)"
        override fun decrypt(encoded: String): String {
            require(encoded.startsWith("enc(") && encoded.endsWith(")")) { "not ciphertext" }
            return encoded.substring(4, encoded.length - 1)
        }
    }

    private val backend = FakeBackend()
    private val store = SecureProviderKeyStore(backend, FakeCipher())

    @Test
    fun saveThenHasAndRead() {
        assertTrue(store.saveKey("openai", "dummy-openai-key"))
        assertTrue(store.hasKey("openai"))
        assertEquals("dummy-openai-key", store.readKey("openai"))
    }

    @Test
    fun blankKeyIsRejectedAndNotStored() {
        assertFalse(store.saveKey("openai", "   "))
        assertFalse(store.hasKey("openai"))
        assertTrue(backend.entries.isEmpty())
    }

    @Test
    fun invalidProviderIdIsRejectedAndNotStored() {
        assertFalse(store.saveKey("bad id", "dummy-key"))
        assertTrue(backend.entries.isEmpty())
    }

    @Test
    fun keysAreIsolatedPerProvider() {
        store.saveKey("openai", "openai-secret")
        store.saveKey("together", "together-secret")

        assertEquals("openai-secret", store.readKey("openai"))
        assertEquals("together-secret", store.readKey("together"))

        store.clearKey("openai")
        assertFalse(store.hasKey("openai"))
        assertTrue(store.hasKey("together"))
        assertEquals("together-secret", store.readKey("together"))
    }

    @Test
    fun providerIdIsCaseInsensitive() {
        store.saveKey("OpenAI", "dummy-key")
        assertTrue(store.hasKey("openai"))
        assertEquals("dummy-key", store.readKey("OPENAI"))
    }

    @Test
    fun clearRemovesKey() {
        store.saveKey("openai", "dummy-key")
        store.clearKey("openai")
        assertFalse(store.hasKey("openai"))
        assertNull(store.readKey("openai"))
    }

    @Test
    fun hasKeyDoesNotDecrypt() {
        backend.put(ProviderKeyRules.storageKey("openai"), "not-valid-ciphertext")
        assertTrue(store.hasKey("openai"))
    }

    @Test
    fun undecryptableEntryIsDroppedOnRead() {
        backend.put(ProviderKeyRules.storageKey("openai"), "not-valid-ciphertext")
        assertNull(store.readKey("openai"))
        assertFalse(backend.contains(ProviderKeyRules.storageKey("openai")))
    }

    @Test
    fun readMissingKeyIsNull() {
        assertNull(store.readKey("openai"))
    }
}
