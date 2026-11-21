package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.documents.DocumentTransferManager
import com.example.data.documents.DocumentTransferState
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.localization.LocalAppStrings
import com.example.ui.components.CreateDocumentWithMimeContract
import com.example.ui.components.DocumentMoreActionsSheet
import com.example.ui.components.DocumentTransferBanner
import com.example.ui.components.DownloadDestinationDialog
import com.example.ui.components.ExpiryStatusPill
import com.example.ui.components.formatDateMillis
import com.example.ui.components.formatFileSize
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: FamilyDocument,
    members: List<FamilyMember>,
    isAdmin: Boolean,
    loadDecryptedBytes: suspend (FamilyDocument) -> ByteArray?,
    prepareShareFile: suspend (FamilyDocument) -> File? = { null },
    onExportToUri: (FamilyDocument, Uri) -> Unit = { _, _ -> },
    onRenameAndUpdateDates: (FamilyDocument, String, Long, Long, String?, String?) -> Unit,
    onUpdatePermissions: (FamilyDocument, List<String>) -> Unit,
    onDeleteDocument: (FamilyDocument) -> Unit,
    onBack: () -> Unit,
    cloudConfig: EffectiveCloudConfig = EffectiveCloudConfig(),
    transferState: DocumentTransferState? = null,
    onShare: (() -> Unit)? = null,
    onDownloadToDownloads: (() -> Unit)? = null,
    onDownloadToSafUri: ((Uri) -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onOpenSavedFile: ((Uri, String) -> Unit)? = null,
    onDismissTransferState: (() -> Unit)? = null,
    onExternalPickerLaunching: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    var previewPages by remember(document.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoadingPreview by remember(document.id) { mutableStateOf(true) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreActionsSheet by remember { mutableStateOf(false) }
    var showDownloadDestinationDialog by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val mimeType = DocumentTransferManager.resolveMimeType(document)
    val safeFileName = DocumentTransferManager.buildSafeFileName(document)

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentWithMimeContract()
    ) { uri ->
        if (uri != null) {
            if (onDownloadToSafUri != null) {
                onDownloadToSafUri(uri)
            } else {
                onExportToUri(document, uri)
            }
        }
    }

    fun triggerShare() {
        if (onShare != null) {
            onShare()
        } else {
            scope.launch {
                val file = prepareShareFile(document) ?: return@launch
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = DocumentTransferManager.buildSingleShareIntent(
                    context = context,
                    doc = document,
                    contentUri = contentUri,
                    chooserTitle = strings.shareDocument
                )
                onExternalPickerLaunching()
                runCatching { context.startActivity(shareIntent) }
            }
        }
    }

    fun triggerOpenWith() {
        if (onOpenWith != null) {
            onOpenWith()
        } else {
            scope.launch {
                val file = prepareShareFile(document) ?: return@launch
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val openIntent = DocumentTransferManager.buildOpenWithIntent(
                    context = context,
                    contentUri = contentUri,
                    mimeType = mimeType,
                    chooserTitle = strings.docActions.openWithLabel
                )
                onExternalPickerLaunching()
                runCatching { context.startActivity(openIntent) }
            }
        }
    }

    fun triggerDownload() {
        if (onDownloadToDownloads != null) {
            showDownloadDestinationDialog = true
        } else {
            onExternalPickerLaunching()
            createDocLauncher.launch(safeFileName to mimeType)
        }
    }

    LaunchedEffect(document.id, document.storagePath, document.isBackedUp) {
        isLoadingPreview = true
        previewPages = withContext(Dispatchers.IO) {
            val bytes = loadDecryptedBytes(document) ?: return@withContext emptyList()
            if (document.fileType == "pdf") {
                renderPdfBitmaps(context.cacheDir, document.id, bytes)
            } else {
                listOfNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
        }
        isLoadingPreview = false
    }

    val category = cloudConfig.resolveCategoryOption(document.category)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.title,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.testTag("detail_edit_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = strings.renameDocument)
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_delete_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = strings.deleteDocument,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = { showMoreActionsSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_more_actions_button")
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = strings.docActions.moreActionsLabel
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Encrypted Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${strings.documentPreviewTitle} • ${document.fileType.uppercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        ExpiryStatusPill(
                            document = document,
                            warningDays = cloudConfig.expiryWarningDays
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingPreview) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (previewPages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.localOnlyBadge,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            previewPages.forEachIndexed { idx, bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "${document.title} page ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, _, zoom, _ ->
                                                zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                            }
                                        }
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons (Download / Share)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { triggerDownload() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("detail_download_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.docActions.downloadAction, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { triggerShare() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("detail_share_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.docActions.shareAction, maxLines = 1)
                }
            }

            if (transferState != null) {
                DocumentTransferBanner(
                    transferState = transferState,
                    onOpenFile = { uri, mime -> onOpenSavedFile?.invoke(uri, mime) },
                    onDismiss = { onDismissTransferState?.invoke() },
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            // Metadata Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaRow(strings.categoryLabel, category.localizedLabel(strings.languageCode))
                    MetaRow(strings.memberOwnerLabel, document.memberName)
                    MetaRow("File", "${document.fileName} (${formatFileSize(document.fileSizeBytes)})")
                    document.documentNumberMasked?.takeIf { it.isNotBlank() }?.let {
                        MetaRow(strings.docNumberOptionalLabel, it)
                    }
                    MetaRow(strings.issueDateLabel, formatDateMillis(document.issueDateMillis))
                    MetaRow(
                        strings.expiryDateLabel,
                        if (document.expiryDateMillis > 0L) formatDateMillis(document.expiryDateMillis) else strings.noExpirySet
                    )
                    document.notes?.takeIf { it.isNotBlank() }?.let {
                        MetaRow(strings.notesOptionalLabel, it)
                    }
                }
            }

            // Admin Permissions Card
            if (isAdmin) {
                val linkedMembers = members.filter { it.linkedUserId.isNotBlank() }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 920.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.whoCanAccessTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (linkedMembers.size <= 1) {
                            Text(
                                text = strings.noOtherLinkedUsersNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            var allowedSet by remember(document.allowedUids) {
                                mutableStateOf(document.allowedUids.toSet())
                            }
                            linkedMembers.forEach { member ->
                                val isFixedAdmin = member.linkedUserId == document.adminUid
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
                                            onUpdatePermissions(document, allowedSet.toList())
                                        }
                                    )
                                    Text(
                                        text = "${member.name} (${member.relation})",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showRenameDialog) {
        RenameAndDatesDialog(
            document = document,
            cloudConfig = cloudConfig,
            onDismiss = { showRenameDialog = false },
            onSave = { newTitle, issueMs, expiryMs, newNotes, newCategory ->
                onRenameAndUpdateDates(document, newTitle, issueMs, expiryMs, newNotes, newCategory)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(strings.deleteConfirmTitle) },
            text = { Text(strings.deleteConfirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteDocument(document)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_document_button")
                ) {
                    Text(strings.confirmDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDownloadDestinationDialog) {
        DownloadDestinationDialog(
            targetLabel = safeFileName,
            onSaveToDownloads = {
                showDownloadDestinationDialog = false
                onDownloadToDownloads?.invoke()
            },
            onChooseFolder = {
                showDownloadDestinationDialog = false
                onExternalPickerLaunching()
                createDocLauncher.launch(safeFileName to mimeType)
            },
            onDismiss = { showDownloadDestinationDialog = false }
        )
    }

    if (showMoreActionsSheet) {
        DocumentMoreActionsSheet(
            document = document,
            onDismiss = { showMoreActionsSheet = false },
            onShare = { triggerShare() },
            onDownload = { triggerDownload() },
            onOpenWith = { triggerOpenWith() },
            onCopyLink = { onCopyLink?.invoke() }
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameAndDatesDialog(
    document: FamilyDocument,
    cloudConfig: EffectiveCloudConfig,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String?, String) -> Unit
) {
    val strings = LocalAppStrings.current
    val categoryOptions = remember(cloudConfig) { cloudConfig.allCategoryOptions() }
    var title by remember(document) { mutableStateOf(document.title) }
    var selectedCategory by remember(document, cloudConfig) {
        mutableStateOf(cloudConfig.resolveCategoryOption(document.category))
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var notes by remember(document) { mutableStateOf(document.notes.orEmpty()) }
    var issueMs by remember(document) { mutableLongStateOf(document.issueDateMillis) }
    var expiryMs by remember(document) { mutableLongStateOf(document.expiryDateMillis) }
    var pickIssue by remember { mutableStateOf(false) }
    var pickExpiry by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameDocument) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.documentTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_document_input")
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickIssue = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (issueMs > 0L) formatDateMillis(issueMs) else strings.issueDateLabel, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { pickExpiry = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (expiryMs > 0L) formatDateMillis(expiryMs) else strings.expiryDateLabel, maxLines = 1)
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
                    onSave(
                        title,
                        issueMs,
                        expiryMs,
                        notes.trim().ifBlank { null },
                        selectedCategory.storageKey
                    )
                },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_rename_document_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )

    if (pickIssue) {
        val state = rememberDatePickerState(initialSelectedDateMillis = issueMs.takeIf { it > 0L } ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickIssue = false },
            confirmButton = {
                TextButton(onClick = {
                    issueMs = state.selectedDateMillis ?: 0L
                    pickIssue = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    issueMs = 0L
                    pickIssue = false
                }) { Text(strings.clearDate) }
            }
        ) { DatePicker(state = state) }
    }

    if (pickExpiry) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expiryMs.takeIf { it > 0L } ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickExpiry = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryMs = state.selectedDateMillis ?: 0L
                    pickExpiry = false
                }) { Text(strings.selectDate) }
            },
            dismissButton = {
                TextButton(onClick = {
                    expiryMs = 0L
                    pickExpiry = false
                }) { Text(strings.clearDate) }
            }
        ) { DatePicker(state = state) }
    }
}

private fun renderPdfBitmaps(cacheDir: File, docId: String, pdfBytes: ByteArray): List<Bitmap> {
    val tempFile = File(cacheDir, "preview_${docId}.pdf")
    tempFile.writeBytes(pdfBytes)
    val result = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val count = renderer.pageCount.coerceAtMost(8)
        for (i in 0 until count) {
            val page = renderer.openPage(i)
            val width = (page.width * 2).coerceIn(600, 1400)
            val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(200)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            result.add(bmp)
        }
    } catch (_: Exception) {
        // Ignore corrupted pdf preview
    } finally {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        tempFile.delete()
    }
    return result
}
