package io.devnogari.gajaedeck.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
actual fun BindBrowserNavigation(navController: NavHostController) {
    // No browser address bar on iOS.
}
