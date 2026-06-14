package io.devnogari.gajaedeck.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageAssuranceTest {

    @Test
    fun osBackedStoresAreNotLowerAssurance() {
        assertFalse(StorageAssurance.OS_BACKED.isLowerAssurance)
        assertEquals(
            StorageAssurance.OS_BACKED,
            InMemorySecureStore().assurance,
            "default SecureStore backends are OS-backed",
        )
    }

    @Test
    fun browserLocalStorageIsExposedAsLowerAssurance() {
        // The web backend stores secrets in localStorage (readable by same-origin JS); this assurance
        // signal must be exposed so the settings UI can warn the user.
        assertTrue(
            StorageAssurance.BROWSER_LOCAL_STORAGE.isLowerAssurance,
            "web localStorage must be flagged as lower-assurance",
        )
    }
}
