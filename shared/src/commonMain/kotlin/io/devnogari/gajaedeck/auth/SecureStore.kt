package io.devnogari.gajaedeck.auth

/**
 * Assurance level of a [SecureStore] backend, surfaced to the user so they understand how their
 * bridge credentials are protected on the current platform.
 */
enum class StorageAssurance {
    /** OS-backed secret storage (Android Keystore/EncryptedSharedPreferences, iOS Keychain, encrypted desktop file). */
    OS_BACKED,

    /** Browser localStorage: readable by same-origin JavaScript, so a weaker guarantee than OS-backed stores. */
    BROWSER_LOCAL_STORAGE,
    ;

    /** True when the store does not provide OS-backed protection and the UI must warn the user. */
    val isLowerAssurance: Boolean get() = this != OS_BACKED
}

/**
 * Persists [StoredPairing] secrets. Platform implementations back this with the OS secure
 * store (Android Keystore/EncryptedSharedPreferences, iOS Keychain, encrypted desktop file,
 * browser storage). [InMemorySecureStore] is for tests and offline development.
 */
interface SecureStore {
    /** How well this backend protects stored secrets on the current platform. */
    val assurance: StorageAssurance get() = StorageAssurance.OS_BACKED

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
