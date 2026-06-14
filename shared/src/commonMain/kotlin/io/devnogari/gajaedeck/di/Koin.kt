package io.devnogari.gajaedeck.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Per-platform Koin module. Filled with real providers (SecureStore, ObservableSettings, etc.)
 * in later stories; kept minimal here so app behavior is unchanged until the DI graph is ready.
 */
expect fun platformModule(): Module

/** Aggregate module list for the app. Expanded as feature modules are introduced. */
fun appModules(): List<Module> = listOf(platformModule())

/**
 * Idempotent Koin startup. Calling this more than once is a no-op that returns the existing
 * [Koin] instead of throwing KoinAppAlreadyStartedException, so platform entry points
 * (Android Application, iOS/Desktop/Web main) can each call it defensively.
 */
fun initGajaeDeckKoinOnce(appDeclaration: KoinAppDeclaration = {}): Koin {
    KoinPlatform.getKoinOrNull()?.let { return it }
    return startKoin {
        appDeclaration()
        modules(appModules())
    }.koin
}
