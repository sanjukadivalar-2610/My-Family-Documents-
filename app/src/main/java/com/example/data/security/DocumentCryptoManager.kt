package com.example.data.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DocumentCryptoManager(private val context: Context) {

    private val encryptedVaultDir: File
        get() = File(context.filesDir, "vault_encrypted").apply {
            if (!exists()) mkdirs()
        }

    private val familyKeyCacheDir: File
        get() = File(context.filesDir, "vault_keys").apply {
            if (!exists()) mkdirs()
        }

    private val exportCacheDir: File
        get() = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }

    private val inMemoryFamilyKeys = mutableMapOf<String, ByteArray>()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    private fun getOrCreateDeviceSecretKey(): SecretKey {
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Generates a cryptographically random 256-bit AES vault master key encoded as Base64.
     * Stored strictly in the member-only `/families/{familyId}/secrets/vault_key` document.
     */
    fun generateFamilyVaultKeyBase64(): String {
        val keyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * Caches a family's 256-bit vault key locally. Wrapped with hardware-backed AndroidKeyStore
     * when available on device; falls back cleanly to app-private memory/file in JVM unit tests.
     */
    fun storeFamilyVaultKeyBase64(familyId: String, keyBase64: String) {
        if (familyId.isBlank() || keyBase64.isBlank()) return
        val raw = runCatching { Base64.decode(keyBase64.trim(), Base64.DEFAULT) }.getOrNull() ?: return
        if (raw.size != 32) return
        synchronized(inMemoryFamilyKeys) {
            inMemoryFamilyKeys[familyId] = raw.copyOf()
        }
        val target = getFamilyKeyFile(familyId)
        val wrapped = runCatching { encryptBytes(raw) }.getOrNull()
        if (wrapped != null) {
            target.writeBytes(byteArrayOf(WRAP_MODE_KEYSTORE) + wrapped)
        } else {
            // Non-KeyStore JVM / Robolectric test fallback only
            target.writeBytes(byteArrayOf(WRAP_MODE_TEST_RAW) + raw)
        }
    }

    fun hasFamilyVaultKey(familyId: String): Boolean {
        if (familyId.isBlank()) return false
        synchronized(inMemoryFamilyKeys) {
            if (inMemoryFamilyKeys.containsKey(familyId)) return true
        }
        return loadFamilySecretKeyBytes(familyId) != null
    }

    private fun loadFamilySecretKeyBytes(familyId: String): ByteArray? {
        if (familyId.isBlank()) return null
        synchronized(inMemoryFamilyKeys) {
            inMemoryFamilyKeys[familyId]?.let { return it.copyOf() }
        }
        val file = getFamilyKeyFile(familyId)
        if (!file.exists()) return null
        val content = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (content.size <= 1) return null
        val mode = content[0]
        val body = content.copyOfRange(1, content.size)
        val raw = when (mode) {
            WRAP_MODE_KEYSTORE -> runCatching { decryptWithDeviceKeyStore(body) }.getOrNull()
            WRAP_MODE_TEST_RAW -> if (body.size == 32) body else null
            else -> null
        } ?: return null
        if (raw.size != 32) return null
        synchronized(inMemoryFamilyKeys) {
            inMemoryFamilyKeys[familyId] = raw.copyOf()
        }
        return raw
    }

    /**
     * Derives the legacy deterministic v2 AES key scoped to `(familyId, adminUid)` so existing
     * `MFD\u0002` documents continue decrypting seamlessly across all devices.
     */
    fun deriveFamilyVaultKey(familyId: String, adminUid: String): SecretKey {
        require(familyId.isNotBlank() && adminUid.isNotBlank()) {
            "familyId and adminUid are required to derive the shared family vault key"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val material = "$VAULT_KEY_DOMAIN|$familyId|$adminUid".toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(material)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun isFamilyEncryptedFormat(payload: ByteArray): Boolean =
        isFormatV3(payload) || isFormatV2(payload)

    fun isFormatV3(payload: ByteArray): Boolean {
        if (payload.size <= MAGIC_FAMILY_V3.size + GCM_IV_LENGTH + 16) return false
        for (i in MAGIC_FAMILY_V3.indices) {
            if (payload[i] != MAGIC_FAMILY_V3[i]) return false
        }
        return true
    }

    private fun isFormatV2(payload: ByteArray): Boolean {
        if (payload.size <= MAGIC_FAMILY_V2.size + GCM_IV_LENGTH + 16) return false
        for (i in MAGIC_FAMILY_V2.indices) {
            if (payload[i] != MAGIC_FAMILY_V2[i]) return false
        }
        return true
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateDeviceSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        return ByteArray(1 + iv.size + cipherText.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(cipherText, 0, this, 1 + iv.size, cipherText.size)
        }
    }

    /**
     * Encrypts bytes using the member-scoped random 256-bit secret key (`MFD\u0003`) if cached
     * for [familyId], falling back cleanly to `MFD\u0002` if the secret key isn't loaded yet.
     */
    fun encryptBytesForFamily(
        plainBytes: ByteArray,
        familyId: String,
        adminUid: String
    ): ByteArray {
        val secretBytes = loadFamilySecretKeyBytes(familyId)
        val (magic, secretKey) = if (secretBytes != null) {
            MAGIC_FAMILY_V3 to SecretKeySpec(secretBytes, "AES")
        } else {
            MAGIC_FAMILY_V2 to deriveFamilyVaultKey(familyId, adminUid)
        }

        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainBytes)
        val headerSize = magic.size + 1 + iv.size
        return ByteArray(headerSize + cipherText.size).apply {
            System.arraycopy(magic, 0, this, 0, magic.size)
            this[magic.size] = iv.size.toByte()
            System.arraycopy(iv, 0, this, magic.size + 1, iv.size)
            System.arraycopy(cipherText, 0, this, headerSize, cipherText.size)
        }
    }

    fun decryptBytes(
        encryptedPayload: ByteArray,
        familyId: String? = null,
        adminUid: String? = null
    ): ByteArray {
        if (isFormatV3(encryptedPayload)) {
            require(!familyId.isNullOrBlank()) {
                "Family ID required to decrypt shared v3 vault document"
            }
            val keyBytes = loadFamilySecretKeyBytes(familyId)
                ?: throw IllegalStateException("Family decryption key not loaded for $familyId")
            return decryptFamilyPayload(
                encryptedPayload = encryptedPayload,
                magicLen = MAGIC_FAMILY_V3.size,
                secretKey = SecretKeySpec(keyBytes, "AES")
            )
        }

        if (isFormatV2(encryptedPayload)) {
            require(!familyId.isNullOrBlank() && !adminUid.isNullOrBlank()) {
                "Family credentials required to decrypt shared vault document"
            }
            return decryptFamilyPayload(
                encryptedPayload = encryptedPayload,
                magicLen = MAGIC_FAMILY_V2.size,
                secretKey = deriveFamilyVaultKey(familyId, adminUid)
            )
        }

        return decryptWithDeviceKeyStore(encryptedPayload)
    }

    private fun decryptFamilyPayload(
        encryptedPayload: ByteArray,
        magicLen: Int,
        secretKey: SecretKey
    ): ByteArray {
        val ivLength = encryptedPayload[magicLen].toInt() and 0xFF
        require(ivLength in 12..16 && encryptedPayload.size > magicLen + 1 + ivLength) {
            "Invalid family encrypted payload"
        }
        val ivStart = magicLen + 1
        val iv = encryptedPayload.copyOfRange(ivStart, ivStart + ivLength)
        val cipherText = encryptedPayload.copyOfRange(ivStart + ivLength, encryptedPayload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun decryptWithDeviceKeyStore(encryptedPayload: ByteArray): ByteArray {
        require(encryptedPayload.size > 14) { "Invalid encrypted payload" }
        val ivLength = encryptedPayload[0].toInt() and 0xFF
        val iv = encryptedPayload.copyOfRange(1, 1 + ivLength)
        val cipherText = encryptedPayload.copyOfRange(1 + ivLength, encryptedPayload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateDeviceSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun saveEncryptedDocument(docId: String, plainBytes: ByteArray): File {
        val targetFile = getEncryptedFile(docId)
        val encrypted = encryptBytes(plainBytes)
        targetFile.writeBytes(encrypted)
        return targetFile
    }

    fun saveEncryptedDocumentForFamily(
        docId: String,
        plainBytes: ByteArray,
        familyId: String,
        adminUid: String
    ): File {
        val targetFile = getEncryptedFile(docId)
        val encrypted = encryptBytesForFamily(plainBytes, familyId, adminUid)
        targetFile.writeBytes(encrypted)
        return targetFile
    }

    fun saveEncryptedFromUri(docId: String, uri: Uri): Long {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read selected document")
        saveEncryptedDocument(docId, bytes)
        return bytes.size.toLong()
    }

    fun saveEncryptedFromUriForFamily(
        docId: String,
        uri: Uri,
        familyId: String,
        adminUid: String
    ): Long {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read selected document")
        saveEncryptedDocumentForFamily(docId, bytes, familyId, adminUid)
        return bytes.size.toLong()
    }

    fun readEncryptedPayloadBytes(docId: String): ByteArray? {
        val file = getEncryptedFile(docId)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Ensures the encrypted payload on disk uses the cross-device family cipher format before
     * uploading to Firebase Storage, migrating legacy device-keystore payloads transparently.
     */
    fun reencryptLegacyPayloadForFamilyIfNeeded(
        docId: String,
        familyId: String,
        adminUid: String
    ): ByteArray? {
        val current = readEncryptedPayloadBytes(docId) ?: return null
        if (isFormatV3(current)) return current
        if (isFormatV2(current) && !hasFamilyVaultKey(familyId)) return current
        val plain = decryptBytes(current, familyId, adminUid)
        val upgraded = encryptBytesForFamily(plain, familyId, adminUid)
        getEncryptedFile(docId).writeBytes(upgraded)
        return upgraded
    }

    fun writeRawEncryptedPayload(docId: String, encryptedBytes: ByteArray) {
        getEncryptedFile(docId).writeBytes(encryptedBytes)
    }

    fun readDecryptedBytes(
        docId: String,
        familyId: String? = null,
        adminUid: String? = null
    ): ByteArray? {
        val file = getEncryptedFile(docId)
        if (!file.exists()) return null
        return decryptBytes(file.readBytes(), familyId, adminUid)
    }

    fun writeDecryptedTempFile(
        docId: String,
        fileName: String,
        familyId: String? = null,
        adminUid: String? = null
    ): File? {
        val decrypted = readDecryptedBytes(docId, familyId, adminUid) ?: return null
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(exportCacheDir, "${docId}_$safeName")
        outFile.writeBytes(decrypted)
        return outFile
    }

    fun hasLocalEncryptedFile(docId: String): Boolean = getEncryptedFile(docId).exists()

    fun listLocalEncryptedDocIds(): List<String> =
        encryptedVaultDir.listFiles()
            ?.filter { it.isFile && it.extension == "enc" }
            ?.map { it.nameWithoutExtension }
            .orEmpty()

    fun deleteLocalEncryptedFile(docId: String) {
        val file = getEncryptedFile(docId)
        if (file.exists()) file.delete()
    }

    fun countLocalEncryptedFiles(): Int =
        encryptedVaultDir.listFiles()?.count { it.extension == "enc" } ?: 0

    fun clearExportCache() {
        exportCacheDir.listFiles()?.forEach { it.delete() }
    }

    fun clearAllLocalVaultData() {
        synchronized(inMemoryFamilyKeys) {
            inMemoryFamilyKeys.clear()
        }
        File(context.filesDir, "vault_encrypted").deleteRecursively()
        File(context.filesDir, "vault_keys").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "camera_scans").deleteRecursively()
    }

    private fun getEncryptedFile(docId: String): File {
        val safeId = docId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(encryptedVaultDir, "$safeId.enc")
    }

    private fun getFamilyKeyFile(familyId: String): File {
        val safeId = familyId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(familyKeyCacheDir, "$safeId.key")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "MyFamilyDocVaultKey_v1"
        private const val VAULT_KEY_DOMAIN = "MyFamilyDocVaultKey_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_LENGTH = 12
        private const val WRAP_MODE_KEYSTORE: Byte = 0x01
        private const val WRAP_MODE_TEST_RAW: Byte = 0x02
        private val MAGIC_FAMILY_V2 = byteArrayOf(0x4D, 0x46, 0x44, 0x02) // "MFD\u0002"
        private val MAGIC_FAMILY_V3 = byteArrayOf(0x4D, 0x46, 0x44, 0x03) // "MFD\u0003"

        fun generateSalt(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val combined = "$salt:$pin:myfamilydoc".toByteArray(Charsets.UTF_8)
            val hashed = digest.digest(combined)
            return hashed.joinToString("") { "%02x".format(it) }
        }
    }
}
