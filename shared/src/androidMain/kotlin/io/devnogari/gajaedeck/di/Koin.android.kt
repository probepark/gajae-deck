package io.devnogari.gajaedeck.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.devnogari.gajaedeck.auth.AndroidSecureStore
import io.devnogari.gajaedeck.auth.SecureStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SecureStore> { AndroidSecureStore(androidContext()) }
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("gajae_deck_settings", Context.MODE_PRIVATE),
        )
    }
}

/**
 * Android startup wrapper. Owned by the application process ([io.devnogari.gajaedeck.GajaeDeckApplication]);
 * installs the applicationContext so Android-backed providers can resolve it. Idempotent via
 * [initGajaeDeckKoinOnce]. Returns Unit so the :androidApp module does not need koin-core on its
 * compile classpath.
 */
fun initGajaeDeckKoinAndroid(context: Context) {
    initGajaeDeckKoinOnce {
        androidContext(context.applicationContext)
    }
}
