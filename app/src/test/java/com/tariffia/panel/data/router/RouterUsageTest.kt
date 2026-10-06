package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for parsing the Router `GET /v1/usage` payload and matching models. */
class RouterUsageTest {

    private val fullBody = """
        {
          "since": "2026-10-06T10:00:00.000Z",
          "day": "2026-10-06",
          "lifetime": {
            "totals": { "requests": 3, "inputTokens": 30, "outputTokens": 15, "totalTokens": 45, "costUsd": 0.02 },
            "providers": [
              { "provider": "openai", "requests": 3, "inputTokens": 30, "outputTokens": 15, "totalTokens": 45, "costUsd": 0.02,
                "models": [ { "model": "openai/gpt-4o-mini", "requests": 3, "inputTokens": 30, "outputTokens": 15, "totalTokens": 45, "costUsd": 0.02 } ] }
            ]
          },
          "today": {
            "totals": { "requests": 1, "inputTokens": 10, "outputTokens": 5, "totalTokens": 15, "costUsd": 0.01 },
            "providers": [
              { "provider": "openai", "requests": 1, "inputTokens": 10, "outputTokens": 5, "totalTokens": 15, "costUsd": 0.01,
                "models": [ { "model": "openai/gpt-4o-mini", "requests": 1, "inputTokens": 10, "outputTokens": 5, "totalTokens": 15, "costUsd": 0.01 } ] }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parsesNestedProviderModelAndTotals() {
        val usage = parseUsage(fullBody)
        assertEquals("2026-10-06", usage.day)
        assertEquals("2026-10-06T10:00:00.000Z", usage.since)

        assertEquals(3L, usage.lifetime.totals.requests)
        assertEquals(45L, usage.lifetime.totals.totalTokens)
        assertEquals(0.02, usage.lifetime.totals.costUsd, 0.0)

        assertEquals(1L, usage.today.totals.requests)
        assertEquals(15L, usage.today.totals.totalTokens)

        val provider = usage.today.providers.single()
        assertEquals("openai", provider.provider)
        val model = provider.models.single()
        assertEquals("openai/gpt-4o-mini", model.model)
        assertEquals(10L, model.inputTokens)
        assertEquals(5L, model.outputTokens)
    }

    @Test
    fun emptyBodyUsesDefaults() {
        val usage = parseUsage("{}")
        assertEquals("", usage.day)
        assertEquals(0L, usage.today.totals.requests)
        assertEquals(0.0, usage.today.totals.costUsd, 0.0)
        assertTrue(usage.today.providers.isEmpty())
        assertTrue(usage.lifetime.providers.isEmpty())
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val body = """{"day":"2026-10-06","future":1,"today":{"totals":{"requests":2},"extra":true}}"""
        assertEquals(2L, parseUsage(body).today.totals.requests)
    }

    @Test
    fun malformedBodyThrows() {
        var threw = false
        try {
            parseUsage("not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun secretLookingFieldsAreNotSurfaced() {
        val secret = "sk-super-secret-value"
        val body = """{"day":"2026-10-06","key":"$secret","authorization":"$secret",
                       "today":{"totals":{"requests":1},"providers":[{"provider":"openai","apiKey":"$secret"}]}}"""
        val usage = parseUsage(body)
        assertFalse(usage.toString().contains(secret))
    }

    @Test
    fun usageForModelMatchesExactKey() {
        val scope = UsageScope(
            providers = listOf(
                UsageProvider(
                    provider = "openai",
                    models = listOf(
                        UsageModel(model = "openai/gpt-4o-mini", requests = 2, totalTokens = 30),
                        UsageModel(model = "openai/gpt-4o", requests = 1, totalTokens = 5),
                    ),
                ),
            ),
        )
        val match = usageForModel(scope, "openai/gpt-4o-mini")
        assertNotNull(match)
        assertEquals(2L, match!!.requests)
        assertEquals(30L, match.totalTokens)
    }

    @Test
    fun usageForModelMissingIsNull() {
        val scope = UsageScope(
            providers = listOf(UsageProvider(provider = "openai", models = listOf(UsageModel(model = "openai/gpt-4o")))),
        )
        assertNull(usageForModel(scope, "openai/gpt-4o-mini"))
        assertNull(usageForModel(UsageScope(), "openai/gpt-4o-mini"))
    }

    @Test
    fun usageForModelZeroUsageReturnsRecordNotMissing() {
        val scope = UsageScope(
            providers = listOf(
                UsageProvider(provider = "openai", models = listOf(UsageModel(model = "openai/x"))),
            ),
        )
        val match = usageForModel(scope, "openai/x")
        assertNotNull(match)
        assertEquals(0L, match!!.requests)
        assertEquals(0.0, match.costUsd, 0.0)
    }

    @Test
    fun usageForModelSearchesAcrossProviders() {
        val scope = UsageScope(
            providers = listOf(
                UsageProvider(provider = "openai", models = listOf(UsageModel(model = "openai/a"))),
                UsageProvider(provider = "deepinfra", models = listOf(UsageModel(model = "deepinfra/b", requests = 7))),
            ),
        )
        assertEquals(7L, usageForModel(scope, "deepinfra/b")!!.requests)
    }
}
