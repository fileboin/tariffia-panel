package com.tariffia.panel.data.router

import android.content.Context
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.providers.ProviderKeySyncRunner
import com.tariffia.panel.data.providers.summaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * PR4: [RouterService] is the only component that drives this runtime; the state and
 * the key-sync summary are exposed as flows for the UI. The singleton and its native
 * one-instance guard are preserved. Embedded Node has no graceful shutdown/restart
 * path, so [markStopped] only updates state before the service terminates the process.
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

    /** Runtime state for the UI. No secrets. */
    sealed interface State {
        data object Idle : State
        data object Starting : State
        data object Ready : State
        data object Stopped : State
        data class Error(val message: String) : State
    }

    @Volatile
    private var started = false

    /** Token the running Router was started with; used by the readiness probe. */
    @Volatile
    private var activeToken: String? = null

    /** Makes [start] atomic so a repeated/racing call can never start a second Node. */
    private val startLock = Any()

    /** Single-flight guard so only one bring-up runs at a time (repeated START is a no-op). */
    private val bringUpGate = StartGate()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _syncSummary = MutableStateFlow<String?>(null)
    val syncSummary: StateFlow<String?> = _syncSummary.asStateFlow()

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
     * Ensures the embedded Router config exists: the loopback URL is used only when no URL
     * is configured, and the token is the stored one or a freshly generated random token.
     * Reuses [SecureSettingsStore] (Keystore encryption) — no separate storage.
     *
     * Persists only what is missing (a defaulted URL and/or a generated token); a
     * deliberately configured URL is never overwritten.
     */
    fun ensureLocalConfig(ctx: Context): RouterLocalConfig.Resolved {
        val store = SecureSettingsStore(ctx)
        val settings = store.load()
        val resolved = RouterLocalConfig.resolve(
            storedUrl = settings.routerUrl,
            storedToken = store.readToken(),
        ) { RouterToken.generate() }

        if (resolved.urlWasDefaulted || resolved.tokenWasGenerated) {
            store.save(
                resolved.url,
                if (resolved.tokenWasGenerated) resolved.token else null,
            )
        }
        return resolved
    }

    /** Extracts the unmodified Router bundle (dist/ + registry/) on first use. */
    private fun ensureRuntime(ctx: Context): File {
        val root = routerDir(ctx)
        // Marker is a file that only exists in the current bundled Router (PR-D adds
        // usage.js), so an existing install re-extracts the new dist exactly once.
        val marker = File(root, "dist/src/core/usage.js")
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

    /** Starts the embedded Node runtime exactly once (atomic; never a second instance). */
    fun start(ctx: Context): StartResult = synchronized(startLock) {
        if (started) return@synchronized StartResult(true, "already started")
        // Reserve before the native call so a repeated/racing call cannot also start.
        started = true

        val root = try {
            ensureRuntime(ctx)
        } catch (e: Exception) {
            started = false
            return@synchronized StartResult(false, "could not prepare runtime: ${e.message}")
        }

        val config = try {
            ensureLocalConfig(ctx)
        } catch (e: Exception) {
            started = false
            return@synchronized StartResult(false, "could not prepare router config: ${e.message}")
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
            started = false
            return@synchronized StartResult(false, "native start failed: ${e.message}")
        }
        if (!ok) {
            started = false
            return@synchronized StartResult(false, "nativeStart returned false")
        }
        StartResult(true, "node::Start launched")
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

    /**
     * Brings the Router up (idempotent) and, once READY, syncs every locally stored
     * provider key (PR3 logic, unchanged). Updates [state] and [syncSummary].
     * Returns true when the Router is READY.
     */
    suspend fun bringUp(ctx: Context): Boolean {
        if (_state.value is State.Ready) return true
        // Single-flight: a repeated START while one bring-up is in flight is a no-op.
        if (!bringUpGate.tryEnter()) return false
        try {
            _state.value = State.Starting
            _syncSummary.value = null

            val outcome = try {
                val healthy = if (isHealthyNow(ctx)) {
                    true
                } else {
                    val result = start(ctx)
                    if (!result.started) {
                        _state.value = RouterStateMapping.from(false, false, result.message)
                        return false
                    }
                    awaitHealthy(ctx)
                }
                RouterStateMapping.from(
                    started = true,
                    healthy = healthy,
                    failureMessage = "Router did not answer /healthz on 127.0.0.1:8910.",
                )
            } catch (t: Throwable) {
                State.Error("runtime init failed: ${t.message}")
            }

            _state.value = outcome
            if (outcome is State.Ready) {
                syncProviderKeys(ctx)
                return true
            }
            return false
        } finally {
            bringUpGate.exit()
        }
    }

    /** After READY, push every locally stored provider key to the Router (PR3). */
    private suspend fun syncProviderKeys(ctx: Context) {
        _syncSummary.value = "Key sync: running…"
        _syncSummary.value = try {
            val config = ensureLocalConfig(ctx)
            ProviderKeySyncRunner.syncToLocalRouter(ctx, config.url, config.token).summaryText()
        } catch (t: Throwable) {
            "Key sync failed: ${t.message}"
        }
    }

    /**
     * Marks the runtime as stopped. Embedded Node has no graceful shutdown/restart
     * path, so this only updates state; [RouterService] then terminates the process.
     */
    fun markStopped() {
        _state.value = State.Stopped
        _syncSummary.value = null
    }

    /** Adds a safe tunnel warning to the existing Home runtime summary without changing state. */
    fun reportSshTunnelFailure() {
        _syncSummary.value = RouterServiceContract.appendSshTunnelFailure(_syncSummary.value)
    }
}
