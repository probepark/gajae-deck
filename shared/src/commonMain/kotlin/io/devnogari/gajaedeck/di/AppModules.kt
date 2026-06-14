package io.devnogari.gajaedeck.di

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.auth.TofuVerifier
import io.devnogari.gajaedeck.auth.TokenLifecycle
import io.devnogari.gajaedeck.pairing.PairingRepository
import io.devnogari.gajaedeck.observability.AppLogger
import io.devnogari.gajaedeck.observability.ErrorHandler
import io.devnogari.gajaedeck.ui.SessionControllerFactory
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.settings.ObservableAppSettings
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/** App-level singletons shared across features. */
val appModule: Module = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
}

/** Auth/secret-layer singletons over the platform [io.devnogari.gajaedeck.auth.SecureStore]. */
val authModule: Module = module {
    single { Redactor() }
    single { TokenLifecycle(get()) }
    single { TofuVerifier(get()) }
}

/** Reactive settings backed by the platform Settings store. */
val settingsModule: Module = module {
    single<AppSettings> { ObservableAppSettings(settings = get()) }
}

/** Pairing invariant owner. */
val pairingModule: Module = module {
    single { PairingRepository(secureStore = get(), settings = get(), tokenLifecycle = get()) }
}

/** Observability: a redaction-by-construction logger and the user-facing error mapper. */
val observabilityModule: Module = module {
    single { AppLogger.redacting(redactor = get()) }
    single { ErrorHandler(redactor = get()) }
}

/** UI composition seam: builds a redaction-seeded SessionController for a saved pairing. */
val uiModule: Module = module {
    single { SessionControllerFactory(secureStore = get()) }
}

/** The full Koin graph: shared feature modules plus the per-platform [platformModule]. */
fun appModules(): List<Module> = listOf(
    appModule,
    authModule,
    observabilityModule,
    settingsModule,
    pairingModule,
    uiModule,
    platformModule(),
)
