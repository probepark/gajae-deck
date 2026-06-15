package io.devnogari.gajaedeck.navigation

import androidx.navigation.NavHostController

/** Navigation helpers keep secrets out of route arguments/back stack. */
class NavigationActions(private val navController: NavHostController) {
    fun openProjects() {
        navController.navigate(ProjectsRoute)
    }

    fun openProjectSessions(projectId: String) {
        navController.navigate(ProjectSessionsRoute(projectId))
    }

    fun openSession(sessionId: String) {
        navController.navigate(SessionRoute(sessionId))
    }

    fun openSettings() {
        navController.navigate(SettingsRoute)
    }

    fun back() {
        navController.popBackStack()
    }
}
