package com.tariffia.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tariffia.panel.ui.TariffiaApp
import com.tariffia.panel.ui.theme.TariffiaPanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TariffiaPanelTheme {
                TariffiaApp()
            }
        }
    }
}
