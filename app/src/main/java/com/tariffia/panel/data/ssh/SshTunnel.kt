package com.tariffia.panel.data.ssh

import android.content.Context
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure, JVM-testable tunnel rules: the local bind endpoint and the remote target.
 *
 * The local listener is bound to loopback ONLY, never 0.0.0.0 or the device LAN address, so
 * the forward is reachable only by the embedded Router in this same process.
 */
internal object SshTunnelRules {
    const val LOCAL_HOST = "127.0.0.1"
    const val LOCAL_PORT = 11434
    /** The VPS-side Ollama endpoint the tunnel forwards to. */
    const val REMOTE_HOST = "127.0.0.1"
    const val REMOTE_PORT = 11434

    /** What the tunnel can do with the stored profile. */
    enum class Plan { START_KEY, NEEDS_KEY, PASSWORD_UNSUPPORTED }

    /**
     * KEY authentication only. A password-mode profile is refused (never stored, never used):
     * an SSH password is session-only and a long-lived tunnel cannot rely on it.
     */
    fun plan(authMethod: SshAuthMethod, hasPrivateKey: Boolean): Plan = when {
        authMethod == SshAuthMethod.PASSWORD -> Plan.PASSWORD_UNSUPPORTED
        hasPrivateKey -> Plan.START_KEY
        else -> Plan.NEEDS_KEY
    }
}

/**
 * A long-lived SSH local port-forward, owned by the foreground
 * [com.tariffia.panel.data.router.RouterService] (the single owner of the Router lifecycle).
 *
 * It forwards `127.0.0.1:11434` on this device through the authenticated SSH session to
 * `127.0.0.1:11434` on the VPS, so the embedded Router's existing Ollama baseUrl
 * (`http://127.0.0.1:11434/v1`) reaches Ollama without any registry change.
 *
 * Authentication is public-key only. The private key and passphrase come from the existing
 * [SecureSshProfileStore] (Android Keystore); no password is ever persisted or used. Host-key
 * verification reuses the existing [HostKeyVerifier] pinning: an unpinned or changed key makes
 * the session fail rather than silently trusting it.
 *
 * Unlike the one-shot [JschSshConnector], this keeps ONE [Session] alive for as long as the
 * Router runs and closes it on stop. Failures are reported via [lastError] and never thrown,
 * so a tunnel problem cannot crash the app or the Router.
 */
object SshTunnel {

    private val lock = Any()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var lastError: String? = null

    private const val IDENTITY_NAME = "tariffia-panel"
    private const val CONNECT_TIMEOUT_MS = 15_000

    /** True while the forwarding session is connected. */
    fun isUp(): Boolean = session?.isConnected == true

    /** The last failure reason, or null. Never contains key material. */
    fun lastError(): String? = lastError

    /**
     * Establishes the tunnel if it is not already up. Returns true on success. Never throws:
     * any failure (no key, password-mode profile, unreachable VPS, unpinned host key, port in
     * use) is caught, recorded in [lastError], and reported as false.
     */
    suspend fun start(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (session?.isConnected == true) return@withContext true
            lastError = null

            val store = SecureSshProfileStore(ctx)
            val profile = store.loadProfile()
            when (SshTunnelRules.plan(profile.authMethod, store.hasPrivateKey())) {
                SshTunnelRules.Plan.PASSWORD_UNSUPPORTED -> {
                    lastError = "SSH tunnel needs key authentication; password auth is session-only and never stored."
                    return@withContext false
                }
                SshTunnelRules.Plan.NEEDS_KEY -> {
                    lastError = "No SSH private key stored for the tunnel."
                    return@withContext false
                }
                SshTunnelRules.Plan.START_KEY -> Unit
            }

            val pem = store.readPrivateKey()
            if (pem.isNullOrBlank()) {
                lastError = "No SSH private key stored for the tunnel."
                return@withContext false
            }
            if (profile.host.isBlank() || profile.username.isBlank()) {
                lastError = "SSH profile is incomplete (host/username)."
                return@withContext false
            }

            val passphrase = store.readPassphrase()?.takeIf { it.isNotEmpty() }
            val pinned = store.getPin(profile.host, profile.port)

            val opened: Session? = try {
                val jsch = JSch()
                jsch.addIdentity(
                    IDENTITY_NAME,
                    pem.toByteArray(Charsets.UTF_8),
                    null,
                    passphrase?.toByteArray(Charsets.UTF_8),
                )
                val s = jsch.getSession(profile.username, profile.host, profile.port).apply {
                    setConfig("StrictHostKeyChecking", "yes")
                    setConfig("PreferredAuthentications", "publickey")
                    setHostKeyRepository(PinnedHostKeyRepository(pinned))
                }
                s.connect(CONNECT_TIMEOUT_MS)
                s.setPortForwardingL(
                    SshTunnelRules.LOCAL_HOST,
                    SshTunnelRules.LOCAL_PORT,
                    SshTunnelRules.REMOTE_HOST,
                    SshTunnelRules.REMOTE_PORT,
                )
                s
            } catch (e: Exception) {
                lastError = e.message ?: "SSH tunnel could not be established."
                null
            }

            if (opened == null) {
                runCatching { session?.disconnect() }
                session = null
                return@withContext false
            }
            session = opened
            lastError = null
            true
        }
    }

    /** Closes the forwarding session. Safe and idempotent; never throws. */
    fun stop() {
        synchronized(lock) {
            runCatching { session?.disconnect() }
            session = null
        }
    }
}

/**
 * JSch host-key repository backed by the existing pinning logic. Returns OK only for the
 * pinned key; an unknown key is NOT_INCLUDED (and with StrictHostKeyChecking=yes JSch then
 * rejects it) and a different key is CHANGED. No auto-accept path.
 */
private class PinnedHostKeyRepository(private val pinned: HostKeyPin?) : HostKeyRepository {

    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null) return HostKeyRepository.NOT_INCLUDED
        return when (HostKeyVerifier.decide(pinned, HostKeyFingerprint.identityOf(key))) {
            HostKeyDecision.Trusted -> HostKeyRepository.OK
            HostKeyDecision.Unknown -> HostKeyRepository.NOT_INCLUDED
            is HostKeyDecision.Changed -> HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = REPOSITORY_ID
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    private companion object {
        const val REPOSITORY_ID = "tariffia-panel"
    }
}
