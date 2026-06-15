package io.devnogari.gajaedeck.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.ui.ControlSessionControllerFactory
import io.devnogari.gajaedeck.ui.ProjectSessionsScreen
import io.devnogari.gajaedeck.ui.ProjectsScreen
import io.devnogari.gajaedeck.ui.SessionController
import io.devnogari.gajaedeck.ui.SessionScreen
import io.devnogari.gajaedeck.ui.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    controlPlaneRepository: ControlPlaneRepository,
    controlPlaneClient: ControlPlaneClient,
    controlSessionControllerFactory: ControlSessionControllerFactory,
    appSettings: AppSettings,
    storageLowerAssurance: Boolean,
) {
    val actions = remember(navController) { NavigationActions(navController) }
    val scope = rememberCoroutineScope()
    val controlPlaneId by produceState<String?>(initialValue = null, controlPlaneRepository) {
        value = controlPlaneRepository.list().firstOrNull()?.id
    }

    NavHost(navController = navController, startDestination = ProjectsRoute) {
        composable<ProjectsRoute> {
            ProjectsScreen(
                controlPlaneClient = controlPlaneClient,
                onProjectSelected = actions::openProjectSessions,
            )
        }
        composable<ProjectSessionsRoute> { entry ->
            val route = entry.toRoute<ProjectSessionsRoute>()
            ProjectSessionsScreen(
                projectId = route.projectId,
                controlPlaneClient = controlPlaneClient,
                onSessionSelected = actions::openSession,
                onSessionStarted = { sessionRoute ->
                    controlSessionControllerFactory.rememberStartedRoute(sessionRoute)
                    actions.openSession(sessionRoute.sessionId)
                },
            )
        }
        composable<SessionRoute> { entry ->
            val route = entry.toRoute<SessionRoute>()
            if (controlPlaneId == null) {
                Text("No control plane configured")
            } else {
                val controllerState = produceState<Result<SessionController>?>(initialValue = null, controlPlaneId, route.sessionId) {
                    value = runCatching {
                        controlSessionControllerFactory.forSession(
                            controlPlaneId = controlPlaneId!!,
                            sessionId = route.sessionId,
                            scope = scope,
                        )
                    }
                }
                when (val controllerResult = controllerState.value) {
                    null -> Loading()
                    else -> controllerResult.fold(
                        onSuccess = { controller ->
                            LaunchedEffect(controller) { controller.connect() }
                            SessionScreen(controller = controller, onBack = actions::back)
                        },
                        onFailure = { error -> Text("Failed to open session: ${error.message ?: "unknown"}") },
                    )
                }
            }
        }
        composable<SettingsRoute> {
            SettingsScreen(appSettings = appSettings, storageLowerAssurance = storageLowerAssurance, onBack = actions::back)
        }
        composable<PairingsListRoute> {
            ProjectsScreen(
                controlPlaneClient = controlPlaneClient,
                onProjectSelected = actions::openProjectSessions,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
