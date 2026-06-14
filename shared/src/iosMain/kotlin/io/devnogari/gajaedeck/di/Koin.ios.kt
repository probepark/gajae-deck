package io.devnogari.gajaedeck.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.devnogari.gajaedeck.auth.IosKeychainSecureStore
import io.devnogari.gajaedeck.auth.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule(): Module = module {
    single<SecureStore> { IosKeychainSecureStore() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
