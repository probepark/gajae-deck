package io.devnogari.gajaedeck.auth

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Desktop [SecureStore]: pairings persisted as AES-GCM encrypted JSON under [dir], with a locally
 * generated key file. Not OS-keychain backed (that is a per-OS follow-up), but secrets are never
 * written in plaintext.
 */
@OptIn(ExperimentalEncodingApi::class)
class DesktopFileSecureStore(private val dir: File) : SecureStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keyFile = File(dir, "store.key")
    private val dataFile = File(dir, "pairings.enc")

    private fun key(): SecretKeySpec {
        if (!keyFile.exists()) {
            dir.mkdirs()
            val k = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            keyFile.writeText(Base64.encode(k.encoded))
        }
        return SecretKeySpec(Base64.decode(keyFile.readText().trim()), "AES")
    }

    private fun readAll(): MutableMap<String, StoredPairing> {
        if (!dataFile.exists()) return linkedMapOf()
        val raw = dataFile.readBytes()
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        val plain = cipher.doFinal(ct).decodeToString()
        val list = json.decodeFromString(ListSerializer(StoredPairing.serializer()), plain)
        return LinkedHashMap<String, StoredPairing>().apply { list.forEach { put(it.id, it) } }
    }

    private fun writeAll(map: Map<String, StoredPairing>) {
        dir.mkdirs()
        val plain = json.encodeToString(ListSerializer(StoredPairing.serializer()), map.values.toList())
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        dataFile.writeBytes(iv + cipher.doFinal(plain.encodeToByteArray()))
    }

    override suspend fun save(pairing: StoredPairing) {
        val map = readAll(); map[pairing.id] = pairing; writeAll(map)
    }

    override suspend fun load(id: String): StoredPairing? = readAll()[id]
    override suspend fun list(): List<StoredPairing> = readAll().values.toList()
    override suspend fun delete(id: String) {
        val map = readAll(); map.remove(id); writeAll(map)
    }
}
