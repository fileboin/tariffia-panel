package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.router.ProviderWarning
import com.tariffia.panel.data.router.RouterHealth
import com.tariffia.panel.data.router.RouterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for mapping router results onto Home status, summary and model errors. */
class HomeStatusResolverTest {

    private fun health(
        providers: List<String> = listOf("ollama"),
        warnings: List<ProviderWarning> = emptyList(),
        candidates: Int = 1,
    ) = RouterHealth(providers = providers, warnings = warnings, candidates = candidates)

    @Test
    fun healthSuccess_isOnline() {
        assertEquals(HomeStatus.Online, HomeStatusResolver.fromHealthResult(RouterResult.Success(health())))
    }

    @Test
    fun health401_isAuthFailed() {
        assertEquals(HomeStatus.AuthFailed, HomeStatusResolver.fromHealthResult(RouterResult.AuthenticationFailed))
    }

    @Test
    fun healthHttpError_keepsCode() {
        assertEquals(HomeStatus.HttpError(500), HomeStatusResolver.fromHealthResult(RouterResult.HttpError(500)))
    }

    @Test
    fun healthInvalidResponse_keepsMessage() {
        val status = HomeStatusResolver.fromHealthResult(RouterResult.InvalidResponse("bad body"))
        assertTrue(status is HomeStatus.InvalidResponse)
        assertEquals("bad body", (status as HomeStatus.InvalidResponse).message)
    }

    @Test
    fun healthConnectionFailed_isConnectionFailed() {
        assertEquals(
            HomeStatus.ConnectionFailed,
            HomeStatusResolver.fromHealthResult(RouterResult.ConnectionFailed("down")),
        )
    }

    @Test
    fun summary_countsConfiguredWarnedAndCandidates() {
        val summary = HomeStatusResolver.summaryOf(
            health(
                providers = listOf("ollama", "openai"),
                warnings = listOf(ProviderWarning("gemini", "missing env GEMINI_API_KEY")),
                candidates = 7,
            ),
        )
        assertEquals(2, summary.configured)
        assertEquals(1, summary.warned)
        assertEquals(7, summary.candidates)
        assertEquals(listOf("gemini"), summary.warnedProviderIds)
    }

    @Test
    fun summary_emptyHealthIsAllZero() {
        val summary = HomeStatusResolver.summaryOf(health(providers = emptyList(), warnings = emptyList(), candidates = 0))
        assertEquals(0, summary.configured)
        assertEquals(0, summary.warned)
        assertEquals(0, summary.candidates)
        assertTrue(summary.warnedProviderIds.isEmpty())
    }

    @Test
    fun modelsError_successIsNull() {
        assertNull(HomeStatusResolver.modelsError(RouterResult.Success(listOf("m1"))))
    }

    @Test
    fun modelsError_httpErrorIsShort() {
        assertEquals("Models unavailable (HTTP 503).", HomeStatusResolver.modelsError(RouterResult.HttpError(503)))
    }

    @Test
    fun modelsError_invalidResponseIsShort() {
        assertEquals("Models response was not valid.", HomeStatusResolver.modelsError(RouterResult.InvalidResponse("x")))
    }

    @Test
    fun modelsError_connectionFailedIsShort() {
        assertEquals("Could not load models.", HomeStatusResolver.modelsError(RouterResult.ConnectionFailed("x")))
    }

    @Test
    fun modelsError_authFailed() {
        assertEquals("Authentication failed.", HomeStatusResolver.modelsError(RouterResult.AuthenticationFailed))
    }
}
