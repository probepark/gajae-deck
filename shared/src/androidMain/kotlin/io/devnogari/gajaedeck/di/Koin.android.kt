package io.devnogari.gajaedeck.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // Android platform providers (SecureStore, ObservableSettings, ...) are registered in later stories.
}

/**
 * Android startup wrapper. Owned by the application process ([GajaeDeckApplication]); installs the
 * applicationContext so Android-backed providers can resolve it. Idempotent via [initGajaeDeckKoinOnce].
 * Returns Unit so the :androidApp module does not need koin-core on its compile classpath.
 */
fun initGajaeDeckKoinAndroid(context: Context) {
    initGajaeDeckKoinOnce {
        androidContext(context.applicationContext)
    }
}
