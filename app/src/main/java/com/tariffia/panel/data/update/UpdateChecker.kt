package com.tariffia.panel.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A newer public release the user can open in the browser. Never an APK. */
data class AvailableUpdate(val version: String, val url: String)

@Serializable
internal data class GitHubRelease(
    val tag_name: String = "",
    val html_url: String = "",
)

internal val updateJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Best-effort check of the latest PUBLIC NON-PRERELEASE GitHub release.
 *
 * No telemetry, no analytics, no third-party service: one anonymous GET to
 * api.github.com. Any failure (offline, timeout, 4xx/5xx, malformed, missing/invalid
 * fields, unparseable version) yields null and never throws into the UI.
 *
 * Reuses the existing OkHttp stack; no new dependency.
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultClient,
    private val latestUrl: String = LATEST_URL,
) {

    suspend fun check(currentVersion: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val release = updateJson.decodeFromString<GitHubRelease>(body)
                evaluateUpdate(currentVersion, release.tag_name, release.html_url)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val LATEST_URL =
            "https://api.github.com/repos/fileboin/tariffia-panel/releases/latest"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}

/**
 * Pure decision: a valid http(s) [htmlUrl] and a release tag numerically greater than
 * [currentVersion] yield an [AvailableUpdate]; anything unparseable yields null.
 */
internal fun evaluateUpdate(currentVersion: String, tagName: String, htmlUrl: String): AvailableUpdate? {
    val url = htmlUrl.trim()
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val latest = parseVersion(tagName) ?: return null
    val current = parseVersion(currentVersion) ?: return null
    if (compareVersions(latest, current) <= 0) return null
    return AvailableUpdate(version = latest.joinToString("."), url = url)
}

/**
 * Leading numeric dotted version of a tag, e.g. "v0.0.3" -> [0,0,3],
 * "v0.0.2-router" -> [0,0,2], "garbage"/"" -> null.
 */
internal fun parseVersion(raw: String): List<Int>? {
    val s = raw.trim().removePrefix("v").removePrefix("V")
    val match = Regex("^(\\d+(?:\\.\\d+)*)").find(s) ?: return null
    val parts = match.value.split('.').map { it.toIntOrNull() ?: return null }
    return parts.ifEmpty { null }
}

/** Component-wise numeric comparison; missing components are zero. */
internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}
