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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tariffia.panel.data.ssh.HostKeyIdentity

/**
 * VPS/SSH screen. The private key is entered here but never read back after saving;
 * the passphrase field is masked. Test Connection enforces host-key verification with
 * an explicit enrollment step.
 */
@Composable
fun VpsScreen(viewModel: VpsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "VPS / SSH", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::onHostChange,
            label = { Text("Host") },
            placeholder = { Text("vps.example.com") },
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = viewModel::onPortChange,
            label = { Text("Port") },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.privateKeyInput,
            onValueChange = viewModel::onPrivateKeyChange,
            label = { Text("SSH private key") },
            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            enabled = !state.isLoading,
            supportingText = {
                Text(
                    if (state.hasStoredKey) {
                        "A private key is stored securely. Leave blank to keep it."
                    } else {
                        "Paste an OpenSSH/PEM private key. Stored encrypted; never shown again."
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.passphraseInput,
            onValueChange = viewModel::onPassphraseChange,
            label = { Text("Passphrase (optional)") },
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                Text(
                    if (state.hasStoredPassphrase) {
                        "A passphrase is stored securely. Leave blank to keep it."
                    } else {
                        "Only if the private key is encrypted."
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::saveProfile,
            enabled = !state.isLoading && !state.isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Profile")
        }

        Button(
            onClick = viewModel::testConnection,
            enabled = !state.isLoading && !state.isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test Connection")
        }

        OutlinedButton(
            onClick = viewModel::forgetPinnedKey,
            enabled = !state.isLoading && !state.isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Forget pinned host key")
        }

        if (state.isTesting) {
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

    state.enrollment?.let { presented ->
        EnrollmentDialog(
            presented = presented,
            onConfirm = viewModel::confirmEnrollment,
            onCancel = viewModel::cancelEnrollment,
        )
    }

    state.hostKeyChanged?.let { change ->
        HostKeyChangedDialog(
            pinnedFingerprint = change.pinned.identity.fingerprint,
            presentedFingerprint = change.presented.fingerprint,
            onDismiss = viewModel::dismissHostKeyChanged,
        )
    }
}

@Composable
private fun EnrollmentDialog(
    presented: HostKeyIdentity,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unknown host key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Verify this fingerprint out-of-band before trusting it.")
                Text("Type: ${presented.keyType}", style = MaterialTheme.typography.bodySmall)
                Text("Fingerprint: ${presented.fingerprint}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Trust & Connect") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun HostKeyChangedDialog(
    pinnedFingerprint: String,
    presentedFingerprint: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host key changed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The server presented a different host key. This could indicate a man-in-the-middle attack. Connection refused.")
                Text("Pinned: $pinnedFingerprint", style = MaterialTheme.typography.bodySmall)
                Text("Presented: $presentedFingerprint", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
