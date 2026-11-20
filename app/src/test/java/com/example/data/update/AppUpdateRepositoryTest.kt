package com.example.data.update

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
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
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppUpdateRepositoryTest {

    private lateinit var context: Context
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    private val expectedCertSha256 =
        "C143F1209A2E98794C3C4CEDC8723FF9C9EF7AD13E2DAD9AD35D4EC26955B59A"
    private val pkgId = "com.aistudio.myfamilydoc.k7m2px"

    @Before
    fun setUp() {
        clearFileProviderCache()
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "updates").deleteRecursively()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
        File(context.cacheDir, "updates").deleteRecursively()
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
    fun checkForUpdates_sameVersion_reportsUpToDateAndNoUpdateAvailable() = runBlocking {
        val manifestJson = """
            {
              "versionCode": 1,
              "versionName": "1.0",
              "packageId": "$pkgId",
              "apkUrl": "$baseUrl/app-debug.apk",
              "signingCertSha256": "$expectedCertSha256",
              "releaseNotes": "Initial release"
            }
        """.trimIndent()

        server.createContext("/version.json") { exchange ->
            val bytes = manifestJson.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val inspector = FakeApkInspector(
            installed = InstalledAppInfo(pkgId, 1L, "1.0", setOf(expectedCertSha256)),
            archiveInspection = null
        )
        val repo = AppUpdateRepository(
            context = context,
            inspector = inspector,
            versionJsonUrl = "$baseUrl/version.json",
            fallbackVersionJsonUrl = ""
        )

        val result = repo.checkForUpdates()
        assertTrue(result.isSuccess)
        val state = repo.state.value
        assertFalse(state.hasUpdate)
        assertEquals(UpdatePhase.UP_TO_DATE, state.phase)
        assertEquals(1L, state.installedVersionCode)
        assertEquals(1L, state.remoteVersion?.versionCode)
        assertNull(state.verifiedApkFile)
    }

    @Test
    fun checkDownloadVerifyAndCancelInstall_newVersion_preservesExistingVaultData() = runBlocking {
        val fakeApkPayload = "PK_FAKE_APK_PAYLOAD_VERSION_2_FOR_TEST".repeat(256).toByteArray()
        val apkSha256 = sha256Hex(fakeApkPayload)

        val manifestJson = """
            {
              "versionCode": 2,
              "versionName": "1.1",
              "packageId": "$pkgId",
              "apkUrl": "$baseUrl/app-debug.apk",
              "apkSha256": "$apkSha256",
              "signingCertSha256": "$expectedCertSha256",
              "releaseNotes": "Bug fixes and performance improvements"
            }
        """.trimIndent()

        server.createContext("/version.json") { exchange ->
            val bytes = manifestJson.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/app-debug.apk") { exchange ->
            exchange.sendResponseHeaders(200, fakeApkPayload.size.toLong())
            exchange.responseBody.use { it.write(fakeApkPayload) }
        }

        // Seed existing user encrypted document & preferences before update
        val cryptoManager = DocumentCryptoManager(context)
        val existingSecretDoc = "AADHAAR_CONFIDENTIAL_DATA".toByteArray()
        cryptoManager.saveEncryptedDocumentForFamily(
            docId = "doc_preserve_test",
            plainBytes = existingSecretDoc,
            familyId = "fam_preserve",
            adminUid = "uid_admin"
        )
        val prefs = SecurityPreferences(context)
        prefs.setLanguage("kn")
        prefs.setPin("4321")

        val inspector = FakeApkInspector(
            installed = InstalledAppInfo(pkgId, 1L, "1.0", setOf(expectedCertSha256)),
            archiveInspection = ApkInspectionInfo(pkgId, 2L, "1.1", setOf(expectedCertSha256)),
            allowPackageInstalls = false
        )
        val repo = AppUpdateRepository(
            context = context,
            inspector = inspector,
            versionJsonUrl = "$baseUrl/version.json",
            fallbackVersionJsonUrl = ""
        )

        val checkResult = repo.checkForUpdates()
        assertTrue(checkResult.isSuccess)
        assertTrue(repo.state.value.hasUpdate)
        assertEquals(UpdatePhase.UPDATE_AVAILABLE, repo.state.value.phase)
        assertEquals("1.1", repo.state.value.remoteVersion?.versionName)

        // Download & verify
        val downloadResult = repo.downloadAndVerifyLatestApk()
        assertTrue(downloadResult.isSuccess)
        val downloadedFile = downloadResult.getOrThrow()
        assertTrue(downloadedFile.exists())
        assertEquals(fakeApkPayload.size.toLong(), downloadedFile.length())
        assertEquals(100, repo.state.value.downloadProgressPercent)
        assertTrue(repo.state.value.packageVerified)
        assertTrue(repo.state.value.signatureVerified)
        assertTrue(repo.state.value.versionVerified)
        assertEquals(UpdatePhase.NEEDS_INSTALL_PERMISSION, repo.state.value.phase)

        // Simulate user granting install unknown sources permission
        inspector.allowPackageInstalls = true
        repo.refreshInstallPermissionState()
        assertEquals(UpdatePhase.READY_TO_INSTALL, repo.state.value.phase)

        // Verify PackageInstaller intent flags for safe in-place update & cancel detection
        val installIntent = repo.buildPackageInstallerIntent(downloadedFile)
        assertEquals(Intent.ACTION_VIEW, installIntent.action)
        assertEquals("application/vnd.android.package-archive", installIntent.type)
        assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertEquals(0, installIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(installIntent.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false))
        assertTrue(installIntent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))

        // Simulate user canceling Android's system installer
        repo.markInstallingStarted()
        repo.markInstallCanceledOrReturned()
        assertEquals(UpdatePhase.INSTALL_CANCELED, repo.state.value.phase)
        assertNotNull(repo.state.value.verifiedApkFile)

        // Confirm all existing local vault data & preferences remain 100% intact
        val encryptedAfterCancel = cryptoManager.readEncryptedPayloadBytes("doc_preserve_test")
        assertNotNull(encryptedAfterCancel)
        val decryptedAfterCancel = cryptoManager.decryptBytes(
            encryptedPayload = encryptedAfterCancel!!,
            familyId = "fam_preserve",
            adminUid = "uid_admin"
        )
        assertEquals(String(existingSecretDoc), String(decryptedAfterCancel))
        val currentSettings = prefs.settingsFlow.first()
        assertEquals("kn", currentSettings.languageCode)
        assertTrue(currentSettings.pinEnabled)
    }

    @Test
    fun verifyDownloadedApk_rejectsMismatchedSignatureOrPackageOrDowngrade() = runBlocking {
        val apkBytes = "MOCK_APK_BYTES".toByteArray()
        val sha256 = sha256Hex(apkBytes)
        val apkFile = File(context.cacheDir, "updates/test.apk").apply {
            parentFile?.mkdirs()
            writeBytes(apkBytes)
        }

        val installed = InstalledAppInfo(pkgId, 5L, "1.5", setOf(expectedCertSha256))
        val remote = RemoteVersionInfo(
            versionCode = 6L,
            versionName = "1.6",
            packageId = pkgId,
            apkUrl = "$baseUrl/app-debug.apk",
            apkSha256 = sha256,
            signingCertSha256 = expectedCertSha256
        )

        // 1. Mismatched package ID
        val wrongPkgRepo = AppUpdateRepository(
            context = context,
            inspector = FakeApkInspector(
                installed = installed,
                archiveInspection = ApkInspectionInfo("com.evil.spoof", 6L, "1.6", setOf(expectedCertSha256))
            )
        )
        val pkgRes = wrongPkgRepo.verifyDownloadedApk(apkFile, remote, installed)
        assertTrue(pkgRes is ApkVerificationResult.Failed)

        // 2. Mismatched signing certificate
        val wrongSigRepo = AppUpdateRepository(
            context = context,
            inspector = FakeApkInspector(
                installed = installed,
                archiveInspection = ApkInspectionInfo(pkgId, 6L, "1.6", setOf("DEADBEEF"))
            )
        )
        val sigRes = wrongSigRepo.verifyDownloadedApk(apkFile, remote, installed)
        assertTrue(sigRes is ApkVerificationResult.Failed)

        // 3. Version downgrade / equal version
        val downgradeRepo = AppUpdateRepository(
            context = context,
            inspector = FakeApkInspector(
                installed = installed,
                archiveInspection = ApkInspectionInfo(pkgId, 5L, "1.5", setOf(expectedCertSha256))
            )
        )
        val verRes = downgradeRepo.verifyDownloadedApk(apkFile, remote, installed)
        assertTrue(verRes is ApkVerificationResult.Failed)

        // 4. Checksum mismatch
        val badHashRes = wrongPkgRepo.verifyDownloadedApk(
            apkFile = apkFile,
            remote = remote.copy(apkSha256 = "0000"),
            installed = installed
        )
        assertTrue(badHashRes is ApkVerificationResult.Failed)
    }

    @Test
    fun checkForUpdates_handlesNetworkErrorSafely() = runBlocking {
        server.createContext("/version.json") { exchange ->
            exchange.sendResponseHeaders(500, -1)
        }
        val repo = AppUpdateRepository(
            context = context,
            inspector = FakeApkInspector(
                installed = InstalledAppInfo(pkgId, 1L, "1.0", setOf(expectedCertSha256)),
                archiveInspection = null
            ),
            versionJsonUrl = "$baseUrl/version.json",
            fallbackVersionJsonUrl = ""
        )

        val result = repo.checkForUpdates()
        assertTrue(result.isFailure)
        assertFalse(repo.state.value.hasUpdate)
        assertEquals(UpdatePhase.ERROR, repo.state.value.phase)
        assertNotNull(repo.state.value.errorMessage)
    }

    private class FakeApkInspector(
        private val installed: InstalledAppInfo,
        private val archiveInspection: ApkInspectionInfo?,
        var allowPackageInstalls: Boolean = true
    ) : ApkArchiveInspector {
        override fun getInstalledAppInfo(): InstalledAppInfo = installed
        override fun inspectApkArchive(apkFile: File): ApkInspectionInfo? = archiveInspection
        override fun canRequestPackageInstalls(): Boolean = allowPackageInstalls
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
