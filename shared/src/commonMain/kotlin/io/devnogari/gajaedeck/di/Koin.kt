package io.devnogari.gajaedeck.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Per-platform Koin module providing platform-backed singletons (SecureStore, Settings).
 * The actual implementations live in each platform source set; [appModules] composes this with the
 * shared feature modules.
 */
expect fun platformModule(): Module

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
