package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.router.RouterHealth
import com.tariffia.panel.data.router.RouterResult

/**
 * Pure mapping from router results onto Home UI state pieces. Kept free of Android
 * APIs so the mapping can be unit tested on the JVM.
 */
object HomeStatusResolver {

    /** Maps a `/healthz` result to a [HomeStatus]. Success becomes [HomeStatus.Online]. */
    fun fromHealthResult(result: RouterResult<RouterHealth>): HomeStatus = when (result) {
        is RouterResult.Success -> HomeStatus.Online
        RouterResult.AuthenticationFailed -> HomeStatus.AuthFailed
        is RouterResult.HttpError -> HomeStatus.HttpError(result.code)
        is RouterResult.InvalidResponse -> HomeStatus.InvalidResponse(result.reason)
        is RouterResult.ConnectionFailed -> HomeStatus.ConnectionFailed
    }

    /** Counts directly from the router's `/healthz` payload; nothing is inferred. */
    fun summaryOf(health: RouterHealth): HomeProviderSummary = HomeProviderSummary(
        configured = health.providers.size,
        warned = health.warnings.size,
        candidates = health.candidates,
        warnedProviderIds = health.warnings.map { it.providerId },
    )

    /**
     * Short message for a failed `/v1/models` call while the router itself is Online.
     * Null for success (no error).
     */
    fun modelsError(result: RouterResult<List<String>>): String? = when (result) {
        is RouterResult.Success -> null
        RouterResult.AuthenticationFailed -> "Authentication failed."
        is RouterResult.HttpError -> "Models unavailable (HTTP ${result.code})."
        is RouterResult.InvalidResponse -> "Models response was not valid."
        is RouterResult.ConnectionFailed -> "Could not load models."
    }
}
