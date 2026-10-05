package com.tariffia.panel.data.router

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Exercises [RouterClient] against a local [MockWebServer]: success, bearer auth,
 * 401/403, other HTTP errors, malformed bodies and connection failure. No real
 * router is required.
 */
class RouterClientTest {

    private lateinit var server: MockWebServer
    private val client = RouterClient()

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

    private fun baseUrl() = server.url("/").toString()

    @Test
    fun checkHealth_success() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        assertTrue(client.checkHealth(baseUrl(), "token") is RouterResult.Success)
    }

    @Test
    fun sendsBearerTokenAndHitsHealthz() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        client.checkHealth(baseUrl(), "token")
        val recorded = server.takeRequest()
        assertEquals("/healthz", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
    }

    @Test
    fun missingTokenOmitsAuthorizationHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        client.checkHealth(baseUrl(), null)
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun checkHealth_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(RouterResult.AuthenticationFailed, client.checkHealth(baseUrl(), "t"))
    }

    @Test
    fun checkHealth_403_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(RouterResult.AuthenticationFailed, client.checkHealth(baseUrl(), "t"))
    }

    @Test
    fun checkHealth_500_isHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(RouterResult.HttpError(500), client.checkHealth(baseUrl(), "t"))
    }

    @Test
    fun fetchModels_success_returnsIds() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":[{"id":"m1"},{"id":"m2"}]}"""),
        )
        when (val result = client.fetchModels(baseUrl(), "t")) {
            is RouterResult.Success -> assertEquals(listOf("m1", "m2"), result.value)
            else -> fail("Expected Success but was $result")
        }
    }

    @Test
    fun fetchModels_hitsModelsPath() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        client.fetchModels(baseUrl(), "t")
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun fetchModels_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.fetchModels(baseUrl(), "t") is RouterResult.InvalidResponse)
    }

    @Test
    fun unreachableHost_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        assertTrue(client.checkHealth(url, "t") is RouterResult.ConnectionFailed)
    }

    @Test
    fun invalidUrlScheme_isConnectionFailedNotCrash() = runBlocking {
        assertTrue(client.checkHealth("ftp://example.com", "t") is RouterResult.ConnectionFailed)
    }
}
