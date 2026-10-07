package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the JSch PreferredAuthentications selection. */
class SshAuthConfigTest {

    @Test
    fun keyModeUsesPublicKeyOnly() {
        assertEquals("publickey", SshAuthConfig.preferredAuthentications(SshAuthMethod.KEY))
    }

    @Test
    fun passwordModeAlsoOffersKeyboardInteractive() {
        // Many PAM/OpenSSH servers advertise only keyboard-interactive for passwords.
        assertEquals(
            "password,keyboard-interactive",
            SshAuthConfig.preferredAuthentications(SshAuthMethod.PASSWORD),
        )
    }
}
