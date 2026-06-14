package io.devnogari.gajaedeck.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import io.devnogari.gajaedeck.auth.WebStorageMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
class AppSettingsTest {

    private fun meta(id: String) =
        PairingMetadata(id = id, displayName = "dev-$id", host = "h", port = 4077, baseUrl = "https://h:4077")

    @Test
    fun themeModeRoundTripsAndDefaultsToSystem() = runTest(UnconfinedTestDispatcher()) {
        val settings = ObservableAppSettings(MapSettings(), backgroundScope)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
    }

    @Test
    fun invalidPersistedThemeDegradesToSystem() = runTest(UnconfinedTestDispatcher()) {
        val backing = MapSettings(SettingsKeys.THEME_MODE to "PURPLE")
        val settings = ObservableAppSettings(backing, backgroundScope)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
    }

    @Test
    fun lastPairingIdRoundTripsIncludingClear() = runTest(UnconfinedTestDispatcher()) {
        val settings = ObservableAppSettings(MapSettings(), backgroundScope)
        assertNull(settings.lastPairingId.value)
        settings.setLastPairingId("p1")
        assertEquals("p1", settings.lastPairingId.value)
        settings.setLastPairingId(null)
        assertNull(settings.lastPairingId.value)
    }

    @Test
    fun pairingsUpsertIsKeyedAndRemoveWorks() = runTest(UnconfinedTestDispatcher()) {
        val settings = ObservableAppSettings(MapSettings(), backgroundScope)
        assertTrue(settings.pairings.value.isEmpty())
        settings.upsertPairing(meta("a"))
        settings.upsertPairing(meta("b"))
        assertEquals(2, settings.pairings.value.size)
        // upsert same id replaces, never duplicates
        settings.upsertPairing(meta("a").copy(displayName = "renamed"))
        assertEquals(2, settings.pairings.value.size)
        assertEquals("renamed", settings.pairings.value.first { it.id == "a" }.displayName)
        settings.removePairing("a")
        assertEquals(listOf("b"), settings.pairings.value.map { it.id })
    }

    @Test
    fun pairingMetadataNeverSerializesSecrets() {
        val encoded = Json.encodeToString(PairingMetadata.serializer(), meta("x"))
        assertFalse(encoded.contains("token"), "PairingMetadata must not carry token: $encoded")
        assertFalse(encoded.contains("ownerToken"), "PairingMetadata must not carry ownerToken: $encoded")
    }

    @Test
    fun webLowerAssuranceFlagsRoundTripThroughSettings() = runTest(UnconfinedTestDispatcher()) {
        val settings = ObservableAppSettings(MapSettings(), backgroundScope)
        val webPairing = meta("web").copy(
            webStorageMode = WebStorageMode.SESSION_ONLY,
            webTrustedTls = false,
        )
        settings.upsertPairing(webPairing)
        val restored = settings.pairings.value.first { it.id == "web" }
        // The lower-assurance signals the settings UI uses to warn the user must survive persistence.
        assertEquals(WebStorageMode.SESSION_ONLY, restored.webStorageMode)
        assertFalse(restored.webTrustedTls, "untrusted-TLS web pairing must stay flagged after round-trip")
    }
}
