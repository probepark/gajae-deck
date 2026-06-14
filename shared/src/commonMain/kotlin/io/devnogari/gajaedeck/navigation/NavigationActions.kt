package io.devnogari.gajaedeck.navigation

import androidx.navigation.NavHostController

/**
 * Typed navigation entry points over a [NavHostController]. Created per-NavHost (no global singleton),
 * so navigation is testable and scoped to the composition that owns the controller.
 */
class NavigationActions(private val navController: NavHostController) {
    fun openSession(pairingId: String) {
        navController.navigate(SessionRoute(pairingId))
    }

    fun openSettings() {
        navController.navigate(SettingsRoute)
    }

    fun back() {
        navController.popBackStack()
    }
}
