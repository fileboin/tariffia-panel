package com.tariffia.panel.data.router

import android.content.Context
import com.tariffia.panel.data.SecureSettingsStore
import kotlinx.coroutines.Dispatchers
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
 * PR2: the Router URL is auto-set to loopback and a local random bearer token is
 * generated once and stored (encrypted) in [SecureSettingsStore]; the same token is
 * passed to the Router as `TARIFFIA_TOKEN`. Readiness is a bounded `/healthz` poll
 * using that token. No key sync, no foreground service, no production lifecycle.
 */
object RouterRuntime {

    const val HOST = "127.0.0.1"
    const val PORT = 8910
    const val BASE_URL = RouterLocalConfig.LOCAL_URL

    private const val ROUTER_DIR_NAME = "router"
    private const val DIST_ZIP_ASSET = "router-dist.zip"
    private const val LAUNCHER_ASSET = "router-launcher.mjs"

    private const val HEALTH_TIMEOUT_MS = 20_000L
    private const val HEALTH_POLL_INTERVAL_MS = 500L

    @Volatile
    private var started = false

    /** Token the running Router was started with; used by the readiness probe. */
    @Volatile
    private var activeToken: String? = null

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

    /**
     * Ensures the embedded Router config exists: URL = loopback, token = the stored
     * one or a freshly generated random token. Reuses [SecureSettingsStore] (Keystore
     * encryption) — no separate storage.
     */
    fun ensureLocalConfig(ctx: Context): RouterLocalConfig.Resolved {
        val store = SecureSettingsStore(ctx)
        val resolved = RouterLocalConfig.resolve(store.readToken()) { RouterToken.generate() }
        // Persist the loopback URL; store the token only when it was just generated.
        store.save(
            RouterLocalConfig.LOCAL_URL,
            if (resolved.tokenWasGenerated) resolved.token else null,
        )
        return resolved
    }

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

        val config = try {
            ensureLocalConfig(ctx)
        } catch (e: Exception) {
            return StartResult(false, "could not prepare router config: ${e.message}")
        }
        activeToken = config.token

        val launcher = File(root, LAUNCHER_ASSET).absolutePath
        val entry = File(root, "dist/src/cli/index.js").absolutePath
        val registry = File(root, "registry/ollama.json").absolutePath
        val stdout = File(root, "node.out").absolutePath
        val stderr = File(root, "node.err").absolutePath

        val env = arrayOf(
            "TARIFFIA_HOST", HOST,
            "TARIFFIA_PORT", PORT.toString(),
            "TARIFFIA_TOKEN", config.token,
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
     * One-shot `/healthz` probe with the stored token. Used to detect an already
     * running Router so a second Node/Router process is never started.
     */
    suspend fun isHealthyNow(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val config = ensureLocalConfig(ctx)
        client.fetchHealth(config.url, config.token) is RouterResult.Success
    }

    /**
     * Bounded readiness check: polls `GET /healthz` (with the Router bearer token)
     * until it returns 200 or the timeout elapses.
     */
    suspend fun awaitHealthy(
        ctx: Context,
        timeoutMs: Long = HEALTH_TIMEOUT_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        val token = activeToken ?: ensureLocalConfig(ctx).token
        ReadinessWaiter.awaitReady(
            timeoutMs = timeoutMs,
            pollIntervalMs = HEALTH_POLL_INTERVAL_MS,
            probe = { client.fetchHealth(BASE_URL, token) is RouterResult.Success },
        )
    }
}
