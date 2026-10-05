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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val OnlineColor = Color(0xFF2E7D32)
private val OfflineColor = Color(0xFFC62828)

/**
 * Home/Status screen: shows router reachability and the model IDs the router
 * reports. The router token is never displayed; only configuration state is.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Tariffia Router", style = MaterialTheme.typography.headlineSmall)

        StatusRow(state.status)

        when (val status = state.status) {
            HomeStatus.NotConfigured -> {
                Text(
                    text = "Router credentials not configured.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            HomeStatus.AuthFailed -> {
                Text(text = "Authentication failed.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Check the router token in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            is HomeStatus.Offline -> {
                Text(text = status.message, style = MaterialTheme.typography.bodyMedium)
            }
            else -> Unit
        }

        if (state.status == HomeStatus.Online) {
            Text(text = "Models", style = MaterialTheme.typography.titleMedium)
            if (state.models.isEmpty()) {
                Text(
                    text = state.modelsError ?: "No models reported.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.models.forEach { id ->
                    Text(text = "• $id", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = viewModel::refresh,
            enabled = !state.isLoading,
        ) {
            Text("Refresh")
        }

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusRow(status: HomeStatus) {
    val color = when (status) {
        HomeStatus.Online -> OnlineColor
        is HomeStatus.Offline, HomeStatus.AuthFailed -> OfflineColor
        else -> MaterialTheme.colorScheme.outline
    }
    val label = when (status) {
        HomeStatus.Unknown -> "Checking…"
        HomeStatus.Online -> "Online"
        HomeStatus.AuthFailed -> "Authentication failed"
        HomeStatus.NotConfigured -> "Not configured"
        is HomeStatus.Offline -> "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "●", color = color, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Status: $label", style = MaterialTheme.typography.bodyLarge)
    }
}
