package com.tariffia.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Provider details: router-reported status is shown separately from the locally
 * stored API key status. The key is entered masked and is never read back.
 */
@Composable
fun ProviderDetailsScreen(
    onBack: () -> Unit,
    viewModel: ProviderDetailsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }

        Text(text = state.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(text = "Provider ID: ${state.providerId}", style = MaterialTheme.typography.bodyMedium)

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(text = "Router status", style = MaterialTheme.typography.titleMedium)
        Text(
            text = providerStatusLabel(state.routerStatus),
            style = MaterialTheme.typography.bodyLarge,
            color = providerStatusColor(state.routerStatus),
        )
        state.routerNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Text(text = "API key (stored locally)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Status: ${if (state.hasStoredKey) "Configured" else "Not configured"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = state.keyInput,
            onValueChange = viewModel::onKeyChange,
            label = { Text("API key") },
            placeholder = { Text("Paste the provider API key") },
            singleLine = true,
            visualTransformation = if (state.keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = viewModel::onToggleKeyVisibility) {
                    Text(if (state.keyVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Stored encrypted on this device (Android Keystore). Not sent to the router or VPS yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Button(
            onClick = viewModel::saveKey,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save API key")
        }

        OutlinedButton(
            onClick = viewModel::requestClear,
            enabled = !state.isLoading && state.hasStoredKey,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear API key")
        }

        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClear,
            title = { Text("Clear API key?") },
            text = { Text("This removes the locally stored key for ${state.displayName}.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClear) { Text("Clear") } },
            dismissButton = { TextButton(onClick = viewModel::cancelClear) { Text("Cancel") } },
        )
    }
}
