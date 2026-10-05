package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
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
}
