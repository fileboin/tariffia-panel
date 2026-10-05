package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.ssh.HostKeyPin
import com.tariffia.panel.data.ssh.JschSshConnector
import com.tariffia.panel.data.ssh.SecureSshProfileStore
import com.tariffia.panel.data.ssh.SshConnector
import com.tariffia.panel.data.ssh.SshProfile
import com.tariffia.panel.data.ssh.SshProfileRules
import com.tariffia.panel.data.ssh.SshProfileValidation
import com.tariffia.panel.data.ssh.SshProfileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VpsUiState(
    val host: String = "",
    val port: String = SshProfile.DEFAULT_PORT.toString(),
    val username: String = "",
    val privateKeyInput: String = "",
    val passphraseInput: String = "",
    val hasStoredKey: Boolean = false,
    val hasStoredPassphrase: Boolean = false,
    val isLoading: Boolean = true,
    val connection: VpsConnectionState = VpsConnectionState.NotConfigured,
    val profileMessage: String? = null,
    val showForgetConfirmation: Boolean = false,
) {
    val isTesting: Boolean get() = connection is VpsConnectionState.Testing
}

/**
 * Drives the VPS/SSH screen: stores the profile and secrets, and runs Test Connection
 * with mandatory host-key verification (explicit enrollment on first use).
 *
 * Secrets (private key, passphrase) are never placed into [profileMessage] or any
 * connection message, and are never read back into the UI after saving.
 */
class VpsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSshProfileStore(application)
    private val connector: SshConnector = JschSshConnector()
    private var testJob: Job? = null

    private val _uiState = MutableStateFlow(VpsUiState())
    val uiState: StateFlow<VpsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { store.loadProfile() }
            val hasKey = withContext(Dispatchers.IO) { store.hasPrivateKey() }
            val hasPassphrase = withContext(Dispatchers.IO) { store.hasPassphrase() }
            val ready = profile.host.isNotBlank() && profile.username.isNotBlank() && hasKey
            _uiState.update {
                it.copy(
                    host = profile.host,
                    port = profile.port.toString(),
                    username = profile.username,
                    hasStoredKey = hasKey,
                    hasStoredPassphrase = hasPassphrase,
                    isLoading = false,
                    connection = if (ready) VpsConnectionState.Idle else VpsConnectionState.NotConfigured,
                )
            }
        }
    }

    fun onHostChange(value: String) = onEdit { it.copy(host = value) }
    fun onPortChange(value: String) = onEdit { it.copy(port = value) }
    fun onUsernameChange(value: String) = onEdit { it.copy(username = value) }
    fun onPrivateKeyChange(value: String) = onEdit { it.copy(privateKeyInput = value) }
    fun onPassphraseChange(value: String) = onEdit { it.copy(passphraseInput = value) }

    /** Any edit invalidates the previous test result and clears feedback. */
    private fun onEdit(transform: (VpsUiState) -> VpsUiState) {
        _uiState.update {
            transform(it).copy(connection = VpsConnectionState.Idle, profileMessage = null)
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        val profile = validatedProfile(state) ?: return
        val keyInput = state.privateKeyInput
        if (keyInput.isNotBlank() && !SshProfileRules.looksLikePrivateKey(keyInput)) {
            _uiState.update { it.copy(profileMessage = "That does not look like a private key.") }
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
                    connection = VpsConnectionState.Idle,
                    profileMessage = "Profile saved.",
                )
            }
        }
    }

    fun testConnection() {
        // Ignore a second Test Connection while one is already running.
        if (testJob?.isActive == true) return
        val state = _uiState.value
        val profile = validatedProfile(state) ?: return

        _uiState.update { it.copy(connection = VpsConnectionState.Testing, profileMessage = null) }
        testJob = viewModelScope.launch {
            val storedKey = withContext(Dispatchers.IO) { store.readPrivateKey() }
            val storedPassphrase = withContext(Dispatchers.IO) { store.readPassphrase() }
            val key = state.privateKeyInput.ifBlank { storedKey }
            if (key.isNullOrBlank()) {
                _uiState.update { it.copy(connection = VpsConnectionState.ConnectionFailed("Private key required.")) }
                return@launch
            }
            val passphrase = state.passphraseInput.ifBlank { storedPassphrase }
            val pinned = withContext(Dispatchers.IO) { store.getPin(profile.host, profile.port) }
            val outcome = connector.connect(profile, key, passphrase, pinned)
            _uiState.update { it.copy(connection = VpsConnectionResolver.fromOutcome(outcome)) }
        }
    }

    /** The user explicitly trusted the presented host key; pin it and retry. */
    fun confirmEnrollment() {
        val state = _uiState.value
        val presented = (state.connection as? VpsConnectionState.HostKeyConfirmationRequired)?.presented ?: return
        val profile = validatedProfile(state) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.savePin(HostKeyPin(profile.host, profile.port, presented)) }
            _uiState.update { it.copy(connection = VpsConnectionState.Idle) }
            testConnection()
        }
    }

    fun cancelEnrollment() {
        _uiState.update { it.copy(connection = VpsConnectionState.Idle, profileMessage = "Host key not trusted.") }
    }

    fun dismissHostKeyChanged() {
        _uiState.update { it.copy(connection = VpsConnectionState.Idle) }
    }

    fun requestForgetPinnedKey() {
        _uiState.update { it.copy(showForgetConfirmation = true) }
    }

    fun cancelForgetPinnedKey() {
        _uiState.update { it.copy(showForgetConfirmation = false) }
    }

    /** Deliberately removes the pin so the user can re-enroll after a key rotation. */
    fun confirmForgetPinnedKey() {
        val state = _uiState.value
        val host = SshProfileRules.normalizeHost(state.host)
        val port = SshProfileRules.parsePort(state.port)
        if (!SshProfileRules.isValidHost(host) || port == null || !SshProfileRules.isValidPort(port)) {
            _uiState.update {
                it.copy(showForgetConfirmation = false, profileMessage = "Enter a valid host and port first.")
            }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearPin(host, port) }
            _uiState.update {
                it.copy(
                    showForgetConfirmation = false,
                    connection = VpsConnectionState.Idle,
                    profileMessage = "Pinned host key removed. Test Connection will ask you to trust the server again.",
                )
            }
        }
    }

    private fun validatedProfile(state: VpsUiState): SshProfile? {
        val hasKey = state.privateKeyInput.isNotBlank() || state.hasStoredKey
        return when (val validation = SshProfileValidator.validate(state.host, state.port, state.username, hasKey)) {
            is SshProfileValidation.Valid -> validation.profile
            is SshProfileValidation.Invalid -> {
                _uiState.update { it.copy(profileMessage = validation.message) }
                null
            }
        }
    }
}
