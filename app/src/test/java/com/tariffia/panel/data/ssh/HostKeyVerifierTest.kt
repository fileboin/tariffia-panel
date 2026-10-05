package com.tariffia.panel.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the host-key trust decision. */
class HostKeyVerifierTest {

    private val identityA = HostKeyIdentity("ssh-ed25519", "AAAA", "SHA256:aaa")
    private val identityB = HostKeyIdentity("ssh-ed25519", "BBBB", "SHA256:bbb")
    private val identityOtherType = HostKeyIdentity("ssh-rsa", "AAAA", "SHA256:aaa")
    private val pin = HostKeyPin("vps.example.com", 22, identityA)

    @Test
    fun noPin_isUnknown() {
        assertEquals(HostKeyDecision.Unknown, HostKeyVerifier.decide(null, identityA))
    }

    @Test
    fun matchingPin_isTrusted() {
        assertEquals(HostKeyDecision.Trusted, HostKeyVerifier.decide(pin, identityA))
    }

    @Test
    fun differentKeyBlob_isChanged() {
        val decision = HostKeyVerifier.decide(pin, identityB)
        assertTrue(decision is HostKeyDecision.Changed)
        assertEquals(identityA, (decision as HostKeyDecision.Changed).pinned.identity)
        assertEquals(identityB, decision.presented)
    }

    @Test
    fun differentKeyType_isChanged() {
        assertTrue(HostKeyVerifier.decide(pin, identityOtherType) is HostKeyDecision.Changed)
    }
}
