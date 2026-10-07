package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the in-memory-only SSH session password holder. */
class SshSessionSecretsTest {

    @Test
    fun setPasswordStoresInMemory() {
        SshSessionSecrets.clear()
        SshSessionSecrets.setPassword("s3cret-value")
        assertTrue(SshSessionSecrets.hasPassword())
        assertEquals("s3cret-value", SshSessionSecrets.password())
        SshSessionSecrets.clear()
    }

    @Test
    fun clearRemovesThePassword() {
        SshSessionSecrets.setPassword("s3cret-value")
        SshSessionSecrets.clear()
        assertFalse(SshSessionSecrets.hasPassword())
        assertNull(SshSessionSecrets.password())
    }

    @Test
    fun blankOrNullClearsThePassword() {
        SshSessionSecrets.setPassword("s3cret-value")
        SshSessionSecrets.setPassword("")
        assertFalse(SshSessionSecrets.hasPassword())
        SshSessionSecrets.setPassword("again")
        SshSessionSecrets.setPassword(null)
        assertFalse(SshSessionSecrets.hasPassword())
    }

    @Test
    fun overwriteReplacesThePassword() {
        SshSessionSecrets.setPassword("first")
        SshSessionSecrets.setPassword("second")
        assertEquals("second", SshSessionSecrets.password())
        SshSessionSecrets.clear()
    }

    @Test
    fun startsEmpty() {
        SshSessionSecrets.clear()
        assertFalse(SshSessionSecrets.hasPassword())
        assertNull(SshSessionSecrets.password())
    }
}
