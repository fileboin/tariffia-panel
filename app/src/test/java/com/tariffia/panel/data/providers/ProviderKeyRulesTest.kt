package com.tariffia.panel.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for provider-key naming, ID validation and blank-key rejection. */
class ProviderKeyRulesTest {

    @Test
    fun normalizeProviderId_trimsAndLowercases() {
        assertEquals("openai", ProviderKeyRules.normalizeProviderId("  OpenAI "))
    }

    @Test
    fun validProviderIds() {
        assertTrue(ProviderKeyRules.isValidProviderId("openai"))
        assertTrue(ProviderKeyRules.isValidProviderId("token-router"))
        assertTrue(ProviderKeyRules.isValidProviderId("z.ai"))
        assertTrue(ProviderKeyRules.isValidProviderId("provider_1"))
    }

    @Test
    fun invalidProviderIds() {
        assertFalse(ProviderKeyRules.isValidProviderId(""))
        assertFalse(ProviderKeyRules.isValidProviderId("   "))
        assertFalse(ProviderKeyRules.isValidProviderId("bad id"))
        assertFalse(ProviderKeyRules.isValidProviderId("a/b"))
        assertFalse(ProviderKeyRules.isValidProviderId("-leading"))
        assertFalse(ProviderKeyRules.isValidProviderId("x".repeat(65)))
    }

    @Test
    fun storageKey_isNamespacedPerProvider() {
        assertEquals("provider_key_enc_openai", ProviderKeyRules.storageKey("OpenAI"))
        assertNotEquals(ProviderKeyRules.storageKey("openai"), ProviderKeyRules.storageKey("together"))
    }

    @Test
    fun storageKey_rejectsInvalidProviderId() {
        var threw = false
        try {
            ProviderKeyRules.storageKey("bad id")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun blankKeysAreRejected() {
        assertFalse(ProviderKeyRules.isValidKey(""))
        assertFalse(ProviderKeyRules.isValidKey("   "))
        assertFalse(ProviderKeyRules.isValidKey("\n\t"))
    }

    @Test
    fun nonBlankKeysAreAccepted() {
        assertTrue(ProviderKeyRules.isValidKey("dummy-key-value"))
        assertTrue(ProviderKeyRules.isValidKey("  padded  "))
    }

    @Test
    fun normalizeKey_trimsSurroundingWhitespace() {
        assertEquals("dummy-key", ProviderKeyRules.normalizeKey("  dummy-key \n"))
    }
}
