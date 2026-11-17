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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.DocumentCategory
import com.example.domain.model.FamilyDocument
import com.example.domain.model.SyncState
import com.example.localization.LocalAppStrings
import com.example.ui.components.BankingHeroGradientBrush
import com.example.ui.components.SectionHeader
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.formatDateMillis
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft

@Composable
fun BackupScreen(
    backupStatus: BackupStatusInfo,
    documents: List<FamilyDocument>,
    onBackupNow: () -> Unit,
    onRestoreFromCloud: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onSelectIntervalDays: (Int) -> Unit,
    onOpenDocumentDetail: (FamilyDocument) -> Unit
) {
    val strings = LocalAppStrings.current
    val isSyncing = backupStatus.syncState == SyncState.SYNCING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    Text(
                        text = strings.backupCenterTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (!backupStatus.isOnline) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .testTag("offline_mode_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = AmberSoft,
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = strings.offlineStatus,
                                tint = AmberWarning
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.offlineModeBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NavyPrimary
                            )
                        }
                    }
                }
            }
        }

        // Main Backup Health Hero Card
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
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = strings.backupStatusTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${strings.lastBackupLabel} ${
                                        if (backupStatus.lastBackupAtMillis > 0L) {
                                            formatDateMillis(backupStatus.lastBackupAtMillis)
                                        } else {
                                            strings.neverBackedUp
                                        }
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SkySoft
                                )
                            }
                            SyncStatusBadge(syncState = backupStatus.syncState)
                        }

                        if (isSyncing) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = SkyAccent,
                                trackColor = PureWhite.copy(alpha = 0.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = PureWhite.copy(alpha = 0.12f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${backupStatus.backedUpDocs} / ${backupStatus.totalDocs}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.cloudSyncedBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SkySoft
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = PureWhite.copy(alpha = 0.12f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${backupStatus.localEncryptedDocs}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.encryptedLocalCount,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SkySoft
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onBackupNow,
                                enabled = backupStatus.isOnline && !isSyncing,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SkyAccent,
                                    contentColor = NavyPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("manual_backup_now_button")
                            ) {
                                Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.manualBackupNow, maxLines = 1, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onRestoreFromCloud,
                                enabled = backupStatus.isOnline && !isSyncing,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_cloud_button")
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.restoreFromCloud, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Auto Backup & Schedule Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.autoBackupTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.autoBackupSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = backupStatus.autoBackupEnabled,
                                onCheckedChange = onToggleAutoBackup,
                                modifier = Modifier.testTag("auto_backup_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.backupFrequencyLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            FilterChip(
                                selected = backupStatus.backupIntervalDays <= 1,
                                onClick = { onSelectIntervalDays(1) },
                                label = { Text(strings.daily) },
                                enabled = backupStatus.autoBackupEnabled,
                                modifier = Modifier.testTag("backup_freq_daily")
                            )
                            FilterChip(
                                selected = backupStatus.backupIntervalDays > 1,
                                onClick = { onSelectIntervalDays(7) },
                                label = { Text(strings.weekly) },
                                enabled = backupStatus.autoBackupEnabled,
                                modifier = Modifier.testTag("backup_freq_weekly")
                            )
                        }
                    }
                }
            }
        }

        // Per-document backup & encryption audit
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SectionHeader(
                    title = "${strings.navDocuments} (${documents.size})",
                    modifier = Modifier.widthIn(max = 960.dp)
                )
            }
        }

        items(documents, key = { it.id }) { doc ->
            val category = DocumentCategory.fromKey(doc.category)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .clickable { onOpenDocumentDetail(doc) }
                        .testTag("backup_doc_row_${doc.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.localizedLabel(strings.languageCode),
                            tint = category.tintColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = strings.encryptedBadge,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${strings.encryptedBadge} • ${formatFileSize(doc.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (doc.isBackedUp) EmeraldSoft else SkySoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (doc.isBackedUp) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                                    contentDescription = if (doc.isBackedUp) strings.cloudSyncedBadge else strings.localOnlyBadge,
                                    tint = if (doc.isBackedUp) EmeraldVerified else NavyPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (doc.isBackedUp) strings.cloudSyncedBadge else strings.localOnlyBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (doc.isBackedUp) EmeraldVerified else NavyPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
