package com.tariffia.panel.data.router

import kotlinx.serialization.SerialName
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

/* -------------------------------------------------------------------------- */
/* Full catalog (read-only model metadata)                                    */
/* -------------------------------------------------------------------------- */

/**
 * The Router-defined `mesh` block on a model entry. Field names mirror the wire
 * format exactly (snake_case). Unknown fields are ignored. No secret is present.
 */
@Serializable
data class RouterModelMesh(
    val kind: String = "",
    val capabilities: List<String> = emptyList(),
    @SerialName("context_window") val contextWindow: Int = 0,
    @SerialName("price_per_mtok_blended") val pricePerMTokBlended: Double = 0.0,
    @SerialName("max_privacy") val maxPrivacy: String? = null,
)

/**
 * One entry of `GET /v1/models`. The Router emits both routing profiles
 * (`mesh.kind == "profile"`, `owned_by == "inferencemesh"`) and real models
 * (`mesh.kind == "model"`, `owned_by == provider id`). Metadata only — never a key.
 */
@Serializable
data class RouterModel(
    val id: String,
    @SerialName("owned_by") val ownedBy: String = "",
    val mesh: RouterModelMesh = RouterModelMesh(),
)

@Serializable
internal data class CatalogResponse(val data: List<RouterModel> = emptyList())

/**
 * Parses the full `GET /v1/models` catalog (profiles + models). Unknown fields are
 * ignored and never surfaced.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseModelCatalog(body: String): List<RouterModel> =
    routerJson.decodeFromString<CatalogResponse>(body).data

/**
 * The models the Router reports for one provider. The Router defines the relationship
 * explicitly via `owned_by`; this does not infer it from the id prefix.
 *
 * - `mesh.kind` must be exactly `"model"` (routing profiles are excluded).
 * - `owned_by` must match [providerId] case-insensitively.
 * - Router ordering is preserved; an empty list is returned when nothing matches.
 */
internal fun modelsForProvider(models: List<RouterModel>, providerId: String): List<RouterModel> {
    val id = providerId.trim().lowercase()
    return models.filter { it.mesh.kind == "model" && it.ownedBy.trim().lowercase() == id }
}
