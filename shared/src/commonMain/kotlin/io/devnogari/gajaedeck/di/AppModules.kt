package io.devnogari.gajaedeck.di

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.auth.StoredControlPlane
import io.devnogari.gajaedeck.auth.TofuVerifier
import io.devnogari.gajaedeck.auth.TokenLifecycle
import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.ControlPlaneRepository
import io.devnogari.gajaedeck.control.KtorControlPlaneClient
import io.devnogari.gajaedeck.notifications.DeviceRegistrar
import io.devnogari.gajaedeck.notifications.DeepLinkResumeHandler
import io.devnogari.gajaedeck.notifications.PushTokenProvider
import io.devnogari.gajaedeck.notifications.platformDeviceRegistrar
import io.devnogari.gajaedeck.notifications.platformPushTokenProvider
import io.devnogari.gajaedeck.observability.AppLogger
import io.devnogari.gajaedeck.observability.ErrorHandler
import io.devnogari.gajaedeck.pairing.PairingRepository
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.settings.ObservableAppSettings
import io.devnogari.gajaedeck.ui.ControlSessionControllerFactory
import io.devnogari.gajaedeck.ui.SessionControllerFactory
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val appModule: Module = module {
    single { Json { ignoreUnknownKeys = true } }
}

/** Auth/secret-layer singletons backed by platform SecureStore. */
val authModule: Module = module {
    single { Redactor() }
    single { TokenLifecycle(get()) }
    single { TofuVerifier(get()) }
}

/** Reactive settings backed by the platform Settings store. */
val settingsModule: Module = module {
    single<AppSettings> { ObservableAppSettings(get()) }
}

/** Legacy pairing owner retained for migration. */
val pairingModule: Module = module {
    single { PairingRepository(secureStore = get(), settings = get(), tokenLifecycle = get()) }
}

/** Wave-1 control-plane integration. */
val controlModule: Module = module {
    single { ControlPlaneRepository(secureStore = get()) }
    factory<ControlPlaneClient> { (controlPlane: StoredControlPlane) ->
        KtorControlPlaneClient(
            supervisorBaseUrl = controlPlane.supervisorBaseUrl,
            controlToken = controlPlane.controlToken,
            redactor = get<Redactor>().withSecrets(setOf(controlPlane.controlToken)),
        )
    }
    single<PushTokenProvider> { platformPushTokenProvider() }
    single<DeviceRegistrar> { platformDeviceRegistrar() }
    factory { (controlPlane: StoredControlPlane) ->
        DeepLinkResumeHandler(
            controlPlaneClient = get { parametersOf(controlPlane) },
            controllerFactory = get { parametersOf(controlPlane) },
        )
    }
    factory { (controlPlane: StoredControlPlane) ->
        ControlSessionControllerFactory(
            repository = get(),
            controlPlaneClient = get { parametersOf(controlPlane) },
        )
    }
}

/** Observability: a redaction-by-construction logger mapper. */
val observabilityModule: Module = module {
    single { AppLogger.redacting(redactor = get()) }
    single { ErrorHandler(redactor = get()) }
}

/** UI composition builds redaction-seeded SessionControllers. */
val uiModule: Module = module {
    single { SessionControllerFactory(secureStore = get()) }
}

fun appModules(): List<Module> = listOf(
    appModule,
    authModule,
    observabilityModule,
    settingsModule,
    pairingModule,
    controlModule,
    uiModule,
    platformModule(),
)
