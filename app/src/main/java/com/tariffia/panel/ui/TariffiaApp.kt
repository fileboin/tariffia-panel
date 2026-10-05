package com.tariffia.panel.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tariffia.panel.ui.screens.HomeScreen
import com.tariffia.panel.ui.screens.PlaceholderScreen
import com.tariffia.panel.ui.screens.SettingsScreen

/** The four MVP destinations. */
enum class Destination(val route: String, val label: String) {
    Home("home", "Home"),
    Vps("vps", "VPS / SSH"),
    Providers("providers", "Providers"),
    Settings("settings", "Settings"),
}

@Composable
fun TariffiaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(Destination.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenSettings = {
                        navController.navigate(Destination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Destination.Vps.route) {
                PlaceholderScreen("VPS / SSH", "VPS host, SSH key and test connection will go here.")
            }
            composable(Destination.Providers.route) {
                PlaceholderScreen("Providers", "Provider API keys and configured status will go here.")
            }
            composable(Destination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
