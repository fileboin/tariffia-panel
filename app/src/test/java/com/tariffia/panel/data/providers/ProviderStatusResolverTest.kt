package com.tariffia.panel.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for mapping router `/healthz` output onto provider rows. */
class ProviderStatusResolverTest {

    private fun byName(configured: List<String>, warned: Map<String, String>) =
        ProviderStatusResolver.resolve(configured, warned).associateBy { it.displayName }

    @Test
    fun configuredId_isConfigured() {
        val rows = byName(listOf("openai"), emptyMap())
        assertEquals(ProviderStatus.CONFIGURED, rows.getValue("OpenAI").status)
    }

    @Test
    fun warnedId_isNotConfiguredWithReason() {
        val rows = byName(emptyList(), mapOf("together" to "missing env TOGETHER_API_KEY"))
        val row = rows.getValue("Together")
        assertEquals(ProviderStatus.NOT_CONFIGURED, row.status)
        assertEquals("missing env TOGETHER_API_KEY", row.note)
    }

    @Test
    fun unmentionedId_isUnknown() {
        val rows = byName(emptyList(), emptyMap())
        assertEquals(ProviderStatus.UNKNOWN, rows.getValue("Gemini").status)
        assertEquals(ProviderStatus.UNKNOWN, rows.getValue("Ollama").status)
    }

    @Test
    fun aliases_matchKnownProvider() {
        val rows = byName(listOf("z-ai"), emptyMap())
        assertEquals(ProviderStatus.CONFIGURED, rows.getValue("Z.ai").status)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val rows = byName(listOf("Ollama"), emptyMap())
        assertEquals(ProviderStatus.CONFIGURED, rows.getValue("Ollama").status)
    }

    @Test
    fun unknownReportedId_isAppendedNotHidden() {
        val rows = ProviderStatusResolver.resolve(listOf("mystery-provider"), emptyMap())
        val extra = rows.first { it.id == "mystery-provider" }
        assertEquals(ProviderStatus.CONFIGURED, extra.status)
        assertEquals("mystery-provider", extra.displayName)
    }

    @Test
    fun unknown_returnsWholeCatalogAsUnknown() {
        val rows = ProviderStatusResolver.unknown()
        assertEquals(ProviderCatalog.known.size, rows.size)
        assertTrue(rows.all { it.status == ProviderStatus.UNKNOWN })
    }

    @Test
    fun catalogIdsAreNotDuplicatedAsExtras() {
        val rows = ProviderStatusResolver.resolve(listOf("openai", "ollama"), emptyMap())
        assertEquals(rows.size, rows.map { it.displayName }.distinct().size)
        assertEquals(ProviderCatalog.known.size, rows.size)
    }
}
