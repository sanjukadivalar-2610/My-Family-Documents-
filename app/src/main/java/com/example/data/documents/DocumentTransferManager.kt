package com.example.data.documents

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface DocumentTransferState {
    data class Preparing(val docTitle: String, val isShare: Boolean) : DocumentTransferState

    data class Downloading(
        val docTitle: String,
        val completedCount: Int,
        val totalCount: Int,
        val bytesRead: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : DocumentTransferState

    data class Completed(
        val fileName: String,
        val savedUri: Uri,
        val mimeType: String,
        val count: Int,
        val isBatch: Boolean
    ) : DocumentTransferState

    data class Failed(val message: String) : DocumentTransferState
}

data class VaultLinkTarget(
    val familyId: String,
    val docId: String
)

data class SavedDocumentResult(
    val uri: Uri,
    val fileName: String,
    val mimeType: String
)

object DocumentTransferManager {

    private const val EXPORTS_DIR_NAME = "exports"
    private const val DOWNLOADS_SUBFOLDER = "FAMVIORA"
    private const val TEMP_MAX_AGE_MS = 30L * 60L * 1000L
    private const val STREAM_CHUNK_BYTES = 16 * 1024

    fun verifyDocumentAccess(
        doc: FamilyDocument,
        currentUid: String,
        family: FamilyAccount?
    ): Boolean {
        if (currentUid.isBlank() || doc.id.isBlank()) return false
        if (family != null) {
            if (doc.familyId.isNotBlank() && doc.familyId != family.familyId) return false
            val isMember = family.adminUid == currentUid || currentUid in family.memberUids
            if (!isMember) return false
        }
        return doc.adminUid == currentUid ||
            doc.uploadedByUid == currentUid ||
            currentUid in doc.allowedUids
    }

    fun resolveMimeType(doc: FamilyDocument): String {
        val ext = doc.fileName.substringAfterLast('.', "").trim().lowercase()
        val fromExt = when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> null
        }
        if (fromExt != null) return fromExt
        return when (doc.fileType.trim().lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun resolveCommonShareMimeType(items: List<Pair<FamilyDocument, Uri>>): String {
        if (items.isEmpty()) return "*/*"
        val mimes = items.map { resolveMimeType(it.first) }.distinct()
        if (mimes.size == 1) return mimes.first()
        if (mimes.all { it.startsWith("image/") }) return "image/*"
        return "*/*"
    }

    fun buildSafeFileName(doc: FamilyDocument): String {
        val fallbackExt = when (doc.fileType.trim().lowercase()) {
            "pdf" -> "pdf"
            "png" -> "png"
            "webp" -> "webp"
            "gif" -> "gif"
            "txt" -> "txt"
            else -> "jpg"
        }
        val rawCandidate = doc.fileName.trim()
            .ifBlank { "${doc.title.trim().ifBlank { doc.id }}.$fallbackExt" }
            .substringAfterLast('/')
            .substringAfterLast('\\')

        val cleaned = rawCandidate
            .replace("..", "_")
            .replace(Regex("[^a-zA-Z0-9._() \\-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.')
            .ifBlank { "document.$fallbackExt" }

        val dotIdx = cleaned.lastIndexOf('.')
        val (base, ext) = if (dotIdx in 1 until cleaned.lastIndex) {
            cleaned.substring(0, dotIdx) to cleaned.substring(dotIdx + 1).lowercase()
        } else {
            cleaned to fallbackExt
        }
        val truncatedBase = base.take(100).ifBlank { "document" }
        return "$truncatedBase.$ext"
    }

    suspend fun createTempDecryptedShareFile(
        context: Context,
        doc: FamilyDocument,
        plainBytes: ByteArray
    ): File = withContext(Dispatchers.IO) {
        cleanupStaleShareFiles(context)
        val safeDocSegment = doc.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "doc" }
        val docExportDir = File(File(context.cacheDir, EXPORTS_DIR_NAME), safeDocSegment).apply {
            if (!exists()) mkdirs()
        }
        val target = File(docExportDir, buildSafeFileName(doc))
        FileOutputStream(target).use { out ->
            out.write(plainBytes)
            out.flush()
        }
        target
    }

    fun getShareContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun cleanupStaleShareFiles(context: Context, maxAgeMillis: Long = TEMP_MAX_AGE_MS) {
        runCatching {
            val root = File(context.cacheDir, EXPORTS_DIR_NAME)
            if (!root.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            root.listFiles()?.forEach { child ->
                if (child.lastModified() < cutoff) {
                    child.deleteRecursively()
                }
            }
        }
    }

    fun clearAllShareTempFiles(context: Context) {
        runCatching {
            val root = File(context.cacheDir, EXPORTS_DIR_NAME)
            if (root.exists()) root.deleteRecursively()
        }
    }

    fun buildSingleShareIntent(
        context: Context,
        doc: FamilyDocument,
        contentUri: Uri,
        chooserTitle: String
    ): Intent {
        val mime = resolveMimeType(doc)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            clipData = ClipData.newUri(context.contentResolver, doc.title, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildMultiShareIntent(
        context: Context,
        items: List<Pair<FamilyDocument, Uri>>,
        chooserTitle: String
    ): Intent {
        require(items.isNotEmpty()) { "At least one document URI is required for multi-share." }
        if (items.size == 1) {
            return buildSingleShareIntent(context, items.first().first, items.first().second, chooserTitle)
        }
        val commonMime = resolveCommonShareMimeType(items)
        val uriList = ArrayList(items.map { it.second })
        val clip = ClipData.newUri(context.contentResolver, chooserTitle, items.first().second)
        items.drop(1).forEach { (_, uri) ->
            clip.addItem(ClipData.Item(uri))
        }
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildOpenWithIntent(
        context: Context,
        contentUri: Uri,
        mimeType: String,
        chooserTitle: String
    ): Intent {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            clipData = ClipData.newRawUri(chooserTitle, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(viewIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Builds an app-only authenticated deep link requiring active signed-in family access.
     * Never exposes Firebase Storage download URLs or public tokens.
     */
    fun buildAuthorizedVaultLink(doc: FamilyDocument): String {
        return Uri.Builder()
            .scheme("famviora")
            .authority("vault")
            .appendPath("family")
            .appendPath(doc.familyId)
            .appendPath("doc")
            .appendPath(doc.id)
            .build()
            .toString()
    }

    fun parseAuthorizedVaultLink(uri: Uri?): VaultLinkTarget? {
        if (uri == null) return null
        if (uri.scheme != "famviora" || uri.host != "vault") return null
        val segments = uri.pathSegments ?: return null
        if (segments.size != 4 || segments[0] != "family" || segments[2] != "doc") return null
        val famId = segments[1].trim()
        val docId = segments[3].trim()
        if (famId.isBlank() || docId.isBlank()) return null
        return VaultLinkTarget(familyId = famId, docId = docId)
    }

    suspend fun saveToDownloads(
        context: Context,
        safeFileName: String,
        mimeType: String,
        bytes: ByteArray,
        onProgress: (Long, Long) -> Unit
    ): Result<SavedDocumentResult> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStoreDownloads(context, safeFileName, mimeType, bytes, onProgress)
        } else {
            saveToAppExternalDownloads(context, safeFileName, mimeType, bytes, onProgress)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveToMediaStoreDownloads(
        context: Context,
        safeFileName: String,
        mimeType: String,
        bytes: ByteArray,
        onProgress: (Long, Long) -> Unit
    ): Result<SavedDocumentResult> {
        val resolver = context.contentResolver
        val uniqueName = deduplicateMediaStoreName(context, safeFileName)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBFOLDER"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("Unable to create file in Downloads."))

        return try {
            resolver.openOutputStream(insertedUri, "w")?.use { output ->
                writeBytesWithProgress(bytes, output, onProgress)
            } ?: throw IllegalStateException("Unable to open Downloads output stream.")

            val finalizeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, finalizeValues, null, null)
            Result.success(SavedDocumentResult(uri = insertedUri, fileName = uniqueName, mimeType = mimeType))
        } catch (t: Throwable) {
            runCatching { resolver.delete(insertedUri, null, null) }
            Result.failure(t)
        }
    }

    private suspend fun saveToAppExternalDownloads(
        context: Context,
        safeFileName: String,
        mimeType: String,
        bytes: ByteArray,
        onProgress: (Long, Long) -> Unit
    ): Result<SavedDocumentResult> {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
        val folder = File(baseDir, DOWNLOADS_SUBFOLDER).apply { if (!exists()) mkdirs() }
        val existingNames = folder.list().orEmpty().toSet()
        val uniqueName = deduplicateFileName(safeFileName, existingNames)
        val destFile = File(folder, uniqueName)

        return try {
            FileOutputStream(destFile).use { output ->
                writeBytesWithProgress(bytes, output, onProgress)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            Result.success(SavedDocumentResult(uri = uri, fileName = uniqueName, mimeType = mimeType))
        } catch (t: Throwable) {
            runCatching { if (destFile.exists()) destFile.delete() }
            Result.failure(t)
        }
    }

    suspend fun writeBytesToSafUri(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                writeBytesWithProgress(bytes, output, onProgress)
            } ?: context.contentResolver.openOutputStream(uri)?.use { output ->
                writeBytesWithProgress(bytes, output, onProgress)
            } ?: throw IllegalStateException("Unable to open destination file.")
            Result.success(Unit)
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            Result.failure(t)
        }
    }

    suspend fun saveMultipleToTreeUri(
        context: Context,
        treeUri: Uri,
        items: List<Triple<FamilyDocument, String, ByteArray>>,
        onFileProgress: (index: Int, totalCount: Int, doc: FamilyDocument, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<SavedDocumentResult> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val existingNames = queryTreeChildNames(context, treeUri, treeDocId).toMutableSet()

        var lastResult: SavedDocumentResult? = null
        for ((idx, item) in items.withIndex()) {
            val (doc, desiredName, bytes) = item
            val mime = resolveMimeType(doc)
            val uniqueName = deduplicateFileName(desiredName, existingNames)
            existingNames.add(uniqueName)

            val createdUri = DocumentsContract.createDocument(resolver, parentDocUri, mime, uniqueName)
                ?: return@withContext Result.failure(
                    IllegalStateException("Unable to create $uniqueName in selected folder.")
                )
            try {
                resolver.openOutputStream(createdUri, "wt")?.use { output ->
                    writeBytesWithProgress(bytes, output) { read, total ->
                        onFileProgress(idx, items.size, doc, read, total)
                    }
                } ?: throw IllegalStateException("Unable to write $uniqueName to selected folder.")
                lastResult = SavedDocumentResult(uri = createdUri, fileName = uniqueName, mimeType = mime)
            } catch (t: Throwable) {
                runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
                return@withContext Result.failure(t)
            }
        }
        val finalResult = lastResult
            ?: return@withContext Result.failure(IllegalStateException("No documents selected."))
        Result.success(finalResult)
    }

    private suspend fun writeBytesWithProgress(
        bytes: ByteArray,
        output: java.io.OutputStream,
        onProgress: (Long, Long) -> Unit
    ) {
        val total = bytes.size.toLong().coerceAtLeast(1L)
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val len = minOf(STREAM_CHUNK_BYTES, bytes.size - offset)
            output.write(bytes, offset, len)
            offset += len
            onProgress(offset.toLong(), total)
        }
        output.flush()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deduplicateMediaStoreName(context: Context, desiredName: String): String {
        val existing = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val relPath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBFOLDER/"
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$relPath%"),
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                while (idx >= 0 && cursor.moveToNext()) {
                    cursor.getString(idx)?.let { existing.add(it) }
                }
            }
        }
        return deduplicateFileName(desiredName, existing)
    }

    private fun queryTreeChildNames(context: Context, treeUri: Uri, parentDocId: String): Set<String> {
        val names = mutableSetOf<String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (idx >= 0 && cursor.moveToNext()) {
                    cursor.getString(idx)?.let { names.add(it) }
                }
            }
        }
        return names
    }

    fun deduplicateFileName(desiredName: String, existingNames: Set<String>): String {
        if (desiredName !in existingNames) return desiredName
        val dot = desiredName.lastIndexOf('.')
        val (base, ext) = if (dot in 1 until desiredName.lastIndex) {
            desiredName.substring(0, dot) to desiredName.substring(dot)
        } else {
            desiredName to ""
        }
        var counter = 1
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (candidate !in existingNames) return candidate
            counter++
        }
    }
}
