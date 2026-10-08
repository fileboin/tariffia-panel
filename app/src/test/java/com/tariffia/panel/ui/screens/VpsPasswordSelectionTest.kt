package com.tariffia.panel.ui.screens

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpsPasswordSelectionTest {

    @Test
    fun testConnectionUsesEnteredPasswordFirst() = runBlocking {
        var readStored = false
        val selected = passwordForConnection("typed-password", hasStoredPassword = true) {
            readStored = true
            "stored-password"
        }

        assertEquals("typed-password", selected)
        assertEquals(false, readStored)
    }

    @Test
    fun testConnectionRestoresStoredPasswordWhenFieldIsBlank() = runBlocking {
        val selected = passwordForConnection("", hasStoredPassword = true) { "stored-password" }
        assertEquals("stored-password", selected)
    }

    @Test
    fun blankFieldWithoutSavedPasswordRemainsMissing() = runBlocking {
        val selected = passwordForConnection("", hasStoredPassword = false) {
            throw AssertionError("must not read storage when presence flag is false")
        }
        assertNull(selected)
    }
}
