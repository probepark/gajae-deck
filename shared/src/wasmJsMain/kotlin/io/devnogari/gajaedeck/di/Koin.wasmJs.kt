package io.devnogari.gajaedeck.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.WebLocalStorageSecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SecureStore> { WebLocalStorageSecureStore() }
    single<Settings> { StorageSettings() }
}
