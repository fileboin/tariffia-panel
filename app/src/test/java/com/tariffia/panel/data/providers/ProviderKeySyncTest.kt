package com.tariffia.panel.data.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderKeySyncTest {

    private fun makeSync(
        stored: Map<String, String>,
        failFor: Set<String> = emptySet(),
    ): Pair<ProviderKeySync, MutableList<Pair<String, String>>> {
        val calls = mutableListOf<Pair<String, String>>()
        val sync = ProviderKeySync(
            hasKey = { it in stored },
            readKey = { stored[it] },
            putKey = { id, key ->
                calls += id to key
                if (id in failFor) throw RuntimeException("boom") else ProviderSyncResult.Synced
            },
        )
        return sync to calls
    }

    @Test
    fun existingLocalKeyIsSyncedWhenReady() = runBlocking {
        val (sync, calls) = makeSync(mapOf("openai" to "sk-openai"))
        val report = sync.syncAll(listOf("openai"))

        assertEquals(1, report.synced)
        assertEquals(0, report.skipped)
        assertEquals(0, report.failed)
        assertEquals(listOf("openai" to "sk-openai"), calls)
    }

    @Test
    fun multipleKeysAreAllAttempted() = runBlocking {
        val (sync, calls) = makeSync(mapOf("openai" to "k1", "deepinfra" to "k2"))
        val report = sync.syncAll(listOf("openai", "deepinfra"))

        assertEquals(2, report.synced)
        assertEquals(2, calls.size)
        assertEquals(setOf("openai", "deepinfra"), calls.map { it.first }.toSet())
    }

    @Test
    fun providerWithoutKeyIsSkipped() = runBlocking {
        val (sync, calls) = makeSync(mapOf("openai" to "k1"))
        val report = sync.syncAll(listOf("openai", "deepinfra"))

        assertEquals(1, report.synced)
        assertEquals(1, report.skipped)
        assertEquals(0, report.failed)
        assertEquals(listOf("openai"), calls.map { it.first })
    }

    @Test
    fun oneFailureDoesNotStopTheOthers() = runBlocking {
        val (sync, calls) = makeSync(
            stored = mapOf("openai" to "k1", "deepinfra" to "k2", "zai" to "k3"),
            failFor = setOf("deepinfra"),
        )
        val report = sync.syncAll(listOf("openai", "deepinfra", "zai"))

        assertEquals(2, report.synced)
        assertEquals(1, report.failed)
        assertEquals(listOf("deepinfra"), report.failedProviderIds)
        // All three were attempted despite the middle failure.
        assertEquals(listOf("openai", "deepinfra", "zai"), calls.map { it.first })
    }

    @Test
    fun keyNeverAppearsInReportOrSummary() = runBlocking {
        val secret = "sk-super-secret-value"
        val (sync, _) = makeSync(mapOf("openai" to secret))
        val report = sync.syncAll(listOf("openai"))

        assertFalse(report.toString().contains(secret))
        assertFalse(report.outcomes.toString().contains(secret))
        assertFalse(report.failedProviderIds.toString().contains(secret))
        assertFalse(report.summaryText().contains(secret))
    }
}
