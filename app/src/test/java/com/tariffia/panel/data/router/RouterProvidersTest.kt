package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** JVM tests for parsing the Router `GET /v1/providers` payload. */
class RouterProvidersTest {

    @Test
    fun parsesAllSupportedFields() {
        val body = """
            {
              "providers": [
                {
                  "id": "openai",
                  "summary": "OpenAI official",
                  "freeTierNote": "paid",
                  "signupUrl": "https://platform.openai.com",
                  "signupSteps": { "en": ["go to page", "copy key"] },
                  "keyPrefix": "sk-",
                  "keyless": false,
                  "configured": true,
                  "models": 4
                }
              ],
              "candidates": 4,
              "usable": ["openai"]
            }
        """.trimIndent()
        val providers = parseProviders(body)
        assertEquals(1, providers.size)
        val p = providers.single()
        assertEquals("openai", p.id)
        assertEquals("OpenAI official", p.summary)
        assertEquals("paid", p.freeTierNote)
        assertEquals("https://platform.openai.com", p.signupUrl)
        assertEquals(listOf("go to page", "copy key"), p.signupSteps?.get("en"))
        assertEquals("sk-", p.keyPrefix)
        assertFalse(p.keyless)
        assertTrue(p.configured)
        assertEquals(4, p.models)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val providers = parseProviders("""{"providers":[{"id":"ollama"}]}""")
        val p = providers.single()
        assertEquals("ollama", p.id)
        assertNull(p.summary)
        assertNull(p.freeTierNote)
        assertNull(p.signupUrl)
        assertNull(p.signupSteps)
        assertNull(p.keyPrefix)
        assertFalse(p.keyless)
        assertFalse(p.configured)
        assertEquals(0, p.models)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val body = """{"providers":[{"id":"openai","configured":true,"models":2,"futureField":{"x":1}}],"extra":true}"""
        val providers = parseProviders(body)
        assertEquals("openai", providers.single().id)
        assertEquals(2, providers.single().models)
    }

    @Test
    fun aSecretLookingFieldIsNotSurfaced() {
        // The Router never sends a key; even if one appeared, the DTO has no field for
        // it and unknown keys are ignored, so it can never reach a row or a message.
        val secret = "sk-super-secret-value"
        val body = """{"providers":[{"id":"openai","key":"$secret","apiKey":"$secret"}]}"""
        val providers = parseProviders(body)
        assertFalse(providers.toString().contains(secret))
    }

    @Test
    fun emptyProvidersParsesToEmptyList() {
        assertTrue(parseProviders("""{"providers":[]}""").isEmpty())
        assertTrue(parseProviders("""{}""").isEmpty())
    }

    @Test
    fun malformedBodyThrows() {
        try {
            parseProviders("not json")
            fail("expected a serialization failure")
        } catch (expected: Exception) {
            // RouterClient maps this to RouterResult.InvalidResponse.
        }
    }
}
