package com.tariffia.panel.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** JVM tests for the version comparator and the best-effort update check. */
class UpdateCheckerTest {

    /* ------------------------------- pure helpers ------------------------------- */

    @Test
    fun parseVersionHandlesPrefixAndSuffix() {
        assertEquals(listOf(0, 0, 3), parseVersion("v0.0.3"))
        assertEquals(listOf(0, 0, 2), parseVersion("0.0.2-router"))
        assertEquals(listOf(1, 2, 3), parseVersion("V1.2.3"))
        assertEquals(listOf(2), parseVersion("2"))
        assertNull(parseVersion("garbage"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("v"))
    }

    @Test
    fun compareVersionsIsNumericAndZeroFilled() {
        assertTrue(compareVersions(listOf(0, 0, 10), listOf(0, 0, 9)) > 0)
        assertEquals(0, compareVersions(listOf(0, 0, 2), listOf(0, 0, 2)))
        assertEquals(0, compareVersions(listOf(1), listOf(1, 0, 0)))
        assertTrue(compareVersions(listOf(0, 1), listOf(0, 0, 5)) > 0)
    }

    @Test
    fun evaluateUpdate_cases() {
        val url = "https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.3"
        assertEquals(AvailableUpdate("0.0.3", url), evaluateUpdate("0.0.2", "v0.0.3", url))
        assertNull(evaluateUpdate("0.0.2", "v0.0.2", url))
        assertEquals(AvailableUpdate("0.0.10", url), evaluateUpdate("0.0.9", "v0.0.10", url))
        assertEquals(AvailableUpdate("0.0.10", url), evaluateUpdate("0.0.2", "v0.0.10", url))
        assertNull(evaluateUpdate("0.0.2", "v0.0.2-router", url))
        assertNull(evaluateUpdate("0.0.2", "garbage", url))
        assertNull(evaluateUpdate("0.0.2", "", url))
        assertNull(evaluateUpdate("", "v0.0.3", url))
        assertNull(evaluateUpdate("0.0.2", "v0.0.3", ""))
        assertNull(evaluateUpdate("0.0.2", "v0.0.3", "ftp://example.com"))
    }

    /* ------------------------------- HTTP behavior ------------------------------ */

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (ignored: Exception) {
            // Already shut down by a test.
        }
    }

    private fun checker() = UpdateChecker(OkHttpClient(), server.url("/releases/latest").toString())

    @Test
    fun newerRelease_returnsUpdate_andSendsAcceptHeader() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"tag_name":"v0.0.3","html_url":"https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.3","prerelease":false}""",
            ),
        )
        val result = checker().check("0.0.2")
        assertNotNull(result)
        assertEquals("0.0.3", result!!.version)
        assertEquals("https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.3", result.url)
        val recorded = server.takeRequest()
        assertEquals("/releases/latest", recorded.path)
        assertEquals("application/vnd.github+json", recorded.getHeader("Accept"))
    }

    @Test
    fun currentRelease_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"tag_name":"v0.0.2","html_url":"https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.2"}"""))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun malformedJson_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun missingTagName_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"html_url":"https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.3"}"""))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun invalidHtmlUrl_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"tag_name":"v0.0.3","html_url":""}"""))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun http404_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun http500_returnsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(checker().check("0.0.2"))
    }

    @Test
    fun unreachable_returnsNullWithoutThrowing() = runBlocking {
        val c = checker()
        server.shutdown()
        assertNull(c.check("0.0.2"))
    }
}
