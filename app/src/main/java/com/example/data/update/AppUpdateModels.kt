package com.example.data.update

import org.json.JSONObject
import java.io.File

data class RemoteVersionInfo(
    val versionCode: Long,
    val versionName: String,
    val packageId: String,
    val apkUrl: String,
    val apkSha256: String = "",
    val signingCertSha256: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val publishedAt: String = ""
) {
    companion object {
        fun parseJson(rawJson: String): RemoteVersionInfo {
            val obj = JSONObject(rawJson)
            val versionCode = obj.optLong("versionCode", 0L)
            val versionName = obj.optString("versionName", "").trim()
            val packageId = obj.optString("packageId", "").trim()
            val apkUrl = obj.optString("apkUrl", "").trim()
            val apkSha256 = obj.optString("apkSha256", "").trim().lowercase()
            val signingCertSha256 = obj.optString("signingCertSha256", "")
                .trim()
                .replace(":", "")
                .uppercase()
            val releaseNotes = obj.optString("releaseNotes", "").trim()
            val mandatory = obj.optBoolean("mandatory", false)
            val publishedAt = obj.optString("publishedAt", "").trim()

            require(versionCode > 0L) { "Invalid versionCode in version.json" }
            require(versionName.isNotEmpty()) { "Invalid versionName in version.json" }
            require(isAllowedDownloadUrl(apkUrl)) { "Unsafe or missing apkUrl in version.json" }

            return RemoteVersionInfo(
                versionCode = versionCode,
                versionName = versionName,
                packageId = packageId,
                apkUrl = apkUrl,
                apkSha256 = apkSha256,
                signingCertSha256 = signingCertSha256,
                releaseNotes = releaseNotes,
                mandatory = mandatory,
                publishedAt = publishedAt
            )
        }

        fun isAllowedDownloadUrl(url: String): Boolean {
            return url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://127.0.0.1", ignoreCase = true) ||
                url.startsWith("http://localhost", ignoreCase = true)
        }
    }
}

data class InstalledAppInfo(
    val packageId: String,
    val versionCode: Long,
    val versionName: String,
    val signingCertSha256s: Set<String>
)

data class ApkInspectionInfo(
    val packageId: String,
    val versionCode: Long,
    val versionName: String,
    val signingCertSha256s: Set<String>
)

sealed interface ApkVerificationResult {
    data class Verified(
        val inspection: ApkInspectionInfo,
        val apkSha256: String
    ) : ApkVerificationResult

    data class Failed(val reason: String) : ApkVerificationResult
}

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    NEEDS_INSTALL_PERMISSION,
    READY_TO_INSTALL,
    INSTALLING,
    INSTALL_CANCELED,
    ERROR
}

data class AppUpdateState(
    val installedPackageId: String = "",
    val installedVersionCode: Long = 1L,
    val installedVersionName: String = "1.0",
    val remoteVersion: RemoteVersionInfo? = null,
    val hasUpdate: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val downloadProgressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val verifiedApkFile: File? = null,
    val verifiedApkSha256: String? = null,
    val packageVerified: Boolean = false,
    val signatureVerified: Boolean = false,
    val versionVerified: Boolean = false,
    val lastCheckedAtMillis: Long = 0L,
    val errorMessage: String? = null
)
