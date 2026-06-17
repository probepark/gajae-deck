package io.devnogari.gajaedeck.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * Keeps the [navController] back stack in sync with the browser URL on web (deep-linkable URLs +
 * working back/forward). No-op on platforms without a browser address bar.
 */
@Composable
expect fun BindBrowserNavigation(navController: NavHostController)
