package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.providers.ProviderStatus
import com.tariffia.panel.data.router.ProviderWarning
import com.tariffia.panel.data.router.RouterHealth
import com.tariffia.panel.data.router.RouterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for mapping a router result onto the Provider Details router status view. */
class ProviderRouterStatusResolverTest {

    private fun health(
        providers: List<String> = emptyList(),
        warnings: List<ProviderWarning> = emptyList(),
        candidates: Int = 0,
    ) = RouterHealth(providers = providers, warnings = warnings, candidates = candidates)

    @Test
    fun connectionFailure_isUnavailableNotFabricated() {
        val view = ProviderRouterStatusResolver.fromHealthResult(RouterResult.ConnectionFailed("x"), "openai")
        assertEquals(RouterStatusView.Unavailable, view)
        assertFalse(view is RouterStatusView.Available)
    }

    @Test
    fun httpError_isUnavailable() {
        assertEquals(
            RouterStatusView.Unavailable,
            ProviderRouterStatusResolver.fromHealthResult(RouterResult.HttpError(500), "openai"),
        )
    }

    @Test
    fun authFailure_isUnavailable() {
        assertEquals(
            RouterStatusView.Unavailable,
            ProviderRouterStatusResolver.fromHealthResult(RouterResult.AuthenticationFailed, "openai"),
        )
    }

    @Test
    fun invalidResponse_isUnavailable() {
        assertEquals(
            RouterStatusView.Unavailable,
            ProviderRouterStatusResolver.fromHealthResult(RouterResult.InvalidResponse("bad"), "openai"),
        )
    }

    @Test
    fun reportedConfigured_isAvailableConfigured() {
        val view = ProviderRouterStatusResolver.fromHealthResult(
            RouterResult.Success(health(providers = listOf("openai"), candidates = 1)),
            "openai",
        )
        assertEquals(RouterStatusView.Available(ProviderStatus.CONFIGURED, null), view)
    }

    @Test
    fun reportedWarning_isAvailableNotConfiguredWithNote() {
        val view = ProviderRouterStatusResolver.fromHealthResult(
            RouterResult.Success(health(warnings = listOf(ProviderWarning("openai", "missing env OPENAI_API_KEY")))),
            "openai",
        )
        assertEquals(
            RouterStatusView.Available(ProviderStatus.NOT_CONFIGURED, "missing env OPENAI_API_KEY"),
            view,
        )
    }

    @Test
    fun routerSilentAboutProvider_isAvailableUnknown() {
        val view = ProviderRouterStatusResolver.fromHealthResult(
            RouterResult.Success(health(providers = listOf("ollama"), candidates = 1)),
            "openai",
        )
        assertEquals(RouterStatusView.Available(ProviderStatus.UNKNOWN, null), view)
    }

    @Test
    fun unavailableIsNeverAvailable() {
        val view = ProviderRouterStatusResolver.fromHealthResult(RouterResult.HttpError(503), "openai")
        assertTrue(view is RouterStatusView.Unavailable)
        assertFalse(view is RouterStatusView.Available)
    }
}
