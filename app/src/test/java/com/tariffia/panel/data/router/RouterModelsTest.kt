package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for parsing the OpenAI-compatible `/v1/models` payload. */
class RouterModelsTest {

    @Test
    fun parsesModelIdsInOrder() {
        val body = """
            {"object":"list","data":[
                {"id":"llama3.2","object":"model"},
                {"id":"qwen2.5-coder:7b","object":"model"}
            ]}
        """.trimIndent()

        assertEquals(listOf("llama3.2", "qwen2.5-coder:7b"), parseModelIds(body))
    }

    @Test
    fun parsesEmptyData() {
        assertEquals(emptyList<String>(), parseModelIds("""{"data":[]}"""))
    }

    @Test
    fun missingDataDefaultsToEmpty() {
        assertEquals(emptyList<String>(), parseModelIds("""{"object":"list"}"""))
    }

    @Test
    fun ignoresUnknownFields() {
        val body = """{"data":[{"id":"m1","owned_by":"x","created":1}],"extra":true}"""
        assertEquals(listOf("m1"), parseModelIds(body))
    }

    @Test
    fun malformedBodyThrows() {
        var threw = false
        try {
            parseModelIds("not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    /* ----------------------------- full catalog ----------------------------- */

    @Test
    fun parsesModelCatalogFullFields() {
        val body = """
            {"object":"list","data":[
              {"id":"openai/gpt-4o-mini","object":"model","owned_by":"openai","created":1,
               "mesh":{"kind":"model","capabilities":["text","vision"],
                       "context_window":128000,"price_per_mtok_blended":0.2625,
                       "max_privacy":"public"}}
            ]}
        """.trimIndent()
        val model = parseModelCatalog(body).single()
        assertEquals("openai/gpt-4o-mini", model.id)
        assertEquals("openai", model.ownedBy)
        assertEquals("model", model.mesh.kind)
        assertEquals(listOf("text", "vision"), model.mesh.capabilities)
        assertEquals(128000, model.mesh.contextWindow)
        assertEquals(0.2625, model.mesh.pricePerMTokBlended, 0.0)
        assertEquals("public", model.mesh.maxPrivacy)
    }

    @Test
    fun profilesAndModelsAreDistinguished() {
        val body = """
            {"data":[
              {"id":"mesh/free","owned_by":"inferencemesh","mesh":{"kind":"profile"}},
              {"id":"ollama/llama3.2","owned_by":"ollama",
               "mesh":{"kind":"model","capabilities":["text"],"context_window":131072,
                       "price_per_mtok_blended":0.0,"max_privacy":"highly_confidential"}}
            ]}
        """.trimIndent()
        val models = parseModelCatalog(body)
        assertEquals(2, models.size)
        assertEquals("profile", models[0].mesh.kind)
        assertEquals("model", models[1].mesh.kind)
    }

    @Test
    fun modelMissingMeshUsesDefaults() {
        val model = parseModelCatalog("""{"data":[{"id":"x/y","owned_by":"x"}]}""").single()
        assertEquals("", model.mesh.kind)
        assertTrue(model.mesh.capabilities.isEmpty())
        assertEquals(0, model.mesh.contextWindow)
        assertEquals(0.0, model.mesh.pricePerMTokBlended, 0.0)
        assertNull(model.mesh.maxPrivacy)
    }

    @Test
    fun catalogUnknownFieldsAreIgnored() {
        val body = """{"data":[{"id":"a/b","owned_by":"a","mesh":{"kind":"model","future":1},"extra":true}],"page":2}"""
        assertEquals("a/b", parseModelCatalog(body).single().id)
    }

    @Test
    fun catalogMalformedBodyThrows() {
        var threw = false
        try {
            parseModelCatalog("not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun catalogSecretLookingFieldsAreNotSurfaced() {
        val secret = "sk-super-secret-value"
        val body = """{"data":[{"id":"a/b","owned_by":"a","key":"$secret",
                       "mesh":{"kind":"model","authorization":"$secret"}}]}"""
        val models = parseModelCatalog(body)
        assertFalse(models.toString().contains(secret))
    }

    /* ----------------------------- modelsForProvider ----------------------------- */

    private val catalog = """
        {"data":[
          {"id":"mesh/free","owned_by":"inferencemesh","mesh":{"kind":"profile"}},
          {"id":"openai/gpt-4o-mini","owned_by":"openai",
           "mesh":{"kind":"model","capabilities":["text"],"context_window":128000,"price_per_mtok_blended":0.26}},
          {"id":"openai/gpt-4o","owned_by":"openai",
           "mesh":{"kind":"model","capabilities":["text","vision"],"context_window":128000,"price_per_mtok_blended":2.5}},
          {"id":"ollama/llama3.2","owned_by":"ollama",
           "mesh":{"kind":"model","capabilities":["text"],"context_window":131072,"price_per_mtok_blended":0.0}}
        ]}
    """.trimIndent()

    @Test
    fun modelsForProviderMatchesOwner() {
        val models = modelsForProvider(parseModelCatalog(catalog), "openai")
        assertEquals(listOf("openai/gpt-4o-mini", "openai/gpt-4o"), models.map { it.id })
    }

    @Test
    fun modelsForProviderIsCaseInsensitive() {
        val models = modelsForProvider(parseModelCatalog(catalog), "OpenAI")
        assertEquals(2, models.size)
    }

    @Test
    fun modelsForProviderExcludesProfiles() {
        // "inferencemesh" owns the mesh/* profiles; none must ever be returned.
        assertTrue(modelsForProvider(parseModelCatalog(catalog), "inferencemesh").isEmpty())
    }

    @Test
    fun modelsForProviderRequiresKindModel() {
        val onlyProfile = """{"data":[{"id":"openai/x","owned_by":"openai","mesh":{"kind":"profile"}}]}"""
        assertTrue(modelsForProvider(parseModelCatalog(onlyProfile), "openai").isEmpty())
    }

    @Test
    fun modelsForProviderPreservesOrderAndEmptyOnNoMatch() {
        val ids = modelsForProvider(parseModelCatalog(catalog), "openai").map { it.id }
        assertEquals(listOf("openai/gpt-4o-mini", "openai/gpt-4o"), ids)
        assertTrue(modelsForProvider(parseModelCatalog(catalog), "missing").isEmpty())
    }
}
