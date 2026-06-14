package io.devnogari.gajaedeck.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * [AppSettings] over a plain multiplatform-settings [Settings], which works on every platform
 * including web localStorage (where no observable Settings exists). Reactivity is provided by
 * write-through [StateFlow]s the class owns: because pairing/settings mutation flows through a single
 * writer ([io.devnogari.gajaedeck.pairing.PairingRepository] and this class), updating the flow in
 * each setter keeps observers in sync without depending on a platform change-listener.
 */
class ObservableAppSettings(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : AppSettings {

    private val pairingListSerializer = ListSerializer(PairingMetadata.serializer())

    private val themeState = MutableStateFlow(readTheme())
    private val lastState = MutableStateFlow(settings.getStringOrNull(SettingsKeys.LAST_PAIRING_ID))
    private val pairingState = MutableStateFlow(readPairings())

    override val themeMode: StateFlow<ThemeMode> = themeState.asStateFlow()
    override val lastPairingId: StateFlow<String?> = lastState.asStateFlow()
    override val pairings: StateFlow<List<PairingMetadata>> = pairingState.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        settings.putString(SettingsKeys.THEME_MODE, mode.name)
        themeState.value = mode
    }

    override fun setLastPairingId(id: String?) {
        if (id == null) settings.remove(SettingsKeys.LAST_PAIRING_ID) else settings.putString(SettingsKeys.LAST_PAIRING_ID, id)
        lastState.value = id
    }

    override fun setPairings(list: List<PairingMetadata>) {
        settings.putString(SettingsKeys.PAIRINGS, json.encodeToString(pairingListSerializer, list))
        pairingState.value = list
    }

    override fun upsertPairing(metadata: PairingMetadata) {
        setPairings(pairingState.value.filterNot { it.id == metadata.id } + metadata)
    }

    override fun removePairing(id: String) {
        setPairings(pairingState.value.filterNot { it.id == id })
    }

    private fun readTheme(): ThemeMode =
        // Persisted theme is app-owned data at a storage boundary; a malformed/legacy value degrades
        // to SYSTEM rather than crashing the UI. This is not masking a logic error.
        runCatching { ThemeMode.valueOf(settings.getString(SettingsKeys.THEME_MODE, ThemeMode.SYSTEM.name)) }
            .getOrDefault(ThemeMode.SYSTEM)

    private fun readPairings(): List<PairingMetadata> =
        runCatching { json.decodeFromString(pairingListSerializer, settings.getString(SettingsKeys.PAIRINGS, "[]")) }
            .getOrDefault(emptyList())
}
