package io.devnogari.gajaedeck.pairing

import io.devnogari.gajaedeck.auth.InMemorySecureStore
import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.StoredPairing
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.settings.PairingMetadata
import io.devnogari.gajaedeck.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

private fun pairing(id: String, token: String = "secret-$id") = StoredPairing(
    id = id, displayName = "dev-$id", baseUrl = "https://h:4077", host = "h", port = 4077, token = token,
)

private class FakeAppSettings(
    var failUpsert: Boolean = false,
    var failSetLast: Boolean = false,
) : AppSettings {
    private val theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val last = MutableStateFlow<String?>(null)
    private val pairingList = MutableStateFlow<List<PairingMetadata>>(emptyList())
    override val themeMode: StateFlow<ThemeMode> = theme.asStateFlow()
    override val lastPairingId: StateFlow<String?> = last.asStateFlow()
    override val pairings: StateFlow<List<PairingMetadata>> = pairingList.asStateFlow()
    override fun setThemeMode(mode: ThemeMode) { theme.value = mode }
    override fun setLastPairingId(id: String?) {
        if (failSetLast) throw IllegalStateException("simulated active-id write failure")
        last.value = id
    }
    override fun setPairings(list: List<PairingMetadata>) { pairingList.value = list }
    override fun upsertPairing(metadata: PairingMetadata) {
        if (failUpsert) throw IllegalStateException("simulated metadata write failure")
        pairingList.value = pairingList.value.filterNot { it.id == metadata.id } + metadata
    }
    override fun removePairing(id: String) { pairingList.value = pairingList.value.filterNot { it.id == id } }
}

private class FaultySecureStore(
    private val delegate: SecureStore = InMemorySecureStore(),
    var failSave: Boolean = false,
) : SecureStore {
    override suspend fun save(pairing: StoredPairing) {
        if (failSave) throw IllegalStateException("simulated secret write failure")
        delegate.save(pairing)
    }
    override suspend fun load(id: String) = delegate.load(id)
    override suspend fun list() = delegate.list()
    override suspend fun delete(id: String) = delegate.delete(id)
}

class PairingRepositoryTest {

    @Test
    fun createPersistsSecretMetadataAndActiveId() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)

        val result = repo.createPairing(pairing("a"))

        assertIs<PairingWriteResult.Success>(result)
        assertEquals("secret-a", store.load("a")?.token)
        assertEquals(listOf("a"), settings.pairings.value.map { it.id })
        assertEquals("a", settings.lastPairingId.value)
    }

    @Test
    fun createRejectsDuplicateUnlessReplacing() = runTest {
        val repo = PairingRepository(InMemorySecureStore(), FakeAppSettings())
        repo.createPairing(pairing("a"))
        val dup = repo.createPairing(pairing("a", token = "other"))
        assertEquals(PairingError.DUPLICATE_ID, (dup as PairingWriteResult.Failure).error)
    }

    @Test
    fun importReplacesWhenAllowed() = runTest {
        val store = InMemorySecureStore()
        val repo = PairingRepository(store, FakeAppSettings())
        repo.createPairing(pairing("a", token = "v1"))
        val result = repo.importPairing(pairing("a", token = "v2"), replaceExisting = true)
        assertIs<PairingWriteResult.Success>(result)
        assertEquals("v2", store.load("a")?.token)
    }

    @Test
    fun renameUpdatesMetadataOnlyAndReportsNotFound() = runTest {
        val repo = PairingRepository(InMemorySecureStore(), FakeAppSettings())
        repo.createPairing(pairing("a"))
        val ok = repo.renamePairing("a", "renamed")
        assertEquals("renamed", (ok as PairingWriteResult.Success).metadata.displayName)
        val missing = repo.renamePairing("zzz", "x")
        assertEquals(PairingError.NOT_FOUND, (missing as PairingWriteResult.Failure).error)
    }

    @Test
    fun rotateTokenReplacesSecretAndClearsSessionState() = runTest {
        val store = InMemorySecureStore()
        val repo = PairingRepository(store, FakeAppSettings())
        store.save(pairing("a", token = "old").copy(lastSessionId = "s1", lastAcceptedSeq = 9))
        repo.createPairing(pairing("a", token = "old"), replaceExisting = true)

        val result = repo.rotateToken("a", newToken = "new")
        assertIs<PairingWriteResult.Success>(result)
        val stored = store.load("a")
        assertEquals("new", stored?.token)
        assertNull(stored?.lastSessionId)
        assertEquals(0, stored?.lastAcceptedSeq)
        assertEquals(PairingError.NOT_FOUND, (repo.rotateToken("zzz", "x") as PairingWriteResult.Failure).error)
    }

    @Test
    fun deleteActivePairingMovesLastPairingId() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)
        repo.createPairing(pairing("a"))
        repo.createPairing(pairing("b"))
        // "b" is active (last created); deleting it should move the active id to a remaining pairing.
        repo.deletePairing("b")
        assertNull(store.load("b"))
        assertEquals(listOf("a"), settings.pairings.value.map { it.id })
        assertEquals("a", settings.lastPairingId.value)
    }

    @Test
    fun deleteLastRemainingPairingClearsActiveId() = runTest {
        val settings = FakeAppSettings()
        val repo = PairingRepository(InMemorySecureStore(), settings)
        repo.createPairing(pairing("a"))
        repo.deletePairing("a")
        assertNull(settings.lastPairingId.value)
    }

    @Test
    fun metadataWriteFailureRollsBackSecret() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings(failUpsert = true)
        val repo = PairingRepository(store, settings)

        val result = repo.createPairing(pairing("a"))

        assertEquals(PairingError.METADATA_WRITE_FAILED, (result as PairingWriteResult.Failure).error)
        assertNull(store.load("a"), "secret must be rolled back when metadata write fails")
        assertTrue(settings.pairings.value.isEmpty())
    }

    @Test
    fun secretWriteFailureLeavesNoMetadata() = runTest {
        val store = FaultySecureStore(failSave = true)
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)

        val result = repo.createPairing(pairing("a"))

        assertEquals(PairingError.SECRET_WRITE_FAILED, (result as PairingWriteResult.Failure).error)
        assertTrue(settings.pairings.value.isEmpty(), "no metadata when the secret never persisted")
        assertNull(settings.lastPairingId.value)
    }

    @Test
    fun detectAndCleanupDriftReconcilesBothDirections() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)
        repo.createPairing(pairing("a"))
        // Drift: metadata without secret (orphan meta), and secret without metadata (orphan secret).
        store.delete("a")
        store.save(pairing("orphanSecret"))

        val drift = repo.detectDrift()
        assertEquals(listOf("a"), drift.metadataWithoutSecret)
        assertEquals(listOf("orphanSecret"), drift.secretWithoutMetadata)
        assertTrue(drift.hasDrift)

        repo.cleanupDrift()
        assertTrue(settings.pairings.value.isEmpty())
        assertNull(store.load("orphanSecret"))
        assertNull(settings.lastPairingId.value)
        assertTrue(!repo.detectDrift().hasDrift)
    }

    @Test
    fun activeIdFailureOnCreateKeepsSecretAndMetadataConsistent() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings(failSetLast = true)
        val repo = PairingRepository(store, settings)

        val result = repo.createPairing(pairing("a"))

        // Active-id pointer failed AFTER secret + metadata committed: not cross-store drift.
        assertEquals(PairingError.ACTIVE_ID_WRITE_FAILED, (result as PairingWriteResult.Failure).error)
        assertEquals("secret-a", store.load("a")?.token)
        assertEquals(listOf("a"), settings.pairings.value.map { it.id })
        assertNull(settings.lastPairingId.value)
        assertTrue(!repo.detectDrift().hasDrift, "secret and metadata must stay consistent on active-id failure")
    }

    @Test
    fun activeIdFailureOnReplaceKeepsSecretAndMetadataConsistent() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)
        repo.createPairing(pairing("a", token = "v1"))

        settings.failSetLast = true
        val result = repo.importPairing(pairing("a", token = "v2"), replaceExisting = true)

        assertEquals(PairingError.ACTIVE_ID_WRITE_FAILED, (result as PairingWriteResult.Failure).error)
        // The replace committed: new secret + metadata present and consistent (no drift).
        assertEquals("v2", store.load("a")?.token)
        assertEquals(listOf("a"), settings.pairings.value.map { it.id })
        assertTrue(!repo.detectDrift().hasDrift)
    }

    @Test
    fun deleteActiveSelectsMostRecentlyConnectedRemaining() = runTest {
        val store = InMemorySecureStore()
        val settings = FakeAppSettings()
        val repo = PairingRepository(store, settings)
        // b is first in the list (older connect), a connected more recently, c (active) is deleted.
        repo.createPairing(pairing("b").copy(lastSuccessfulAuthAt = 100))
        repo.createPairing(pairing("a").copy(lastSuccessfulAuthAt = 200))
        repo.createPairing(pairing("c"))

        repo.deletePairing("c")

        // First-in-list would be "b" (the old bug); max lastConnectedAt selects "a".
        assertEquals("a", settings.lastPairingId.value)
    }
}
