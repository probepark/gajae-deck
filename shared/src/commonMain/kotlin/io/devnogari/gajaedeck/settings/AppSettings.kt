package io.devnogari.gajaedeck.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullStateFlow
import com.russhwolf.settings.coroutines.getStringStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Keys used by [AppSettings]; centralized so they cannot drift between read and write paths. */
object SettingsKeys {
    const val THEME_MODE = "theme_mode"
    const val LAST_PAIRING_ID = "last_pairing_id"
    const val PAIRINGS = "pairings"
}

/**
 * Reactive, non-secret application settings: theme, last-used pairing, and pairing metadata list.
 * Backed by multiplatform-settings; secrets are never stored here (see [PairingMetadata]).
 */
interface AppSettings {
    val themeMode: StateFlow<ThemeMode>
    val lastPairingId: StateFlow<String?>
    val pairings: StateFlow<List<PairingMetadata>>

    fun setThemeMode(mode: ThemeMode)
    fun setLastPairingId(id: String?)
    fun setPairings(list: List<PairingMetadata>)
    fun upsertPairing(metadata: PairingMetadata)
    fun removePairing(id: String)
}

@OptIn(ExperimentalSettingsApi::class)
class ObservableAppSettings(
    private val settings: ObservableSettings,
    scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : AppSettings {

    private val pairingListSerializer = ListSerializer(PairingMetadata.serializer())

    override val themeMode: StateFlow<ThemeMode> =
        settings.getStringStateFlow(scope, SettingsKeys.THEME_MODE, ThemeMode.SYSTEM.name)
            .map { it.toThemeModeOrDefault() }
            .stateIn(scope, SharingStarted.Eagerly, settings.themeModeNow())

    override val lastPairingId: StateFlow<String?> =
        settings.getStringOrNullStateFlow(scope, SettingsKeys.LAST_PAIRING_ID)

    override val pairings: StateFlow<List<PairingMetadata>> =
        settings.getStringStateFlow(scope, SettingsKeys.PAIRINGS, EMPTY_JSON_ARRAY)
            .map { decodePairings(it) }
            .stateIn(scope, SharingStarted.Eagerly, decodePairings(settings.pairingsJsonNow()))

    override fun setThemeMode(mode: ThemeMode) {
        settings.putString(SettingsKeys.THEME_MODE, mode.name)
    }

    override fun setLastPairingId(id: String?) {
        if (id == null) settings.remove(SettingsKeys.LAST_PAIRING_ID)
        else settings.putString(SettingsKeys.LAST_PAIRING_ID, id)
    }

    override fun setPairings(list: List<PairingMetadata>) {
        settings.putString(SettingsKeys.PAIRINGS, json.encodeToString(pairingListSerializer, list))
    }

    override fun upsertPairing(metadata: PairingMetadata) {
        val next = pairings.value.filterNot { it.id == metadata.id } + metadata
        setPairings(next)
    }

    override fun removePairing(id: String) {
        setPairings(pairings.value.filterNot { it.id == id })
    }

    private fun decodePairings(raw: String): List<PairingMetadata> =
        // Persisted JSON is app-owned data at a storage boundary; a malformed/legacy blob degrades to
        // an empty list rather than crashing the UI. This is not masking a logic error.
        runCatching { json.decodeFromString(pairingListSerializer, raw) }.getOrDefault(emptyList())

    private fun ObservableSettings.themeModeNow(): ThemeMode =
        getString(SettingsKeys.THEME_MODE, ThemeMode.SYSTEM.name).toThemeModeOrDefault()

    private fun ObservableSettings.pairingsJsonNow(): String =
        getString(SettingsKeys.PAIRINGS, EMPTY_JSON_ARRAY)

    private companion object {
        const val EMPTY_JSON_ARRAY = "[]"
    }
}

private fun String.toThemeModeOrDefault(): ThemeMode =
    runCatching { ThemeMode.valueOf(this) }.getOrDefault(ThemeMode.SYSTEM)
