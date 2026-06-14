package io.devnogari.gajaedeck.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.devnogari.gajaedeck.auth.DesktopFileSecureStore
import io.devnogari.gajaedeck.auth.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.prefs.Preferences

actual fun platformModule(): Module = module {
    single<SecureStore> { DesktopFileSecureStore(File(System.getProperty("user.home"), ".gajae-deck/secure")) }
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("io.devnogari.gajaedeck")) }
}
