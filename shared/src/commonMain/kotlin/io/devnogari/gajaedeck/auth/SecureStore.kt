package io.devnogari.gajaedeck.auth

/**
 * Persists [StoredPairing] secrets. Platform implementations back this with the OS secure
 * store (Android Keystore/EncryptedSharedPreferences, iOS Keychain, encrypted desktop file,
 * browser storage). [InMemorySecureStore] is for tests and offline development.
 */
interface SecureStore {
    suspend fun save(pairing: StoredPairing)
    suspend fun load(id: String): StoredPairing?
    suspend fun list(): List<StoredPairing>
    suspend fun delete(id: String)
}

class InMemorySecureStore(initial: List<StoredPairing> = emptyList()) : SecureStore {
    private val map = LinkedHashMap<String, StoredPairing>().apply { initial.forEach { put(it.id, it) } }

    override suspend fun save(pairing: StoredPairing) { map[pairing.id] = pairing }
    override suspend fun load(id: String): StoredPairing? = map[id]
    override suspend fun list(): List<StoredPairing> = map.values.toList()
    override suspend fun delete(id: String) { map.remove(id) }
}
