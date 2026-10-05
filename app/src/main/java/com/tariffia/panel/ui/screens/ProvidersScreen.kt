package com.tariffia.panel.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tariffia.panel.data.providers.ProviderRow

/**
 * Providers screen. Shows only what the router reports through `/healthz`; statuses
 * are never hard-coded and default to Unknown when unavailable. Each row also shows
 * whether an API key is stored locally, kept clearly separate from router status.
 */
@Composable
fun ProvidersScreen(
    onOpenSettings: () -> Unit,
    onProviderClick: (ProviderRow) -> Unit,
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
                    text = "Router status Unknown until the router is reachable. Local key status is unaffected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ProvidersLoadState.READY -> {
                Text(
                    text = "Router status from /healthz. Tap a provider for details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (state.loadState != ProvidersLoadState.LOADING) {
            state.rows.forEach { row ->
                ProviderRowItem(row = row, onClick = { onProviderClick(row) })
            }
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
private fun ProviderRowItem(row: ProviderRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = providerStatusLabel(row.status),
                style = MaterialTheme.typography.bodyMedium,
                color = providerStatusColor(row.status),
            )
        }
        Text(
            text = "API key stored locally: ${if (row.hasLocalKey) "Yes" else "No"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        row.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
