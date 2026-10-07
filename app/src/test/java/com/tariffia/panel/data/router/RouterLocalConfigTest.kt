package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterLocalConfigTest {

    @Test
    fun localUrlIsLoopbackOn8910() {
        assertEquals("http://127.0.0.1:8910", RouterLocalConfig.LOCAL_URL)
    }

    @Test
    fun blankOrMissingUrlDefaultsToLoopback() {
        val missing = RouterLocalConfig.resolve(null, "stored-token") { "generated" }
        assertEquals(RouterLocalConfig.LOCAL_URL, missing.url)
        assertTrue(missing.urlWasDefaulted)

        val blank = RouterLocalConfig.resolve("   ", "stored-token") { "generated" }
        assertEquals(RouterLocalConfig.LOCAL_URL, blank.url)
        assertTrue(blank.urlWasDefaulted)
    }

    @Test
    fun configuredRemoteUrlIsPreservedNotOverwritten() {
        val resolved = RouterLocalConfig.resolve("https://router.example.com", "stored-token") { "generated" }
        assertEquals("https://router.example.com", resolved.url)
        assertFalse(resolved.urlWasDefaulted)
        assertEquals("stored-token", resolved.token)
        assertFalse(resolved.tokenWasGenerated)
    }

    @Test
    fun reusesStoredToken() {
        val resolved = RouterLocalConfig.resolve(null, "stored-token") { "generated" }
        assertEquals("stored-token", resolved.token)
        assertFalse(resolved.tokenWasGenerated)
    }

    @Test
    fun generatesWhenStoredTokenIsNull() {
        val resolved = RouterLocalConfig.resolve(null, null) { "generated" }
        assertEquals("generated", resolved.token)
        assertTrue(resolved.tokenWasGenerated)
    }

    @Test
    fun generatesWhenStoredTokenIsBlank() {
        val resolved = RouterLocalConfig.resolve(null, "   ") { "generated" }
        assertEquals("generated", resolved.token)
        assertTrue(resolved.tokenWasGenerated)
    }

    @Test
    fun remoteUrlAndStoredTokenAreBothKept() {
        val resolved = RouterLocalConfig.resolve("https://router.example.com", "stored-token") { "generated" }
        assertEquals("https://router.example.com", resolved.url)
        assertEquals("stored-token", resolved.token)
        assertFalse(resolved.urlWasDefaulted)
        assertFalse(resolved.tokenWasGenerated)
    }
}
