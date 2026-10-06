package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable

/**
 * OpenAI/Router `GET /v1/providers` payload. The Router registry is the single source
 * of truth for which providers exist; this is metadata only. No key/secret field is
 * ever part of this shape (the Router reports `configured`/`keyless`, never a value).
 *
 * Unknown fields are ignored (see [routerJson]) so future Router metadata does not
 * break the Panel.
 */
@Serializable
internal data class ProvidersResponse(
    val providers: List<RouterProvider> = emptyList(),
    val candidates: Int = 0,
    val usable: List<String> = emptyList(),
)

@Serializable
data class RouterProvider(
    val id: String,
    val summary: String? = null,
    val freeTierNote: String? = null,
    val signupUrl: String? = null,
    val signupSteps: Map<String, List<String>>? = null,
    val keyPrefix: String? = null,
    val keyless: Boolean = false,
    val configured: Boolean = false,
    val models: Int = 0,
)

/**
 * Parses a `GET /v1/providers` body into the list of providers the Router reports.
 * No provider list is hard-coded; the Router is authoritative.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseProviders(body: String): List<RouterProvider> =
    routerJson.decodeFromString<ProvidersResponse>(body).providers
