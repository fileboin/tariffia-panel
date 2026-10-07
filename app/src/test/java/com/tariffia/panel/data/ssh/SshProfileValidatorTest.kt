package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for Save/Test profile validation (host, username, port, method secret). */
class SshProfileValidatorTest {

    private fun validate(
        host: String = "vps.example.com",
        port: String = "22",
        username: String = "root",
        authMethod: SshAuthMethod = SshAuthMethod.KEY,
        secretAvailable: Boolean = true,
    ): SshProfileValidation =
        SshProfileValidator.validate(host, port, username, authMethod, secretAvailable)

    @Test
    fun validProfile_returnsTrimmedValues() {
        val result = validate(host = "  vps.example.com ", port = " 2222 ", username = " deploy ")
        assertTrue(result is SshProfileValidation.Valid)
        val profile = (result as SshProfileValidation.Valid).profile
        assertEquals("vps.example.com", profile.host)
        assertEquals(2222, profile.port)
        assertEquals("deploy", profile.username)
        assertEquals(SshAuthMethod.KEY, profile.authMethod)
    }

    @Test
    fun blankHost_isInvalid() {
        assertTrue(validate(host = "") is SshProfileValidation.Invalid)
        assertTrue(validate(host = "   ") is SshProfileValidation.Invalid)
    }

    @Test
    fun hostWithWhitespace_isInvalid() {
        assertTrue(validate(host = "bad host") is SshProfileValidation.Invalid)
    }

    @Test
    fun blankUsername_isInvalid() {
        assertTrue(validate(username = "") is SshProfileValidation.Invalid)
        assertTrue(validate(username = "   ") is SshProfileValidation.Invalid)
    }

    @Test
    fun usernameWithSeparators_isInvalid() {
        assertTrue(validate(username = "a:b") is SshProfileValidation.Invalid)
        assertTrue(validate(username = "a@b") is SshProfileValidation.Invalid)
    }

    @Test
    fun portBoundaries_areValid() {
        assertEquals(1, (validate(port = "1") as SshProfileValidation.Valid).profile.port)
        assertEquals(65535, (validate(port = "65535") as SshProfileValidation.Valid).profile.port)
    }

    @Test
    fun portOutOfRangeOrNonNumeric_isInvalid() {
        assertTrue(validate(port = "0") is SshProfileValidation.Invalid)
        assertTrue(validate(port = "65536") is SshProfileValidation.Invalid)
        assertTrue(validate(port = "-1") is SshProfileValidation.Invalid)
        assertTrue(validate(port = "abc") is SshProfileValidation.Invalid)
        assertTrue(validate(port = "") is SshProfileValidation.Invalid)
    }

    /* ------------------------------ key method ------------------------------ */

    @Test
    fun keyModeWithoutKey_isInvalid() {
        val result = validate(authMethod = SshAuthMethod.KEY, secretAvailable = false)
        assertTrue(result is SshProfileValidation.Invalid)
        assertEquals("Paste an SSH private key.", (result as SshProfileValidation.Invalid).message)
    }

    @Test
    fun keyModeWithKey_isValid() {
        val result = validate(authMethod = SshAuthMethod.KEY, secretAvailable = true)
        assertTrue(result is SshProfileValidation.Valid)
        assertEquals(SshAuthMethod.KEY, (result as SshProfileValidation.Valid).profile.authMethod)
    }

    /* ---------------------------- password method --------------------------- */

    @Test
    fun passwordModeWithoutSecret_isInvalidWithClearMessage() {
        val result = validate(authMethod = SshAuthMethod.PASSWORD, secretAvailable = false)
        assertTrue(result is SshProfileValidation.Invalid)
        assertEquals("Enter the SSH password.", (result as SshProfileValidation.Invalid).message)
    }

    @Test
    fun passwordModeWithSecret_isValid() {
        val result = validate(authMethod = SshAuthMethod.PASSWORD, secretAvailable = true)
        assertTrue(result is SshProfileValidation.Valid)
        assertEquals(SshAuthMethod.PASSWORD, (result as SshProfileValidation.Valid).profile.authMethod)
    }

    @Test
    fun passwordModeStillValidatesHostPortUsername() {
        assertTrue(validate(host = "", authMethod = SshAuthMethod.PASSWORD) is SshProfileValidation.Invalid)
        assertTrue(validate(port = "0", authMethod = SshAuthMethod.PASSWORD) is SshProfileValidation.Invalid)
        assertTrue(validate(username = "", authMethod = SshAuthMethod.PASSWORD) is SshProfileValidation.Invalid)
    }

    @Test
    fun validationMessagesNeverContainASecret() {
        // The validator never receives the password/key; messages are fixed strings.
        val sentinel = "super-secret-sentinel-value"
        val results = listOf(
            validate(authMethod = SshAuthMethod.PASSWORD, secretAvailable = false),
            validate(authMethod = SshAuthMethod.KEY, secretAvailable = false),
        )
        results.forEach { assertFalse(it.toString().contains(sentinel)) }
    }
}
