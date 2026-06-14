package io.devnogari.gajaedeck.pairing

import io.devnogari.gajaedeck.auth.SecureStore
import io.devnogari.gajaedeck.auth.StoredPairing
import io.devnogari.gajaedeck.auth.TokenLifecycle
import io.devnogari.gajaedeck.settings.AppSettings
import io.devnogari.gajaedeck.settings.PairingMetadata
import kotlinx.coroutines.flow.StateFlow

/** Result of a pairing mutation. [error] never carries a secret value. */
sealed interface PairingWriteResult {
    data class Success(val metadata: PairingMetadata) : PairingWriteResult
    data class Failure(val error: PairingError) : PairingWriteResult
}

enum class PairingError {
    NOT_FOUND,
    DUPLICATE_ID,
    SECRET_WRITE_FAILED,
    METADATA_WRITE_FAILED,
    ACTIVE_ID_WRITE_FAILED,
}

/** Difference between the secret store (credentials) and settings metadata. */
data class PairingDrift(
    val metadataWithoutSecret: List<String>,
    val secretWithoutMetadata: List<String>,
) {
    val hasDrift: Boolean get() = metadataWithoutSecret.isNotEmpty() || secretWithoutMetadata.isNotEmpty()
}

/**
 * Single production owner of pairing invariants. Coordinates the secret [SecureStore] with the
 * non-secret [AppSettings] metadata + lastPairingId so the two never drift. UI/screens read the
 * exposed flows and call these methods; they must not write [SecureStore]/[AppSettings] directly.
 *
 * Token-only mutation is delegated to [TokenLifecycle] (which is metadata-neutral, since
 * [PairingMetadata] holds no secret). Write ordering is secret-first: a credential is persisted
 * before its metadata, and a metadata failure compensates the secret (restoring the prior value on
 * replace, or deleting the new value on create) so settings never reference a missing/mismatched
 * secret. A compensation that itself fails is surfaced loudly rather than masked.
 */
class PairingRepository(
    private val secureStore: SecureStore,
    private val settings: AppSettings,
    private val tokenLifecycle: TokenLifecycle = TokenLifecycle(secureStore),
) {
    val pairings: StateFlow<List<PairingMetadata>> get() = settings.pairings
    val lastPairingId: StateFlow<String?> get() = settings.lastPairingId

    suspend fun createPairing(pairing: StoredPairing, replaceExisting: Boolean = false): PairingWriteResult {
        val existing = settings.pairings.value.firstOrNull { it.id == pairing.id }
        if (existing != null && !replaceExisting) {
            return PairingWriteResult.Failure(PairingError.DUPLICATE_ID)
        }
        // Capture the prior secret so a replace can be rolled back to it (not deleted) on failure.
        val priorSecret = if (existing != null) secureStore.load(pairing.id) else null

        runCatching { secureStore.save(pairing) }
            .onFailure { return PairingWriteResult.Failure(PairingError.SECRET_WRITE_FAILED) }

        val metadata = pairing.toMetadata()

        // Metadata write. If it fails before committing, the secret+metadata would diverge, so
        // compensate the secret (restore prior on replace, delete new on create). The compensation
        // runs outside runCatching so a compensation failure propagates loudly, never masked.
        val metadataWrite = runCatching { settings.upsertPairing(metadata) }
        if (metadataWrite.isFailure) {
            if (priorSecret != null) secureStore.save(priorSecret) else secureStore.delete(pairing.id)
            return PairingWriteResult.Failure(PairingError.METADATA_WRITE_FAILED)
        }

        // Active-id pointer last. Secret and metadata are already consistent (detectDrift would be
        // clean), so a failure here is NOT cross-store drift: keep both and surface a distinct,
        // secret-free error rather than deleting a committed secret/metadata pair.
        runCatching { settings.setLastPairingId(pairing.id) }
            .onFailure { return PairingWriteResult.Failure(PairingError.ACTIVE_ID_WRITE_FAILED) }

        return PairingWriteResult.Success(metadata)
    }

    /** Import is a create with an explicit duplicate policy (replace vs reject). */
    suspend fun importPairing(pairing: StoredPairing, replaceExisting: Boolean): PairingWriteResult =
        createPairing(pairing, replaceExisting)

    suspend fun renamePairing(id: String, displayName: String): PairingWriteResult {
        val current = settings.pairings.value.firstOrNull { it.id == id }
            ?: return PairingWriteResult.Failure(PairingError.NOT_FOUND)
        val updated = current.copy(displayName = displayName)
        settings.upsertPairing(updated)
        return PairingWriteResult.Success(updated)
    }

    suspend fun rotateToken(id: String, newToken: String, newOwnerToken: String? = null): PairingWriteResult {
        // Secret-level rotation (token/ownerToken/pin/session state) is owned by TokenLifecycle.
        val rotated = tokenLifecycle.replaceToken(id, newToken, newOwnerToken)
            ?: return PairingWriteResult.Failure(PairingError.NOT_FOUND)
        val metadata = rotated.toMetadata()
        runCatching { settings.upsertPairing(metadata) }
            .onFailure { return PairingWriteResult.Failure(PairingError.METADATA_WRITE_FAILED) }
        return PairingWriteResult.Success(metadata)
    }

    suspend fun deletePairing(id: String) {
        secureStore.delete(id)
        settings.removePairing(id)
        if (settings.lastPairingId.value == id) {
            settings.setLastPairingId(settings.recentRemainingId())
        }
    }

    suspend fun detectDrift(): PairingDrift {
        val secretIds = secureStore.list().map { it.id }.toSet()
        val metadataIds = settings.pairings.value.map { it.id }.toSet()
        return PairingDrift(
            metadataWithoutSecret = (metadataIds - secretIds).toList(),
            secretWithoutMetadata = (secretIds - metadataIds).toList(),
        )
    }

    /** Reconcile drift by dropping dangling metadata and orphan secrets, then fixing lastPairingId. */
    suspend fun cleanupDrift(): PairingDrift {
        val drift = detectDrift()
        drift.metadataWithoutSecret.forEach { settings.removePairing(it) }
        drift.secretWithoutMetadata.forEach { secureStore.delete(it) }
        val active = settings.lastPairingId.value
        if (active != null && settings.pairings.value.none { it.id == active }) {
            settings.setLastPairingId(settings.recentRemainingId())
        }
        return drift
    }
}

/** The most-recently-connected remaining pairing, falling back to the most-recently-added one. */
private fun AppSettings.recentRemainingId(): String? {
    val remaining = pairings.value
    return remaining.filter { it.lastConnectedAt != null }.maxByOrNull { it.lastConnectedAt!! }?.id
        ?: remaining.lastOrNull()?.id
}

private fun StoredPairing.toMetadata(): PairingMetadata = PairingMetadata(
    id = id,
    displayName = displayName,
    host = host,
    port = port,
    baseUrl = baseUrl,
    webStorageMode = webStorageMode,
    webTrustedTls = webTrustedTls,
    lastConnectedAt = lastSuccessfulAuthAt,
)
