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
    fun reusesStoredToken() {
        val resolved = RouterLocalConfig.resolve("stored-token") { "generated" }
        assertEquals(RouterLocalConfig.LOCAL_URL, resolved.url)
        assertEquals("stored-token", resolved.token)
        assertFalse(resolved.tokenWasGenerated)
    }

    @Test
    fun generatesWhenStoredTokenIsNull() {
        val resolved = RouterLocalConfig.resolve(null) { "generated" }
        assertEquals("generated", resolved.token)
        assertTrue(resolved.tokenWasGenerated)
    }

    @Test
    fun generatesWhenStoredTokenIsBlank() {
        val resolved = RouterLocalConfig.resolve("   ") { "generated" }
        assertEquals("generated", resolved.token)
        assertTrue(resolved.tokenWasGenerated)
    }
}
