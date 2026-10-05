package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.SettingsRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the Settings screen. [tokenInput] holds only what the user is
 * currently typing (shown masked); the stored token is never loaded into it.
 */
data class SettingsUiState(
    val routerUrl: String = "",
    val tokenInput: String = "",
    val hasSavedToken: Boolean = false,
    val isLoading: Boolean = true,
    val statusMessage: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { store.load() }
            _uiState.update {
                it.copy(
                    routerUrl = settings.routerUrl,
                    hasSavedToken = settings.hasToken,
                    isLoading = false,
                )
            }
        }
    }

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(routerUrl = value, statusMessage = null) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(tokenInput = value, statusMessage = null) }
    }

    fun save() {
        val current = _uiState.value
        val normalized = SettingsRules.normalizeUrl(current.routerUrl)
        if (!SettingsRules.isValidUrl(normalized)) {
            _uiState.update { it.copy(statusMessage = "Enter a valid http(s) router URL.") }
            return
        }
        val replacingToken = current.tokenInput.isNotBlank()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(normalized, current.tokenInput) }
            _uiState.update {
                it.copy(
                    routerUrl = normalized,
                    // Never keep the typed secret in UI state after saving.
                    tokenInput = "",
                    hasSavedToken = it.hasSavedToken || replacingToken,
                    statusMessage = "Saved.",
                )
            }
        }
    }
}
