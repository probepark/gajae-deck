package io.devnogari.gajaedeck.auth

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/**
 * Web [SecureStore] backed by namespaced localStorage. The browser has no OS-backed secure storage,
 * so this is a LOWER-ASSURANCE store: values are readable by same-origin JavaScript. Callers should
 * surface that to the user (see the settings screen) and prefer session-scoped tokens on web.
 */
class WebLocalStorageSecureStore(
    private val namespace: String = "gajae-deck:pairing:",
) : SecureStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val assurance: StorageAssurance = StorageAssurance.BROWSER_LOCAL_STORAGE

    private fun keyOf(id: String) = "$namespace$id"

    override suspend fun save(pairing: StoredPairing) {
        localStorage.setItem(keyOf(pairing.id), json.encodeToString(StoredPairing.serializer(), pairing))
    }

    override suspend fun load(id: String): StoredPairing? =
        localStorage.getItem(keyOf(id))?.let { json.decodeFromString(StoredPairing.serializer(), it) }

    override suspend fun list(): List<StoredPairing> =
        buildList {
            for (i in 0 until localStorage.length) {
                val key = localStorage.key(i) ?: continue
                if (!key.startsWith(namespace)) continue
                val raw = localStorage.getItem(key) ?: continue
                add(json.decodeFromString(StoredPairing.serializer(), raw))
            }
        }

    override suspend fun delete(id: String) {
        localStorage.removeItem(keyOf(id))
    }
}
