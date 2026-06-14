package io.devnogari.gajaedeck

import android.app.Application
import io.devnogari.gajaedeck.di.initGajaeDeckKoinAndroid

/**
 * Owns Koin startup for the Android process. [MainActivity] stays UI-only; DI is started here so
 * the graph exists before any Activity is created.
 */
class GajaeDeckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initGajaeDeckKoinAndroid(this)
    }
}
