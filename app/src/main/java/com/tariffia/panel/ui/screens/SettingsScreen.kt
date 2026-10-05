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
import com.tariffia.panel.data.SettingsRules

/**
 * Settings for the Tariffia Router connection. The URL is ordinary config; the token
 * is entered through a masked field and stored via [SettingsViewModel] in the Android
 * Keystore. The stored token is never read back into the UI.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val fieldsEnabled = !state.isLoading && !state.isSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Router connection", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "URL and token for the Tariffia Router. These are separate from VPS/SSH credentials and provider API keys.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        OutlinedTextField(
            value = state.routerUrl,
            onValueChange = viewModel::onUrlChange,
            label = { Text("Router URL") },
            placeholder = { Text("https://router.example.com") },
            singleLine = true,
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.tokenInput,
            onValueChange = viewModel::onTokenChange,
            label = { Text("Router Token") },
            placeholder = {
                Text(if (state.hasSavedToken) "${SettingsRules.maskedTokenHint()} (saved)" else "Enter router token")
            },
            singleLine = true,
            enabled = fieldsEnabled,
            visualTransformation = if (state.tokenVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = viewModel::onToggleTokenVisibility) {
                    Text(if (state.tokenVisible) "Hide" else "Show")
                }
            },
            supportingText = {
                Text(SettingsRules.tokenStatusLabel(state.hasSavedToken))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::save,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }

        OutlinedButton(
            onClick = viewModel::requestClearToken,
            enabled = fieldsEnabled && state.hasSavedToken,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear token")
        }

        if (state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
            onDismissRequest = viewModel::cancelClearToken,
            title = { Text("Clear router token?") },
            text = { Text("The stored router token will be removed. You can add it again later.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearToken) { Text("Clear") } },
            dismissButton = { TextButton(onClick = viewModel::cancelClearToken) { Text("Cancel") } },
        )
    }
}
