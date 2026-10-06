package com.tariffia.panel.data.router

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun usesInjectedHttpClient() = runBlocking {
        val injectedClient = RouterClient(OkHttpClient())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        assertTrue(injectedClient.checkHealth(baseUrl(), "token") is RouterResult.Success)
        assertEquals("/healthz", server.takeRequest().path)
    }

    @Test
    fun sharedHttpClientIsSingleton() {
        assertSame(SharedRouterHttpClient.instance, SharedRouterHttpClient.instance)
    }

    @Test
    fun syncProviderKey_success_sendsPutWithKeyAndAuth() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"ok":true,"provider":"openai","configured":true}"""),
        )
        val result = client.syncProviderKey(baseUrl(), "token", "openai", DUMMY_KEY)
        assertTrue(result is RouterResult.Success)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/v1/providers/openai/key", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains(DUMMY_KEY))
    }

    @Test
    fun syncProviderKey_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(
            RouterResult.AuthenticationFailed,
            client.syncProviderKey(baseUrl(), "t", "openai", DUMMY_KEY),
        )
    }

    @Test
    fun syncProviderKey_403_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(
            RouterResult.AuthenticationFailed,
            client.syncProviderKey(baseUrl(), "t", "openai", DUMMY_KEY),
        )
    }

    @Test
    fun syncProviderKey_404_isHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(
            RouterResult.HttpError(404),
            client.syncProviderKey(baseUrl(), "t", "openai", DUMMY_KEY),
        )
    }

    @Test
    fun syncProviderKey_unreachable_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        val result = client.syncProviderKey(url, "t", "openai", DUMMY_KEY)
        assertTrue(result is RouterResult.ConnectionFailed)
        assertFalse(result.toString().contains(DUMMY_KEY))
    }

    @Test
    fun syncProviderKey_failureNeverCarriesTheKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server exploded"))
        val result = client.syncProviderKey(baseUrl(), "t", "openai", DUMMY_KEY)
        assertEquals(RouterResult.HttpError(500), result)
        assertFalse(result.toString().contains(DUMMY_KEY))
    }

    @Test
    fun fetchProviders_success_returnsProvidersAndAuth() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"providers":[{"id":"openai","configured":true,"models":2}]}"""),
        )
        when (val result = client.fetchProviders(baseUrl(), "token")) {
            is RouterResult.Success -> {
                assertEquals(1, result.value.size)
                assertEquals("openai", result.value.single().id)
                assertEquals(2, result.value.single().models)
            }
            else -> fail("Expected Success but was $result")
        }
        val recorded = server.takeRequest()
        assertEquals("/v1/providers", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
    }

    @Test
    fun fetchProviders_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.fetchProviders(baseUrl(), "t") is RouterResult.InvalidResponse)
    }

    @Test
    fun fetchProviders_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(RouterResult.AuthenticationFailed, client.fetchProviders(baseUrl(), "t"))
    }

    @Test
    fun fetchProviders_unreachable_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        assertTrue(client.fetchProviders(url, "t") is RouterResult.ConnectionFailed)
    }

    private companion object {
        // A dummy value, not a real secret.
        const val DUMMY_KEY = "dummy-provider-key-value"
    }
}
