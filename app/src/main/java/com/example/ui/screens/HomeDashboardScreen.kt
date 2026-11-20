package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.update.AppUpdateState
import com.example.data.update.UpdatePhase
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.ExpiryStatus
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.domain.model.UserProfile
import com.example.localization.LocalAppStrings
import com.example.ui.components.BankingHeroGradientBrush
import com.example.ui.components.BankingStatCard
import com.example.ui.components.DocumentCardItem
import com.example.ui.components.LanguageSwitcherPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.formatDateMillis
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft

@Composable
fun HomeDashboardScreen(
    userProfile: UserProfile?,
    family: FamilyAccount,
    members: List<FamilyMember>,
    documents: List<FamilyDocument>,
    backupStatus: BackupStatusInfo,
    isAdmin: Boolean,
    isWideScreen: Boolean,
    languageCode: String,
    onSelectLanguage: (String) -> Unit,
    onQuickUpload: () -> Unit,
    onQuickScan: () -> Unit,
    onQuickAddMember: () -> Unit,
    onLoadSamples: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenDocumentDetail: (FamilyDocument) -> Unit,
    onShowMessage: (String) -> Unit,
    updateState: AppUpdateState = AppUpdateState(),
    cloudConfig: EffectiveCloudConfig = EffectiveCloudConfig(),
    onOpenUpdates: () -> Unit = {},
    onStartAppUpdate: () -> Unit = {},
    onShareDocument: ((FamilyDocument) -> Unit)? = null,
    onDownloadDocument: ((FamilyDocument) -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val now = System.currentTimeMillis()
    val expiringDocs = documents.filter {
        val status = it.expiryStatus(now, cloudConfig.expiryWarningDays)
        status == ExpiryStatus.EXPIRING_SOON || status == ExpiryStatus.EXPIRED
    }
    val recentDocs = documents.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Welcome & Family Banking Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BankingHeroGradientBrush())
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_brand_header"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PureWhite.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = strings.appName,
                                            tint = SkyAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (languageCode == "kn") {
                                                "${strings.appName} • ${strings.docActions.brandPronunciationKn}"
                                            } else {
                                                strings.appName
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PureWhite,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = strings.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkySoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.goodDayGreeting} ${userProfile?.displayName ?: ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SkySoft
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = family.familyName,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                LanguageSwitcherPill(
                                    currentLanguage = languageCode,
                                    onSelectLanguage = onSelectLanguage
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = PureWhite.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .clickable {
                                            clipboard.setText(AnnotatedString(family.familyCode))
                                            onShowMessage(strings.copiedFamilyId)
                                        }
                                        .testTag("copy_family_id_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${strings.familyIdPrefix} ${family.familyCode}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PureWhite,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = strings.copyFamilyId,
                                            tint = SkyAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SkyAccent.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = if (isAdmin) strings.roleAdmin else strings.roleMember,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SkyAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            val bannerLabel = cloudConfig.localizedHomeBannerLabel(languageCode)
                            if (bannerLabel.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PureWhite.copy(alpha = 0.14f),
                                    modifier = Modifier.testTag("home_cloud_banner_label")
                                ) {
                                    Text(
                                        text = bannerLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SkySoft,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onQuickUpload,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SkyAccent,
                                        contentColor = NavyPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_quick_upload_button")
                                ) {
                                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.quickUpload,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                if (isAdmin) {
                                    Button(
                                        onClick = onOpenAdminPanel,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PureWhite.copy(alpha = 0.16f),
                                            contentColor = PureWhite
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("home_admin_panel_button")
                                    ) {
                                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = strings.adminConsoleButton, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.adminConsoleButton, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic APK Update Banner (ONLY shown when remote versionCode > installed versionCode)
        if (updateState.hasUpdate) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .clickable(onClick = onOpenUpdates)
                            .testTag("home_update_banner"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SkySoft),
                        border = BorderStroke(1.dp, SkyAccent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NavyPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = strings.newUpdateAvailableTitle,
                                    tint = SkyAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.newUpdateAvailableTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                val remote = updateState.remoteVersion
                                if (remote != null) {
                                    Text(
                                        text = "v${remote.versionName} (${remote.versionCode}) • ${remote.releaseNotes.ifBlank { strings.preserveDataAssuranceNote }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            when (updateState.phase) {
                                UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING -> {
                                    CircularProgressIndicator(
                                        color = NavyPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                else -> {
                                    Button(
                                        onClick = {
                                            if (updateState.verifiedApkFile != null || updateState.phase == UpdatePhase.NEEDS_INSTALL_PERMISSION) {
                                                onOpenUpdates()
                                            } else {
                                                onStartAppUpdate()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NavyPrimary,
                                            contentColor = PureWhite
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("home_update_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.updateButtonLabel, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cloud-Synced Family/App Notice Banner (delivered live without APK rebuild)
        val cloudNoticeBody = cloudConfig.localizedNoticeBody(languageCode)
        if (cloudConfig.noticeActive && cloudNoticeBody.isNotBlank()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .clickable(onClick = onOpenUpdates)
                            .testTag("home_cloud_notice_banner"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AmberSoft),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val noticeTitle = cloudConfig.localizedNoticeTitle(languageCode)
                                if (noticeTitle.isNotBlank()) {
                                    Text(
                                        text = noticeTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                }
                                Text(
                                    text = cloudNoticeBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Key Metrics Grid (Responsive: 4 across on tablets, 2x2 on phones)
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BankingStatCard(
                                title = strings.totalMembers,
                                value = "${members.size}",
                                icon = Icons.Rounded.Group,
                                accentColor = NavyPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            BankingStatCard(
                                title = strings.totalDocuments,
                                value = "${documents.size}",
                                icon = Icons.Rounded.Description,
                                accentColor = SkyAccent,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDocuments
                            )
                            BankingStatCard(
                                title = strings.expiringSoonCount,
                                value = "${expiringDocs.size}",
                                icon = Icons.Rounded.Schedule,
                                accentColor = AmberWarning,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDocuments
                            )
                            BankingStatCard(
                                title = strings.encryptedLocalCount,
                                value = "${backupStatus.localEncryptedDocs}",
                                icon = Icons.Rounded.Lock,
                                accentColor = EmeraldVerified,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenBackup
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BankingStatCard(
                                title = strings.totalMembers,
                                value = "${members.size}",
                                icon = Icons.Rounded.Group,
                                accentColor = NavyPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            BankingStatCard(
                                title = strings.totalDocuments,
                                value = "${documents.size}",
                                icon = Icons.Rounded.Description,
                                accentColor = SkyAccent,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDocuments
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BankingStatCard(
                                title = strings.expiringSoonCount,
                                value = "${expiringDocs.size}",
                                icon = Icons.Rounded.Schedule,
                                accentColor = AmberWarning,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDocuments
                            )
                            BankingStatCard(
                                title = strings.encryptedLocalCount,
                                value = "${backupStatus.localEncryptedDocs}",
                                icon = Icons.Rounded.Lock,
                                accentColor = EmeraldVerified,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenBackup
                            )
                        }
                    }
                }
            }
        }

        // Backup Status Summary Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .clickable(onClick = onOpenBackup)
                        .testTag("home_backup_status_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SkySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Backup,
                                contentDescription = strings.backupStatusTitle,
                                tint = NavyPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.backupStatusTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${strings.lastBackupLabel} ${
                                    if (backupStatus.lastBackupAtMillis > 0L) formatDateMillis(backupStatus.lastBackupAtMillis)
                                    else strings.neverBackedUp
                                } • ${backupStatus.backedUpDocs}/${backupStatus.totalDocs}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SyncStatusBadge(syncState = backupStatus.syncState)
                        }
                    }
                }
            }
        }

        // Quick Actions Strip
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    SectionHeader(title = strings.quickActions)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            QuickActionTile(
                                icon = Icons.Rounded.CameraAlt,
                                label = strings.scanDocument,
                                tint = NavyPrimary,
                                tag = "home_scan_action",
                                onClick = onQuickScan
                            )
                        }
                        if (isAdmin) {
                            item {
                                QuickActionTile(
                                    icon = Icons.Rounded.PersonAdd,
                                    label = strings.addMember,
                                    tint = Color(0xFF0284C7),
                                    tag = "home_add_member_action",
                                    onClick = onQuickAddMember
                                )
                            }
                        }
                        if (cloudConfig.showSampleDocs) {
                            item {
                                QuickActionTile(
                                    icon = Icons.Rounded.AutoAwesome,
                                    label = strings.loadSampleDocs,
                                    tint = EmeraldVerified,
                                    tag = "home_load_samples_action",
                                    onClick = onLoadSamples
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expiry Alerts Section
        if (expiringDocs.isNotEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                        SectionHeader(title = strings.expiryAlertTitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            expiringDocs.take(3).forEach { doc ->
                                DocumentCardItem(
                                    document = doc,
                                    onClick = { onOpenDocumentDetail(doc) },
                                    cloudConfig = cloudConfig,
                                    onShareClick = onShareDocument?.let { cb -> { cb(doc) } },
                                    onDownloadClick = onDownloadDocument?.let { cb -> { cb(doc) } }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Documents
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SectionHeader(
                    title = strings.recentDocuments,
                    actionLabel = strings.viewAll,
                    onActionClick = onOpenDocuments,
                    modifier = Modifier.widthIn(max = 960.dp)
                )
            }
        }

        if (recentDocs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.emptyDocumentsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.emptyDocumentsSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(recentDocs, key = { it.id }) { doc ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DocumentCardItem(
                        document = doc,
                        onClick = { onOpenDocumentDetail(doc) },
                        modifier = Modifier.widthIn(max = 960.dp),
                        cloudConfig = cloudConfig,
                        onShareClick = onShareDocument?.let { cb -> { cb(doc) } },
                        onDownloadClick = onDownloadDocument?.let { cb -> { cb(doc) } }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
