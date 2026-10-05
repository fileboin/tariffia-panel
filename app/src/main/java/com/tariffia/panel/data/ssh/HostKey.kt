package com.tariffia.panel.data.ssh

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Base64

/** The cryptographic identity of a server host key (type + blob + fingerprint). */
@Serializable
data class HostKeyIdentity(
    val keyType: String,
    val keyBlobBase64: String,
    val fingerprint: String,
)

/** A host key the user has explicitly trusted, pinned for host[:port]. */
@Serializable
data class HostKeyPin(
    val host: String,
    val port: Int,
    val identity: HostKeyIdentity,
)

sealed interface HostKeyDecision {
    data object Trusted : HostKeyDecision
    data object Unknown : HostKeyDecision
    data class Changed(val pinned: HostKeyPin, val presented: HostKeyIdentity) : HostKeyDecision
}

/**
 * Host-key fingerprint helpers. Pure (JVM-testable), no Android APIs.
 * Uses the OpenSSH `SHA256:<base64-no-padding>` format.
 */
object HostKeyFingerprint {

    fun sha256(keyBlob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
        val base64 = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$base64"
    }

    /** Parses the leading `string` of an SSH key blob (the key type). */
    fun parseKeyType(keyBlob: ByteArray): String {
        if (keyBlob.size < 4) return "unknown"
        val length = ((keyBlob[0].toInt() and 0xFF) shl 24) or
            ((keyBlob[1].toInt() and 0xFF) shl 16) or
            ((keyBlob[2].toInt() and 0xFF) shl 8) or
            (keyBlob[3].toInt() and 0xFF)
        if (length <= 0 || 4 + length > keyBlob.size) return "unknown"
        return String(keyBlob, 4, length, Charsets.US_ASCII)
    }

    fun identityOf(keyBlob: ByteArray): HostKeyIdentity = HostKeyIdentity(
        keyType = parseKeyType(keyBlob),
        keyBlobBase64 = Base64.getEncoder().encodeToString(keyBlob),
        fingerprint = sha256(keyBlob),
    )
}

/** Decides whether a presented host key is trusted, unknown, or changed. */
object HostKeyVerifier {
    fun decide(pinned: HostKeyPin?, presented: HostKeyIdentity): HostKeyDecision = when {
        pinned == null -> HostKeyDecision.Unknown
        pinned.identity.keyType == presented.keyType &&
            pinned.identity.keyBlobBase64 == presented.keyBlobBase64 -> HostKeyDecision.Trusted
        else -> HostKeyDecision.Changed(pinned, presented)
    }
}
