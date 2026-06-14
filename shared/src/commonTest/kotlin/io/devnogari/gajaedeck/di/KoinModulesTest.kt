package io.devnogari.gajaedeck.di

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.devnogari.gajaedeck.auth.InMemorySecureStore
import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.TofuVerifier
import io.devnogari.gajaedeck.auth.TokenLifecycle
import io.devnogari.gajaedeck.pairing.PairingRepository
import io.devnogari.gajaedeck.observability.AppLogger
import io.devnogari.gajaedeck.observability.ErrorHandler
import io.devnogari.gajaedeck.ui.SessionControllerFactory
import io.devnogari.gajaedeck.settings.AppSettings
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class KoinModulesTest {

    // Platform module stand-in so the shared graph can be verified on the JVM test target without a
    // real Android Context / Keychain. Mirrors the singletons the real platformModule provides.
    private fun testPlatformModule() = module {
        single<SecureStore> { InMemorySecureStore() }
        single<Settings> { MapSettings() }
    }

    @AfterTest
    fun tearDown() {
        if (KoinPlatform.getKoinOrNull() != null) stopKoin()
    }

    @Test
    fun featureGraphResolvesEverySingleton() {
        val koin = startKoin {
            modules(appModule, authModule, observabilityModule, settingsModule, pairingModule, uiModule, testPlatformModule())
        }.koin

        // Every feature singleton must be constructible from the wired graph.
        assertNotNull(koin.get<Json>())
        assertNotNull(koin.get<Redactor>())
        assertNotNull(koin.get<AppLogger>())
        assertNotNull(koin.get<ErrorHandler>())
        assertNotNull(koin.get<TokenLifecycle>())
        assertNotNull(koin.get<TofuVerifier>())
        assertNotNull(koin.get<AppSettings>())
        assertNotNull(koin.get<SecureStore>())
        assertNotNull(koin.get<Settings>())
        assertNotNull(koin.get<PairingRepository>())
        assertNotNull(koin.get<SessionControllerFactory>())
    }

    @Test
    fun singletonsAreSingletons() {
        val koin = startKoin {
            modules(appModule, authModule, observabilityModule, settingsModule, pairingModule, uiModule, testPlatformModule())
        }.koin
        assertSame(koin.get<PairingRepository>(), koin.get<PairingRepository>())
        assertSame(koin.get<AppSettings>(), koin.get<AppSettings>())
    }

    @Test
    fun appModulesIncludesAllFeatureModules() {
        // appModules() composes the feature modules + the real platformModule; structural sanity check.
        val expected = listOf(appModule, authModule, observabilityModule, settingsModule, pairingModule, uiModule)
        val composed = appModules()
        assertNotNull(composed)
        expected.forEach { module -> assertNotNull(composed.firstOrNull { it === module }, "missing feature module") }
    }
}
