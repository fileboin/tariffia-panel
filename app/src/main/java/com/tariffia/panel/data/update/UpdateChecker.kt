package com.tariffia.panel.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/** The installed app: its versionName and the time it was last updated (epoch millis). */
data class InstalledApp(val versionName: String, val lastUpdateTimeMs: Long)

/** A newer release the user can download and install. [htmlUrl] is the browser fallback. */
data class AvailableUpdate(val version: String, val htmlUrl: String, val apkUrl: String)

/** Rich outcome of an update check, so the UI can tell "up to date" from "unable to check". */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val update: AvailableUpdate) : UpdateResult
    data object Failed : UpdateResult
}

@Serializable
internal data class GitHubRelease(
    val tag_name: String = "",
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

internal val updateJson: Json = Json { ignoreUnknownKeys = true }

/** The installed versionName, read at runtime (BuildConfig is not enabled). */
fun installedVersion(context: Context): String = installedApp(context).versionName

/** The installed versionName and lastUpdateTime, read at runtime. */
fun installedApp(context: Context): InstalledApp =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        InstalledApp(info.versionName.orEmpty(), info.lastUpdateTime)
    }.getOrElse { InstalledApp("", 0L) }

/**
 * Best-effort check of the project's GitHub releases.
 *
 * The project ships PRERELEASE, ad-hoc-tagged releases, so `/releases/latest` (which only
 * returns non-prerelease releases) can never find them. This reads the releases LIST, keeps
 * releases that carry an APK asset, and selects the most recently published one — it never
 * assumes the first entry is correct.
 *
 * "Newer" is decided by numeric version when both the tag and the installed versionName
 * parse as dotted versions; otherwise it falls back to comparing the release's `published_at`
 * with the installed package's `lastUpdateTime`. This keeps a semantic-version model working
 * while still detecting new ad-hoc releases.
 *
 * No telemetry, no analytics, no third-party service: one anonymous GET to api.github.com.
 * Any failure (offline, timeout, 4xx/5xx, malformed) yields [UpdateResult.Failed], never throws.
 * Reuses the existing OkHttp stack; no new dependency.
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultClient,
    private val releasesUrl: String = RELEASES_URL,
) {

    /** Detailed outcome, for a manual "Check for updates" action. */
    suspend fun checkDetailed(installed: InstalledApp): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateResult.Failed
                val body = response.body?.string().orEmpty()
                val releases = updateJson.decodeFromString<List<GitHubRelease>>(body)
                val update = selectUpdate(releases, installed)
                if (update != null) UpdateResult.Available(update) else UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Failed
        }
    }

    /** Convenience for the automatic Home check: an update, or null (up to date / failed). */
    suspend fun check(installed: InstalledApp): AvailableUpdate? =
        when (val result = checkDetailed(installed)) {
            is UpdateResult.Available -> result.update
            else -> null
        }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/fileboin/tariffia-panel/releases?per_page=50"

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
 * Pure selection. Picks the most recently published non-draft release that carries an
 * installable APK asset, and returns it only when it is newer than [installed].
 * Returns null when there is no usable release or nothing newer.
 */
internal fun selectUpdate(releases: List<GitHubRelease>, installed: InstalledApp): AvailableUpdate? {
    val candidates = releases
        .asSequence()
        .filter { !it.draft }
        .mapNotNull { release -> apkAssetOf(release)?.let { release to it } }
        .toList()
    if (candidates.isEmpty()) return null

    val (release, asset) = candidates.maxByOrNull { parseTimestamp(it.first.published_at) ?: 0L }
        ?: return null

    val htmlUrl = release.html_url.trim()
    if (!isHttpUrl(htmlUrl)) return null
    if (!asset.browser_download_url.startsWith("https://")) return null
    if (!isNewer(release, installed)) return null

    val display = parseVersion(release.tag_name)?.joinToString(".") ?: release.tag_name.trim()
    if (display.isBlank()) return null
    return AvailableUpdate(display, htmlUrl, asset.browser_download_url)
}

/**
 * The installable APK asset of a release, if any. Prefers an asset whose name mentions the
 * panel; otherwise the first `.apk` asset. Never guesses a URL that is not https.
 */
internal fun apkAssetOf(release: GitHubRelease): GitHubAsset? {
    val apks = release.assets.filter {
        it.name.endsWith(".apk", ignoreCase = true) && it.browser_download_url.startsWith("https://")
    }
    return apks.firstOrNull { it.name.contains("panel", ignoreCase = true) } ?: apks.firstOrNull()
}

/** Whether [release] is newer than [installed]: numeric version first, then publish time. */
internal fun isNewer(release: GitHubRelease, installed: InstalledApp): Boolean {
    val latest = parseVersion(release.tag_name)
    val current = parseVersion(installed.versionName)
    if (latest != null && current != null) return compareVersions(latest, current) > 0
    val published = parseTimestamp(release.published_at)
    if (published != null && installed.lastUpdateTimeMs > 0L) return published > installed.lastUpdateTimeMs
    return false
}

private fun isHttpUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("http://")

/** GitHub ISO-8601 timestamp -> epoch millis, or null when unparseable. */
internal fun parseTimestamp(raw: String): Long? =
    runCatching { Instant.parse(raw.trim()).toEpochMilli() }.getOrNull()

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
