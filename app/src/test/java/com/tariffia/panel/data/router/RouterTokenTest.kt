package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTokenTest {

    @Test
    fun generatesLowercaseHexOf64Chars() {
        val token = RouterToken.generate()
        assertEquals(64, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun generatesDistinctTokens() {
        assertNotEquals(RouterToken.generate(), RouterToken.generate())
    }
}
