package com.tariffia.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Minimal local chat workspace: pick a model the Router reports and send real
 * non-streaming chat requests through the embedded Router. Not an IDE/agent surface.
 */
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Chat", style = MaterialTheme.typography.headlineSmall)

        when {
            state.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            !state.isConfigured -> {
                Text(
                    text = "Router not configured. Set the router URL and token in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }

            else -> {
                ModelSelector(state = state, viewModel = viewModel)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages) { message ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = message.role,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = NotConfiguredColor,
                    )
                }

                if (state.isSending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        enabled = !state.isSending,
                        maxLines = 4,
                    )
                    Button(
                        onClick = viewModel::send,
                        enabled = !state.isSending && state.input.isNotBlank() && state.selectedModel != null,
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

/** Model selector over the ids the Router reports (`/v1/models`). */
@Composable
private fun ModelSelector(state: ChatUiState, viewModel: ChatViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = state.models.isNotEmpty()) {
            Text(text = state.selectedModel ?: "Select model")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        viewModel.onModelSelected(model)
                        expanded = false
                    },
                )
            }
        }
    }
}
