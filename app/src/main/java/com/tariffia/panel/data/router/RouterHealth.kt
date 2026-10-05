package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable

/**
 * The subset of `GET /healthz` the Providers screen uses. The router reports
 * `providers` (IDs that loaded successfully) and `warnings` (providers skipped at
 * load time, e.g. a missing credential). Both are non-secret.
 */
data class RouterHealth(
    val providers: List<String>,
    val warnings: List<ProviderWarning>,
    val candidates: Int,
)

data class ProviderWarning(val providerId: String, val reason: String)

@Serializable
internal data class HealthResponse(
    val providers: List<String> = emptyList(),
    val candidates: Int = 0,
    val warnings: List<WarningEntry> = emptyList(),
)

@Serializable
internal data class WarningEntry(
    val providerId: String,
    val reason: String = "",
)

/**
 * Parses a `/healthz` body. Unknown fields (health, quota) are ignored.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseHealth(body: String): RouterHealth {
    val parsed = routerJson.decodeFromString<HealthResponse>(body)
    return RouterHealth(
        providers = parsed.providers,
        warnings = parsed.warnings.map { ProviderWarning(it.providerId, it.reason) },
        candidates = parsed.candidates,
    )
}
