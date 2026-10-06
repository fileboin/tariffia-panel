package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for parsing the `/healthz` provider status + reliability payload. */
class RouterHealthTest {

    @Test
    fun parsesProvidersWarningsCandidatesAndHealth() {
        val body = """
            {"ok":true,"providers":["ollama","openai"],"candidates":5,
             "warnings":[{"providerId":"gemini","reason":"missing env GEMINI_API_KEY"}],
             "health":{"openai/gpt-4o-mini":{"open":false,"openForMs":0,"ewmaMs":420,
                       "attempts":7,"successRate":0.857}},
             "quota":{"openai/gpt-4o-mini":{"day":1}}}
        """.trimIndent()

        val health = parseHealth(body)
        assertEquals(listOf("ollama", "openai"), health.providers)
        assertEquals(5, health.candidates)
        assertEquals(1, health.warnings.size)
        assertEquals("gemini", health.warnings[0].providerId)
        assertEquals("missing env GEMINI_API_KEY", health.warnings[0].reason)

        val candidate = health.health.getValue("openai/gpt-4o-mini")
        assertFalse(candidate.open)
        assertEquals(0L, candidate.openForMs)
        assertEquals(420L, candidate.ewmaMs)
        assertEquals(7, candidate.attempts)
        assertEquals(0.857, candidate.successRate!!, 0.0)
    }

    @Test
    fun openCircuitIsParsed() {
        val body = """{"health":{"a/b":{"open":true,"openForMs":30000,"ewmaMs":null,"attempts":3,"successRate":0.0}}}"""
        val candidate = parseHealth(body).health.getValue("a/b")
        assertTrue(candidate.open)
        assertEquals(30000L, candidate.openForMs)
        assertNull(candidate.ewmaMs)
        assertEquals(3, candidate.attempts)
        assertEquals(0.0, candidate.successRate!!, 0.0)
    }

    @Test
    fun missingMeasurementsStayNullNotZero() {
        val body = """{"health":{"a/b":{"open":false,"attempts":0}}}"""
        val candidate = parseHealth(body).health.getValue("a/b")
        assertNull(candidate.ewmaMs)
        assertNull(candidate.successRate)
    }

    @Test
    fun missingHealthDefaultsToEmpty() {
        val health = parseHealth("""{"providers":["ollama"]}""")
        assertTrue(health.health.isEmpty())
    }

    @Test
    fun missingFieldsDefaultToEmpty() {
        val health = parseHealth("{}")
        assertEquals(emptyList<String>(), health.providers)
        assertEquals(0, health.candidates)
        assertTrue(health.warnings.isEmpty())
        assertTrue(health.health.isEmpty())
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

    @Test
    fun secretLookingFieldsAreNotSurfaced() {
        val secret = "sk-super-secret-value"
        val body = """{"providers":[],"health":{"a/b":{"open":false,"attempts":1,"key":"$secret"}},
                       "authorization":"$secret"}"""
        val health = parseHealth(body)
        assertFalse(health.toString().contains(secret))
    }
}
