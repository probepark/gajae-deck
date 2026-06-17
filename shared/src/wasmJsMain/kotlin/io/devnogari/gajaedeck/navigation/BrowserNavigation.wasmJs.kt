package io.devnogari.gajaedeck.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
actual fun BindBrowserNavigation(navController: NavHostController) {
    LaunchedEffect(navController) {
        // Mirrors the back stack into window.history and restores destinations from the URL,
        // so the address bar drives navigation and browser back/forward work.
        navController.bindToBrowserNavigation()
    }
}
