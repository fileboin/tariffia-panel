package com.tariffia.panel.data.router

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Minimal HTTP client for the Tariffia Router. Only the two read-only endpoints the
 * Home screen needs are implemented: `GET /healthz` and `GET /v1/models`.
 *
 * The token is sent as `Authorization: Bearer <token>`. It is never logged and never
 * included in any error surfaced to callers (failures carry no response body).
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

    private companion object {
        const val PATH_HEALTHZ = "/healthz"
        const val PATH_MODELS = "/v1/models"
    }
}
