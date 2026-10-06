package com.tariffia.panel.data.router

import java.security.SecureRandom

/**
 * Generates the local Router bearer token: 32 random bytes, lowercase hex.
 * The value is never logged and is only ever handed to the Router and the
 * existing [com.tariffia.panel.data.SecureSettingsStore].
 */
object RouterToken {

    private const val BYTE_COUNT = 32
    private val HEX = "0123456789abcdef".toCharArray()

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(BYTE_COUNT)
        random.nextBytes(bytes)
        val out = CharArray(BYTE_COUNT * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX[v ushr 4]
            out[i++] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
