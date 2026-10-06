package com.tariffia.panel.data.providers

/** Provider status as reported by the router. Never invented. */
enum class ProviderStatus {
    CONFIGURED,
    NOT_CONFIGURED,
    UNKNOWN,
}

/** One row on the Providers screen. */
data class ProviderRow(
    val id: String,
    val displayName: String,
    val status: ProviderStatus,
    /** Router load-time warning reason for this provider, if any. */
    val note: String? = null,
    /** Whether an API key for this provider is stored locally (device only). */
    val hasLocalKey: Boolean = false,
    /** Router-reported one-line summary (metadata only). */
    val summary: String? = null,
    /** Router-reported free-tier note (metadata only). */
    val freeTierNote: String? = null,
    /** True when the provider needs no credential at all. */
    val keyless: Boolean = false,
    /** Number of models the Router registry declares for this provider. */
    val modelCount: Int = 0,
)

/**
 * The Router-reported facts for one provider, as returned by `GET /v1/providers`.
 * This is the authoritative input for the provider list; the Panel never invents a
 * provider that the Router did not report.
 */
data class ProviderFacts(
    val id: String,
    val configured: Boolean,
    val keyless: Boolean,
    val modelCount: Int,
    val summary: String? = null,
    val freeTierNote: String? = null,
)

/**
 * Optional, display-only names. This is NOT a provider list: it must never decide
 * which providers exist (the Router does). Unknown ids fall back to the id itself.
 */
object ProviderDisplayNames {
    private val names = mapOf(
        "openai" to "OpenAI",
        "deepinfra" to "DeepInfra",
        "openrouter" to "OpenRouter",
        "ollama" to "Ollama",
        "anthropic" to "Anthropic",
        "gemini" to "Gemini",
        "google" to "Gemini",
        "together" to "Together",
        "zai" to "Z.ai",
    )

    fun nameFor(id: String): String = names[id.trim().lowercase()] ?: id
}

/**
 * Maps Router-reported data onto [ProviderRow]s.
 *
 * - The list comes ONLY from [facts] (the Router registry); nothing is hard-coded.
 * - A provider is usable when it is Router-configured or keyless; otherwise it is
 *   Not configured (with the Router warning reason when present).
 * - The local (device-only) key flag is carried separately and never decrypts a key.
 */
object ProviderStatusResolver {

    /** Status for one provider from the `/healthz` view (configured ids + warnings). */
    fun statusFor(
        providerId: String,
        configuredIds: List<String>,
        warnedReasons: Map<String, String>,
    ): Pair<ProviderStatus, String?> {
        val id = providerId.trim().lowercase()
        val configured = configuredIds.map { it.trim().lowercase() }.toSet()
        val warned = warnedReasons.mapKeys { it.key.trim().lowercase() }
        return when {
            id in configured -> ProviderStatus.CONFIGURED to null
            id in warned -> ProviderStatus.NOT_CONFIGURED to warned[id]
            else -> ProviderStatus.UNKNOWN to null
        }
    }

    /**
     * Builds the provider rows from Router facts. Ordering is stable (by provider id).
     * [hasLocalKey] only affects the local-key flag, never the Router status.
     */
    fun resolve(
        facts: List<ProviderFacts>,
        warnedReasons: Map<String, String> = emptyMap(),
        hasLocalKey: (String) -> Boolean = { false },
    ): List<ProviderRow> {
        val warned = warnedReasons.mapKeys { it.key.trim().lowercase() }
        return facts
            .map { it.id.trim().lowercase() to it }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .map { (id, fact) ->
                val usable = fact.configured || fact.keyless
                ProviderRow(
                    id = id,
                    displayName = ProviderDisplayNames.nameFor(id),
                    status = if (usable) ProviderStatus.CONFIGURED else ProviderStatus.NOT_CONFIGURED,
                    note = warned[id],
                    hasLocalKey = hasLocalKey(id),
                    summary = fact.summary,
                    freeTierNote = fact.freeTierNote,
                    keyless = fact.keyless,
                    modelCount = fact.modelCount,
                )
            }
    }
}
