package com.tariffia.panel.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tariffia.panel.data.SecureSettingsStore
import com.tariffia.panel.data.router.ChatMessage
import com.tariffia.panel.data.router.RouterClient
import com.tariffia.panel.data.router.RouterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One rendered chat bubble. In-memory only. */
data class ChatMessageUi(val role: String, val content: String)

data class ChatUiState(
    /** Initial config/model load in flight. */
    val isLoading: Boolean = true,
    /** Router URL + token are present. */
    val isConfigured: Boolean = false,
    val models: List<String> = emptyList(),
    val selectedModel: String? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
)

/**
 * Minimal chat workspace: lists the models the Router reports (`/v1/models`), sends
 * non-streaming chat requests through the embedded Router (`/v1/chat/completions`) and
 * shows the reply. The conversation is in-memory only; no token/cost is computed here
 * (that stays in the Router). One request is in flight at a time.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureSettingsStore(application)
    private val client = RouterClient()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val settings = withContext(Dispatchers.IO) { store.load() }
            val token = withContext(Dispatchers.IO) { store.readToken() }
            val url = settings.routerUrl
            if (url.isBlank() || token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, isConfigured = false, models = emptyList(), selectedModel = null)
                }
                return@launch
            }
            when (val models = client.fetchModels(url, token)) {
                is RouterResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConfigured = true,
                        models = models.value,
                        selectedModel = it.selectedModel?.takeIf { s -> s in models.value }
                            ?: models.value.firstOrNull(),
                        error = null,
                    )
                }
                else -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConfigured = true,
                        models = emptyList(),
                        selectedModel = null,
                        error = "Could not load models from the router.",
                    )
                }
            }
        }
    }

    fun onModelSelected(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun send() {
        val current = _uiState.value
        if (current.isSending) return
        val text = current.input.trim()
        val model = current.selectedModel
        if (text.isEmpty() || model == null) return

        val history = current.messages + ChatMessageUi("user", text)
        _uiState.update { it.copy(messages = history, input = "", isSending = true, error = null) }

        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { store.load() }
            val token = withContext(Dispatchers.IO) { store.readToken() }
            val url = settings.routerUrl
            if (url.isBlank() || token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isSending = false, isConfigured = false, error = "Router not configured (URL/token).")
                }
                return@launch
            }
            val payload = history.map { ChatMessage(it.role, it.content) }
            val result = client.sendChat(url, token, model, payload)
            _uiState.update { state ->
                when (result) {
                    is RouterResult.Success -> state.copy(
                        messages = state.messages + ChatMessageUi("assistant", result.value),
                        isSending = false,
                        error = null,
                    )
                    RouterResult.AuthenticationFailed ->
                        state.copy(isSending = false, error = "Router rejected the credentials.")
                    is RouterResult.HttpError ->
                        state.copy(isSending = false, error = "Router error (HTTP ${result.code}).")
                    is RouterResult.InvalidResponse ->
                        state.copy(isSending = false, error = "Unexpected router response.")
                    is RouterResult.ConnectionFailed ->
                        state.copy(isSending = false, error = "Cannot reach the router.")
                }
            }
        }
    }
}
