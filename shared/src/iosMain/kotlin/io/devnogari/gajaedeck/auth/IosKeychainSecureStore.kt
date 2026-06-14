package io.devnogari.gajaedeck.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS [SecureStore] backed by the Keychain (kSecClassGenericPassword). Each pairing is a generic
 * password item keyed by (service, account=id) whose value is the serialized [StoredPairing]; the
 * OS encrypts and access-controls the data. Items are scoped to [service].
 *
 * Note: iOS is a compile-and-screenshot target in this milestone (device runtime is out of scope),
 * so this implementation is verified by compilation; on-device behavior is a follow-up.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecureStore(
    private val service: String = "io.devnogari.gajaedeck.securestore",
) : SecureStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun save(pairing: StoredPairing) {
        val data = nsString(json.encodeToString(StoredPairing.serializer(), pairing))
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return
        deleteRaw(pairing.id)
        val owned = OwnedRefs()
        val attributes = dictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to owned.bridge(service),
            kSecAttrAccount to owned.bridge(pairing.id),
            kSecValueData to owned.retain(CFBridgingRetain(data)),
        )
        try {
            SecItemAdd(attributes, null)
        } finally {
            CFRelease(attributes)
            owned.releaseAll()
        }
    }

    override suspend fun load(id: String): StoredPairing? = memScoped {
        val owned = OwnedRefs()
        val query = dictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to owned.bridge(service),
            kSecAttrAccount to owned.bridge(id),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = try {
            SecItemCopyMatching(query, result.ptr)
        } finally {
            CFRelease(query)
            owned.releaseAll()
        }
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        decode(data)
    }

    override suspend fun list(): List<StoredPairing> = memScoped {
        val owned = OwnedRefs()
        val query = dictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to owned.bridge(service),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitAll,
        )
        val result = alloc<CFTypeRefVar>()
        val status = try {
            SecItemCopyMatching(query, result.ptr)
        } finally {
            CFRelease(query)
            owned.releaseAll()
        }
        if (status != errSecSuccess) return@memScoped emptyList()
        val items = CFBridgingRelease(result.value) as? List<*> ?: return@memScoped emptyList()
        items.mapNotNull { item -> (item as? NSData)?.let { decode(it) } }
    }

    override suspend fun delete(id: String) {
        deleteRaw(id)
    }

    private fun deleteRaw(account: String) {
        val owned = OwnedRefs()
        val query = dictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to owned.bridge(service),
            kSecAttrAccount to owned.bridge(account),
        )
        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
            owned.releaseAll()
        }
    }

    private fun decode(data: NSData): StoredPairing? {
        val raw = NSString.create(data, NSUTF8StringEncoding) as String? ?: return null
        return json.decodeFromString(StoredPairing.serializer(), raw)
    }

    private fun nsString(value: String): NSString = NSString.create(string = value)

    private fun dictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, pairs.size.convert(), null, null)
        for ((key, value) in pairs) CFDictionaryAddValue(dict, key, value)
        return dict
    }

    /** Tracks CFTypeRefs created via CFBridgingRetain so they can be released after a Keychain call. */
    private inner class OwnedRefs {
        private val refs = mutableListOf<CFTypeRef?>()

        fun retain(ref: CFTypeRef?): CFTypeRef? {
            refs.add(ref)
            return ref
        }

        fun bridge(value: String): CFTypeRef? = retain(CFBridgingRetain(nsString(value)))

        fun releaseAll() {
            refs.forEach { ref -> ref?.let { CFRelease(it) } }
            refs.clear()
        }
    }
}
