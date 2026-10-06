package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable

/**
 * Read-only usage/cost aggregates from the Router's `GET /v1/usage` (Router PR-D).
 * Metadata only — provider/model ids, request counts, token counts and cost. No key,
 * token, header, prompt or provider body is ever part of this shape.
 *
 * Unknown fields are ignored (see [routerJson]) so future Router additions do not break
 * the Panel. The Router is the single source of truth; the Panel never computes usage.
 */
@Serializable
data class RouterUsage(
    val since: String = "",
    val day: String = "",
    val lifetime: UsageScope = UsageScope(),
    val today: UsageScope = UsageScope(),
)

@Serializable
data class UsageScope(
    val totals: UsageTotals = UsageTotals(),
    val providers: List<UsageProvider> = emptyList(),
)

@Serializable
data class UsageTotals(
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
)

@Serializable
data class UsageProvider(
    val provider: String = "",
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
    val models: List<UsageModel> = emptyList(),
)

@Serializable
data class UsageModel(
    /** `${provider}/${model}` — the Router candidate key. */
    val model: String = "",
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
)

/**
 * Parses a `GET /v1/usage` body.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseUsage(body: String): RouterUsage =
    routerJson.decodeFromString<RouterUsage>(body)

/**
 * The usage entry for one model inside a scope, matched EXACTLY by the Router candidate
 * key (`provider/model` == [RouterModel.id]). Null when the Router has no entry for it
 * (no usage yet); the Router is never second-guessed.
 */
internal fun usageForModel(scope: UsageScope, modelId: String): UsageModel? =
    scope.providers.asSequence()
        .flatMap { it.models.asSequence() }
        .firstOrNull { it.model == modelId }
