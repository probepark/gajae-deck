package io.devnogari.gajaedeck.auth

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFileSecureStoreTest {

    private fun tempDir(): File =
        File.createTempFile("gajaedeck-store", "").let { it.delete(); it.mkdirs(); it }

    private fun pairing(id: String = "p1", token: String = "super-secret-token-123") = StoredPairing(
        id = id, displayName = "dev", baseUrl = "https://h:4077", host = "h", port = 4077, token = token,
    )

    @Test
    fun persistsAcrossInstancesAndEncryptsOnDisk() = runBlocking {
        val dir = tempDir()
        DesktopFileSecureStore(dir).save(pairing())

        // New instance reads the same persisted data.
        val reopened = DesktopFileSecureStore(dir).load("p1")
        assertEquals("p1", reopened?.id)
        assertEquals("super-secret-token-123", reopened?.token)

        // On-disk payload must not contain the plaintext secret.
        val onDisk = File(dir, "pairings.enc").readText()
        assertFalse(onDisk.contains("super-secret-token-123"))
    }

    @Test
    fun crud() = runBlocking {
        val store = DesktopFileSecureStore(tempDir())
        store.save(pairing("a"))
        store.save(pairing("b"))
        assertEquals(2, store.list().size)
        store.delete("a")
        assertNull(store.load("a"))
        assertTrue(store.list().any { it.id == "b" })
    }
}
