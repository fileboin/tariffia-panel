package com.tariffia.panel.ui.screens

import com.tariffia.panel.data.providers.ProviderStatus
import com.tariffia.panel.data.providers.ProviderStatusResolver
import com.tariffia.panel.data.router.RouterHealth
import com.tariffia.panel.data.router.RouterResult

/**
 * Router-reported status for one provider, with explicit states so the UI never shows
 * a fabricated Configured/Not configured when the router could not be reached.
 */
sealed interface RouterStatusView {
    data object Loading : RouterStatusView

    /** No router URL/token configured yet. */
    data object RouterNotConfigured : RouterStatusView

    /** The router was configured but could not be reached or answered with an error. */
    data object Unavailable : RouterStatusView

    /** The router answered; [status] is what it actually reported for this provider. */
    data class Available(val status: ProviderStatus, val note: String? = null) : RouterStatusView
}

/**
 * Pure mapping from a `/healthz` result to a [RouterStatusView]. A failed call is
 * [RouterStatusView.Unavailable]; a provider the router does not mention is
 * [ProviderStatus.UNKNOWN]. Status is never invented.
 */
object ProviderRouterStatusResolver {
    fun fromHealthResult(result: RouterResult<RouterHealth>, providerId: String): RouterStatusView =
        when (result) {
            is RouterResult.Success -> {
                val row = ProviderStatusResolver.resolve(
                    configuredIds = result.value.providers,
                    warnedReasons = result.value.warnings.associate { it.providerId to it.reason },
                ).firstOrNull { it.id.equals(providerId, ignoreCase = true) }
                RouterStatusView.Available(row?.status ?: ProviderStatus.UNKNOWN, row?.note)
            }
            else -> RouterStatusView.Unavailable
        }
}
