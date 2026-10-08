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
    fun setOllamaAvailability_sendsAuthenticatedBooleanControl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        assertTrue(client.setOllamaAvailability(baseUrl(), "router-token", true) is RouterResult.Success)
        assertTrue(client.setOllamaAvailability(baseUrl(), "router-token", false) is RouterResult.Success)

        val enabled = server.takeRequest()
        assertEquals("PUT", enabled.method)
        assertEquals("/internal/runtime/ollama-availability", enabled.path)
        assertEquals("Bearer router-token", enabled.getHeader("Authorization"))
        assertEquals("{\"available\":true}", enabled.body.readUtf8())

        val disabled = server.takeRequest()
        assertEquals("PUT", disabled.method)
        assertEquals("/internal/runtime/ollama-availability", disabled.path)
        assertEquals("Bearer router-token", disabled.getHeader("Authorization"))
        assertEquals("{\"available\":false}", disabled.body.readUtf8())
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

    @Test
    fun fetchModelCatalog_success_returnsMetadataAndAuth() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"id":"openai/gpt-4o-mini","owned_by":"openai",
                     "mesh":{"kind":"model","capabilities":["text"],"context_window":128000,
                             "price_per_mtok_blended":0.2625,"max_privacy":"public"}}]}""",
            ),
        )
        when (val result = client.fetchModelCatalog(baseUrl(), "token")) {
            is RouterResult.Success -> {
                val model = result.value.single()
                assertEquals("openai/gpt-4o-mini", model.id)
                assertEquals("openai", model.ownedBy)
                assertEquals("model", model.mesh.kind)
                assertEquals(128000, model.mesh.contextWindow)
            }
            else -> fail("Expected Success but was $result")
        }
        val recorded = server.takeRequest()
        assertEquals("/v1/models", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
    }

    @Test
    fun fetchModelCatalog_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.fetchModelCatalog(baseUrl(), "t") is RouterResult.InvalidResponse)
    }

    @Test
    fun fetchModelCatalog_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(RouterResult.AuthenticationFailed, client.fetchModelCatalog(baseUrl(), "t"))
    }

    @Test
    fun fetchModelCatalog_unreachable_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        assertTrue(client.fetchModelCatalog(url, "t") is RouterResult.ConnectionFailed)
    }

    @Test
    fun fetchUsage_success_hitsUsagePathWithAuth() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"day":"2026-10-06","today":{"totals":{"requests":1,"totalTokens":15}}}""",
            ),
        )
        when (val result = client.fetchUsage(baseUrl(), "token")) {
            is RouterResult.Success -> {
                assertEquals("2026-10-06", result.value.day)
                assertEquals(1L, result.value.today.totals.requests)
                assertEquals(15L, result.value.today.totals.totalTokens)
            }
            else -> fail("Expected Success but was $result")
        }
        val recorded = server.takeRequest()
        assertEquals("/v1/usage", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
    }

    @Test
    fun fetchUsage_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.fetchUsage(baseUrl(), "t") is RouterResult.InvalidResponse)
    }

    @Test
    fun fetchUsage_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(RouterResult.AuthenticationFailed, client.fetchUsage(baseUrl(), "t"))
    }

    @Test
    fun fetchUsage_404_isHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(RouterResult.HttpError(404), client.fetchUsage(baseUrl(), "t"))
    }

    @Test
    fun fetchUsage_unreachable_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        assertTrue(client.fetchUsage(url, "t") is RouterResult.ConnectionFailed)
    }

    @Test
    fun sendChat_success_postsModelMessagesAndAuth() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"hi there"}}]}"""),
        )
        val result = client.sendChat(baseUrl(), "token", "mesh/free", listOf(ChatMessage("user", "hello")))
        when (result) {
            is RouterResult.Success -> assertEquals("hi there", result.value)
            else -> fail("Expected Success but was $result")
        }
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"mesh/free\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content\":\"hello\""))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun sendChat_401_isAuthenticationFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(
            RouterResult.AuthenticationFailed,
            client.sendChat(baseUrl(), "t", "mesh/free", listOf(ChatMessage("user", "hi"))),
        )
    }

    @Test
    fun sendChat_500_isHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(
            RouterResult.HttpError(500),
            client.sendChat(baseUrl(), "t", "mesh/free", listOf(ChatMessage("user", "hi"))),
        )
    }

    @Test
    fun sendChat_malformedBody_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.sendChat(baseUrl(), "t", "mesh/free", listOf(ChatMessage("user", "hi"))) is RouterResult.InvalidResponse)
    }

    @Test
    fun sendChat_noAssistantContent_isInvalidResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        assertTrue(client.sendChat(baseUrl(), "t", "mesh/free", listOf(ChatMessage("user", "hi"))) is RouterResult.InvalidResponse)
    }

    @Test
    fun sendChat_unreachable_isConnectionFailed() = runBlocking {
        val url = baseUrl()
        server.shutdown()
        assertTrue(client.sendChat(url, "t", "mesh/free", listOf(ChatMessage("user", "hi"))) is RouterResult.ConnectionFailed)
    }

    private companion object {
        // A dummy value, not a real secret.
        const val DUMMY_KEY = "dummy-provider-key-value"
    }
}
