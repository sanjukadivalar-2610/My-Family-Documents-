package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.localization.LocalAppStrings
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft

@Composable
fun FamilyScreen(
    family: FamilyAccount,
    members: List<FamilyMember>,
    documents: List<FamilyDocument>,
    currentUserId: String,
    isAdmin: Boolean,
    openAddDialogInitially: Boolean,
    onConsumeInitialAdd: () -> Unit,
    onAddOrUpdateMember: (
        existing: FamilyMember?,
        name: String,
        relation: String,
        role: String,
        email: String,
        photoUri: Uri?
    ) -> Unit,
    onDeleteMember: (String) -> Unit,
    onOpenMemberDocuments: (String) -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    val strings = LocalAppStrings.current
    var editingMember by remember { mutableStateOf<FamilyMember?>(null) }
    var showMemberDialog by remember(openAddDialogInitially) {
        mutableStateOf(openAddDialogInitially && isAdmin).also {
            if (openAddDialogInitially) onConsumeInitialAdd()
        }
    }
    var memberPendingDelete by remember { mutableStateOf<FamilyMember?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = {
                        editingMember = null
                        showMemberDialog = true
                    },
                    containerColor = NavyPrimary,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("add_member_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = strings.addMember)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.familyMembersTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${family.familyName} • ${members.size} ${strings.memberCountSuffix}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = onOpenAdminPanel,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("family_admin_panel_button")
                            ) {
                                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.adminConsoleButton)
                            }
                        }
                    }
                }
            }

            if (!isAdmin) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 960.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = SkySoft.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = strings.adminOnlyManageNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NavyPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            items(members, key = { it.id }) { member ->
                val memberDocCount = documents.count { it.memberId == member.id }
                val canEditThisMember = isAdmin || member.linkedUserId == currentUserId
                val canDeleteThisMember = isAdmin && member.linkedUserId != family.adminUid

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .clickable { onOpenMemberDocuments(member.id) }
                            .testTag("member_card_${member.id}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MemberAvatar(
                                name = member.name,
                                photoBase64 = member.photoBase64,
                                size = 54.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (member.isAdmin) NavyPrimary else SkySoft
                                    ) {
                                        Text(
                                            text = if (member.isAdmin) strings.roleAdmin else strings.roleMember,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (member.isAdmin) SkyAccent else NavyPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${member.relation} • $memberDocCount ${strings.navDocuments}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (member.email.isNotBlank()) {
                                    Text(
                                        text = member.email,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (canEditThisMember) {
                                IconButton(
                                    onClick = {
                                        editingMember = member
                                        showMemberDialog = true
                                    },
                                    modifier = Modifier.testTag("edit_member_${member.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = strings.editMember,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (canDeleteThisMember) {
                                IconButton(
                                    onClick = { memberPendingDelete = member },
                                    modifier = Modifier.testTag("delete_member_${member.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = strings.removeMember,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showMemberDialog) {
        MemberEditDialog(
            existing = editingMember,
            isAdmin = isAdmin,
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
                TextButton(onClick = { memberPendingDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun MemberEditDialog(
    existing: FamilyMember?,
    isAdmin: Boolean,
    isRootAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, role: String, email: String, photoUri: Uri?) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var relation by remember(existing) { mutableStateOf(existing?.relation ?: "Member") }
    var role by remember(existing) { mutableStateOf(existing?.role ?: "member") }
    var email by remember(existing) { mutableStateOf(existing?.email.orEmpty()) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedPhotoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) strings.addMember else strings.editMember) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pickedPhotoUri != null) {
                        AsyncImage(
                            model = pickedPhotoUri,
                            contentDescription = strings.selectPhoto,
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        MemberAvatar(
                            name = name.ifBlank { "M" },
                            photoBase64 = existing?.photoBase64,
                            size = 56.dp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_member_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.selectPhoto)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.memberNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("member_name_input")
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text(strings.relationLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("member_relation_input")
                )
                if (isAdmin && !isRootAdmin) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.linkedEmailLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (role == "admin") strings.roleAdmin else strings.roleMember,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, relation, role, email, pickedPhotoUri) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_member_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
