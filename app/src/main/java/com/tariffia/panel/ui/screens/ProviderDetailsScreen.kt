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
import com.tariffia.panel.data.router.CandidateHealth
import com.tariffia.panel.data.router.ModelWithHealth
import kotlin.math.roundToInt

/**
 * Provider details: router-reported status (with an explicit unavailable state) is shown
 * separately from the locally stored API key status. The key is entered masked and is
 * never read back. The Models section is read-only Router metadata.
 */
@Composable
fun ProviderDetailsScreen(
    onBack: () -> Unit,
    viewModel: ProviderDetailsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val actionsEnabled = !state.isLoading && !state.isBusy

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
        RouterStatusContent(state.routerStatusView)

        Text(text = "Models (${state.models.size})", style = MaterialTheme.typography.titleMedium)
        if (state.models.isEmpty()) {
            Text(
                text = "No models reported by the router.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            state.models.forEach { item -> ModelItem(item) }
        }

        Text(text = "API key", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "API key stored locally: ${if (state.hasStoredKey) "Yes" else "No"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = state.keyInput,
            onValueChange = viewModel::onKeyChange,
            label = { Text("API key") },
            placeholder = { Text("Paste the provider API key") },
            singleLine = true,
            enabled = actionsEnabled,
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
            enabled = actionsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save API key")
        }

        OutlinedButton(
            onClick = viewModel::requestClear,
            enabled = actionsEnabled && state.hasStoredKey,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear API key")
        }

        OutlinedButton(
            onClick = viewModel::syncKeyToRouter,
            enabled = actionsEnabled && state.hasStoredKey,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sync to Router")
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
            confirmButton = {
                TextButton(onClick = viewModel::confirmClear, enabled = !state.isBusy) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelClear) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RouterStatusContent(view: RouterStatusView) {
    when (view) {
        RouterStatusView.Loading -> Text(
            text = "Checking…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        RouterStatusView.RouterNotConfigured -> Text(
            text = "Router not configured. Set the router URL and token in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        RouterStatusView.Unavailable -> Text(
            text = "Router status unavailable.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        is RouterStatusView.Available -> {
            Text(
                text = providerStatusLabel(view.status),
                style = MaterialTheme.typography.bodyLarge,
                color = providerStatusColor(view.status),
            )
            view.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Read-only Router model metadata + observed reliability. Never shows a key, token or header. */
@Composable
private fun ModelItem(item: ModelWithHealth) {
    val model = item.model
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = model.id, style = MaterialTheme.typography.bodyLarge)
        val meta = buildList {
            if (model.mesh.capabilities.isNotEmpty()) {
                add(model.mesh.capabilities.joinToString(", "))
            }
            if (model.mesh.contextWindow > 0) {
                add("${model.mesh.contextWindow} ctx")
            }
        }.joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(text = "Price: ${blendedPriceLabel(model.mesh.pricePerMTokBlended)}", style = MaterialTheme.typography.bodySmall)
        model.mesh.maxPrivacy?.let { privacy ->
            Text(
                text = "Max privacy: $privacy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        ReliabilityContent(item.health)
    }
}

/** Router-observed reliability. A missing entry is "no data", never a fabricated value. */
@Composable
private fun ReliabilityContent(health: CandidateHealth?) {
    if (health == null) {
        Text(
            text = "Reliability: No data yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        return
    }
    Text(
        text = "Reliability: ${if (health.open) "Open (cooling down)" else "Healthy"}",
        style = MaterialTheme.typography.bodySmall,
        color = if (health.open) ErrorColor else ConfiguredColor,
    )
    val stats = buildList {
        health.ewmaMs?.let { add("Latency: $it ms") }
        health.successRate?.let { add("Success: ${(it * 100).roundToInt()}%") }
        add("Attempts: ${health.attempts}")
    }.joinToString(" · ")
    Text(
        text = stats,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    if (health.open && health.openForMs > 0) {
        Text(
            text = "Cooldown: ${health.openForMs / 1000} s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** The Router exposes only a blended price; never invent separate in/out prices. */
private fun blendedPriceLabel(blended: Double): String =
    if (blended == 0.0) "Free" else "\$$blended / MTok (blended)"
