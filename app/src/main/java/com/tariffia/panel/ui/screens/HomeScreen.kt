package com.tariffia.panel.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tariffia.panel.data.router.RouterRuntime

private val OnlineColor = Color(0xFF2E7D32)
private val ErrorColor = Color(0xFFC62828)

/**
 * Home/Status dashboard: router reachability, a short provider summary and the model
 * IDs the router reports. The router token is never displayed.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    routerRuntimeViewModel: RouterRuntimeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val runtimeStatus by routerRuntimeViewModel.state.collectAsState()
    val syncSummary by routerRuntimeViewModel.syncSummary.collectAsState()
    val context = LocalContext.current
    // Request the notification permission (Android 13+) but never block startup on it.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or denied: the service runs either way */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Tariffia Router", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Status dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        (updateState as? UpdateState.Available)?.let { update ->
            UpdateBanner(
                version = update.version,
                onView = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
                    }
                },
            )
        }

        RouterRuntimeCard(
            status = runtimeStatus,
            syncSummary = syncSummary,
            onStart = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                routerRuntimeViewModel.start(context)
            },
            onStop = { routerRuntimeViewModel.stop(context) },
        )

        StatusCard(state.status)

        when (state.status) {
            HomeStatus.NotConfigured -> ActionCard(
                message = "Router credentials not configured.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )
            HomeStatus.AuthFailed -> ActionCard(
                message = "Authentication failed. Check the router token in Settings.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )
            else -> Unit
        }

        if (state.status == HomeStatus.Online) {
            state.providerSummary?.let { ProviderSummaryCard(it) }
            ModelsCard(models = state.models, modelsError = state.modelsError)
        }

        Button(
            onClick = viewModel::refresh,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh")
        }

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusCard(status: HomeStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "●", color = statusColor(status), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = statusLabel(status), style = MaterialTheme.typography.titleMedium)
            }
            statusDescription(status)?.let { description ->
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
private fun ActionCard(message: String, actionLabel: String, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ProviderSummaryCard(summary: HomeProviderSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Providers", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${summary.configured} configured · ${summary.warned} with warnings",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.warnedProviderIds.isNotEmpty()) {
                Text(
                    text = "With warnings: ${summary.warnedProviderIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = "${summary.candidates} model candidates",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ModelsCard(models: List<String>, modelsError: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Models (${models.size})", style = MaterialTheme.typography.titleMedium)
            when {
                modelsError != null -> Text(
                    text = modelsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorColor,
                )
                models.isEmpty() -> Text(
                    text = "No models reported by the router.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                else -> models.forEach { id ->
                    Text(text = "• $id", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun statusLabel(status: HomeStatus): String = when (status) {
    HomeStatus.Checking -> "Checking…"
    HomeStatus.Online -> "Online"
    HomeStatus.NotConfigured -> "Not configured"
    HomeStatus.AuthFailed -> "Authentication failed"
    HomeStatus.ConnectionFailed -> "Offline"
    is HomeStatus.HttpError -> "Router error"
    is HomeStatus.InvalidResponse -> "Unexpected response"
}

private fun statusDescription(status: HomeStatus): String? = when (status) {
    HomeStatus.Checking -> null
    HomeStatus.Online -> "Router is reachable."
    HomeStatus.NotConfigured -> null
    HomeStatus.AuthFailed -> null
    HomeStatus.ConnectionFailed -> "Cannot reach the router."
    is HomeStatus.HttpError -> "Router returned HTTP ${status.code}."
    is HomeStatus.InvalidResponse -> "Unexpected response from the router."
}

@Composable
private fun statusColor(status: HomeStatus): Color = when (status) {
    HomeStatus.Online -> OnlineColor
    HomeStatus.Checking, HomeStatus.NotConfigured -> MaterialTheme.colorScheme.outline
    else -> ErrorColor
}

/** Compact, unobtrusive "update available" banner. Read-only; opens the browser. */
@Composable
private fun UpdateBanner(version: String, onView: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Update available", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "A new version of Tariffia Panel is available.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Version $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(onClick = onView) { Text("View update") }
        }
    }
}

/** Controls the embedded Router runtime via its foreground service. */
@Composable
private fun RouterRuntimeCard(
    status: RouterRuntime.State,
    syncSummary: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val running = status is RouterRuntime.State.Starting || status is RouterRuntime.State.Ready
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Embedded Router runtime", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "●",
                    color = routerRuntimeColor(status),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = routerRuntimeLabel(status), style = MaterialTheme.typography.bodyMedium)
            }
            if (status is RouterRuntime.State.Error) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorColor,
                )
            }
            syncSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start Router")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("STOP")
                }
            }
            Text(
                text = "STOP terminates the whole Panel process (the embedded Node runtime " +
                    "cannot be shut down gracefully).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (status is RouterRuntime.State.Starting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun routerRuntimeLabel(status: RouterRuntime.State): String = when (status) {
    RouterRuntime.State.Idle -> "Not started"
    RouterRuntime.State.Starting -> "STARTING…"
    RouterRuntime.State.Ready -> "READY (127.0.0.1:8910)"
    RouterRuntime.State.Stopped -> "STOPPED"
    is RouterRuntime.State.Error -> "ERROR"
}

@Composable
private fun routerRuntimeColor(status: RouterRuntime.State): Color = when (status) {
    RouterRuntime.State.Ready -> OnlineColor
    RouterRuntime.State.Starting, RouterRuntime.State.Idle, RouterRuntime.State.Stopped ->
        MaterialTheme.colorScheme.outline
    is RouterRuntime.State.Error -> ErrorColor
}
