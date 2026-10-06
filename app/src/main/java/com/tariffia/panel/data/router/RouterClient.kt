package com.tariffia.panel.data.router

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * Minimal HTTP client for the Tariffia Router.
 *
 * Read-only endpoints: `GET /healthz` and `GET /v1/models`. It also writes a single
 * provider key: `PUT /v1/providers/{providerId}/key`, the narrow key-sync route.
 *
 * The token is sent as `Authorization: Bearer <token>`. It is never logged, and no
 * failure carries a response body or the key.
 *
 * [httpClient] defaults to the shared, process-scoped [SharedRouterHttpClient]; this
 * class does not own or close it. Tests may inject a client.
 */
class RouterClient(
    private val httpClient: OkHttpClient = SharedRouterHttpClient.instance,
) {

    suspend fun checkHealth(baseUrl: String, token: String?): RouterResult<Unit> =
        request(baseUrl, PATH_HEALTHZ, token) { RouterResult.Success(Unit) }

    suspend fun fetchHealth(baseUrl: String, token: String?): RouterResult<RouterHealth> =
        request(baseUrl, PATH_HEALTHZ, token) { body ->
            try {
                RouterResult.Success(parseHealth(body))
            } catch (e: Exception) {
                RouterResult.InvalidResponse("Could not parse the health response.")
            }
        }

    suspend fun fetchModels(baseUrl: String, token: String?): RouterResult<List<String>> =
        request(baseUrl, PATH_MODELS, token) { body ->
            try {
                RouterResult.Success(parseModelIds(body))
            } catch (e: Exception) {
                RouterResult.InvalidResponse("Could not parse the models response.")
            }
        }

    /**
     * Reads the Router's provider list and metadata (`GET /v1/providers`). The Router
     * registry is the single source of truth; no provider list is hard-coded in Panel.
     * The response carries no key/secret field.
     */
    suspend fun fetchProviders(baseUrl: String, token: String?): RouterResult<List<RouterProvider>> =
        request(baseUrl, PATH_PROVIDERS, token) { body ->
            try {
                RouterResult.Success(parseProviders(body))
            } catch (e: Exception) {
                RouterResult.InvalidResponse("Could not parse the providers response.")
            }
        }

    /**
     * Sends a provider API key to the Router's key-sync route. The key is placed in the
     * request body only; it is never logged and never surfaced in the result.
     */
    suspend fun syncProviderKey(
        baseUrl: String,
        token: String?,
        providerId: String,
        key: String,
    ): RouterResult<Unit> {
        val path = "/v1/providers/${URLEncoder.encode(providerId, "UTF-8")}/key"
        val jsonBody = buildJsonObject { put("key", key) }.toString()
        return requestWithBody(baseUrl, path, token, jsonBody) { RouterResult.Success(Unit) }
    }

    private suspend fun <T> request(
        baseUrl: String,
        path: String,
        token: String?,
        onSuccess: (body: String) -> RouterResult<T>,
    ): RouterResult<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .get()
                .header("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> onSuccess(response.body?.string().orEmpty())
                    response.code == 401 || response.code == 403 -> RouterResult.AuthenticationFailed
                    else -> RouterResult.HttpError(response.code)
                }
            }
        } catch (e: IOException) {
            RouterResult.ConnectionFailed("Connection failed")
        } catch (e: IllegalArgumentException) {
            RouterResult.ConnectionFailed("Invalid router URL")
        }
    }

    private suspend fun <T> requestWithBody(
        baseUrl: String,
        path: String,
        token: String?,
        jsonBody: String,
        onSuccess: (body: String) -> RouterResult<T>,
    ): RouterResult<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .put(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> onSuccess(response.body?.string().orEmpty())
                    response.code == 401 || response.code == 403 -> RouterResult.AuthenticationFailed
                    else -> RouterResult.HttpError(response.code)
                }
            }
        } catch (e: IOException) {
            RouterResult.ConnectionFailed("Connection failed")
        } catch (e: IllegalArgumentException) {
            RouterResult.ConnectionFailed("Invalid router URL")
        }
    }

    private companion object {
        const val PATH_HEALTHZ = "/healthz"
        const val PATH_MODELS = "/v1/models"
        const val PATH_PROVIDERS = "/v1/providers"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
