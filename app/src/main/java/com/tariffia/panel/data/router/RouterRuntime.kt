package com.tariffia.panel.data.router

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Hosts the embedded Node 24 runtime and starts the existing Tariffia Router
 * `serve` CLI inside the Panel process (one Node instance, one Router process).
 *
 * The native plumbing is ported from the proven PoC: the unmodified Router `dist`
 * + `registry` bundle is extracted from assets into the app files dir, environment
 * variables are set before boot, and `node::Start()` runs on a dedicated thread.
 *
 * PR1 scope only: a single start with a temporary local test token. The Router is
 * bound to 127.0.0.1:8910. No key sync, no foreground service, no production lifecycle.
 */
object RouterRuntime {

    const val HOST = "127.0.0.1"
    const val PORT = 8910
    const val BASE_URL = "http://$HOST:$PORT"

    /** Temporary PR1 token shared by the started Router and the readiness check. */
    const val TEST_TOKEN = "panel-local-test-token-0000000000000000"

    private const val ROUTER_DIR_NAME = "router"
    private const val DIST_ZIP_ASSET = "router-dist.zip"
    private const val LAUNCHER_ASSET = "router-launcher.mjs"

    private const val HEALTH_TIMEOUT_MS = 20_000L
    private const val HEALTH_POLL_INTERVAL_MS = 500L

    @Volatile
    private var started = false

    private val client = RouterClient()

    init {
        // libnode.so must be resolvable before the bridge that depends on it.
        System.loadLibrary("node")
        System.loadLibrary("nodepoc")
    }

    private external fun nativeStart(
        workDir: String,
        scriptPath: String,
        stdoutPath: String,
        stderrPath: String,
        envPairs: Array<String>,
        nodeArgs: Array<String>
    ): Boolean

    external fun nativeNodeVersion(): String

    data class StartResult(val started: Boolean, val message: String)

    private fun routerDir(ctx: Context): File = File(ctx.filesDir, ROUTER_DIR_NAME)

    private fun infoFile(ctx: Context): File = File(ctx.filesDir, "node-info.json")

    /** Extracts the unmodified Router bundle (dist/ + registry/) on first use. */
    private fun ensureRuntime(ctx: Context): File {
        val root = routerDir(ctx)
        val marker = File(root, "dist/src/cli/index.js")
        if (!marker.exists()) {
            root.mkdirs()
            ctx.assets.open(DIST_ZIP_ASSET).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(root, entry.name)
                        // Defensive: keep every extracted path inside root.
                        if (!out.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                            throw SecurityException("zip entry escapes root: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { fos -> zip.copyTo(fos) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }
        val launcher = File(root, LAUNCHER_ASSET)
        if (!launcher.exists()) {
            ctx.assets.open(LAUNCHER_ASSET).use { input ->
                FileOutputStream(launcher).use { fos -> input.copyTo(fos) }
            }
        }
        return root
    }

    /** Starts the embedded Node runtime exactly once. */
    fun start(ctx: Context): StartResult {
        if (started) return StartResult(true, "already started")
        val root = try {
            ensureRuntime(ctx)
        } catch (e: Exception) {
            return StartResult(false, "could not prepare runtime: ${e.message}")
        }

        val launcher = File(root, LAUNCHER_ASSET).absolutePath
        val entry = File(root, "dist/src/cli/index.js").absolutePath
        val registry = File(root, "registry/ollama.json").absolutePath
        val stdout = File(root, "node.out").absolutePath
        val stderr = File(root, "node.err").absolutePath

        val env = arrayOf(
            "TARIFFIA_HOST", HOST,
            "TARIFFIA_PORT", PORT.toString(),
            "TARIFFIA_TOKEN", TEST_TOKEN,
            "TARIFFIA_MODE", "FREE_ONLY",
            "TARIFFIA_REGISTRY", registry,
            "TMPDIR", ctx.cacheDir.absolutePath,
            "HOME", ctx.filesDir.absolutePath,
            "NODE_OPTIONS", "--max-old-space-size-percentage=50",
            "NODE_COMPILE_CACHE", File(ctx.cacheDir, "ncc").absolutePath,
            "NODE_COMPILE_CACHE_PORTABLE", "1",
            "ROUTER_ENTRY", entry,
            "ROUTER_INFO_FILE", infoFile(ctx).absolutePath
        )

        val ok = try {
            nativeStart(root.absolutePath, launcher, stdout, stderr, env, arrayOf(launcher))
        } catch (e: Throwable) {
            return StartResult(false, "native start failed: ${e.message}")
        }
        if (!ok) return StartResult(false, "nativeStart returned false")
        started = true
        return StartResult(true, "node::Start launched")
    }

    /**
     * Bounded readiness check: polls `GET /healthz` (with the Router bearer token)
     * until it returns 200 or the timeout elapses.
     */
    suspend fun awaitHealthy(
        baseUrl: String = BASE_URL,
        token: String = TEST_TOKEN,
        timeoutMs: Long = HEALTH_TIMEOUT_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (client.fetchHealth(baseUrl, token)) {
                is RouterResult.Success -> return@withContext true
                else -> delay(HEALTH_POLL_INTERVAL_MS)
            }
        }
        false
    }
}
