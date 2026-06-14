package io.devnogari.gajaedeck.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json

/**
 * Android [SecureStore] backed by EncryptedSharedPreferences (AES256-SIV keys, AES256-GCM values)
 * with a hardware-backed [MasterKey]. Each pairing is stored as a serialized [StoredPairing] under a
 * namespaced key; secrets are encrypted at rest by the OS keystore.
 */
class AndroidSecureStore(
    context: Context,
    fileName: String = "gajae_deck_secure_pairings",
) : SecureStore {

    private val appContext: Context = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun keyOf(id: String) = "$KEY_PREFIX$id"

    override suspend fun save(pairing: StoredPairing) {
        prefs.edit()
            .putString(keyOf(pairing.id), json.encodeToString(StoredPairing.serializer(), pairing))
            .apply()
    }

    override suspend fun load(id: String): StoredPairing? =
        prefs.getString(keyOf(id), null)?.let { json.decodeFromString(StoredPairing.serializer(), it) }

    override suspend fun list(): List<StoredPairing> =
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { prefs.getString(it, null) }
            .map { json.decodeFromString(StoredPairing.serializer(), it) }

    override suspend fun delete(id: String) {
        prefs.edit().remove(keyOf(id)).apply()
    }

    private companion object {
        const val KEY_PREFIX = "pairing:"
    }
}
