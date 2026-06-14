package io.devnogari.gajaedeck.di

import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // Web (wasmJs) platform providers are registered in later stories.
}
