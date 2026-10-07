package com.tariffia.panel.data.ssh

/**
 * Process-local, IN-MEMORY-ONLY holder for the SSH password used by the long-lived Ollama
 * tunnel. The password is supplied by the existing VPS "Test Connection" flow and exists only
 * for the life of the process.
 *
 * It is NEVER persisted: not to SharedPreferences/DataStore, files, database, environment, or
 * logs, and it is never sent to the Router. [clear] zeroes the backing array; Android process
 * death discards it, after which the user must re-enter the password.
 */
internal object SshSessionSecrets {

    private val lock = Any()
    private var password: CharArray? = null

    /** Replaces the held password (null/blank clears it). Memory only. */
    fun setPassword(value: String?) {
        synchronized(lock) {
            password?.fill('\u0000')
            password = value?.takeIf { it.isNotEmpty() }?.toCharArray()
        }
    }

    /** True when a session password is currently held. */
    fun hasPassword(): Boolean = synchronized(lock) { password != null }

    /**
     * The held password, or null. Returns a transient copy for the SSH call site (JSch needs a
     * String); the copy is not stored anywhere. Never logged.
     */
    fun password(): String? = synchronized(lock) { password?.concatToString() }

    /** Zeroes and drops the held password. Safe and idempotent. */
    fun clear() {
        synchronized(lock) {
            password?.fill('\u0000')
            password = null
        }
    }
}
