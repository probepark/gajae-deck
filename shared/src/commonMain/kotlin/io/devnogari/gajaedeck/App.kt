package io.devnogari.gajaedeck

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.navigation.AppNavHost
import io.devnogari.gajaedeck.pairing.PairingRepository
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.theme.GajaeDeckTheme
import io.devnogari.gajaedeck.ui.SessionControllerFactory
import org.koin.compose.koinInject

/**
 * Koin-backed composition root. Pulls the app graph from Koin (started by each platform entry point
 * via initGajaeDeckKoinOnce), applies the gajae-deck theme from the persisted theme mode, and renders
 * the navigation host. No connectors or controllers are constructed here — that is owned by
 * [SessionControllerFactory] / [PairingRepository].
 */
@Composable
fun App() {
    val appSettings = koinInject<AppSettings>()
    val pairingRepository = koinInject<PairingRepository>()
    val sessionControllerFactory = koinInject<SessionControllerFactory>()
    val secureStore = koinInject<SecureStore>()

    val themeMode by appSettings.themeMode.collectAsState()

    GajaeDeckTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                pairingRepository = pairingRepository,
                appSettings = appSettings,
                storageLowerAssurance = secureStore.assurance.isLowerAssurance,
                sessionControllerFactory = sessionControllerFactory,
            )
        }
    }
}
