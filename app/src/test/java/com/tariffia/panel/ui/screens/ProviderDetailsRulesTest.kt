package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.router.RouterProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the keyless decisions in Provider Details (no Android APIs). */
class ProviderDetailsRulesTest {

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

    @Test
    fun keylessProviderNeverPushesAKey() {
        // The PUT/transport path is gated on this decision: keyless must be false so no
        // empty or placeholder key is ever sent to the Router.
        assertFalse(ProviderDetailsRules.shouldPushKey(keyless = true))
    }

    @Test
    fun keyRequiringProviderStillPushesAKey() {
        assertTrue(ProviderDetailsRules.shouldPushKey(keyless = false))
    }
}
