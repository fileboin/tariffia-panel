package com.tariffia.panel.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** JVM tests for version comparison, release/asset selection and the best-effort check. */
class UpdateCheckerTest {

    private fun release(
        tag: String,
        publishedAt: String = "2026-10-01T00:00:00Z",
        draft: Boolean = false,
        htmlUrl: String = "https://github.com/fileboin/tariffia-panel/releases/tag/$tag",
        assets: List<GitHubAsset> = listOf(apk("tariffia-panel-$tag.apk")),
    ) = GitHubRelease(
        tag_name = tag,
        html_url = htmlUrl,
        draft = draft,
        prerelease = true,
        published_at = publishedAt,
        assets = assets,
    )

    private fun apk(name: String) = GitHubAsset(
        name = name,
        browser_download_url = "https://github.com/fileboin/tariffia-panel/releases/download/x/$name",
    )

    private val installed = InstalledApp("0.0.2", parseTimestamp("2026-10-01T00:00:00Z")!!)

    /* ------------------------------- pure helpers ------------------------------- */

    @Test
    fun parseVersionHandlesPrefixAndSuffix() {
        assertEquals(listOf(0, 0, 3), parseVersion("v0.0.3"))
        assertEquals(listOf(0, 0, 2), parseVersion("0.0.2-router"))
        assertEquals(listOf(1, 2, 3), parseVersion("V1.2.3"))
        assertEquals(listOf(2), parseVersion("2"))
        assertNull(parseVersion("garbage"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("v"))
        assertNull(parseVersion("pr19-keyless-ssh-start-550abd4"))
    }

    @Test
    fun compareVersionsIsNumericAndZeroFilled() {
        assertTrue(compareVersions(listOf(0, 0, 10), listOf(0, 0, 9)) > 0)
        assertEquals(0, compareVersions(listOf(0, 0, 2), listOf(0, 0, 2)))
        assertEquals(0, compareVersions(listOf(1), listOf(1, 0, 0)))
        assertTrue(compareVersions(listOf(0, 1), listOf(0, 0, 5)) > 0)
    }

    @Test
    fun parseTimestamp_iso8601() {
        assertEquals(0L, parseTimestamp("1970-01-01T00:00:00Z"))
        assertNull(parseTimestamp("not a date"))
        assertNull(parseTimestamp(""))
    }

    /* ------------------------------- selection ---------------------------------- */

    @Test
    fun doesNotAssumeFirstReleaseIsCorrect() {
        // First entry is a draft; second has no APK asset; third is the real one.
        val releases = listOf(
            release("draft", draft = true),
            release("no-asset", assets = emptyList()),
            release("v0.0.3"),
        )
        val update = selectUpdate(releases, installed)
        assertEquals("0.0.3", update?.version)
    }

    @Test
    fun picksMostRecentlyPublishedApkBearingRelease() {
        val releases = listOf(
            release("v0.0.3", publishedAt = "2026-09-01T00:00:00Z"),
            release("v0.0.4", publishedAt = "2026-09-20T00:00:00Z"),
            release("v0.0.5", publishedAt = "2026-09-10T00:00:00Z"),
        )
        val update = selectUpdate(releases, installed)
        assertEquals("0.0.4", update?.version)
    }

    @Test
    fun newestPublishedReleaseIsSelected() {
        val releases = listOf(
            release("v0.0.5", publishedAt = "2026-09-01T00:00:00Z"),
            release("v0.0.4", publishedAt = "2026-09-20T00:00:00Z"),
        )
        // Selection is by newest published_at; the version is then compared to decide "newer".
        assertEquals("0.0.4", selectUpdate(releases, installed)?.version)
    }

    @Test
    fun currentVersionIsUpToDate() {
        assertNull(selectUpdate(listOf(release("v0.0.2")), installed))
    }

    @Test
    fun olderVersionIsUpToDate() {
        assertNull(selectUpdate(listOf(release("v0.0.1")), installed))
    }

    @Test
    fun adHocTagFallsBackToPublishedTime() {
        // Tag does not parse: a release published after the install time is an update.
        val newer = release("pr19-keyless-ssh-start-550abd4", publishedAt = "2026-10-05T00:00:00Z")
        val update = selectUpdate(listOf(newer), installed)
        assertEquals("pr19-keyless-ssh-start-550abd4", update?.version)
        assertTrue(update!!.apkUrl.endsWith(".apk"))
    }

    @Test
    fun adHocTagPublishedBeforeInstallIsUpToDate() {
        val older = release("pr18-fix", publishedAt = "2026-09-01T00:00:00Z")
        assertNull(selectUpdate(listOf(older), installed))
    }

    @Test
    fun assetSelectionPrefersPanelApk() {
        val r = release(
            "v0.0.3",
            assets = listOf(apk("other.apk"), apk("tariffia-panel-debug.apk")),
        )
        assertEquals("tariffia-panel-debug.apk", apkAssetOf(r)?.name)
    }

    @Test
    fun nonHttpsAssetIsIgnored() {
        val r = release("v0.0.3", assets = listOf(GitHubAsset("x.apk", "http://insecure/x.apk")))
        assertNull(apkAssetOf(r))
        assertNull(selectUpdate(listOf(r), installed))
    }

    @Test
    fun emptyReleasesIsNull() {
        assertNull(selectUpdate(emptyList(), installed))
    }

    /* ------------------------------- HTTP behavior ------------------------------ */

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (ignored: Exception) {
            // Already shut down by a test.
        }
    }

    private fun checker() = UpdateChecker(OkHttpClient(), server.url("/releases?per_page=50").toString())

    private fun body(vararg tags: String): String {
        val entries = tags.joinToString(",") { tag ->
            """{"tag_name":"$tag","html_url":"https://github.com/fileboin/tariffia-panel/releases/tag/$tag","draft":false,"prerelease":true,"published_at":"2026-10-05T00:00:00Z","assets":[{"name":"tariffia-panel-$tag.apk","browser_download_url":"https://github.com/fileboin/tariffia-panel/releases/download/$tag/tariffia-panel-$tag.apk"}]}"""
        }
        return "[$entries]"
    }

    @Test
    fun newerRelease_returnsUpdate_andSendsAcceptHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body("v0.0.3")))
        val result = checker().checkDetailed(installed)
        assertTrue(result is UpdateResult.Available)
        val update = (result as UpdateResult.Available).update
        assertEquals("0.0.3", update.version)
        assertEquals("https://github.com/fileboin/tariffia-panel/releases/tag/v0.0.3", update.htmlUrl)
        assertTrue(update.apkUrl.endsWith(".apk"))
        val recorded = server.takeRequest()
        assertEquals("/releases?per_page=50", recorded.path)
        assertEquals("application/vnd.github+json", recorded.getHeader("Accept"))
    }

    @Test
    fun adHocPrereleaseTag_isFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body("pr19-keyless-ssh-start-550abd4")))
        val result = checker().checkDetailed(installed)
        assertNotNull(result as? UpdateResult.Available)
    }

    @Test
    fun emptyReleaseList_isUpToDate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertEquals(UpdateResult.UpToDate, checker().checkDetailed(installed))
    }

    @Test
    fun malformedJson_isFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertEquals(UpdateResult.Failed, checker().checkDetailed(installed))
    }

    @Test
    fun http404_isFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(UpdateResult.Failed, checker().checkDetailed(installed))
    }

    @Test
    fun unreachable_isFailedWithoutThrowing() = runBlocking {
        val c = checker()
        server.shutdown()
        assertEquals(UpdateResult.Failed, c.checkDetailed(installed))
        assertNull(c.check(installed))
    }
}
