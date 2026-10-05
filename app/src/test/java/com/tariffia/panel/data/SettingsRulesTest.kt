package com.tariffia.panel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure settings rules used by the secure store and the
 * Settings screen. The Keystore-backed encryption itself requires an Android
 * runtime and is exercised by the instrumented build, not here.
 */
class SettingsRulesTest {

    @Test
    fun normalizeUrl_trimsWhitespaceAndSingleTrailingSlash() {
        assertEquals(
            "https://router.example.com",
            SettingsRules.normalizeUrl("  https://router.example.com/  "),
        )
    }

    @Test
    fun normalizeUrl_keepsBareSlash() {
        assertEquals("/", SettingsRules.normalizeUrl("/"))
    }

    @Test
    fun normalizeUrl_doesNotStripPathSlash() {
        assertEquals("https://host/api/v1", SettingsRules.normalizeUrl("https://host/api/v1/"))
    }

    @Test
    fun isValidUrl_acceptsHttpAndHttpsWithHost() {
        assertTrue(SettingsRules.isValidUrl("https://router.example.com"))
        assertTrue(SettingsRules.isValidUrl("http://127.0.0.1:8910"))
    }

    @Test
    fun isValidUrl_rejectsBlankNonHttpAndHostless() {
        assertFalse(SettingsRules.isValidUrl(""))
        assertFalse(SettingsRules.isValidUrl("   "))
        assertFalse(SettingsRules.isValidUrl("router.example.com"))
        assertFalse(SettingsRules.isValidUrl("ftp://router.example.com"))
        assertFalse(SettingsRules.isValidUrl("https://"))
    }

    @Test
    fun maskedTokenHint_isFixedWidthAndDoesNotReflectInput() {
        assertEquals(8, SettingsRules.maskedTokenHint().length)
    }
}
