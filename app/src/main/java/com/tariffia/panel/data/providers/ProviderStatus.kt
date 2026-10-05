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
    val note: String? = null,
)

/** A display name plus the registry IDs it may appear under. */
data class KnownProvider(val displayName: String, val aliases: List<String>)

/** The providers the screen is expected to show. Names only; statuses come from data. */
object ProviderCatalog {
    val known: List<KnownProvider> = listOf(
        KnownProvider("OpenAI", listOf("openai")),
        KnownProvider("Together", listOf("together", "togetherai", "together-ai")),
        KnownProvider("DeepInfra", listOf("deepinfra")),
        KnownProvider("Z.ai", listOf("zai", "z-ai", "zai-org", "zhipu")),
        KnownProvider("Gemini", listOf("gemini", "google", "google-gemini")),
        KnownProvider("Token Router", listOf("token-router", "tokenrouter", "token_router")),
        KnownProvider("Ollama", listOf("ollama")),
    )
}

/**
 * Maps the router's `/healthz` output onto [ProviderRow]s.
 *
 * - An ID in `configuredIds` -> [ProviderStatus.CONFIGURED].
 * - An ID with a warning (skipped at load, e.g. missing credential) ->
 *   [ProviderStatus.NOT_CONFIGURED], with the reason as a note.
 * - Anything the router does not mention -> [ProviderStatus.UNKNOWN]. Status is
 *   never invented.
 * - Provider IDs the router reports that are not in the catalog are appended as-is.
 */
object ProviderStatusResolver {

    fun resolve(
        configuredIds: List<String>,
        warnedReasons: Map<String, String>,
    ): List<ProviderRow> {
        val configured = configuredIds.map { it.trim().lowercase() }.toSet()
        val warned = warnedReasons.mapKeys { it.key.trim().lowercase() }
        val claimed = mutableSetOf<String>()

        val knownRows = ProviderCatalog.known.map { known ->
            val match = known.aliases
                .map { it.lowercase() }
                .firstOrNull { alias -> alias in configured || alias in warned }
            if (match != null) claimed += match
            ProviderRow(
                id = match ?: known.aliases.first(),
                displayName = known.displayName,
                status = when {
                    match == null -> ProviderStatus.UNKNOWN
                    match in configured -> ProviderStatus.CONFIGURED
                    else -> ProviderStatus.NOT_CONFIGURED
                },
                note = match?.let { warned[it] },
            )
        }

        val extraRows = (configured + warned.keys)
            .filter { it !in claimed }
            .sorted()
            .map { id ->
                ProviderRow(
                    id = id,
                    displayName = id,
                    status = if (id in configured) ProviderStatus.CONFIGURED else ProviderStatus.NOT_CONFIGURED,
                    note = warned[id],
                )
            }

        return knownRows + extraRows
    }

    /** All catalog rows with [ProviderStatus.UNKNOWN] (used when no data is available). */
    fun unknown(): List<ProviderRow> = resolve(emptyList(), emptyMap())
}
