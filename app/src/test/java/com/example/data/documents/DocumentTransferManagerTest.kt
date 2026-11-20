package com.example.data.documents

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.worker.ExpiryNotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentTransferManagerTest {

    private lateinit var context: Context

    private val family = FamilyAccount(
        familyId = "fam_123",
        familyName = "Sharma Family",
        familyCode = "MFD-1020-AB",
        adminUid = "uid_admin",
        memberUids = listOf("uid_admin", "uid_member_allowed", "uid_member_blocked")
    )

    private val docPdf = FamilyDocument(
        id = "doc_passport",
        familyId = "fam_123",
        title = "Passport",
        category = "PASSPORT",
        memberId = "m1",
        memberName = "Aarav",
        uploadedByUid = "uid_admin",
        adminUid = "uid_admin",
        allowedUids = listOf("uid_admin", "uid_member_allowed"),
        fileType = "pdf",
        fileName = "passport_scan.pdf",
        fileSizeBytes = 2048L
    )

    private val docJpg = docPdf.copy(
        id = "doc_photo",
        title = "Aadhaar Photo",
        fileType = "jpg",
        fileName = "aadhaar.jpg"
    )

    @Before
    fun setUp() {
        clearFileProviderCache()
        context = ApplicationProvider.getApplicationContext()
        DocumentTransferManager.clearAllShareTempFiles(context)
    }

    @After
    fun tearDown() {
        DocumentTransferManager.clearAllShareTempFiles(context)
        clearFileProviderCache()
    }

    private fun clearFileProviderCache() {
        runCatching {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun verifyDocumentAccess_enforcesFamilyAndAllowedUids() {
        assertTrue(DocumentTransferManager.verifyDocumentAccess(docPdf, "uid_admin", family))
        assertTrue(DocumentTransferManager.verifyDocumentAccess(docPdf, "uid_member_allowed", family))
        assertFalse(DocumentTransferManager.verifyDocumentAccess(docPdf, "uid_member_blocked", family))
        assertFalse(DocumentTransferManager.verifyDocumentAccess(docPdf, "uid_outsider", family))

        val foreignDoc = docPdf.copy(familyId = "fam_other")
        assertFalse(DocumentTransferManager.verifyDocumentAccess(foreignDoc, "uid_admin", family))
    }

    @Test
    fun resolveMimeType_andCommonShareMimeType_handleAllSupportedFormats() {
        assertEquals("application/pdf", DocumentTransferManager.resolveMimeType(docPdf))
        assertEquals("image/jpeg", DocumentTransferManager.resolveMimeType(docJpg))
        assertEquals(
            "image/png",
            DocumentTransferManager.resolveMimeType(docJpg.copy(fileType = "png", fileName = "card.png"))
        )
        assertEquals(
            "image/webp",
            DocumentTransferManager.resolveMimeType(docJpg.copy(fileType = "webp", fileName = "scan.webp"))
        )
        assertEquals(
            "image/gif",
            DocumentTransferManager.resolveMimeType(docJpg.copy(fileType = "gif", fileName = "anim.gif"))
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocumentTransferManager.resolveMimeType(docJpg.copy(fileType = "docx", fileName = "deed.docx"))
        )

        val uriA = Uri.parse("content://example/a")
        val uriB = Uri.parse("content://example/b")
        assertEquals(
            "image/*",
            DocumentTransferManager.resolveCommonShareMimeType(
                listOf(
                    docJpg to uriA,
                    docJpg.copy(id = "d2", fileType = "png", fileName = "b.png") to uriB
                )
            )
        )
        assertEquals(
            "*/*",
            DocumentTransferManager.resolveCommonShareMimeType(
                listOf(docPdf to uriA, docJpg to uriB)
            )
        )
    }

    @Test
    fun buildSafeFileName_andDeduplicate_preserveExtensionAndPreventTraversal() {
        val unsafeDoc = docPdf.copy(fileName = "../../../evil<script>.pdf")
        val safe = DocumentTransferManager.buildSafeFileName(unsafeDoc)
        assertFalse(safe.contains(".."))
        assertFalse(safe.contains("/"))
        assertTrue(safe.endsWith(".pdf"))

        val existing = setOf("passport.pdf", "passport (1).pdf")
        assertEquals("passport (2).pdf", DocumentTransferManager.deduplicateFileName("passport.pdf", existing))
    }

    @Test
    fun tempDecryptedShareFile_createsAndCleansUpSafely() {
        runBlocking {
            val payload = "%PDF-1.4\nSample".toByteArray()
            val tempFile = DocumentTransferManager.createTempDecryptedShareFile(context, docPdf, payload)
            assertTrue(tempFile.exists())
            assertEquals(payload.size, tempFile.readBytes().size)

            // Mark stale and run stale cleanup
            tempFile.parentFile?.setLastModified(System.currentTimeMillis() - (60L * 60L * 1000L))
            DocumentTransferManager.cleanupStaleShareFiles(context)
            assertFalse(tempFile.exists())

            // Recreate & test full wipe
            val temp2 = DocumentTransferManager.createTempDecryptedShareFile(context, docPdf, payload)
            assertTrue(temp2.exists())
            DocumentTransferManager.clearAllShareTempFiles(context)
            assertFalse(temp2.exists())
        }
    }

    @Test
    fun shareAndOpenIntents_includeGrantsClipDataAndTargetApps() {
        runBlocking {
            val tempPdf = DocumentTransferManager.createTempDecryptedShareFile(context, docPdf, "pdf".toByteArray())
            val tempImg = DocumentTransferManager.createTempDecryptedShareFile(context, docJpg, "jpg".toByteArray())
            val pdfUri = DocumentTransferManager.getShareContentUri(context, tempPdf)
            val imgUri = DocumentTransferManager.getShareContentUri(context, tempImg)
            assertEquals("${BuildConfig.APPLICATION_ID}.fileprovider", pdfUri.authority)
            assertEquals("${BuildConfig.APPLICATION_ID}.fileprovider", imgUri.authority)

            // Register simulated WhatsApp and Gmail handlers in Robolectric PackageManager
            val shadowPm = Shadows.shadowOf(context.packageManager)
            val sendProbe = Intent(Intent.ACTION_SEND).apply { type = "application/pdf" }
            listOf("com.whatsapp", "com.google.android.gm").forEach { pkg ->
                shadowPm.addResolveInfoForIntent(
                    sendProbe,
                    ResolveInfo().apply {
                        activityInfo = ActivityInfo().apply {
                            packageName = pkg
                            name = "$pkg.ShareActivity"
                        }
                    }
                )
            }

            val chooser = DocumentTransferManager.buildSingleShareIntent(context, docPdf, pdfUri, "Share")
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            assertTrue((chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            @Suppress("DEPRECATION")
            val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(target)
            assertEquals(Intent.ACTION_SEND, target?.action)
            assertEquals("application/pdf", target?.type)
            assertNotNull(target?.clipData)
            assertEquals(2, context.packageManager.queryIntentActivities(target!!, 0).size)

            val multiChooser = DocumentTransferManager.buildMultiShareIntent(
                context,
                listOf(docPdf to pdfUri, docJpg to imgUri),
                "Share 2 files"
            )
            @Suppress("DEPRECATION")
            val multiTarget = multiChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, multiTarget?.action)
            assertEquals("*/*", multiTarget?.type)
            assertEquals(2, multiTarget?.clipData?.itemCount)

            val openChooser = DocumentTransferManager.buildOpenWithIntent(context, pdfUri, "application/pdf", "Open With")
            @Suppress("DEPRECATION")
            val viewTarget = openChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertEquals(Intent.ACTION_VIEW, viewTarget?.action)
            assertEquals("application/pdf", viewTarget?.type)
            assertTrue((viewTarget!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        }
    }

    @Test
    fun authorizedVaultLink_roundTripsWithoutExposingStorageUrls() {
        val link = DocumentTransferManager.buildAuthorizedVaultLink(docPdf)
        assertEquals("famviora://vault/family/fam_123/doc/doc_passport", link)
        assertFalse(link.contains("firebasestorage", ignoreCase = true))
        assertFalse(link.contains("token=", ignoreCase = true))
        assertFalse(link.startsWith("http", ignoreCase = true))

        val parsed = DocumentTransferManager.parseAuthorizedVaultLink(Uri.parse(link))
        assertEquals("fam_123", parsed?.familyId)
        assertEquals("doc_passport", parsed?.docId)

        assertNull(DocumentTransferManager.parseAuthorizedVaultLink(Uri.parse("https://evil.com/vault/family/a/doc/b")))
    }

    @Test
    fun writeBytesToSafUri_writesSuccessfullyAndReportsProgress() {
        runBlocking {
            val targetFile = File(context.cacheDir, "saf_out_test.pdf")
            targetFile.delete()
            val uri = Uri.fromFile(targetFile)
            val payload = ByteArray(40 * 1024) { (it % 127).toByte() }
            var lastRead = 0L

            val res = DocumentTransferManager.writeBytesToSafUri(context, uri, payload) { read, _ ->
                lastRead = read
            }
            assertTrue(res.isSuccess)
            assertEquals(payload.size.toLong(), lastRead)
            assertEquals(payload.size, targetFile.readBytes().size)
            targetFile.delete()
        }
    }

    @Test
    fun expiryNotifications_andBuildConfig_reflectFamvioraAndCorrectGithubRepo() {
        assertEquals("com.aistudio.myfamilydoc.k7m2px", BuildConfig.APPLICATION_ID)
        assertEquals(
            "https://github.com/sanjukadivalar-2610/My-Family-Documents/releases/download/debug-latest/version.json",
            BuildConfig.VERSION_JSON_URL
        )
        assertEquals(
            "https://raw.githubusercontent.com/sanjukadivalar-2610/My-Family-Documents/main/version.json",
            BuildConfig.VERSION_JSON_FALLBACK_URL
        )

        Shadows.shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val soonDoc = docPdf.copy(
            expiryDateMillis = System.currentTimeMillis() + (5L * 24L * 60L * 60L * 1000L)
        )
        val count = ExpiryNotificationHelper.notifyExpiringDocuments(context, listOf(soonDoc), "en")
        assertEquals(1, count)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).allNotifications.firstOrNull()
        assertNotNull(posted)
        val title = posted?.extras?.getCharSequence("android.title")?.toString().orEmpty()
        assertTrue("Expected FAMVIORA in notification title, got: $title", title.contains("FAMVIORA"))
    }
}
