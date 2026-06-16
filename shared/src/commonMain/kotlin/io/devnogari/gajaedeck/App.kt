package io.devnogari.gajaedeck

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.devnogari.gajaedeck.auth.StoredControlPlane
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.control.KtorControlPlaneClient
import io.devnogari.gajaedeck.navigation.AppNavHost
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.theme.GajaeDeckTheme
import io.devnogari.gajaedeck.ui.ControlSessionControllerFactory
import org.koin.compose.koinInject

@Composable
fun App(storageLowerAssurance: Boolean = false) {
    val controlPlaneRepository: ControlPlaneRepository = koinInject()
    val appSettings: AppSettings = koinInject()
    val themeMode by appSettings.themeMode.collectAsState()
    val stored = produceState<StoredControlPlane?>(initialValue = null, controlPlaneRepository) {
        value = controlPlaneRepository.list().firstOrNull()
    }.value

    GajaeDeckTheme(themeMode = themeMode) {
        Surface(Modifier.fillMaxSize()) {
            val controlPlane = stored
            if (controlPlane == null) {
                Text("No control plane configured")
            } else {
                val controlPlaneClient = KtorControlPlaneClient(
                    supervisorBaseUrl = controlPlane.supervisorBaseUrl,
                    controlToken = controlPlane.controlToken,
                )
                AppNavHost(
                    navController = rememberNavController(),
                    controlPlaneRepository = controlPlaneRepository,
                    controlPlaneClient = controlPlaneClient,
                        appSettings = appSettings,
                    controlSessionControllerFactory = ControlSessionControllerFactory(
                        repository = controlPlaneRepository,
                        controlPlaneClient = controlPlaneClient,
                    ),
                    storageLowerAssurance = storageLowerAssurance,
                )
            }
        }
    }
}
