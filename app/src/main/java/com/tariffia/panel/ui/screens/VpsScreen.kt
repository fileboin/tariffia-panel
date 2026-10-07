package com.tariffia.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tariffia.panel.data.ssh.HostKeyIdentity
import com.tariffia.panel.data.ssh.SshAuthMethod

private val ConnectedColor = Color(0xFF2E7D32)
private val FailedColor = Color(0xFFC62828)

/**
 * VPS/SSH screen. The private key is entered here but never read back after saving;
 * the passphrase field is masked. Test Connection enforces host-key verification with
 * an explicit enrollment step and never auto-accepts a key.
 */
@Composable
fun VpsScreen(viewModel: VpsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val fieldsEnabled = !state.isLoading && !state.isTesting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "VPS / SSH", style = MaterialTheme.typography.headlineSmall)

        ConnectionCard(state.connection)

        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::onHostChange,
            label = { Text("Host") },
            placeholder = { Text("vps.example.com") },
            singleLine = true,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = viewModel::onPortChange,
            label = { Text("Port") },
            singleLine = true,
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(text = "Authentication", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.authMethod == SshAuthMethod.KEY,
                onClick = { viewModel.onAuthMethodChange(SshAuthMethod.KEY) },
                enabled = fieldsEnabled,
            )
            Text("SSH Key")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = state.authMethod == SshAuthMethod.PASSWORD,
                onClick = { viewModel.onAuthMethodChange(SshAuthMethod.PASSWORD) },
                enabled = fieldsEnabled,
            )
            Text("Password")
        }

        when (state.authMethod) {
            SshAuthMethod.KEY -> {
                OutlinedTextField(
                    value = state.privateKeyInput,
                    onValueChange = viewModel::onPrivateKeyChange,
                    label = { Text("SSH private key") },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    enabled = fieldsEnabled,
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
                    enabled = fieldsEnabled,
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
            }

            SshAuthMethod.PASSWORD -> {
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = fieldsEnabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Used for this session only; never stored.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = viewModel::saveProfile,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Profile")
        }

        Button(
            onClick = viewModel::testConnection,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test Connection")
        }

        OutlinedButton(
            onClick = viewModel::requestForgetPinnedKey,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Forget pinned host key")
        }

        if (state.isTesting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.profileMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    (state.connection as? VpsConnectionState.HostKeyConfirmationRequired)?.let { required ->
        EnrollmentDialog(
            presented = required.presented,
            onConfirm = viewModel::confirmEnrollment,
            onCancel = viewModel::cancelEnrollment,
        )
    }

    (state.connection as? VpsConnectionState.HostKeyChanged)?.let { changed ->
        HostKeyChangedDialog(
            pinnedFingerprint = changed.pinned.identity.fingerprint,
            presentedFingerprint = changed.presented.fingerprint,
            onDismiss = viewModel::dismissHostKeyChanged,
        )
    }

    if (state.showForgetConfirmation) {
        ForgetHostKeyDialog(
            onConfirm = viewModel::confirmForgetPinnedKey,
            onCancel = viewModel::cancelForgetPinnedKey,
        )
    }
}

@Composable
private fun ConnectionCard(connection: VpsConnectionState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "●", color = connectionColor(connection), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = connectionLabel(connection), style = MaterialTheme.typography.titleMedium)
            }
            connectionDescription(connection)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
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

@Composable
private fun ForgetHostKeyDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Forget pinned host key?") },
        text = { Text("The next Test Connection will ask you to trust the server again.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Forget") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private fun connectionLabel(connection: VpsConnectionState): String = when (connection) {
    VpsConnectionState.NotConfigured -> "Not configured"
    VpsConnectionState.Idle -> "Not tested"
    VpsConnectionState.Testing -> "Testing…"
    VpsConnectionState.Connected -> "Connected"
    VpsConnectionState.AuthenticationFailed -> "Authentication failed"
    is VpsConnectionState.ConnectionFailed -> "Connection failed"
    is VpsConnectionState.HostKeyConfirmationRequired -> "Host key confirmation required"
    is VpsConnectionState.HostKeyChanged -> "Host key changed"
}

private fun connectionDescription(connection: VpsConnectionState): String? = when (connection) {
    VpsConnectionState.NotConfigured -> "Fill in host, username and a private key."
    VpsConnectionState.Idle -> null
    VpsConnectionState.Testing -> "Connecting…"
    VpsConnectionState.Connected -> "Handshake and authentication succeeded."
    VpsConnectionState.AuthenticationFailed -> "Check the username and private key."
    is VpsConnectionState.ConnectionFailed -> connection.message ?: "Could not reach the server."
    is VpsConnectionState.HostKeyConfirmationRequired -> "Confirm the server fingerprint to continue."
    is VpsConnectionState.HostKeyChanged -> "The server key changed. Connection refused."
}

@Composable
private fun connectionColor(connection: VpsConnectionState): Color = when (connection) {
    VpsConnectionState.Connected -> ConnectedColor
    VpsConnectionState.Idle,
    VpsConnectionState.Testing,
    VpsConnectionState.NotConfigured,
    -> MaterialTheme.colorScheme.outline
    else -> FailedColor
}
