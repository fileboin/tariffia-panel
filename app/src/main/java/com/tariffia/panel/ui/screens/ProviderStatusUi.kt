package com.tariffia.panel.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tariffia.panel.data.providers.ProviderStatus

internal val ConfiguredColor = Color(0xFF2E7D32)
internal val NotConfiguredColor = Color(0xFFC62828)

internal fun providerStatusLabel(status: ProviderStatus): String = when (status) {
    ProviderStatus.CONFIGURED -> "Configured"
    ProviderStatus.NOT_CONFIGURED -> "Not configured"
    ProviderStatus.UNKNOWN -> "Unknown"
}

@Composable
internal fun providerStatusColor(status: ProviderStatus): Color = when (status) {
    ProviderStatus.CONFIGURED -> ConfiguredColor
    ProviderStatus.NOT_CONFIGURED -> NotConfiguredColor
    ProviderStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}
