package io.devnogari.gajaedeck.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.devnogari.gajaedeck.auth.StoredPairing
import io.devnogari.gajaedeck.pairing.PairingWriteResult
import io.devnogari.gajaedeck.pairing.PairingRepository
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.ui.PairingsListScreen
import io.devnogari.gajaedeck.ui.SessionController
import io.devnogari.gajaedeck.ui.SessionScreen
import io.devnogari.gajaedeck.ui.SettingsScreen
import kotlinx.coroutines.launch

/**
 * App navigation graph: PairingsList → Session(pairingId) → Settings. Connecting persists the pairing
 * secret through [PairingRepository] (so the token never enters the backstack) and then navigates by
 * id; [sessionControllerFactory] builds the controller for a saved pairing (wired in the App.kt
 * composition root). Screens receive only the data and callbacks they need.
 */
@Composable
fun AppNavHost(
    pairingRepository: PairingRepository,
    appSettings: AppSettings,
    storageLowerAssurance: Boolean,
    sessionControllerFactory: (pairingId: String) -> SessionController,
    navController: NavHostController = rememberNavController(),
) {
    val actions = remember(navController) { NavigationActions(navController) }
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = PairingsListRoute) {
        composable<PairingsListRoute> {
            val pairings by pairingRepository.pairings.collectAsState()
            var connectFailed by remember { mutableStateOf(false) }
            PairingsListScreen(
                pairings = pairings,
                onConnect = { pairing: StoredPairing ->
                    connectFailed = false
                    scope.launch {
                        // Navigate only after the secret + metadata persist; on failure stay put and
                        // surface a safe localized message (the error never includes the token).
                        when (pairingRepository.createPairing(pairing, replaceExisting = true)) {
                            is PairingWriteResult.Success -> actions.openSession(pairing.id)
                            is PairingWriteResult.Failure -> connectFailed = true
                        }
                    }
                },
                onOpenPairing = actions::openSession,
                onOpenSettings = actions::openSettings,
                connectFailed = connectFailed,
            )
        }
        composable<SessionRoute> { entry ->
            val pairingId = entry.toRoute<SessionRoute>().pairingId
            val controller = remember(pairingId) { sessionControllerFactory(pairingId) }
            LaunchedEffect(controller) { controller.connect() }
            SessionScreen(controller, onBack = actions::back)
        }
        composable<SettingsRoute> {
            SettingsScreen(appSettings = appSettings, storageLowerAssurance = storageLowerAssurance, onBack = actions::back)
        }
    }
}
