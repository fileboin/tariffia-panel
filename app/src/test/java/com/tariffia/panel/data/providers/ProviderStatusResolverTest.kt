package com.tariffia.panel.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for deriving provider rows from Router-reported facts (no hard-coded list). */
class ProviderStatusResolverTest {

    private fun facts(
        vararg entries: Pair<String, Boolean>,
        keyless: Set<String> = emptySet(),
        models: Int = 3,
    ): List<ProviderFacts> = entries.map { (id, configured) ->
        ProviderFacts(
            id = id,
            configured = configured,
            keyless = id in keyless,
            modelCount = models,
            summary = "summary-$id",
            freeTierNote = null,
        )
    }

    @Test
    fun derivesRowsFromRouterIdsOnly() {
        val rows = ProviderStatusResolver.resolve(
            facts = facts("openai" to true, "openrouter" to false),
        )
        assertEquals(listOf("openai", "openrouter"), rows.map { it.id })
    }

    @Test
    fun emptyFactsProduceNoRows() {
        assertTrue(ProviderStatusResolver.resolve(facts = emptyList()).isEmpty())
    }

    @Test
    fun orderingIsStableById() {
        val rows = ProviderStatusResolver.resolve(
            facts = facts("openrouter" to true, "deepinfra" to true, "ollama" to false),
        )
        assertEquals(listOf("deepinfra", "ollama", "openrouter"), rows.map { it.id })
    }

    @Test
    fun configuredFactIsConfigured() {
        val rows = ProviderStatusResolver.resolve(facts = facts("openai" to true))
        assertEquals(ProviderStatus.CONFIGURED, rows.single().status)
    }

    @Test
    fun keylessFactIsUsableAndFlagged() {
        val rows = ProviderStatusResolver.resolve(facts = facts("ollama" to false, keyless = setOf("ollama")))
        assertEquals(ProviderStatus.CONFIGURED, rows.single().status)
        assertTrue(rows.single().keyless)
    }

    @Test
    fun unconfiguredWarnedFactIsNotConfiguredWithReason() {
        val rows = ProviderStatusResolver.resolve(
            facts = facts("openai" to false),
            warnedReasons = mapOf("openai" to "missing env OPENAI_API_KEY"),
        )
        assertEquals(ProviderStatus.NOT_CONFIGURED, rows.single().status)
        assertEquals("missing env OPENAI_API_KEY", rows.single().note)
    }

    @Test
    fun localKeyStatusIsSeparateFromRouterStatus() {
        val rows = ProviderStatusResolver.resolve(
            facts = facts("openai" to false),
            hasLocalKey = { it == "openai" },
        )
        val row = rows.single()
        // A locally stored key does not change the Router status.
        assertEquals(ProviderStatus.NOT_CONFIGURED, row.status)
        assertTrue(row.hasLocalKey)
    }

    @Test
    fun displayNameFallsBackToIdForUnknown() {
        val rows = ProviderStatusResolver.resolve(facts = facts("mystery-provider" to true))
        assertEquals("mystery-provider", rows.single().displayName)
    }

    @Test
    fun statusForMatchesConfiguredWarnedUnknown() {
        val configured = ProviderStatusResolver.statusFor("openai", listOf("openai"), emptyMap())
        assertEquals(ProviderStatus.CONFIGURED, configured.first)

        val warned = ProviderStatusResolver.statusFor("openai", emptyList(), mapOf("openai" to "missing"))
        assertEquals(ProviderStatus.NOT_CONFIGURED, warned.first)
        assertEquals("missing", warned.second)

        val unknown = ProviderStatusResolver.statusFor("openai", listOf("ollama"), emptyMap())
        assertEquals(ProviderStatus.UNKNOWN, unknown.first)
    }

    @Test
    fun noKeyValueAppearsInRows() {
        val secret = "sk-super-secret-value"
        val rows = ProviderStatusResolver.resolve(
            facts = facts("openai" to true),
            hasLocalKey = { true },
        )
        assertFalse(rows.toString().contains(secret))
    }
}
