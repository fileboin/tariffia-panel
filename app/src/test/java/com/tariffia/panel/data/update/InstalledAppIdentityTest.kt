package com.tariffia.panel.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppIdentityTest {

    @Test
    fun digestMatchesKnownSha256VectorForAbc() {
        // FIPS 180-2 vector: SHA-256("abc")
        assertEquals(
            "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD",
            certSha256Colon("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun digestMatchesKnownSha256VectorForEmptyInput() {
        assertEquals(
            "E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55",
            certSha256Colon(ByteArray(0)),
        )
    }

    @Test
    fun digestIsColonSeparatedUppercaseHexOf32Bytes() {
        val digest = certSha256Colon(byteArrayOf(1, 2, 3))
        assertEquals(32, digest.split(':').size)
        assertTrue(digest.split(':').all { it.length == 2 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } })
        assertEquals(95, digest.length)
    }
}
