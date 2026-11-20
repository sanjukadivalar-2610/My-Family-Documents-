package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.update.AppUpdateState
import com.example.data.update.UpdatePhase
import com.example.domain.model.CloudCategoryItem
import com.example.domain.model.EffectiveCloudConfig
import com.example.localization.LocalAppStrings
import com.example.ui.components.BankingHeroGradientBrush
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UpdateScreen(
    updateState: AppUpdateState,
    cloudConfig: EffectiveCloudConfig,
    isAdmin: Boolean,
    checkOnOpen: Boolean = true,
    onCheckForUpdates: () -> Unit,
    onStartDownloadAndInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onLaunchVerifiedInstaller: (File) -> Unit,
    onRequestInstallPermission: () -> Unit,
    onSaveFamilyCloudConfig: (
        noticeActive: Boolean,
        noticeTitleEn: String,
        noticeTitleKn: String,
        noticeBodyEn: String,
        noticeBodyKn: String,
        noticeLevel: String,
        homeBannerLabelEn: String,
        homeBannerLabelKn: String,
        extraCategories: List<CloudCategoryItem>,
        expiryWarningDays: Long,
        showSampleDocs: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current

    BackHandler(onBack = onBack)

    LaunchedEffect(checkOnOpen) {
        if (checkOnOpen) {
            onCheckForUpdates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.appUpdatesTitle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("update_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .testTag("update_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. APK Release Status & Installer Card
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BankingHeroGradientBrush())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.appName} • ${strings.tagline}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SkyAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("update_brand_label")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            updateState.phase == UpdatePhase.CHECKING -> strings.checkingForUpdatesMessage
                                            updateState.hasUpdate -> strings.newUpdateAvailableTitle
                                            updateState.phase == UpdatePhase.ERROR -> updateState.errorMessage ?: strings.appUpToDateMessage
                                            else -> strings.appUpToDateMessage
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("update_status_headline")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${strings.installedVersionLabel}: v${updateState.installedVersionName} (${updateState.installedVersionCode})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SkySoft
                                    )
                                    updateState.remoteVersion?.let { remote ->
                                        Text(
                                            text = "${strings.latestPublishedVersionLabel}: v${remote.versionName} (${remote.versionCode})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SkyAccent
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (updateState.hasUpdate) Icons.Rounded.SystemUpdate else Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = SkyAccent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            if (updateState.hasUpdate && !updateState.remoteVersion?.releaseNotes.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PureWhite.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = strings.releaseNotesLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SkyAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = updateState.remoteVersion!!.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PureWhite
                                        )
                                    }
                                }
                            }

                            when (updateState.phase) {
                                UpdatePhase.CHECKING -> {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp)),
                                        color = SkyAccent,
                                        trackColor = PureWhite.copy(alpha = 0.2f)
                                    )
                                }

                                UpdatePhase.DOWNLOADING -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.testTag("update_downloading_section")
                                    ) {
                                        Text(
                                            text = strings.downloadingUpdateTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = PureWhite,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (updateState.totalBytes > 0L) {
                                            LinearProgressIndicator(
                                                progress = { (updateState.downloadProgressPercent / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                color = SkyAccent,
                                                trackColor = PureWhite.copy(alpha = 0.2f)
                                            )
                                            Text(
                                                text = "${updateState.downloadProgressPercent}% • ${formatFileSize(updateState.downloadedBytes)} / ${formatFileSize(updateState.totalBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SkySoft
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                color = SkyAccent,
                                                trackColor = PureWhite.copy(alpha = 0.2f)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = onCancelDownload,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("update_cancel_download_button")
                                        ) {
                                            Text(strings.cancelDownloadButton)
                                        }
                                    }
                                }

                                UpdatePhase.VERIFYING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.testTag("update_verifying_section")
                                    ) {
                                        CircularProgressIndicator(
                                            color = SkyAccent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = strings.verifyingApkMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PureWhite
                                        )
                                    }
                                }

                                UpdatePhase.NEEDS_INSTALL_PERMISSION -> {
                                    VerificationChecklistSurface(
                                        packageOk = updateState.packageVerified,
                                        signatureOk = updateState.signatureVerified,
                                        versionOk = updateState.versionVerified
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AmberSoft
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = strings.installPermissionTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = strings.installPermissionBody,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NavyPrimary
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = onRequestInstallPermission,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SkyAccent,
                                            contentColor = NavyPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("update_grant_install_permission_button")
                                    ) {
                                        Icon(Icons.Rounded.Security, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.allowAndInstallButton, fontWeight = FontWeight.Bold)
                                    }
                                }

                                UpdatePhase.READY_TO_INSTALL, UpdatePhase.INSTALLING -> {
                                    VerificationChecklistSurface(
                                        packageOk = updateState.packageVerified,
                                        signatureOk = updateState.signatureVerified,
                                        versionOk = updateState.versionVerified
                                    )
                                    Text(
                                        text = strings.preserveDataAssuranceNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SkySoft
                                    )
                                    updateState.verifiedApkFile?.let { apkFile ->
                                        Button(
                                            onClick = { onLaunchVerifiedInstaller(apkFile) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SkyAccent,
                                                contentColor = NavyPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("update_launch_installer_button")
                                        ) {
                                            Icon(Icons.Rounded.InstallMobile, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.launchInstallerButton, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                UpdatePhase.INSTALL_CANCELED -> {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AmberSoft,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = strings.installCanceledTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = strings.installCanceledBody,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NavyPrimary
                                            )
                                        }
                                    }
                                    updateState.verifiedApkFile?.let { apkFile ->
                                        Button(
                                            onClick = { onLaunchVerifiedInstaller(apkFile) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SkyAccent,
                                                contentColor = NavyPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("update_retry_install_button")
                                        ) {
                                            Icon(Icons.Rounded.InstallMobile, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.retryInstallButton, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                UpdatePhase.ERROR -> {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("update_error_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = updateState.errorMessage ?: "Unable to complete update.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                    if (updateState.hasUpdate) {
                                        Button(
                                            onClick = onStartDownloadAndInstall,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SkyAccent,
                                                contentColor = NavyPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("update_install_button")
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.updateButtonLabel, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = onCheckForUpdates,
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.55f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("update_check_again_button")
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.checkUpdatesAgainButton)
                                        }
                                    }
                                }

                                UpdatePhase.UPDATE_AVAILABLE -> {
                                    if (updateState.hasUpdate) {
                                        Button(
                                            onClick = onStartDownloadAndInstall,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SkyAccent,
                                                contentColor = NavyPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("update_install_button")
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.updateButtonLabel, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                UpdatePhase.IDLE, UpdatePhase.UP_TO_DATE -> {
                                    if (updateState.hasUpdate) {
                                        Button(
                                            onClick = onStartDownloadAndInstall,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SkyAccent,
                                                contentColor = NavyPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("update_install_button")
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.updateButtonLabel, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = onCheckForUpdates,
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.55f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("update_check_again_button")
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.checkUpdatesAgainButton)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Cloud-Synced Content & Supported Settings Card (Firestore real-time)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .testTag("cloud_sync_status_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = NavyPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.cloudContentSyncTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(shape = RoundedCornerShape(50), color = EmeraldSoft) {
                                    Text(
                                        text = strings.cloudSyncActiveBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldVerified,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SkySoft
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = strings.cloudCategoriesCountLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${cloudConfig.allCategoryOptions().size}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NavyPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SkySoft
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = strings.cloudExpiryWindowLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${cloudConfig.expiryWarningDays} days",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = NavyPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            val noticeBody = cloudConfig.localizedNoticeBody(strings.languageCode)
                            if (cloudConfig.noticeActive && noticeBody.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AmberSoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("update_screen_cloud_notice")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val noticeTitle = cloudConfig.localizedNoticeTitle(strings.languageCode)
                                        if (noticeTitle.isNotBlank()) {
                                            Text(
                                                text = noticeTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = noticeBody,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Family Admin Live Cloud Config Editor (no APK rebuild needed)
            if (isAdmin) {
                item {
                    AdminCloudConfigEditorCard(
                        cloudConfig = cloudConfig,
                        onSaveFamilyCloudConfig = onSaveFamilyCloudConfig
                    )
                }
            }

            // 4. Explicit Architectural Distinction Card (Requirement 5)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .testTag("apk_vs_cloud_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.apkVsCloudTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = strings.apkUpdateRequiresRebuildNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = strings.cloudUpdatesNoApkNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationChecklistSurface(
    packageOk: Boolean,
    signatureOk: Boolean,
    versionOk: Boolean
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PureWhite.copy(alpha = 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apk_verification_checklist")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (packageOk) {
                ChecklistRow(strings.verifiedPackageLabel)
            }
            if (signatureOk) {
                ChecklistRow(strings.verifiedSignatureLabel)
            }
            if (versionOk) {
                ChecklistRow(strings.verifiedVersionLabel)
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = SkyAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PureWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminCloudConfigEditorCard(
    cloudConfig: EffectiveCloudConfig,
    onSaveFamilyCloudConfig: (
        noticeActive: Boolean,
        noticeTitleEn: String,
        noticeTitleKn: String,
        noticeBodyEn: String,
        noticeBodyKn: String,
        noticeLevel: String,
        homeBannerLabelEn: String,
        homeBannerLabelKn: String,
        extraCategories: List<CloudCategoryItem>,
        expiryWarningDays: Long,
        showSampleDocs: Boolean
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    var noticeActive by remember(cloudConfig) { mutableStateOf(cloudConfig.noticeActive) }
    var noticeTitle by remember(cloudConfig) { mutableStateOf(cloudConfig.noticeTitleEn) }
    var noticeBody by remember(cloudConfig) { mutableStateOf(cloudConfig.noticeBodyEn) }
    var homeBannerLabel by remember(cloudConfig) { mutableStateOf(cloudConfig.homeBannerLabelEn) }
    var expiryDaysText by remember(cloudConfig) { mutableStateOf(cloudConfig.expiryWarningDays.toString()) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryKn by remember { mutableStateOf("") }
    val customCategories = remember(cloudConfig.extraCategories) {
        mutableStateListOf<CloudCategoryItem>().apply { addAll(cloudConfig.extraCategories) }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .testTag("admin_cloud_config_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.adminCloudConfigTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.cloudNoticeToggleLabel, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = noticeActive,
                        onCheckedChange = { noticeActive = it },
                        modifier = Modifier.testTag("cloud_notice_switch")
                    )
                }

                OutlinedTextField(
                    value = noticeTitle,
                    onValueChange = { noticeTitle = it },
                    label = { Text(strings.cloudNoticeTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noticeBody,
                    onValueChange = { noticeBody = it },
                    label = { Text(strings.cloudNoticeBodyLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeBannerLabel,
                    onValueChange = { homeBannerLabel = it },
                    label = { Text(strings.cloudBannerLabelField) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expiryDaysText,
                    onValueChange = { expiryDaysText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text(strings.cloudExpiryDaysField) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                if (customCategories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customCategories.forEach { cat ->
                            AssistChip(
                                onClick = { customCategories.remove(cat) },
                                label = { Text(cat.labelEn) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove category",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(strings.cloudExtraCategoryEnLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newCategoryKn,
                        onValueChange = { newCategoryKn = it },
                        label = { Text(strings.cloudExtraCategoryKnLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clean = newCategoryName.trim()
                        if (clean.length >= 2 && customCategories.none { it.key.equals(clean, ignoreCase = true) }) {
                            customCategories.add(
                                CloudCategoryItem(
                                    key = clean,
                                    labelEn = clean,
                                    labelKn = newCategoryKn.trim().ifBlank { clean }
                                )
                            )
                            newCategoryName = ""
                            newCategoryKn = ""
                        }
                    },
                    enabled = newCategoryName.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addCloudCategoryButton)
                }

                Button(
                    onClick = {
                        val warningDays = expiryDaysText.toLongOrNull()?.coerceIn(1L, 365L) ?: 30L
                        onSaveFamilyCloudConfig(
                            noticeActive,
                            noticeTitle,
                            noticeTitle,
                            noticeBody,
                            noticeBody,
                            "info",
                            homeBannerLabel,
                            homeBannerLabel,
                            customCategories.toList(),
                            warningDays,
                            cloudConfig.showSampleDocs
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_cloud_config_button")
                ) {
                    Text(strings.saveCloudConfigButton, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
