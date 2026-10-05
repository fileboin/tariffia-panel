package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for parsing the `/healthz` provider status payload. */
class RouterHealthTest {

    @Test
    fun parsesProvidersWarningsAndCandidates() {
        val body = """
            {"ok":true,"providers":["ollama","openai"],"candidates":5,
             "warnings":[{"providerId":"gemini","reason":"missing env GEMINI_API_KEY"}],
             "health":{"openai":"up"},"quota":{"window":1}}
        """.trimIndent()

        val health = parseHealth(body)
        assertEquals(listOf("ollama", "openai"), health.providers)
        assertEquals(5, health.candidates)
        assertEquals(1, health.warnings.size)
        assertEquals("gemini", health.warnings[0].providerId)
        assertEquals("missing env GEMINI_API_KEY", health.warnings[0].reason)
    }

    @Test
    fun missingFieldsDefaultToEmpty() {
        val health = parseHealth("{}")
        assertEquals(emptyList<String>(), health.providers)
        assertEquals(0, health.candidates)
        assertTrue(health.warnings.isEmpty())
    }

    @Test
    fun malformedBodyThrows() {
        var threw = false
        try {
            parseHealth("not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }
}
