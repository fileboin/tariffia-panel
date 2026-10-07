package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.router.RouterProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the keyless decisions and the sync core in Provider Details. */
class ProviderDetailsRulesTest {

    /* ------------------------------- keylessFor -------------------------------- */

    @Test
    fun keylessProviderIsKeyless() {
        val providers = listOf(RouterProvider(id = "ollama", keyless = true))
        assertTrue(ProviderDetailsRules.keylessFor(providers, "ollama"))
    }

    @Test
    fun nonKeylessProviderIsNotKeyless() {
        val providers = listOf(RouterProvider(id = "openai", keyless = false))
        assertFalse(ProviderDetailsRules.keylessFor(providers, "openai"))
    }

    @Test
    fun missingProviderDefaultsToNotKeyless() {
        val providers = listOf(RouterProvider(id = "openai", keyless = false))
        assertFalse(ProviderDetailsRules.keylessFor(providers, "ollama"))
        assertFalse(ProviderDetailsRules.keylessFor(emptyList(), "ollama"))
    }

    @Test
    fun providerIdMatchingIsCaseInsensitive() {
        val providers = listOf(RouterProvider(id = "ollama", keyless = true))
        assertTrue(ProviderDetailsRules.keylessFor(providers, "OLLAMA"))
    }

    /* ------------------------------- syncPlan ---------------------------------- */

    @Test
    fun keylessProviderIsStillSynced() {
        // keyless == no key required, NOT "do not sync".
        assertEquals(SyncPlan.SYNC_KEYLESS, ProviderDetailsRules.syncPlan(keyless = true, hasStoredKey = false))
        assertEquals(SyncPlan.SYNC_KEYLESS, ProviderDetailsRules.syncPlan(keyless = true, hasStoredKey = true))
    }

    @Test
    fun keyRequiringProviderSyncsWhenAKeyIsStored() {
        assertEquals(SyncPlan.SYNC_WITH_KEY, ProviderDetailsRules.syncPlan(keyless = false, hasStoredKey = true))
    }

    @Test
    fun keyRequiringProviderWithoutAKeyHasNothingToSync() {
        assertEquals(SyncPlan.NOTHING, ProviderDetailsRules.syncPlan(keyless = false, hasStoredKey = false))
    }

    /* ------------------------------- runProviderSync --------------------------- */

    @Test
    fun keylessSyncReachesTheTransportWithNoKey() = runBlocking {
        val pushed = mutableListOf<String>()
        var readCalled = false
        val result = runProviderSync(
            plan = SyncPlan.SYNC_KEYLESS,
            readKey = { readCalled = true; null },
            putKey = { key -> pushed += key; SyncResult.SyncedKeyless },
        )
        // The keyless provider is synced (transport invoked), with an empty key value.
        assertEquals(listOf(""), pushed)
        assertFalse("keyless sync must not read a key", readCalled)
        assertEquals(SyncResult.SyncedKeyless, result)
    }

    @Test
    fun keySyncReachesTheTransportWithTheStoredKey() = runBlocking {
        val pushed = mutableListOf<String>()
        val result = runProviderSync(
            plan = SyncPlan.SYNC_WITH_KEY,
            readKey = { "sk-test" },
            putKey = { key -> pushed += key; SyncResult.Success },
        )
        assertEquals(listOf("sk-test"), pushed)
        assertEquals(SyncResult.Success, result)
    }

    @Test
    fun keySyncWithoutAStoredKeyDoesNotReachTheTransport() = runBlocking {
        var putCalled = false
        val result = runProviderSync(
            plan = SyncPlan.SYNC_WITH_KEY,
            readKey = { null },
            putKey = { putCalled = true; SyncResult.Success },
        )
        assertFalse(putCalled)
        assertEquals(SyncResult.NoLocalKey, result)
    }

    @Test
    fun nothingPlanDoesNotReachTheTransport() = runBlocking {
        var putCalled = false
        val result = runProviderSync(
            plan = SyncPlan.NOTHING,
            readKey = { "sk-test" },
            putKey = { putCalled = true; SyncResult.Success },
        )
        assertFalse(putCalled)
        assertEquals(SyncResult.NoLocalKey, result)
    }
}
