package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.DocumentCategory
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.domain.model.SyncState
import com.example.localization.LocalAppStrings
import com.example.ui.components.MemberAvatar
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.formatDateMillis
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    family: FamilyAccount,
    members: List<FamilyMember>,
    documents: List<FamilyDocument>,
    backupStatus: BackupStatusInfo,
    onRenameFamily: (String) -> Unit,
    onAddOrUpdateMember: (
        existing: FamilyMember?,
        name: String,
        relation: String,
        role: String,
        email: String,
        photoUri: Uri?
    ) -> Unit,
    onDeleteMember: (String) -> Unit,
    onUpdateDocumentPermissions: (FamilyDocument, List<String>) -> Unit,
    onBackupNow: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current

    BackHandler(onBack = onBack)

    var showRenameFamilyDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<FamilyMember?>(null) }
    var showMemberDialog by remember { mutableStateOf(false) }
    var memberPendingDelete by remember { mutableStateOf<FamilyMember?>(null) }

    val linkedMembers = remember(members) { members.filter { it.linkedUserId.isNotBlank() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.adminPanelTitle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Family Account Management
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
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
                                Text(
                                    text = strings.familyAccountSection,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { showRenameFamilyDialog = true },
                                    modifier = Modifier.testTag("admin_rename_family_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.renameFamily)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = family.familyName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SkySoft,
                                modifier = Modifier
                                    .clickable {
                                        clipboard.setText(AnnotatedString(family.familyCode))
                                        onShowMessage(strings.copiedFamilyId)
                                    }
                                    .testTag("admin_copy_family_id")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.familyIdPrefix} ${family.familyCode}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NavyPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = strings.copyFamilyId,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.familyIdHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Manage Family Members Section
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
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
                                Text(
                                    text = "${strings.familyMembersTitle} (${members.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = {
                                        editingMember = null
                                        showMemberDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                                    modifier = Modifier.testTag("admin_add_member_button")
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.addMember)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            members.forEachIndexed { idx, member ->
                                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(name = member.name, photoBase64 = member.photoBase64, size = 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${member.relation} • ${if (member.isAdmin) strings.roleAdmin else strings.roleMember}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingMember = member
                                            showMemberDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = strings.editMember)
                                    }
                                    if (member.linkedUserId != family.adminUid) {
                                        IconButton(onClick = { memberPendingDelete = member }) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = strings.removeMember,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Document Permissions Section
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = strings.documentPermissionsSection,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (linkedMembers.size <= 1) {
                                Text(
                                    text = strings.noOtherLinkedUsersNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (documents.isEmpty()) {
                                Text(
                                    text = strings.emptyDocumentsTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (linkedMembers.size > 1) {
                items(documents, key = { "perm_${it.id}" }) { doc ->
                    val category = DocumentCategory.fromKey(doc.category)
                    var allowedSet by remember(doc.id, doc.allowedUids) {
                        mutableStateOf(doc.allowedUids.toSet())
                    }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${category.localizedLabel(strings.languageCode)} • ${doc.memberName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                linkedMembers.forEach { member ->
                                    val isFixedAdmin = member.linkedUserId == family.adminUid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isFixedAdmin || allowedSet.contains(member.linkedUserId),
                                            enabled = !isFixedAdmin,
                                            onCheckedChange = { checked ->
                                                allowedSet = if (checked) allowedSet + member.linkedUserId
                                                else allowedSet - member.linkedUserId
                                                onUpdateDocumentPermissions(doc, allowedSet.toList())
                                            }
                                        )
                                        Text(
                                            text = "${member.name} (${member.relation})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Backup Health Section
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
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
                                Text(
                                    text = strings.backupHealthSection,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SyncStatusBadge(syncState = backupStatus.syncState)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${strings.lastBackupLabel} ${
                                    if (backupStatus.lastBackupAtMillis > 0L) formatDateMillis(backupStatus.lastBackupAtMillis)
                                    else strings.neverBackedUp
                                } • ${backupStatus.backedUpDocs}/${backupStatus.totalDocs}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onBackupNow,
                                enabled = backupStatus.isOnline && backupStatus.syncState != SyncState.SYNCING,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_backup_now_button")
                            ) {
                                Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.manualBackupNow)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showRenameFamilyDialog) {
        var newName by remember(family.familyName) { mutableStateOf(family.familyName) }
        AlertDialog(
            onDismissRequest = { showRenameFamilyDialog = false },
            title = { Text(strings.renameFamily) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(strings.familyNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_family_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameFamily(newName)
                        showRenameFamilyDialog = false
                    },
                    enabled = newName.trim().length >= 2,
                    modifier = Modifier.testTag("confirm_rename_family_button")
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFamilyDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showMemberDialog) {
        MemberEditDialog(
            existing = editingMember,
            isAdmin = true,
            isRootAdmin = editingMember?.linkedUserId == family.adminUid,
            onDismiss = { showMemberDialog = false },
            onSave = { name, relation, role, email, photoUri ->
                onAddOrUpdateMember(editingMember, name, relation, role, email, photoUri)
                showMemberDialog = false
            }
        )
    }

    memberPendingDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingDelete = null },
            title = { Text(strings.removeMember) },
            text = { Text("${member.name}: ${strings.removeMemberConfirm}") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMember(member.id)
                        memberPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.confirmDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingDelete = null }) { Text(strings.cancel) }
            }
        )
    }
}
