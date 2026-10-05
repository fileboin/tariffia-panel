package com.tariffia.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tariffia.panel.data.providers.ProviderRow
import com.tariffia.panel.data.providers.ProviderStatus

private val ConfiguredColor = Color(0xFF2E7D32)
private val NotConfiguredColor = Color(0xFFC62828)

/**
 * Providers screen. Shows only what the router reports through `/healthz`; statuses
 * are never hard-coded and default to Unknown when unavailable. No API-key input yet.
 */
@Composable
fun ProvidersScreen(
    onOpenSettings: () -> Unit,
    viewModel: ProvidersViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Providers", style = MaterialTheme.typography.headlineSmall)

        when (state.loadState) {
            ProvidersLoadState.LOADING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ProvidersLoadState.NOT_CONFIGURED -> {
                Text(
                    text = "Router credentials not configured.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            ProvidersLoadState.ERROR -> {
                Text(
                    text = state.errorMessage ?: "Provider status unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NotConfiguredColor,
                )
                Text(
                    text = "Showing Unknown until the router is reachable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ProvidersLoadState.READY -> {
                Text(
                    text = "Reported by the router's /healthz status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (state.loadState != ProvidersLoadState.LOADING) {
            state.rows.forEach { row -> ProviderRowItem(row) }
        }

        Button(
            onClick = viewModel::refresh,
            enabled = state.loadState != ProvidersLoadState.LOADING,
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun ProviderRowItem(row: ProviderRow) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = statusLabel(row.status),
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor(row.status),
            )
        }
        row.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun statusLabel(status: ProviderStatus): String = when (status) {
    ProviderStatus.CONFIGURED -> "Configured"
    ProviderStatus.NOT_CONFIGURED -> "Not configured"
    ProviderStatus.UNKNOWN -> "Unknown"
}

@Composable
private fun statusColor(status: ProviderStatus): Color = when (status) {
    ProviderStatus.CONFIGURED -> ConfiguredColor
    ProviderStatus.NOT_CONFIGURED -> NotConfiguredColor
    ProviderStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}
