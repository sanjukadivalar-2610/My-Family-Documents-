package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategoryOption
import com.example.domain.model.DocumentCategory
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.localization.LocalAppStrings
import com.example.ui.components.DocumentCardItem
import com.example.ui.components.formatDateMillis
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    documents: List<FamilyDocument>,
    members: List<FamilyMember>,
    initialMemberFilterId: String?,
    openUploadInitially: Boolean,
    onConsumeInitialState: () -> Unit,
    onOpenDocumentDetail: (FamilyDocument) -> Unit,
    onOpenCameraScanner: () -> Unit,
    onLoadSampleDocs: () -> Unit,
    onUploadDocumentUri: (
        uri: Uri,
        title: String,
        categoryKey: String,
        member: FamilyMember?,
        fileTypeOverride: String?,
        maskedId: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long
    ) -> Unit,
    cloudConfig: EffectiveCloudConfig = EffectiveCloudConfig()
) {
    val strings = LocalAppStrings.current
    val categoryOptions = remember(cloudConfig) { cloudConfig.allCategoryOptions() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    var selectedMemberId by remember(initialMemberFilterId) { mutableStateOf(initialMemberFilterId) }
    var showUploadSourceSheet by remember(openUploadInitially) {
        mutableStateOf(openUploadInitially).also {
            if (openUploadInitially || initialMemberFilterId != null) onConsumeInitialState()
        }
    }

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileType by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingFileType = "pdf"
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingFileType = null
        }
    }

    val filteredDocs = remember(documents, searchQuery, selectedCategoryKey, selectedMemberId) {
        documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.category.contains(searchQuery, ignoreCase = true) ||
                doc.memberName.contains(searchQuery, ignoreCase = true) ||
                (doc.documentNumberMasked?.contains(searchQuery, ignoreCase = true) == true)
            val matchesCategory = selectedCategoryKey == null ||
                doc.category.equals(selectedCategoryKey, ignoreCase = true)
            val matchesMember = selectedMemberId == null || doc.memberId == selectedMemberId
            matchesQuery && matchesCategory && matchesMember
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showUploadSourceSheet = true },
                containerColor = NavyPrimary,
                contentColor = PureWhite,
                modifier = Modifier.testTag("documents_upload_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = strings.uploadDocumentTitle)
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
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                        Text(
                            text = strings.documentsTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(strings.searchDocumentsHint) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = strings.clearDate)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("documents_search_input")
                        )
                    }
                }
            }

            // 12 Document Categories Filter Row
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .testTag("documents_category_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryKey == null,
                                onClick = { selectedCategoryKey = null },
                                label = { Text(strings.allCategories) },
                                modifier = Modifier.testTag("category_chip_all")
                            )
                        }
                        items(categoryOptions, key = { it.storageKey }) { cat ->
                            val isSelected = selectedCategoryKey.equals(cat.storageKey, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryKey = if (isSelected) null else cat.storageKey
                                },
                                leadingIcon = {
                                    Icon(cat.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(cat.localizedLabel(strings.languageCode)) },
                                modifier = Modifier.testTag("category_chip_${cat.storageKey.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Member Filter Chips
            if (members.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = strings.memberOwnerLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedMemberId == null,
                                    onClick = { selectedMemberId = null },
                                    label = { Text(strings.allMembers) }
                                )
                            }
                            items(members, key = { it.id }) { member ->
                                FilterChip(
                                    selected = selectedMemberId == member.id,
                                    onClick = {
                                        selectedMemberId = if (selectedMemberId == member.id) null else member.id
                                    },
                                    label = { Text(member.name) }
                                )
                            }
                        }
                    }
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 520.dp)
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = strings.emptyDocumentsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.emptyDocumentsSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { showUploadSourceSheet = true }) {
                                    Text(strings.quickUpload)
                                }
                                if (cloudConfig.showSampleDocs) {
                                    OutlinedButton(onClick = onLoadSampleDocs) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.loadSampleDocs)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DocumentCardItem(
                            document = doc,
                            onClick = { onOpenDocumentDetail(doc) },
                            modifier = Modifier.widthIn(max = 960.dp),
                            cloudConfig = cloudConfig
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showUploadSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showUploadSourceSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.uploadDocumentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        showUploadSourceSheet = false
                        pdfPicker.launch(arrayOf("application/pdf"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_pdf_button")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.uploadPdf)
                }
                Button(
                    onClick = {
                        showUploadSourceSheet = false
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_image_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.uploadImage)
                }
                OutlinedButton(
                    onClick = {
                        showUploadSourceSheet = false
                        onOpenCameraScanner()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_camera_scan_button")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.scanWithCamera)
                }
                if (cloudConfig.showSampleDocs) {
                    OutlinedButton(
                        onClick = {
                            showUploadSourceSheet = false
                            onLoadSampleDocs()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("upload_load_samples_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.loadSampleDocs)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    pendingUri?.let { uri ->
        CloudDocumentMetadataDialog(
            members = members,
            categoryOptions = categoryOptions,
            defaultCategoryKey = selectedCategoryKey ?: DocumentCategory.AADHAAR.storageKey,
            onDismiss = { pendingUri = null },
            onConfirm = { title, categoryKey, member, maskedId, notes, issueMs, expiryMs ->
                onUploadDocumentUri(
                    uri,
                    title,
                    categoryKey,
                    member,
                    pendingFileType,
                    maskedId,
                    notes,
                    issueMs,
                    expiryMs
                )
                pendingUri = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDocumentMetadataDialog(
    members: List<FamilyMember>,
    categoryOptions: List<CategoryOption>,
    defaultCategoryKey: String,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        categoryKey: String,
        member: FamilyMember?,
        maskedId: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val initialOption = categoryOptions.firstOrNull {
        it.storageKey.equals(defaultCategoryKey, ignoreCase = true)
    } ?: categoryOptions.first()
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember(initialOption) { mutableStateOf(initialOption) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
    var memberExpanded by remember { mutableStateOf(false) }
    var maskedId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var issueMillis by remember { mutableLongStateOf(0L) }
    var expiryMillis by remember { mutableLongStateOf(0L) }
    var pickingIssueDate by remember { mutableStateOf(false) }
    var pickingExpiryDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.uploadDocumentTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.documentTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("doc_metadata_title_input")
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.localizedLabel(strings.languageCode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.categoryLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryOptions.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.localizedLabel(strings.languageCode)) },
                                leadingIcon = { Icon(cat.icon, contentDescription = null) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = memberExpanded,
                        onExpandedChange = { memberExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.memberOwnerLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = memberExpanded,
                            onDismissRequest = { memberExpanded = false }
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text("${member.name} (${member.relation})") },
                                    onClick = {
                                        selectedMember = member
                                        memberExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = maskedId,
                    onValueChange = { maskedId = it },
                    label = { Text(strings.docNumberOptionalLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingIssueDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (issueMillis > 0L) formatDateMillis(issueMillis) else strings.issueDateLabel,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { pickingExpiryDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (expiryMillis > 0L) formatDateMillis(expiryMillis) else strings.expiryDateLabel,
                            maxLines = 1
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(strings.notesOptionalLabel) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { selectedCategory.localizedLabel(strings.languageCode) }
                    onConfirm(
                        finalTitle,
                        selectedCategory.storageKey,
                        selectedMember,
                        maskedId.trim().ifBlank { null },
                        notes.trim().ifBlank { null },
                        issueMillis,
                        expiryMillis
                    )
                },
                modifier = Modifier.testTag("save_document_metadata_button")
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

    if (pickingIssueDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = issueMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { pickingIssueDate = false },
            confirmButton = {
                TextButton(onClick = {
                    issueMillis = state.selectedDateMillis ?: 0L
                    pickingIssueDate = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    issueMillis = 0L
                    pickingIssueDate = false
                }) { Text(strings.clearDate) }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingExpiryDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = expiryMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { pickingExpiryDate = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryMillis = state.selectedDateMillis ?: 0L
                    pickingExpiryDate = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    expiryMillis = 0L
                    pickingExpiryDate = false
                }) { Text(strings.clearDate) }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentMetadataDialog(
    members: List<FamilyMember>,
    defaultCategory: DocumentCategory = DocumentCategory.AADHAAR,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        category: DocumentCategory,
        member: FamilyMember?,
        maskedId: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
    var memberExpanded by remember { mutableStateOf(false) }
    var maskedId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var issueMillis by remember { mutableLongStateOf(0L) }
    var expiryMillis by remember { mutableLongStateOf(0L) }
    var pickingIssueDate by remember { mutableStateOf(false) }
    var pickingExpiryDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.uploadDocumentTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.documentTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("doc_metadata_title_input")
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.localizedLabel(strings.languageCode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.categoryLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DocumentCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.localizedLabel(strings.languageCode)) },
                                leadingIcon = { Icon(cat.icon, contentDescription = null) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = memberExpanded,
                        onExpandedChange = { memberExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.memberOwnerLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = memberExpanded,
                            onDismissRequest = { memberExpanded = false }
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text("${member.name} (${member.relation})") },
                                    onClick = {
                                        selectedMember = member
                                        memberExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = maskedId,
                    onValueChange = { maskedId = it },
                    label = { Text(strings.docNumberOptionalLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingIssueDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (issueMillis > 0L) formatDateMillis(issueMillis) else strings.issueDateLabel,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { pickingExpiryDate = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (expiryMillis > 0L) formatDateMillis(expiryMillis) else strings.expiryDateLabel,
                            maxLines = 1
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(strings.notesOptionalLabel) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { selectedCategory.localizedLabel(strings.languageCode) }
                    onConfirm(
                        finalTitle,
                        selectedCategory,
                        selectedMember,
                        maskedId.trim().ifBlank { null },
                        notes.trim().ifBlank { null },
                        issueMillis,
                        expiryMillis
                    )
                },
                modifier = Modifier.testTag("save_document_metadata_button")
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

    if (pickingIssueDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = issueMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { pickingIssueDate = false },
            confirmButton = {
                TextButton(onClick = {
                    issueMillis = state.selectedDateMillis ?: 0L
                    pickingIssueDate = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    issueMillis = 0L
                    pickingIssueDate = false
                }) { Text(strings.clearDate) }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingExpiryDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = expiryMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { pickingExpiryDate = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryMillis = state.selectedDateMillis ?: 0L
                    pickingExpiryDate = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    expiryMillis = 0L
                    pickingExpiryDate = false
                }) { Text(strings.clearDate) }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
