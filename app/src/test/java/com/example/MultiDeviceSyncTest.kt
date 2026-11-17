package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.DocumentCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiDeviceSyncTest {

    private fun isolatedDeviceContext(base: Context, deviceName: String): Context {
        val deviceRoot = File(base.filesDir, "sim_$deviceName").apply {
            deleteRecursively()
            mkdirs()
        }
        val cacheRoot = File(base.cacheDir, "sim_${deviceName}_cache").apply {
            deleteRecursively()
            mkdirs()
        }
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = deviceRoot
            override fun getCacheDir(): File = cacheRoot
        }
    }

    @Test
    fun `two devices with same family credentials share and decrypt synced PDF and Image payloads`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val deviceA = DocumentCryptoManager(isolatedDeviceContext(baseContext, "phone_a"))
        val deviceB = DocumentCryptoManager(isolatedDeviceContext(baseContext, "phone_b"))

        val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val adminUid = "uid_google_same_account_123"
        val pdfDocId = "doc_pdf_sync_1"
        val imgDocId = "doc_img_sync_2"

        val pdfBytes = "%PDF-1.4\n1 0 obj<<>>endobj\n%%EOF".toByteArray(Charsets.UTF_8)
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 10, 20, 30, 40, 50)

        // Device A encrypts and saves PDF & Image locally before uploading to Firebase Storage
        deviceA.saveEncryptedDocumentForFamily(pdfDocId, pdfBytes, familyId, adminUid)
        deviceA.saveEncryptedDocumentForFamily(imgDocId, imageBytes, familyId, adminUid)

        val uploadedPdfPayload = deviceA.readEncryptedPayloadBytes(pdfDocId)!!
        val uploadedImagePayload = deviceA.readEncryptedPayloadBytes(imgDocId)!!

        assertTrue(deviceA.isFamilyEncryptedFormat(uploadedPdfPayload))
        assertTrue(deviceA.isFamilyEncryptedFormat(uploadedImagePayload))
        assertFalse(uploadedPdfPayload.decodeToString().contains("%PDF-1.4"))

        // Device B downloads encrypted payloads from Firebase Storage and stores them locally
        deviceB.writeRawEncryptedPayload(pdfDocId, uploadedPdfPayload)
        deviceB.writeRawEncryptedPayload(imgDocId, uploadedImagePayload)

        val decryptedPdfOnB = deviceB.readDecryptedBytes(pdfDocId, familyId, adminUid)
        val decryptedImageOnB = deviceB.readDecryptedBytes(imgDocId, familyId, adminUid)

        assertArrayEquals(pdfBytes, decryptedPdfOnB)
        assertArrayEquals(imageBytes, decryptedImageOnB)
    }

    @Test
    fun `unauthorized family cannot decrypt synced document payload`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val deviceA = DocumentCryptoManager(isolatedDeviceContext(baseContext, "owner_phone"))
        val outsiderDevice = DocumentCryptoManager(isolatedDeviceContext(baseContext, "outsider_phone"))

        val familyId = "fam_authorized_01"
        val adminUid = "uid_owner_01"
        val docId = "doc_secret_aadhaar"
        val secretBytes = "AADHAAR-CONFIDENTIAL-PAYLOAD".toByteArray(Charsets.UTF_8)

        deviceA.saveEncryptedDocumentForFamily(docId, secretBytes, familyId, adminUid)
        val cloudBlob = deviceA.readEncryptedPayloadBytes(docId)!!
        outsiderDevice.writeRawEncryptedPayload(docId, cloudBlob)

        var failedForWrongFamily = false
        try {
            outsiderDevice.readDecryptedBytes(docId, "fam_other_99", "uid_outsider_99")
        } catch (_: Exception) {
            failedForWrongFamily = true
        }
        assertTrue("Outsider family credentials must fail decryption", failedForWrongFamily)
    }

    @Test
    fun `v3 random 256-bit family vault key syncs encrypted PDF and Image across Admin and Family Username phones`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val adminPhone = DocumentCryptoManager(isolatedDeviceContext(baseContext, "v3_admin_phone"))
        val memberPhone = DocumentCryptoManager(isolatedDeviceContext(baseContext, "v3_member_phone"))
        val unauthorizedPhone = DocumentCryptoManager(isolatedDeviceContext(baseContext, "v3_stranger_phone"))

        val familyId = "fam_v3_secure_01"
        val adminUid = "uid_google_admin_01"
        val sharedKeyBase64 = adminPhone.generateFamilyVaultKeyBase64()
        adminPhone.storeFamilyVaultKeyBase64(familyId, sharedKeyBase64)

        val pdfDocId = "doc_v3_passport"
        val imgDocId = "doc_v3_aadhaar_scan"
        val pdfBytes = "%PDF-1.7\nV3-ENCRYPTED-PASSPORT\n%%EOF".toByteArray(Charsets.UTF_8)
        val imgBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 7, 8, 9, 10)

        adminPhone.saveEncryptedDocumentForFamily(pdfDocId, pdfBytes, familyId, adminUid)
        adminPhone.saveEncryptedDocumentForFamily(imgDocId, imgBytes, familyId, adminUid)

        val cloudPdfBlob = adminPhone.readEncryptedPayloadBytes(pdfDocId)!!
        val cloudImgBlob = adminPhone.readEncryptedPayloadBytes(imgDocId)!!

        assertTrue(adminPhone.isFormatV3(cloudPdfBlob))
        assertTrue(adminPhone.isFormatV3(cloudImgBlob))
        assertFalse(cloudPdfBlob.decodeToString().contains("V3-ENCRYPTED-PASSPORT"))

        // Second phone logs in with Family Username -> receives vaultKeyBase64 from Cloud Function
        memberPhone.storeFamilyVaultKeyBase64(familyId, sharedKeyBase64)
        assertTrue(memberPhone.hasFamilyVaultKey(familyId))
        memberPhone.writeRawEncryptedPayload(pdfDocId, cloudPdfBlob)
        memberPhone.writeRawEncryptedPayload(imgDocId, cloudImgBlob)

        assertArrayEquals(pdfBytes, memberPhone.readDecryptedBytes(pdfDocId, familyId, adminUid))
        assertArrayEquals(imgBytes, memberPhone.readDecryptedBytes(imgDocId, familyId, adminUid))

        // Unauthorized phone without shared vault key cannot decrypt v3 payload
        unauthorizedPhone.writeRawEncryptedPayload(pdfDocId, cloudPdfBlob)
        var unauthorizedRejected = false
        try {
            unauthorizedPhone.readDecryptedBytes(pdfDocId, familyId, adminUid)
        } catch (_: Exception) {
            unauthorizedRejected = true
        }
        assertTrue("Unauthorized phone without family vault key must fail v3 decryption", unauthorizedRejected)
    }
}
