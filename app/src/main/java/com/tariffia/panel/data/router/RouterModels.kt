package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** OpenAI-compatible `GET /v1/models` payload. Unknown fields are ignored. */
@Serializable
internal data class ModelsResponse(val data: List<ModelEntry> = emptyList())

@Serializable
internal data class ModelEntry(val id: String)

internal val routerJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Parses a `GET /v1/models` body into the list of model IDs the router actually
 * returned. No model names are hard-coded.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseModelIds(body: String): List<String> =
    routerJson.decodeFromString<ModelsResponse>(body).data.map { it.id }
