package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** JVM tests for host-key fingerprint parsing/formatting. */
class HostKeyFingerprintTest {

    private fun blob(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(4 + typeBytes.size + payload.size)
            .putInt(typeBytes.size)
            .put(typeBytes)
            .put(payload)
            .array()
    }

    @Test
    fun parseKeyType_readsLeadingString() {
        assertEquals("ssh-ed25519", HostKeyFingerprint.parseKeyType(blob("ssh-ed25519", ByteArray(32))))
        assertEquals("ssh-rsa", HostKeyFingerprint.parseKeyType(blob("ssh-rsa", ByteArray(8))))
    }

    @Test
    fun parseKeyType_handlesMalformedBlobs() {
        assertEquals("unknown", HostKeyFingerprint.parseKeyType(ByteArray(0)))
        assertEquals("unknown", HostKeyFingerprint.parseKeyType(ByteArray(2)))
        assertEquals("unknown", HostKeyFingerprint.parseKeyType(byteArrayOf(0, 0, 0, 99, 1, 2)))
    }

    @Test
    fun sha256_usesOpenSshFormat() {
        val fingerprint = HostKeyFingerprint.sha256(blob("ssh-ed25519", ByteArray(32)))
        assertTrue(fingerprint.startsWith("SHA256:"))
        assertFalse(fingerprint.substringAfter("SHA256:").contains("="))
    }

    @Test
    fun sha256_isDeterministicAndInputSensitive() {
        val a = blob("ssh-ed25519", ByteArray(32) { 1 })
        val b = blob("ssh-ed25519", ByteArray(32) { 2 })
        assertEquals(HostKeyFingerprint.sha256(a), HostKeyFingerprint.sha256(a))
        assertFalse(HostKeyFingerprint.sha256(a) == HostKeyFingerprint.sha256(b))
    }

    @Test
    fun identityOf_populatesAllFields() {
        val keyBlob = blob("ssh-ed25519", ByteArray(32) { 7 })
        val identity = HostKeyFingerprint.identityOf(keyBlob)
        assertEquals("ssh-ed25519", identity.keyType)
        assertEquals(HostKeyFingerprint.sha256(keyBlob), identity.fingerprint)
        assertTrue(identity.keyBlobBase64.isNotEmpty())
    }
}
