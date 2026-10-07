package com.tariffia.panel.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A newer public release the user can open in the browser. Never an APK. */
data class AvailableUpdate(val version: String, val url: String)

/** Rich outcome of an update check, so the UI can tell "up to date" from "unable to check". */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val version: String, val url: String) : UpdateResult
    data object Failed : UpdateResult
}

@Serializable
internal data class GitHubRelease(
    val tag_name: String = "",
    val html_url: String = "",
)

internal val updateJson: Json = Json { ignoreUnknownKeys = true }

/** The installed versionName, read at runtime (BuildConfig is not enabled). */
fun installedVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

/**
 * Best-effort check of the latest PUBLIC NON-PRERELEASE GitHub release.
 *
 * No telemetry, no analytics, no third-party service: one anonymous GET to
 * api.github.com. Any failure (offline, timeout, 4xx/5xx, malformed, missing/invalid
 * fields, unparseable version) yields [UpdateResult.Failed] and never throws.
 *
 * Reuses the existing OkHttp stack; no new dependency.
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultClient,
    private val latestUrl: String = LATEST_URL,
) {

    /** Detailed outcome, for a manual "Check for updates" action. */
    suspend fun checkDetailed(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateResult.Failed
                val body = response.body?.string().orEmpty()
                val release = updateJson.decodeFromString<GitHubRelease>(body)
                evaluateResult(currentVersion, release.tag_name, release.html_url)
            }
        } catch (e: Exception) {
            UpdateResult.Failed
        }
    }

    /** Convenience for the automatic Home check: an update, or null (up to date / failed). */
    suspend fun check(currentVersion: String): AvailableUpdate? =
        when (val result = checkDetailed(currentVersion)) {
            is UpdateResult.Available -> AvailableUpdate(result.version, result.url)
            else -> null
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
 * Pure decision. A valid http(s) [htmlUrl] and a parseable release tag produce
 * [UpdateResult.Available] when newer or [UpdateResult.UpToDate] otherwise; an invalid
 * url or an unparseable tag/version is [UpdateResult.Failed] (never a guessed version).
 */
internal fun evaluateResult(currentVersion: String, tagName: String, htmlUrl: String): UpdateResult {
    val url = htmlUrl.trim()
    if (!url.startsWith("https://") && !url.startsWith("http://")) return UpdateResult.Failed
    val latest = parseVersion(tagName) ?: return UpdateResult.Failed
    val current = parseVersion(currentVersion) ?: return UpdateResult.Failed
    return if (compareVersions(latest, current) > 0) {
        UpdateResult.Available(latest.joinToString("."), url)
    } else {
        UpdateResult.UpToDate
    }
}

/** Convenience wrapper over [evaluateResult] for callers that only care about "newer". */
internal fun evaluateUpdate(currentVersion: String, tagName: String, htmlUrl: String): AvailableUpdate? =
    (evaluateResult(currentVersion, tagName, htmlUrl) as? UpdateResult.Available)
        ?.let { AvailableUpdate(it.version, it.url) }

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
