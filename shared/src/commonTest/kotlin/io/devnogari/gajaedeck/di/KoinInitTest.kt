package io.devnogari.gajaedeck.di

import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KoinInitTest {

    @AfterTest
    fun tearDown() {
        if (KoinPlatform.getKoinOrNull() != null) stopKoin()
    }

    @Test
    fun startsKoinWithAppModules() {
        assertNull(KoinPlatform.getKoinOrNull(), "Koin must not be started before init")
        val koin = initGajaeDeckKoinOnce()
        assertSame(koin, KoinPlatform.getKoinOrNull(), "init must register the global Koin instance")
    }

    @Test
    fun initIsIdempotent() {
        val first = initGajaeDeckKoinOnce()
        val second = initGajaeDeckKoinOnce()
        assertSame(first, second, "repeated init must return the existing Koin, not restart it")
    }

    @Test
    fun appModulesContainsPlatformModule() {
        assertTrue(appModules().isNotEmpty(), "appModules must include at least the platform module")
    }
}
