package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.ssh.HostKeyIdentity
import com.tariffia.panel.data.ssh.HostKeyPin
import com.tariffia.panel.data.ssh.JschSshConnector
import com.tariffia.panel.data.ssh.SecureSshProfileStore
import com.tariffia.panel.data.ssh.SshConnectOutcome
import com.tariffia.panel.data.ssh.SshConnector
import com.tariffia.panel.data.ssh.SshProfile
import com.tariffia.panel.data.ssh.SshProfileRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HostKeyChange(val pinned: HostKeyPin, val presented: HostKeyIdentity)

data class VpsUiState(
    val host: String = "",
    val port: String = SshProfile.DEFAULT_PORT.toString(),
    val username: String = "",
    val privateKeyInput: String = "",
    val passphraseInput: String = "",
    val hasStoredKey: Boolean = false,
    val hasStoredPassphrase: Boolean = false,
    val isLoading: Boolean = true,
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val enrollment: HostKeyIdentity? = null,
    val hostKeyChanged: HostKeyChange? = null,
)

/**
 * Drives the VPS/SSH screen: stores the profile and secrets, and runs Test Connection
 * with mandatory host-key verification (explicit enrollment on first use).
 */
class VpsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSshProfileStore(application)
    private val connector: SshConnector = JschSshConnector()

    private val _uiState = MutableStateFlow(VpsUiState())
    val uiState: StateFlow<VpsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { store.loadProfile() }
            val hasKey = withContext(Dispatchers.IO) { store.hasPrivateKey() }
            val hasPassphrase = withContext(Dispatchers.IO) { store.hasPassphrase() }
            _uiState.update {
                it.copy(
                    host = profile.host,
                    port = profile.port.toString(),
                    username = profile.username,
                    hasStoredKey = hasKey,
                    hasStoredPassphrase = hasPassphrase,
                    isLoading = false,
                )
            }
        }
    }

    fun onHostChange(value: String) = _uiState.update { it.copy(host = value, statusMessage = null) }
    fun onPortChange(value: String) = _uiState.update { it.copy(port = value, statusMessage = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, statusMessage = null) }
    fun onPrivateKeyChange(value: String) = _uiState.update { it.copy(privateKeyInput = value, statusMessage = null) }
    fun onPassphraseChange(value: String) = _uiState.update { it.copy(passphraseInput = value, statusMessage = null) }

    fun saveProfile() {
        val state = _uiState.value
        val profile = validatedProfile(state) ?: return
        val keyInput = state.privateKeyInput
        if (keyInput.isBlank() && !state.hasStoredKey) {
            _uiState.update { it.copy(statusMessage = "Paste an SSH private key.") }
            return
        }
        if (keyInput.isNotBlank() && !SshProfileRules.looksLikePrivateKey(keyInput)) {
            _uiState.update { it.copy(statusMessage = "That does not look like a private key.") }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.saveProfile(profile, keyInput.ifBlank { null }, state.passphraseInput)
            }
            _uiState.update {
                it.copy(
                    host = profile.host,
                    port = profile.port.toString(),
                    username = profile.username,
                    privateKeyInput = "",
                    passphraseInput = "",
                    hasStoredKey = it.hasStoredKey || keyInput.isNotBlank(),
                    hasStoredPassphrase = it.hasStoredPassphrase || state.passphraseInput.isNotEmpty(),
                    statusMessage = "Profile saved.",
                )
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        val profile = validatedProfile(state) ?: return
        _uiState.update {
            it.copy(isTesting = true, statusMessage = null, enrollment = null, hostKeyChanged = null)
        }
        viewModelScope.launch {
            val storedKey = withContext(Dispatchers.IO) { store.readPrivateKey() }
            val storedPassphrase = withContext(Dispatchers.IO) { store.readPassphrase() }
            val key = state.privateKeyInput.ifBlank { storedKey }
            if (key.isNullOrBlank()) {
                _uiState.update { it.copy(isTesting = false, statusMessage = "Private key required.") }
                return@launch
            }
            val passphrase = state.passphraseInput.ifBlank { storedPassphrase }
            val pinned = withContext(Dispatchers.IO) { store.getPin(profile.host, profile.port) }
            when (val outcome = connector.connect(profile, key, passphrase, pinned)) {
                SshConnectOutcome.Connected -> _uiState.update {
                    it.copy(isTesting = false, statusMessage = "Connected. Handshake and authentication succeeded.")
                }
                is SshConnectOutcome.HostKeyUnknown -> _uiState.update {
                    it.copy(isTesting = false, enrollment = outcome.presented)
                }
                is SshConnectOutcome.HostKeyChanged -> _uiState.update {
                    it.copy(isTesting = false, hostKeyChanged = HostKeyChange(outcome.pinned, outcome.presented))
                }
                SshConnectOutcome.AuthenticationFailed -> _uiState.update {
                    it.copy(isTesting = false, statusMessage = "Authentication failed. Check the username and private key.")
                }
                is SshConnectOutcome.Failed -> _uiState.update {
                    it.copy(isTesting = false, statusMessage = outcome.reason)
                }
            }
        }
    }

    /** The user explicitly trusted the presented host key; pin it and retry. */
    fun confirmEnrollment() {
        val state = _uiState.value
        val presented = state.enrollment ?: return
        val profile = validatedProfile(state) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.savePin(HostKeyPin(profile.host, profile.port, presented)) }
            _uiState.update { it.copy(enrollment = null) }
            testConnection()
        }
    }

    fun cancelEnrollment() {
        _uiState.update { it.copy(enrollment = null, statusMessage = "Host key not trusted.") }
    }

    fun dismissHostKeyChanged() {
        _uiState.update { it.copy(hostKeyChanged = null) }
    }

    /** Deliberately removes the pin so the user can re-enroll after a key rotation. */
    fun forgetPinnedKey() {
        val state = _uiState.value
        val profile = validatedProfile(state) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearPin(profile.host, profile.port) }
            _uiState.update {
                it.copy(
                    hostKeyChanged = null,
                    statusMessage = "Pinned host key removed. Test Connection will ask you to trust the server again.",
                )
            }
        }
    }

    private fun validatedProfile(state: VpsUiState): SshProfile? {
        val host = SshProfileRules.normalizeHost(state.host)
        val port = SshProfileRules.parsePort(state.port)
        val username = state.username.trim()
        val message = when {
            !SshProfileRules.isValidHost(host) -> "Enter a valid host."
            port == null || !SshProfileRules.isValidPort(port) -> "Port must be 1-65535."
            !SshProfileRules.isValidUsername(username) -> "Enter a valid username."
            else -> null
        }
        if (message != null) {
            _uiState.update { it.copy(statusMessage = message) }
            return null
        }
        return SshProfile(host = host, port = port!!, username = username)
    }
}
