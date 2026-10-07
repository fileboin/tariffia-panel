package com.tariffia.panel.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SSH transport backed by JSch (com.github.mwiede:jsch), chosen because sshj is not
 * usable on Android without problematic BouncyCastle workarounds. JSch is pure Java
 * with no crypto-provider dependency.
 *
 * Host-key verification is mandatory:
 * - `StrictHostKeyChecking=yes` with a custom [HostKeyRepository] bound to the target.
 * - If [pinned] is null the presented key is captured and reported as
 *   [SshConnectOutcome.HostKeyUnknown] (no authentication happens).
 * - If the presented key differs from [pinned], it is reported as
 *   [SshConnectOutcome.HostKeyChanged]. There is no auto-accept path.
 *
 * A successful handshake + authentication is [SshConnectOutcome.Connected]. The fixed
 * `true` exec check afterwards is best-effort: a server that forbids exec must not turn
 * a successful authentication into a failure.
 *
 * The private key and passphrase are never logged and never included in errors.
 */
class JschSshConnector : SshConnector {

    override suspend fun connect(
        profile: SshProfile,
        credentials: SshCredentials,
        pinned: HostKeyPin?,
    ): SshConnectOutcome = withContext(Dispatchers.IO) {
        val jsch = JSch()
        if (credentials is SshCredentials.Key) {
            try {
                val keyBytes = credentials.privateKeyPem.toByteArray(Charsets.UTF_8)
                val passphraseBytes = credentials.passphrase
                    ?.takeIf { it.isNotEmpty() }
                    ?.toByteArray(Charsets.UTF_8)
                jsch.addIdentity(IDENTITY_NAME, keyBytes, null, passphraseBytes)
            } catch (e: JSchException) {
                return@withContext SshConnectOutcome.Failed("Could not load the SSH private key.")
            }
        }

        var connecting: Session? = null
        var presented: HostKeyIdentity? = null
        try {
            val opened = jsch.getSession(profile.username, profile.host, profile.port).apply {
                setConfig("StrictHostKeyChecking", "yes")
                // Password mode also offers keyboard-interactive (common on PAM servers).
                setConfig("PreferredAuthentications", SshAuthConfig.preferredAuthentications(profile.authMethod))
                setHostKeyRepository(object : HostKeyRepository {
                    override fun check(host: String?, key: ByteArray?): Int {
                        if (key == null) return HostKeyRepository.NOT_INCLUDED
                        val identity = HostKeyFingerprint.identityOf(key)
                        presented = identity
                        return when (HostKeyVerifier.decide(pinned, identity)) {
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
                })
            }
            // Password (if any) is set on the session only; it is never logged. A UserInfo
            // answers keyboard-interactive prompts with the same password (no display).
            if (credentials is SshCredentials.Password) {
                opened.setPassword(credentials.password)
                opened.userInfo = PasswordUserInfo(credentials.password)
            }
            connecting = opened
            opened.connect(CONNECT_TIMEOUT_MS)
        } catch (e: JSchException) {
            runCatching { connecting?.disconnect() }
            return@withContext mapException(e, pinned, presented)
        } catch (e: Exception) {
            runCatching { connecting?.disconnect() }
            return@withContext SshConnectOutcome.Failed("Connection failed.")
        }

        val session = connecting
            ?: return@withContext SshConnectOutcome.Failed("Connection failed.")
        try {
            val execCheckSucceeded = runFixedCheckQuietly(session)
            return@withContext SshOutcomeMapper.afterAuthentication(execCheckSucceeded)
        } finally {
            runCatching { session.disconnect() }
        }
    }

    /**
     * Best-effort, harmless check that does not change the system: run `true` over an
     * exec channel. Returns true if the channel ran, false if the server forbids exec.
     * A false result must never be treated as an authentication or connection failure.
     */
    private fun runFixedCheckQuietly(session: Session): Boolean = try {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand("true")
        channel.connect(CHANNEL_TIMEOUT_MS)
        val deadline = System.currentTimeMillis() + CHANNEL_TIMEOUT_MS
        while (!channel.isClosed && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
        channel.disconnect()
        true
    } catch (ignored: Exception) {
        false
    }

    private fun mapException(
        e: JSchException,
        pinned: HostKeyPin?,
        presented: HostKeyIdentity?,
    ): SshConnectOutcome = SshOutcomeMapper.fromFailure(e.message.orEmpty(), pinned, presented)

    private companion object {
        const val IDENTITY_NAME = "tariffia-panel"
        const val REPOSITORY_ID = "tariffia-panel"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val CHANNEL_TIMEOUT_MS = 8_000
        const val POLL_INTERVAL_MS = 50L
    }
}

/**
 * Answers JSch password/keyboard-interactive prompts with the session password. The
 * password is held in memory only and is never logged or displayed.
 */
internal class PasswordUserInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
    override fun getPassword(): String = password
    override fun getPassphrase(): String? = null
    override fun promptPassword(message: String?): Boolean = true
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptYesNo(message: String?): Boolean = false
    override fun showMessage(message: String?) = Unit
    override fun promptKeyboardInteractive(
        destination: String?,
        name: String?,
        instruction: String?,
        prompt: Array<out String>?,
        echo: BooleanArray?,
    ): Array<String> = Array(prompt?.size ?: 0) { password }
}
