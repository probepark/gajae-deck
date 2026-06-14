package io.devnogari.gajaedeck.auth

import io.devnogari.gajaedeck.bridge.BridgeErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun pairing(
    id: String = "p1",
    host: String = "host.ts.net",
    token: String = "tok-secret",
    pin: String? = null,
) = StoredPairing(
    id = id,
    displayName = "dev",
    baseUrl = "https://$host:4077",
    host = host,
    port = 4077,
    token = token,
    pinnedCertFingerprint = pin,
)

class RedactorTest {
    @Test
    fun masksSecretsAndBearer() {
        val r = Redactor(setOf("tok-secret", "owner-xyz"))
        val text = "auth=tok-secret owner=owner-xyz header=Bearer abc.def-123"
        val out = r.redact(text)
        assertFalse(out.contains("tok-secret"))
        assertFalse(out.contains("owner-xyz"))
        assertFalse(out.contains("abc.def-123"))
        assertTrue(out.contains("Bearer ***"))
    }

    @Test
    fun redactsAuthHeaders() {
        val r = Redactor(setOf("tok-secret"))
        val redacted = r.redactHeaders(mapOf("Authorization" to "Bearer tok-secret", "X-Other" to "ok"))
        assertEquals("***", redacted["Authorization"])
        assertEquals("ok", redacted["X-Other"])
    }
}

class SecureStoreTest {
    @Test
    fun crud() = runTest {
        val store = InMemorySecureStore()
        store.save(pairing(id = "a"))
        store.save(pairing(id = "b"))
        assertEquals(2, store.list().size)
        assertEquals("a", store.load("a")?.id)
        store.delete("a")
        assertNull(store.load("a"))
        assertEquals(1, store.list().size)
    }
}

class TokenLifecycleTest {
    @Test
    fun replaceTokenClearsCachedStateAndPin() = runTest {
        val store = InMemorySecureStore(listOf(pairing(pin = "AA:BB").copy(lastSessionId = "s9", lastAcceptedSeq = 42, ownerToken = "own")))
        val lifecycle = TokenLifecycle(store)
        val updated = lifecycle.replaceToken("p1", "new-token")
        assertEquals("new-token", updated?.token)
        assertNull(updated?.ownerToken)
        assertNull(updated?.pinnedCertFingerprint)
        assertNull(updated?.lastSessionId)
        assertEquals(0, updated?.lastAcceptedSeq)
    }

    @Test
    fun diagnosis() {
        val lifecycle = TokenLifecycle(InMemorySecureStore())
        assertEquals(AuthBlockedDiagnosis.MISSING_TOKEN, lifecycle.diagnose(BridgeErrorCode.UNAUTHORIZED, hasToken = false))
        assertEquals(AuthBlockedDiagnosis.REJECTED_OR_ROTATED_TOKEN, lifecycle.diagnose(BridgeErrorCode.UNAUTHORIZED, hasToken = true))
        assertEquals(AuthBlockedDiagnosis.FORBIDDEN_ENDPOINT, lifecycle.diagnose(BridgeErrorCode.SCOPE_DENIED, hasToken = true))
        assertEquals(AuthBlockedDiagnosis.ENDPOINT_DISABLED, lifecycle.diagnose(BridgeErrorCode.ENDPOINT_DISABLED, hasToken = true))
    }
}

class TofuVerifierTest {
    @Test
    fun firstUseWhenNoPin() = runTest {
        val store = InMemorySecureStore(listOf(pairing(pin = null)))
        assertEquals(TofuDecision.TRUST_FIRST_USE, TofuVerifier(store).evaluate("p1", "host.ts.net", "AA:BB"))
    }

    @Test
    fun matchAndMismatch() = runTest {
        val store = InMemorySecureStore(listOf(pairing(pin = "AA:BB")))
        val v = TofuVerifier(store)
        assertEquals(TofuDecision.MATCH, v.evaluate("p1", "host.ts.net", "aa:bb"))
        assertEquals(TofuDecision.MISMATCH, v.evaluate("p1", "host.ts.net", "CC:DD"))
    }

    @Test
    fun wrongHostIsolation() = runTest {
        val store = InMemorySecureStore(listOf(pairing(pin = "AA:BB")))
        assertEquals(TofuDecision.MISMATCH, TofuVerifier(store).evaluate("p1", "evil.ts.net", "AA:BB"))
    }

    @Test
    fun acceptFirstUsePinsFingerprint() = runTest {
        val store = InMemorySecureStore(listOf(pairing(pin = null)))
        val v = TofuVerifier(store)
        v.acceptFirstUse("p1", "AA:BB")
        assertEquals(TofuDecision.MATCH, v.evaluate("p1", "host.ts.net", "AA:BB"))
    }
}
