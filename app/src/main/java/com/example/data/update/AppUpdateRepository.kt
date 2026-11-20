package com.example.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

interface ApkArchiveInspector {
    fun getInstalledAppInfo(): InstalledAppInfo
    fun inspectApkArchive(apkFile: File): ApkInspectionInfo?
    fun canRequestPackageInstalls(): Boolean
}

class AndroidApkArchiveInspector(private val context: Context) : ApkArchiveInspector {

    @Suppress("DEPRECATION")
    override fun getInstalledAppInfo(): InstalledAppInfo {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        pkg,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    )
                } else {
                    pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                }
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            val code = PackageInfoCompat.getLongVersionCode(info)
            val name = info.versionName ?: BuildConfig.VERSION_NAME
            val certs = extractSigningCertSha256s(info)
            InstalledAppInfo(
                packageId = info.packageName ?: pkg,
                versionCode = code,
                versionName = name,
                signingCertSha256s = certs
            )
        } catch (_: Throwable) {
            InstalledAppInfo(
                packageId = pkg,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                versionName = BuildConfig.VERSION_NAME,
                signingCertSha256s = emptySet()
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun inspectApkArchive(apkFile: File): ApkInspectionInfo? {
        if (!apkFile.exists() || apkFile.length() <= 0L) return null
        val pm = context.packageManager
        val path = apkFile.absolutePath

        var info: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(path, flags)
            }
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }

        var certs = info?.let { extractSigningCertSha256s(it) }.orEmpty()
        if (certs.isEmpty()) {
            // Some OEMs/framework implementations populate legacy signatures field only with GET_SIGNATURES
            val legacyInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
            if (info == null) info = legacyInfo
            certs = legacyInfo?.let { extractSigningCertSha256s(it) }.orEmpty()
        }

        val resolvedInfo = info ?: return null
        val pkgName = resolvedInfo.packageName ?: return null
        return ApkInspectionInfo(
            packageId = pkgName,
            versionCode = PackageInfoCompat.getLongVersionCode(resolvedInfo),
            versionName = resolvedInfo.versionName ?: "",
            signingCertSha256s = certs
        )
    }

    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSigningCertSha256s(info: PackageInfo): Set<String> {
        val rawSignatures = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                signingInfo.apkContentsSigners?.let { rawSignatures.addAll(it) }
                signingInfo.signingCertificateHistory?.let { rawSignatures.addAll(it) }
            }
        }
        if (rawSignatures.isEmpty()) {
            info.signatures?.let { rawSignatures.addAll(it) }
        }
        return rawSignatures.mapNotNull { sig ->
            runCatching { sha256Hex(sig.toByteArray()).uppercase() }.getOrNull()
        }.toSet()
    }
}

interface UpdateHttpTransport {
    suspend fun fetchText(url: String): String
    suspend fun downloadFile(
        url: String,
        destFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    )
}

class HttpUrlConnectionUpdateTransport : UpdateHttpTransport {
    override suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val conn = openFollowingRedirects(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while checking updates.")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun downloadFile(
        url: String,
        destFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conn = openFollowingRedirects(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while downloading update APK.")
            }
            val totalBytes = conn.contentLengthLong
            destFile.parentFile?.mkdirs()
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }
            if (downloaded <= 0L) {
                throw IllegalStateException("Downloaded APK file is empty.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            require(RemoteVersionInfo.isAllowedDownloadUrl(currentUrl)) {
                "Blocked unsafe update URL: $currentUrl"
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "MyFamilyDoc-Updater/${BuildConfig.VERSION_NAME}")
            }
            val status = conn.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank() || redirects >= 6) {
                    throw IllegalStateException("Too many redirects while fetching update.")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }
            return conn
        }
    }
}

class AppUpdateRepository(
    private val context: Context,
    private val inspector: ApkArchiveInspector = AndroidApkArchiveInspector(context.applicationContext),
    private val transport: UpdateHttpTransport = HttpUrlConnectionUpdateTransport(),
    private val versionJsonUrl: String = BuildConfig.VERSION_JSON_URL,
    private val fallbackVersionJsonUrl: String = BuildConfig.VERSION_JSON_FALLBACK_URL
) {
    private val installedAtInit: InstalledAppInfo = inspector.getInstalledAppInfo()
    private val _state = MutableStateFlow(
        AppUpdateState(
            installedPackageId = installedAtInit.packageId,
            installedVersionCode = installedAtInit.versionCode,
            installedVersionName = installedAtInit.versionName
        )
    )
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private var downloadJob: Job? = null

    suspend fun checkForUpdates(): Result<AppUpdateState> = checkMutex.withLock {
        val currentPhase = _state.value.phase
        if (currentPhase == UpdatePhase.DOWNLOADING || currentPhase == UpdatePhase.VERIFYING) {
            return Result.success(_state.value)
        }

        val installed = inspector.getInstalledAppInfo()
        _state.value = _state.value.copy(
            installedPackageId = installed.packageId,
            installedVersionCode = installed.versionCode,
            installedVersionName = installed.versionName,
            phase = UpdatePhase.CHECKING,
            errorMessage = null
        )

        return try {
            val rawJson = fetchVersionManifestWithFallback()
            val remote = RemoteVersionInfo.parseJson(rawJson)

            if (remote.packageId.isNotBlank() && remote.packageId != installed.packageId) {
                val err = "Update manifest package ID (${remote.packageId}) does not match installed app (${installed.packageId})."
                val next = _state.value.copy(
                    remoteVersion = null,
                    hasUpdate = false,
                    phase = UpdatePhase.ERROR,
                    lastCheckedAtMillis = System.currentTimeMillis(),
                    errorMessage = err
                )
                _state.value = next
                return Result.failure(IllegalStateException(err))
            }

            val isNewer = remote.versionCode > installed.versionCode
            if (!isNewer) {
                cleanupStaleUpdateFiles()
            }
            val next = _state.value.copy(
                remoteVersion = remote,
                hasUpdate = isNewer,
                phase = if (isNewer) UpdatePhase.UPDATE_AVAILABLE else UpdatePhase.UP_TO_DATE,
                lastCheckedAtMillis = System.currentTimeMillis(),
                errorMessage = null
            )
            _state.value = next
            Result.success(next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val next = _state.value.copy(
                hasUpdate = false,
                phase = UpdatePhase.ERROR,
                lastCheckedAtMillis = System.currentTimeMillis(),
                errorMessage = e.localizedMessage ?: "Unable to check for updates."
            )
            _state.value = next
            Result.failure(e)
        }
    }

    suspend fun downloadAndVerifyLatestApk(): Result<File> {
        val remote = _state.value.remoteVersion
            ?: return Result.failure(IllegalStateException("No update manifest loaded. Check for updates first."))
        val installed = inspector.getInstalledAppInfo()
        if (remote.versionCode <= installed.versionCode) {
            _state.value = _state.value.copy(hasUpdate = false, phase = UpdatePhase.UP_TO_DATE)
            return Result.failure(IllegalStateException("Your app is already up to date."))
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val tempFile = File(updatesDir, "app-update-v${remote.versionCode}.apk.part")
        val targetFile = File(updatesDir, "app-update-v${remote.versionCode}.apk")
        tempFile.delete()

        downloadJob = currentCoroutineContext()[Job]
        _state.value = _state.value.copy(
            phase = UpdatePhase.DOWNLOADING,
            downloadProgressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = -1L,
            verifiedApkFile = null,
            packageVerified = false,
            signatureVerified = false,
            versionVerified = false,
            errorMessage = null
        )

        return try {
            transport.downloadFile(remote.apkUrl, tempFile) { downloaded, total ->
                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                _state.value = _state.value.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    downloadProgressPercent = percent
                )
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            _state.value = _state.value.copy(
                phase = UpdatePhase.VERIFYING,
                downloadProgressPercent = 100
            )

            when (val verification = verifyDownloadedApk(targetFile, remote, installed)) {
                is ApkVerificationResult.Verified -> {
                    val needsPerm = !inspector.canRequestPackageInstalls()
                    _state.value = _state.value.copy(
                        verifiedApkFile = targetFile,
                        verifiedApkSha256 = verification.apkSha256,
                        packageVerified = true,
                        signatureVerified = true,
                        versionVerified = true,
                        phase = if (needsPerm) UpdatePhase.NEEDS_INSTALL_PERMISSION else UpdatePhase.READY_TO_INSTALL,
                        errorMessage = null
                    )
                    Result.success(targetFile)
                }

                is ApkVerificationResult.Failed -> {
                    targetFile.delete()
                    _state.value = _state.value.copy(
                        verifiedApkFile = null,
                        packageVerified = false,
                        signatureVerified = false,
                        versionVerified = false,
                        phase = UpdatePhase.ERROR,
                        errorMessage = verification.reason
                    )
                    Result.failure(SecurityException(verification.reason))
                }
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            _state.value = _state.value.copy(
                phase = if (_state.value.hasUpdate) UpdatePhase.UPDATE_AVAILABLE else UpdatePhase.UP_TO_DATE,
                downloadProgressPercent = 0,
                downloadedBytes = 0L,
                errorMessage = null
            )
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            _state.value = _state.value.copy(
                phase = UpdatePhase.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to download APK update."
            )
            Result.failure(e)
        } finally {
            downloadJob = null
        }
    }

    fun cancelActiveDownload() {
        downloadJob?.cancel()
    }

    fun verifyDownloadedApk(
        apkFile: File,
        remote: RemoteVersionInfo,
        installed: InstalledAppInfo
    ): ApkVerificationResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkVerificationResult.Failed("Downloaded APK file is missing or empty.")
        }

        val actualSha256 = computeFileSha256(apkFile).lowercase()
        if (remote.apkSha256.isNotBlank() && !remote.apkSha256.equals(actualSha256, ignoreCase = true)) {
            return ApkVerificationResult.Failed(
                "APK checksum mismatch. Download may be corrupted or modified."
            )
        }

        val inspected = inspector.inspectApkArchive(apkFile)
            ?: return ApkVerificationResult.Failed("Unable to parse APK package metadata.")

        if (inspected.packageId != installed.packageId) {
            return ApkVerificationResult.Failed(
                "Package ID mismatch (${inspected.packageId}). Expected ${installed.packageId}."
            )
        }

        if (inspected.versionCode <= installed.versionCode) {
            return ApkVerificationResult.Failed(
                "Downloaded APK versionCode (${inspected.versionCode}) is not newer than installed version (${installed.versionCode})."
            )
        }

        if (inspected.signingCertSha256s.isEmpty()) {
            return ApkVerificationResult.Failed("Downloaded APK has no valid signing certificate.")
        }

        if (installed.signingCertSha256s.isNotEmpty()) {
            val matchesInstalled = inspected.signingCertSha256s.any { it in installed.signingCertSha256s }
            if (!matchesInstalled) {
                return ApkVerificationResult.Failed(
                    "Signing certificate mismatch! Update rejected to protect your encrypted local vault data."
                )
            }
        }

        if (remote.signingCertSha256.isNotBlank() &&
            remote.signingCertSha256 !in inspected.signingCertSha256s
        ) {
            return ApkVerificationResult.Failed(
                "Downloaded APK certificate does not match published release manifest."
            )
        }

        return ApkVerificationResult.Verified(inspection = inspected, apkSha256 = actualSha256)
    }

    fun buildPackageInstallerIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun buildInstallUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun canRequestPackageInstalls(): Boolean = inspector.canRequestPackageInstalls()

    fun refreshInstallPermissionState() {
        val file = _state.value.verifiedApkFile ?: return
        if (!file.exists()) {
            _state.value = _state.value.copy(
                phase = if (_state.value.hasUpdate) UpdatePhase.UPDATE_AVAILABLE else UpdatePhase.UP_TO_DATE
            )
            return
        }
        if (inspector.canRequestPackageInstalls()) {
            _state.value = _state.value.copy(phase = UpdatePhase.READY_TO_INSTALL, errorMessage = null)
        } else {
            _state.value = _state.value.copy(phase = UpdatePhase.NEEDS_INSTALL_PERMISSION)
        }
    }

    fun markInstallingStarted() {
        _state.value = _state.value.copy(phase = UpdatePhase.INSTALLING, errorMessage = null)
    }

    fun markInstallCanceledOrReturned() {
        val hasVerifiedFile = _state.value.verifiedApkFile?.exists() == true
        _state.value = _state.value.copy(
            phase = if (hasVerifiedFile) UpdatePhase.INSTALL_CANCELED else UpdatePhase.UPDATE_AVAILABLE,
            errorMessage = null
        )
    }

    fun markInstallLaunchFailed(reason: String) {
        _state.value = _state.value.copy(
            phase = UpdatePhase.ERROR,
            errorMessage = reason
        )
    }

    private suspend fun fetchVersionManifestWithFallback(): String {
        val primaryError = try {
            return transport.fetchText(versionJsonUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

        if (fallbackVersionJsonUrl.isNotBlank() && fallbackVersionJsonUrl != versionJsonUrl) {
            return try {
                transport.fetchText(fallbackVersionJsonUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw primaryError
            }
        }
        throw primaryError
    }

    private fun cleanupStaleUpdateFiles() {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    companion object {
        fun computeFileSha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = fis.read(buf)
                    if (read == -1) break
                    md.update(buf, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
