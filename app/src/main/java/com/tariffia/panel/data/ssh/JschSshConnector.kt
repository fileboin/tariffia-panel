package com.tariffia.panel.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
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
 * The private key and passphrase are never logged and never included in errors.
 */
class JschSshConnector : SshConnector {

    override suspend fun connect(
        profile: SshProfile,
        privateKeyPem: String,
        passphrase: String?,
        pinned: HostKeyPin?,
    ): SshConnectOutcome = withContext(Dispatchers.IO) {
        val jsch = JSch()
        try {
            val keyBytes = privateKeyPem.toByteArray(Charsets.UTF_8)
            val passphraseBytes = passphrase
                ?.takeIf { it.isNotEmpty() }
                ?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity(IDENTITY_NAME, keyBytes, null, passphraseBytes)
        } catch (e: JSchException) {
            return@withContext SshConnectOutcome.Failed("Could not load the SSH private key.")
        }

        var session: Session? = null
        var presented: HostKeyIdentity? = null
        try {
            session = jsch.getSession(profile.username, profile.host, profile.port).apply {
                setConfig("StrictHostKeyChecking", "yes")
                setConfig("PreferredAuthentications", "publickey")
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
            session.connect(CONNECT_TIMEOUT_MS)
            runFixedCheck(session)
            session.disconnect()
            SshConnectOutcome.Connected
        } catch (e: JSchException) {
            session?.disconnect()
            mapException(e, pinned, presented)
        } catch (e: Exception) {
            session?.disconnect()
            SshConnectOutcome.Failed("Connection failed.")
        }
    }

    /**
     * Fixed, harmless check that does not change the system: run `true` over an exec
     * channel and let it finish. Confirms the session channel opens after auth.
     */
    private fun runFixedCheck(session: Session) {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand("true")
        channel.connect(CHANNEL_TIMEOUT_MS)
        val deadline = System.currentTimeMillis() + CHANNEL_TIMEOUT_MS
        while (!channel.isClosed && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
        channel.disconnect()
    }

    private fun mapException(
        e: JSchException,
        pinned: HostKeyPin?,
        presented: HostKeyIdentity?,
    ): SshConnectOutcome {
        val message = e.message.orEmpty()
        return when {
            message.contains("UnknownHostKey", ignoreCase = true) ->
                presented?.let { SshConnectOutcome.HostKeyUnknown(it) }
                    ?: SshConnectOutcome.Failed("Host key not received.")

            message.contains("changed", ignoreCase = true) ->
                if (pinned != null && presented != null) {
                    SshConnectOutcome.HostKeyChanged(pinned, presented)
                } else {
                    SshConnectOutcome.Failed("Host key changed.")
                }

            message.contains("auth", ignoreCase = true) ->
                SshConnectOutcome.AuthenticationFailed

            else -> SshConnectOutcome.Failed("Connection failed.")
        }
    }

    private companion object {
        const val IDENTITY_NAME = "tariffia-panel"
        const val REPOSITORY_ID = "tariffia-panel"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val CHANNEL_TIMEOUT_MS = 8_000
        const val POLL_INTERVAL_MS = 50L
    }
}
