package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for SSH profile validation/normalisation. */
class SshProfileRulesTest {

    @Test
    fun normalizeHost_trimsWhitespace() {
        assertEquals("vps.example.com", SshProfileRules.normalizeHost("  vps.example.com "))
    }

    @Test
    fun validHost_acceptsHostnameAndIp() {
        assertTrue(SshProfileRules.isValidHost("vps.example.com"))
        assertTrue(SshProfileRules.isValidHost("192.168.1.10"))
        assertTrue(SshProfileRules.isValidHost("::1"))
    }

    @Test
    fun invalidHost_rejectsBlankAndWhitespace() {
        assertFalse(SshProfileRules.isValidHost(""))
        assertFalse(SshProfileRules.isValidHost("   "))
        assertFalse(SshProfileRules.isValidHost("bad host"))
    }

    @Test
    fun parsePort_parsesAndRejectsGarbage() {
        assertEquals(22, SshProfileRules.parsePort("22"))
        assertEquals(2222, SshProfileRules.parsePort(" 2222 "))
        assertNull(SshProfileRules.parsePort("abc"))
        assertNull(SshProfileRules.parsePort(""))
    }

    @Test
    fun validPort_enforcesRange() {
        assertTrue(SshProfileRules.isValidPort(1))
        assertTrue(SshProfileRules.isValidPort(65535))
        assertFalse(SshProfileRules.isValidPort(0))
        assertFalse(SshProfileRules.isValidPort(65536))
        assertFalse(SshProfileRules.isValidPort(-1))
    }

    @Test
    fun username_rules() {
        assertTrue(SshProfileRules.isValidUsername("root"))
        assertTrue(SshProfileRules.isValidUsername("deploy-user"))
        assertFalse(SshProfileRules.isValidUsername(""))
        assertFalse(SshProfileRules.isValidUsername("a b"))
        assertFalse(SshProfileRules.isValidUsername("a:b"))
        assertFalse(SshProfileRules.isValidUsername("a@b"))
    }

    @Test
    fun privateKey_acceptsOpenSshAndPem() {
        val openSsh = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"
        val rsa = "-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----"
        val pkcs8 = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----"
        assertTrue(SshProfileRules.looksLikePrivateKey(openSsh))
        assertTrue(SshProfileRules.looksLikePrivateKey(rsa))
        assertTrue(SshProfileRules.looksLikePrivateKey(pkcs8))
    }

    @Test
    fun privateKey_rejectsNonKeyText() {
        assertFalse(SshProfileRules.looksLikePrivateKey(""))
        assertFalse(SshProfileRules.looksLikePrivateKey("ssh-rsa AAAA... user@host"))
        assertFalse(SshProfileRules.looksLikePrivateKey("-----BEGIN OPENSSH PRIVATE KEY-----"))
    }
}
