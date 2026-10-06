package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable

/**
 * The subset of `GET /healthz` the Panel uses. The router reports `providers` (IDs that
 * loaded successfully), `warnings` (providers skipped at load time, e.g. a missing
 * credential), and `health` (observed per-candidate reliability). None of this is secret.
 */
data class RouterHealth(
    val providers: List<String>,
    val warnings: List<ProviderWarning>,
    val candidates: Int,
    /** Observed reliability per candidate key (`provider/model`). Empty until observed. */
    val health: Map<String, CandidateHealth> = emptyMap(),
)

data class ProviderWarning(val providerId: String, val reason: String)

/**
 * Router-observed reliability for one candidate. [ewmaMs] and [successRate] are null until
 * the Router has a sample; a null is "no measurement", never a fabricated zero.
 */
data class CandidateHealth(
    val open: Boolean,
    val openForMs: Long,
    val ewmaMs: Long?,
    val attempts: Int,
    val successRate: Double?,
)

@Serializable
internal data class HealthResponse(
    val providers: List<String> = emptyList(),
    val candidates: Int = 0,
    val warnings: List<WarningEntry> = emptyList(),
    val health: Map<String, CandidateHealthEntry> = emptyMap(),
)

@Serializable
internal data class WarningEntry(
    val providerId: String,
    val reason: String = "",
)

@Serializable
internal data class CandidateHealthEntry(
    val open: Boolean = false,
    val openForMs: Long = 0,
    val ewmaMs: Long? = null,
    val attempts: Int = 0,
    val successRate: Double? = null,
)

/**
 * Parses a `/healthz` body. Unknown fields (quota, ...) are ignored.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseHealth(body: String): RouterHealth {
    val parsed = routerJson.decodeFromString<HealthResponse>(body)
    return RouterHealth(
        providers = parsed.providers,
        warnings = parsed.warnings.map { ProviderWarning(it.providerId, it.reason) },
        candidates = parsed.candidates,
        health = parsed.health.mapValues { (_, entry) ->
            CandidateHealth(
                open = entry.open,
                openForMs = entry.openForMs,
                ewmaMs = entry.ewmaMs,
                attempts = entry.attempts,
                successRate = entry.successRate,
            )
        },
    )
}
