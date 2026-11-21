package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.documents.DocumentTransferManager
import com.example.data.documents.DocumentTransferState
import com.example.data.firebase.CloudConfigRepository
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.sample.SampleDocumentFactory
import com.example.data.security.AndroidBiometricGateway
import com.example.data.security.BiometricGateway
import com.example.data.security.BiometricPromptOutcome
import com.example.data.security.BiometricSupportStatus
import com.example.data.security.BiometricUiFeedback
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.data.security.ThemeMode
import com.example.data.security.toFeedback
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.CloudCategoryItem
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.domain.model.SyncState
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.localization.AppStrings
import com.example.worker.ExpiryNotificationHelper
import com.example.worker.VaultWorkScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class MainViewModel(
    private val repository: FamilyVaultRepository,
    private val cryptoManager: DocumentCryptoManager,
    private val securityPreferences: SecurityPreferences,
    private val appContext: Context,
    val currentUserId: String,
    private val cloudConfigRepository: CloudConfigRepository = CloudConfigRepository(appContext)
) : ViewModel() {

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _transferState = MutableStateFlow<DocumentTransferState?>(null)
    val transferState: StateFlow<DocumentTransferState?> = _transferState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    private val syncMutex = Mutex()
    private val inFlightUploadDocIds = mutableSetOf<String>()
    private val inFlightDownloadDocIds = mutableSetOf<String>()
    private val observedSessionDocIds = mutableSetOf<String>()

    private val _biometricUiFeedback = MutableStateFlow<BiometricUiFeedback?>(null)
    val biometricUiFeedback: StateFlow<BiometricUiFeedback?> = _biometricUiFeedback.asStateFlow()

    private val _isSecuritySettingsLoaded = MutableStateFlow(false)
    val isSecuritySettingsLoaded: StateFlow<Boolean> = _isSecuritySettingsLoaded.asStateFlow()

    val securitySettings: StateFlow<SecuritySettings> = securityPreferences.settingsFlow
        .onEach { _isSecuritySettingsLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SecuritySettings()
        )

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    @Volatile
    var isBiometricPromptActive: Boolean = false
        private set

    val isOnline: StateFlow<Boolean> = observeNetworkOnline(appContext)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = checkIsOnlineNow(appContext)
        )

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val familyAccount: StateFlow<FamilyAccount?> = userProfileState
        .flatMapLatest { state ->
            val familyId = (state as? UiState.Success)?.data?.familyId.orEmpty()
            if (familyId.isBlank()) flowOf(null)
            else repository.observeFamilyAccount(familyId).catch { emit(null) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val familyMembers: StateFlow<List<FamilyMember>> = familyAccount
        .flatMapLatest { family ->
            if (family == null) flowOf(emptyList())
            else repository.observeFamilyMembers(family.familyId, currentUserId).catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val documentsState: StateFlow<UiState<List<FamilyDocument>>> = familyAccount
        .flatMapLatest { family ->
            if (family == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observePermittedDocuments(family.familyId, currentUserId)
                    .map<List<FamilyDocument>, UiState<List<FamilyDocument>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = unwrapFirestoreError(error)
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing documents", root)
                        emit(UiState.Error(root.message ?: "Failed to observe documents"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val effectiveCloudConfig: StateFlow<EffectiveCloudConfig> = familyAccount
        .flatMapLatest { family ->
            cloudConfigRepository.observeEffectiveCloudConfig(family?.familyId.orEmpty())
                .catch { emit(EffectiveCloudConfig()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = EffectiveCloudConfig()
        )

    val backupStatus: StateFlow<BackupStatusInfo> = combine(
        isOnline,
        _isSyncing,
        documentsState,
        familyAccount,
        securitySettings
    ) { online, syncing, docsUi, family, settings ->
        val docs = (docsUi as? UiState.Success)?.data.orEmpty()
        val backedUp = docs.count { it.isBackedUp }
        val localCount = cryptoManager.countLocalEncryptedFiles()
        val syncState = when {
            syncing -> SyncState.SYNCING
            !online -> SyncState.OFFLINE
            docs.any { !it.isBackedUp } -> SyncState.ATTENTION_NEEDED
            else -> SyncState.SYNCED
        }
        val lastAt = maxOf(
            family?.lastBackupAt?.toDate()?.time ?: 0L,
            settings.lastLocalBackupMillis
        )
        BackupStatusInfo(
            syncState = syncState,
            isOnline = online,
            totalDocs = docs.size,
            backedUpDocs = backedUp,
            localEncryptedDocs = localCount,
            lastBackupAtMillis = lastAt,
            autoBackupEnabled = settings.autoBackupEnabled,
            backupIntervalDays = settings.backupIntervalDays
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BackupStatusInfo()
    )

    init {
        DocumentTransferManager.cleanupStaleShareFiles(appContext)
        viewModelScope.launch {
            runCatching { repository.ensureUserProfile(securitySettings.value.languageCode) }
        }

        // Recover family link automatically on secondary devices if UserProfile was initially empty
        viewModelScope.launch {
            combine(isOnline, userProfileState) { online, profileUi -> online to profileUi }
                .collect { (online, profileUi) ->
                    val profile = (profileUi as? UiState.Success)?.data
                    if (online && (profile == null || profile.familyId.isBlank())) {
                        runCatching { repository.recoverFamilyLinkIfNeeded(currentUserId) }
                    }
                }
        }

        viewModelScope.launch {
            combine(familyAccount, familyMembers) { fam, members -> fam to members }
                .collect { (fam, members) ->
                    if (fam != null && members.isNotEmpty()) {
                        repository.syncMemberUidsIfAdmin(fam, members)
                    }
                }
        }

        // Sync shared 256-bit family encryption key when online
        viewModelScope.launch {
            combine(isOnline, familyAccount) { online, fam -> online to fam }
                .collect { (online, fam) ->
                    if (online && fam != null) {
                        runCatching { repository.ensureAndSyncFamilyVaultKey(fam, cryptoManager) }
                    }
                }
        }

        // Bidirectional multi-device sync loop:
        // 1. Uploads any pending locally encrypted PDFs/images when online (auto-sync on upload & reconnect)
        // 2. Pre-caches cloud PDFs/images uploaded from other phones for offline viewing
        // 3. Removes local encrypted cache for documents deleted on another device
        viewModelScope.launch {
            combine(isOnline, familyAccount, documentsState) { online, fam, docsUi ->
                Triple(online, fam, docsUi)
            }.collect { (online, fam, docsUi) ->
                val docs = (docsUi as? UiState.Success)?.data ?: return@collect
                if (fam == null) return@collect

                val currentIds = docs.map { it.id }.toSet()
                if (online) {
                    val removedIds = observedSessionDocIds - currentIds
                    if (removedIds.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            removedIds.forEach { cryptoManager.deleteLocalEncryptedFile(it) }
                        }
                    }
                }
                observedSessionDocIds.addAll(currentIds)

                if (online) {
                    launch {
                        syncPendingWithCloud(fam, docs)
                    }
                }
            }
        }
    }

    private suspend fun syncPendingWithCloud(
        family: FamilyAccount,
        docs: List<FamilyDocument>
    ) {
        syncMutex.withLock {
            val pendingUploads = docs.filter { doc ->
                (!doc.isBackedUp || doc.storagePath.isNullOrBlank()) &&
                    !doc.isSample &&
                    !inFlightUploadDocIds.contains(doc.id) &&
                    withContext(Dispatchers.IO) { cryptoManager.hasLocalEncryptedFile(doc.id) }
            }
            val missingDownloads = docs.filter { doc ->
                !doc.storagePath.isNullOrBlank() &&
                    !inFlightDownloadDocIds.contains(doc.id) &&
                    !withContext(Dispatchers.IO) { cryptoManager.hasLocalEncryptedFile(doc.id) }
            }

            if (pendingUploads.isEmpty() && missingDownloads.isEmpty()) return

            _isSyncing.value = true
            var uploadedAny = false
            try {
                for (doc in pendingUploads) {
                    inFlightUploadDocIds.add(doc.id)
                    try {
                        val encBytes = withContext(Dispatchers.IO) {
                            cryptoManager.reencryptLegacyPayloadForFamilyIfNeeded(
                                docId = doc.id,
                                familyId = family.familyId,
                                adminUid = family.adminUid
                            )
                        }
                        if (encBytes != null) {
                            val res = repository.uploadEncryptedPayloadToStorage(
                                familyId = family.familyId,
                                docId = doc.id,
                                encryptedBytes = encBytes
                            )
                            if (res.isSuccess) uploadedAny = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-sync upload failed for ${doc.id}", e)
                    } finally {
                        inFlightUploadDocIds.remove(doc.id)
                    }
                }

                for (doc in missingDownloads) {
                    val path = doc.storagePath ?: continue
                    inFlightDownloadDocIds.add(doc.id)
                    try {
                        val cloudRes = repository.downloadEncryptedPayloadFromStorage(path)
                        cloudRes.getOrNull()?.let { encrypted ->
                            withContext(Dispatchers.IO) {
                                cryptoManager.writeRawEncryptedPayload(doc.id, encrypted)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-sync download pre-cache failed for ${doc.id}", e)
                    } finally {
                        inFlightDownloadDocIds.remove(doc.id)
                    }
                }

                if (uploadedAny) {
                    runCatching { repository.markBackupTimestamp(family.familyId) }
                    runCatching { securityPreferences.updateLastBackupMillis(System.currentTimeMillis()) }
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearBiometricUiFeedback() {
        _biometricUiFeedback.value = null
    }

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            securityPreferences.setLanguage(code)
            runCatching { repository.updateUserLanguage(code) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            securityPreferences.setThemeMode(mode)
        }
    }

    suspend fun verifyPin(pin: String): Boolean = securityPreferences.verifyPin(pin)

    fun savePin(pin: String) {
        viewModelScope.launch {
            securityPreferences.setPin(pin)
            _isVaultUnlocked.value = true
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            securityPreferences.disablePin()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setBiometricEnabled(enabled)
        }
    }

    fun handleBiometricToggleRequest(
        requestedEnabled: Boolean,
        activity: FragmentActivity?,
        strings: AppStrings,
        gateway: BiometricGateway = AndroidBiometricGateway
    ) {
        if (!requestedEnabled) {
            viewModelScope.launch {
                securityPreferences.setBiometricEnabled(false)
            }
            _biometricUiFeedback.value = BiometricUiFeedback(
                message = strings.biometricDisabledSuccessMessage,
                isError = false,
                needsEnrollmentAction = false
            )
            _statusMessage.value = strings.biometricDisabledSuccessMessage
            return
        }

        if (activity == null) {
            viewModelScope.launch {
                securityPreferences.setBiometricEnabled(false)
            }
            val fb = BiometricSupportStatus.UNKNOWN.toFeedback(strings)
            _biometricUiFeedback.value = fb
            _statusMessage.value = fb?.message ?: strings.biometricNotSupportedMessage
            return
        }

        val supportStatus = gateway.checkSupport(activity)
        if (supportStatus != BiometricSupportStatus.AVAILABLE) {
            viewModelScope.launch {
                securityPreferences.setBiometricEnabled(false)
            }
            val fb = supportStatus.toFeedback(strings)
            _biometricUiFeedback.value = fb
            _statusMessage.value = fb?.message ?: strings.biometricNotSupportedMessage
            return
        }

        _biometricUiFeedback.value = null
        isBiometricPromptActive = true
        gateway.authenticate(
            activity = activity,
            title = strings.biometricEnablePromptTitle,
            subtitle = strings.biometricEnablePromptSubtitle,
            negativeButtonText = strings.cancel,
            allowDeviceCredentialFallback = false
        ) { outcome ->
            when (outcome) {
                BiometricPromptOutcome.Succeeded -> {
                    isBiometricPromptActive = false
                    _isVaultUnlocked.value = true
                    viewModelScope.launch {
                        securityPreferences.setBiometricEnabled(true)
                    }
                    _biometricUiFeedback.value = BiometricUiFeedback(
                        message = strings.biometricEnabledSuccessMessage,
                        isError = false,
                        needsEnrollmentAction = false
                    )
                    _statusMessage.value = strings.biometricEnabledSuccessMessage
                }

                BiometricPromptOutcome.FailedAttempt -> {
                    _biometricUiFeedback.value = BiometricUiFeedback(
                        message = strings.biometricAuthFailedMessage,
                        isError = true,
                        needsEnrollmentAction = false
                    )
                    _statusMessage.value = strings.biometricAuthFailedMessage
                }

                BiometricPromptOutcome.Cancelled -> {
                    isBiometricPromptActive = false
                    viewModelScope.launch {
                        securityPreferences.setBiometricEnabled(false)
                    }
                    _biometricUiFeedback.value = BiometricUiFeedback(
                        message = strings.biometricAuthCancelledMessage,
                        isError = false,
                        needsEnrollmentAction = false
                    )
                    _statusMessage.value = strings.biometricAuthCancelledMessage
                }

                is BiometricPromptOutcome.Error -> {
                    isBiometricPromptActive = false
                    viewModelScope.launch {
                        securityPreferences.setBiometricEnabled(false)
                    }
                    _biometricUiFeedback.value = BiometricUiFeedback(
                        message = strings.biometricAuthFailedMessage,
                        isError = true,
                        needsEnrollmentAction = false
                    )
                    _statusMessage.value = strings.biometricAuthFailedMessage
                }
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setAutoBackupEnabled(enabled)
            val interval = securitySettings.value.backupIntervalDays
            VaultWorkScheduler.scheduleAutoBackup(appContext, enabled, interval)
            familyAccount.value?.let { fam ->
                if (fam.adminUid == currentUserId) {
                    repository.updateFamilyAutoBackup(fam.familyId, enabled)
                }
            }
        }
    }

    fun setBackupIntervalDays(days: Int) {
        viewModelScope.launch {
            securityPreferences.setBackupIntervalDays(days)
            val enabled = securitySettings.value.autoBackupEnabled
            VaultWorkScheduler.scheduleAutoBackup(appContext, enabled, days)
        }
    }

    fun setExpiryRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferences.setExpiryRemindersEnabled(enabled)
        }
    }

    fun checkExpiriesAndNotifyNow() {
        val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
        val count = ExpiryNotificationHelper.notifyExpiringDocuments(
            appContext,
            docs,
            securitySettings.value.languageCode
        )
        _statusMessage.value = if (count > 0) {
            "$count document(s) expiring soon or expired"
        } else {
            "All documents are up to date"
        }
    }

    fun createFamily(familyName: String, creatorName: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = repository.createFamily(
                familyName = familyName,
                creatorName = creatorName,
                preferredLanguage = securitySettings.value.languageCode
            )
            res.onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to create family" }
            _isActionBusy.value = false
        }
    }

    fun joinFamily(familyCode: String, memberName: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = repository.joinFamilyByCode(
                rawCode = familyCode,
                memberName = memberName,
                preferredLanguage = securitySettings.value.languageCode
            )
            res.onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to join family" }
            _isActionBusy.value = false
        }
    }

    fun renameFamily(newName: String) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.renameFamily(family, newName)
                .onFailure { _statusMessage.value = it.localizedMessage }
            _isActionBusy.value = false
        }
    }

    fun addOrUpdateMember(
        existingMember: FamilyMember?,
        name: String,
        relation: String,
        role: String,
        email: String,
        photoUri: Uri?
    ) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            val photoBase64 = if (photoUri != null) {
                withContext(Dispatchers.IO) { compressImageUriToBase64(photoUri) }
            } else {
                existingMember?.photoBase64
            }
            repository.addOrUpdateMember(
                family = family,
                existingMember = existingMember,
                name = name,
                relation = relation,
                role = role,
                email = email,
                photoBase64 = photoBase64
            ).onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to save member"
            }
            _isActionBusy.value = false
        }
    }

    fun deleteMember(memberId: String) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.deleteMember(family.familyId, memberId)
                .onFailure { _statusMessage.value = it.localizedMessage }
            _isActionBusy.value = false
        }
    }

    fun uploadDocumentFromUri(
        uri: Uri,
        title: String,
        categoryKey: String,
        member: FamilyMember?,
        fileTypeOverride: String? = null,
        maskedId: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long,
        onComplete: () -> Unit = {}
    ) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val docId = "doc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val resolvedName = resolveDisplayName(uri) ?: "${title.take(24)}.${fileTypeOverride ?: "pdf"}"
                val mime = appContext.contentResolver.getType(uri).orEmpty()
                val fileType = fileTypeOverride ?: when {
                    mime.contains("pdf", ignoreCase = true) || resolvedName.endsWith(".pdf", true) -> "pdf"
                    mime.contains("png", ignoreCase = true) || resolvedName.endsWith(".png", true) -> "png"
                    else -> "jpg"
                }

                val sizeBytes = withContext(Dispatchers.IO) {
                    cryptoManager.saveEncryptedFromUriForFamily(
                        docId = docId,
                        uri = uri,
                        familyId = family.familyId,
                        adminUid = family.adminUid
                    )
                }
                if (sizeBytes > MAX_CLOUD_DOCUMENT_BYTES) {
                    withContext(Dispatchers.IO) { cryptoManager.deleteLocalEncryptedFile(docId) }
                    _statusMessage.value = "File exceeds 24 MB cloud sync limit"
                    return@launch
                }

                val allowed = buildInitialAllowedUids(family, member)
                val saveRes = repository.saveDocumentMetadata(
                    family = family,
                    docId = docId,
                    title = title,
                    category = categoryKey,
                    memberId = member?.id ?: currentUserId,
                    memberName = member?.name ?: "Family",
                    allowedUids = allowed,
                    fileType = fileType,
                    fileName = resolvedName,
                    fileSizeBytes = sizeBytes,
                    storagePath = null,
                    isEncrypted = true,
                    isBackedUp = false,
                    isSample = false,
                    documentNumberMasked = maskedId,
                    notes = notes,
                    issueDateMillis = issueDateMillis,
                    expiryDateMillis = expiryDateMillis,
                    isNew = true
                )

                saveRes.onSuccess {
                    if (isOnline.value) {
                        val encBytes = withContext(Dispatchers.IO) { cryptoManager.readEncryptedPayloadBytes(docId) }
                        if (encBytes != null) {
                            val storageRes = repository.uploadEncryptedPayloadToStorage(family.familyId, docId, encBytes)
                            if (storageRes.isFailure) {
                                VaultWorkScheduler.enqueuePendingSync(appContext)
                            }
                        }
                        _statusMessage.value = "Document encrypted & synced"
                    } else {
                        VaultWorkScheduler.enqueuePendingSync(appContext)
                        _statusMessage.value = "Saved offline • Will sync when online"
                    }
                    onComplete()
                }.onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to save document"
                }
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Unable to encrypt selected file"
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun saveScannedImageFile(
        imageFile: File,
        title: String,
        categoryKey: String,
        member: FamilyMember?,
        maskedId: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long,
        onComplete: () -> Unit = {}
    ) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val docId = "doc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val bytes = withContext(Dispatchers.IO) {
                    val raw = imageFile.readBytes()
                    cryptoManager.saveEncryptedDocumentForFamily(
                        docId = docId,
                        plainBytes = raw,
                        familyId = family.familyId,
                        adminUid = family.adminUid
                    )
                    imageFile.delete()
                    raw.size.toLong()
                }
                val allowed = buildInitialAllowedUids(family, member)
                repository.saveDocumentMetadata(
                    family = family,
                    docId = docId,
                    title = title,
                    category = categoryKey,
                    memberId = member?.id ?: currentUserId,
                    memberName = member?.name ?: "Family",
                    allowedUids = allowed,
                    fileType = "jpg",
                    fileName = "scan_${docId}.jpg",
                    fileSizeBytes = bytes,
                    storagePath = null,
                    isEncrypted = true,
                    isBackedUp = false,
                    isSample = false,
                    documentNumberMasked = maskedId,
                    notes = notes,
                    issueDateMillis = issueDateMillis,
                    expiryDateMillis = expiryDateMillis,
                    isNew = true
                ).onSuccess {
                    if (isOnline.value) {
                        val encBytes = withContext(Dispatchers.IO) { cryptoManager.readEncryptedPayloadBytes(docId) }
                        if (encBytes != null) {
                            val storageRes = repository.uploadEncryptedPayloadToStorage(family.familyId, docId, encBytes)
                            if (storageRes.isFailure) {
                                VaultWorkScheduler.enqueuePendingSync(appContext)
                            }
                        }
                        _statusMessage.value = "Scan encrypted & synced"
                    } else {
                        VaultWorkScheduler.enqueuePendingSync(appContext)
                        _statusMessage.value = "Scan saved offline • Will sync when online"
                    }
                    onComplete()
                }.onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to save scan"
                }
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun loadSampleDocuments() {
        val family = familyAccount.value ?: return
        val primaryMember = familyMembers.value.firstOrNull { it.linkedUserId == currentUserId }
            ?: familyMembers.value.firstOrNull()
        val memberId = primaryMember?.id ?: currentUserId
        val memberName = primaryMember?.name ?: "Aarav Sharma"

        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                val templates = SampleDocumentFactory.templates(memberName)
                val now = System.currentTimeMillis()
                templates.forEach { tpl ->
                    val docId = "sample_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                    val plainBytes = withContext(Dispatchers.IO) {
                        val bytes = SampleDocumentFactory.renderSampleBytes(tpl, memberName)
                        cryptoManager.saveEncryptedDocumentForFamily(
                            docId = docId,
                            plainBytes = bytes,
                            familyId = family.familyId,
                            adminUid = family.adminUid
                        )
                        bytes
                    }
                    val (issueMs, expiryMs) = SampleDocumentFactory.computeTimestamps(tpl, now)
                    repository.saveDocumentMetadata(
                        family = family,
                        docId = docId,
                        title = tpl.title,
                        category = tpl.category.storageKey,
                        memberId = memberId,
                        memberName = memberName,
                        allowedUids = family.memberUids.ifEmpty { listOf(currentUserId) },
                        fileType = tpl.fileType,
                        fileName = tpl.fileName,
                        fileSizeBytes = plainBytes.size.toLong(),
                        storagePath = null,
                        isEncrypted = true,
                        isBackedUp = false,
                        isSample = true,
                        documentNumberMasked = tpl.maskedId,
                        notes = tpl.notes,
                        issueDateMillis = issueMs,
                        expiryDateMillis = expiryMs,
                        isNew = true
                    )
                }
                _statusMessage.value = "Sample family documents encrypted & loaded"
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    suspend fun loadDecryptedBytesForPreview(document: FamilyDocument): ByteArray? =
        loadDecryptedBytesWithProgress(document) { _, _ -> }

    private suspend fun loadDecryptedBytesWithProgress(
        document: FamilyDocument,
        onCloudProgress: (Long, Long) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            return@withContext null
        }
        val famId = document.familyId.ifBlank { familyAccount.value?.familyId.orEmpty() }.takeIf { it.isNotBlank() }
        val admUid = document.adminUid.ifBlank { familyAccount.value?.adminUid.orEmpty() }.takeIf { it.isNotBlank() }

        val local = runCatching { cryptoManager.readDecryptedBytes(document.id, famId, admUid) }.getOrNull()
        if (local != null) return@withContext local

        val path = document.storagePath
        if (!path.isNullOrBlank()) {
            val cloudTempDir = File(appContext.cacheDir, "cloud_transfers")
            val cloudRes = repository.downloadEncryptedPayloadWithProgress(path, cloudTempDir, onCloudProgress)
            val encrypted = cloudRes.getOrNull()
            if (encrypted != null) {
                cryptoManager.writeRawEncryptedPayload(document.id, encrypted)
                val decrypted = runCatching {
                    cryptoManager.decryptBytes(encrypted, famId, admUid)
                }.getOrNull()
                if (decrypted != null) return@withContext decrypted
            }
        }

        if (document.isSample) {
            val category = com.example.domain.model.DocumentCategory.fromKey(document.category)
            val fallbackTpl = com.example.data.sample.SampleDocumentTemplate(
                title = document.title,
                category = category,
                fileType = if (document.fileType == "pdf") "pdf" else "png",
                fileName = document.fileName,
                maskedId = document.documentNumberMasked ?: "DEMO-0000",
                notes = document.notes ?: "Sample document",
                issueOffsetDays = 0L,
                expiryOffsetDays = 0L
            )
            val generated = SampleDocumentFactory.renderSampleBytes(fallbackTpl, document.memberName)
            if (famId != null && admUid != null) {
                cryptoManager.saveEncryptedDocumentForFamily(document.id, generated, famId, admUid)
            } else {
                cryptoManager.saveEncryptedDocument(document.id, generated)
            }
            return@withContext generated
        }
        null
    }

    fun dismissTransferState() {
        _transferState.value = null
    }

    fun cleanupTemporaryShareFiles() {
        DocumentTransferManager.clearAllShareTempFiles(appContext)
    }

    fun buildAuthorizedLinkIfAllowed(document: FamilyDocument): String? {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            return null
        }
        return DocumentTransferManager.buildAuthorizedVaultLink(document)
    }

    suspend fun prepareShareSingle(document: FamilyDocument, chooserTitle: String): Result<Intent> {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            val msg = "Access denied: you do not have permission for this family document."
            _transferState.value = DocumentTransferState.Failed(msg)
            return Result.failure(SecurityException(msg))
        }
        _transferState.value = DocumentTransferState.Preparing(document.title, isShare = true)
        return try {
            val bytes = loadDecryptedBytesForPreview(document)
            if (bytes == null) {
                val msg = "Document payload is not available locally or in cloud."
                _transferState.value = DocumentTransferState.Failed(msg)
                return Result.failure(IllegalStateException(msg))
            }
            val tempFile = DocumentTransferManager.createTempDecryptedShareFile(appContext, document, bytes)
            val uri = DocumentTransferManager.getShareContentUri(appContext, tempFile)
            val intent = DocumentTransferManager.buildSingleShareIntent(appContext, document, uri, chooserTitle)
            _transferState.value = null
            Result.success(intent)
        } catch (e: CancellationException) {
            _transferState.value = null
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unable to prepare document for sharing."
            _transferState.value = DocumentTransferState.Failed(msg)
            Result.failure(e)
        }
    }

    suspend fun prepareShareMultiple(docs: List<FamilyDocument>, chooserTitle: String): Result<Intent> {
        if (docs.isEmpty()) {
            return Result.failure(IllegalArgumentException("No documents selected."))
        }
        val unauthorized = docs.firstOrNull {
            !DocumentTransferManager.verifyDocumentAccess(it, currentUserId, familyAccount.value)
        }
        if (unauthorized != null) {
            val msg = "Access denied: you do not have permission for \"${unauthorized.title}\"."
            _transferState.value = DocumentTransferState.Failed(msg)
            return Result.failure(SecurityException(msg))
        }
        _transferState.value = DocumentTransferState.Preparing("${docs.size} documents", isShare = true)
        return try {
            val uriPairs = mutableListOf<Pair<FamilyDocument, Uri>>()
            for (doc in docs) {
                val bytes = loadDecryptedBytesForPreview(doc)
                    ?: throw IllegalStateException("Payload missing for \"${doc.title}\".")
                val file = DocumentTransferManager.createTempDecryptedShareFile(appContext, doc, bytes)
                val uri = DocumentTransferManager.getShareContentUri(appContext, file)
                uriPairs.add(doc to uri)
            }
            val intent = DocumentTransferManager.buildMultiShareIntent(appContext, uriPairs, chooserTitle)
            _transferState.value = null
            Result.success(intent)
        } catch (e: CancellationException) {
            _transferState.value = null
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unable to prepare selected documents for sharing."
            _transferState.value = DocumentTransferState.Failed(msg)
            Result.failure(e)
        }
    }

    suspend fun prepareOpenWith(document: FamilyDocument, chooserTitle: String): Result<Intent> {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            val msg = "Access denied: you do not have permission for this family document."
            _transferState.value = DocumentTransferState.Failed(msg)
            return Result.failure(SecurityException(msg))
        }
        _transferState.value = DocumentTransferState.Preparing(document.title, isShare = false)
        return try {
            val bytes = loadDecryptedBytesForPreview(document)
                ?: throw IllegalStateException("Document payload is not available locally or in cloud.")
            val file = DocumentTransferManager.createTempDecryptedShareFile(appContext, document, bytes)
            val uri = DocumentTransferManager.getShareContentUri(appContext, file)
            val mime = DocumentTransferManager.resolveMimeType(document)
            val intent = DocumentTransferManager.buildOpenWithIntent(appContext, uri, mime, chooserTitle)
            _transferState.value = null
            Result.success(intent)
        } catch (e: CancellationException) {
            _transferState.value = null
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unable to open document externally."
            _transferState.value = DocumentTransferState.Failed(msg)
            Result.failure(e)
        }
    }

    fun downloadDocumentToDownloads(document: FamilyDocument) {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            _transferState.value = DocumentTransferState.Failed("Access denied for this document.")
            return
        }
        viewModelScope.launch {
            _transferState.value = DocumentTransferState.Downloading(
                docTitle = document.title,
                completedCount = 0,
                totalCount = 1,
                bytesRead = 0L,
                totalBytes = document.fileSizeBytes.coerceAtLeast(1L),
                progressPercent = 0
            )
            try {
                val bytes = loadDecryptedBytesWithProgress(document) { read, total ->
                    val safeTotal = total.coerceAtLeast(1L)
                    val pct = ((read * 60L) / safeTotal).toInt().coerceIn(0, 60)
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = document.title,
                        completedCount = 0,
                        totalCount = 1,
                        bytesRead = read,
                        totalBytes = total,
                        progressPercent = pct
                    )
                } ?: throw IllegalStateException("Encrypted payload not available locally or in cloud.")

                val safeName = DocumentTransferManager.buildSafeFileName(document)
                val mime = DocumentTransferManager.resolveMimeType(document)
                val saveRes = DocumentTransferManager.saveToDownloads(appContext, safeName, mime, bytes) { written, total ->
                    val safeTotal = total.coerceAtLeast(1L)
                    val pct = 60 + ((written * 40L) / safeTotal).toInt().coerceIn(0, 40)
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = document.title,
                        completedCount = 0,
                        totalCount = 1,
                        bytesRead = written,
                        totalBytes = safeTotal,
                        progressPercent = pct.coerceIn(0, 100)
                    )
                }
                saveRes.onSuccess { saved ->
                    _transferState.value = DocumentTransferState.Completed(
                        fileName = saved.fileName,
                        savedUri = saved.uri,
                        mimeType = saved.mimeType,
                        count = 1,
                        isBatch = false
                    )
                }.onFailure { err ->
                    _transferState.value = DocumentTransferState.Failed(err.localizedMessage ?: "Download failed.")
                }
            } catch (e: CancellationException) {
                _transferState.value = null
                throw e
            } catch (e: Exception) {
                _transferState.value = DocumentTransferState.Failed(e.localizedMessage ?: "Download failed.")
            }
        }
    }

    fun downloadDocumentToSafUri(document: FamilyDocument, targetUri: Uri) {
        if (!DocumentTransferManager.verifyDocumentAccess(document, currentUserId, familyAccount.value)) {
            _transferState.value = DocumentTransferState.Failed("Access denied for this document.")
            return
        }
        viewModelScope.launch {
            _transferState.value = DocumentTransferState.Downloading(
                docTitle = document.title,
                completedCount = 0,
                totalCount = 1,
                bytesRead = 0L,
                totalBytes = document.fileSizeBytes.coerceAtLeast(1L),
                progressPercent = 0
            )
            try {
                val bytes = loadDecryptedBytesWithProgress(document) { read, total ->
                    val safeTotal = total.coerceAtLeast(1L)
                    val pct = ((read * 60L) / safeTotal).toInt().coerceIn(0, 60)
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = document.title,
                        completedCount = 0,
                        totalCount = 1,
                        bytesRead = read,
                        totalBytes = total,
                        progressPercent = pct
                    )
                } ?: throw IllegalStateException("Encrypted payload not available locally or in cloud.")

                val writeRes = DocumentTransferManager.writeBytesToSafUri(appContext, targetUri, bytes) { written, total ->
                    val safeTotal = total.coerceAtLeast(1L)
                    val pct = 60 + ((written * 40L) / safeTotal).toInt().coerceIn(0, 40)
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = document.title,
                        completedCount = 0,
                        totalCount = 1,
                        bytesRead = written,
                        totalBytes = safeTotal,
                        progressPercent = pct.coerceIn(0, 100)
                    )
                }
                val safeName = DocumentTransferManager.buildSafeFileName(document)
                val mime = DocumentTransferManager.resolveMimeType(document)
                writeRes.onSuccess {
                    _transferState.value = DocumentTransferState.Completed(
                        fileName = safeName,
                        savedUri = targetUri,
                        mimeType = mime,
                        count = 1,
                        isBatch = false
                    )
                }.onFailure { err ->
                    _transferState.value = DocumentTransferState.Failed(err.localizedMessage ?: "Export failed.")
                }
            } catch (e: CancellationException) {
                _transferState.value = null
                throw e
            } catch (e: Exception) {
                _transferState.value = DocumentTransferState.Failed(e.localizedMessage ?: "Export failed.")
            }
        }
    }

    fun downloadMultipleToDownloads(docs: List<FamilyDocument>) {
        if (docs.isEmpty()) return
        val unauthorized = docs.firstOrNull {
            !DocumentTransferManager.verifyDocumentAccess(it, currentUserId, familyAccount.value)
        }
        if (unauthorized != null) {
            _transferState.value = DocumentTransferState.Failed("Access denied for \"${unauthorized.title}\".")
            return
        }
        viewModelScope.launch {
            try {
                var lastSaved: com.example.data.documents.SavedDocumentResult? = null
                docs.forEachIndexed { idx, doc ->
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = doc.title,
                        completedCount = idx,
                        totalCount = docs.size,
                        bytesRead = 0L,
                        totalBytes = doc.fileSizeBytes.coerceAtLeast(1L),
                        progressPercent = ((idx * 100) / docs.size).coerceIn(0, 99)
                    )
                    val bytes = loadDecryptedBytesWithProgress(doc) { _, _ -> }
                        ?: throw IllegalStateException("Encrypted payload missing for \"${doc.title}\".")
                    val safeName = DocumentTransferManager.buildSafeFileName(doc)
                    val mime = DocumentTransferManager.resolveMimeType(doc)
                    val res = DocumentTransferManager.saveToDownloads(appContext, safeName, mime, bytes) { written, total ->
                        val basePct = (idx * 100) / docs.size
                        val slicePct = ((written * 100L) / total.coerceAtLeast(1L)).toInt() / docs.size
                        _transferState.value = DocumentTransferState.Downloading(
                            docTitle = doc.title,
                            completedCount = idx,
                            totalCount = docs.size,
                            bytesRead = written,
                            totalBytes = total,
                            progressPercent = (basePct + slicePct).coerceIn(0, 100)
                        )
                    }
                    lastSaved = res.getOrThrow()
                }
                val finalSaved = lastSaved!!
                _transferState.value = DocumentTransferState.Completed(
                    fileName = finalSaved.fileName,
                    savedUri = finalSaved.uri,
                    mimeType = finalSaved.mimeType,
                    count = docs.size,
                    isBatch = true
                )
            } catch (e: CancellationException) {
                _transferState.value = null
                throw e
            } catch (e: Exception) {
                _transferState.value = DocumentTransferState.Failed(e.localizedMessage ?: "Batch download failed.")
            }
        }
    }

    fun downloadMultipleToTreeUri(docs: List<FamilyDocument>, treeUri: Uri) {
        if (docs.isEmpty()) return
        val unauthorized = docs.firstOrNull {
            !DocumentTransferManager.verifyDocumentAccess(it, currentUserId, familyAccount.value)
        }
        if (unauthorized != null) {
            _transferState.value = DocumentTransferState.Failed("Access denied for \"${unauthorized.title}\".")
            return
        }
        viewModelScope.launch {
            try {
                val prepared = mutableListOf<Triple<FamilyDocument, String, ByteArray>>()
                docs.forEachIndexed { idx, doc ->
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = doc.title,
                        completedCount = idx,
                        totalCount = docs.size,
                        bytesRead = 0L,
                        totalBytes = doc.fileSizeBytes.coerceAtLeast(1L),
                        progressPercent = ((idx * 50) / docs.size).coerceIn(0, 50)
                    )
                    val bytes = loadDecryptedBytesWithProgress(doc) { _, _ -> }
                        ?: throw IllegalStateException("Encrypted payload missing for \"${doc.title}\".")
                    prepared.add(Triple(doc, DocumentTransferManager.buildSafeFileName(doc), bytes))
                }
                val res = DocumentTransferManager.saveMultipleToTreeUri(appContext, treeUri, prepared) { idx, totalCount, doc, read, total ->
                    val pct = 50 + ((idx * 50) / totalCount) + (((read * 50L) / total.coerceAtLeast(1L)).toInt() / totalCount)
                    _transferState.value = DocumentTransferState.Downloading(
                        docTitle = doc.title,
                        completedCount = idx,
                        totalCount = totalCount,
                        bytesRead = read,
                        totalBytes = total,
                        progressPercent = pct.coerceIn(0, 100)
                    )
                }
                res.onSuccess { finalSaved ->
                    _transferState.value = DocumentTransferState.Completed(
                        fileName = finalSaved.fileName,
                        savedUri = finalSaved.uri,
                        mimeType = finalSaved.mimeType,
                        count = docs.size,
                        isBatch = true
                    )
                }.onFailure { err ->
                    _transferState.value = DocumentTransferState.Failed(err.localizedMessage ?: "Batch save failed.")
                }
            } catch (e: CancellationException) {
                _transferState.value = null
                throw e
            } catch (e: Exception) {
                _transferState.value = DocumentTransferState.Failed(e.localizedMessage ?: "Batch save failed.")
            }
        }
    }

    fun exportDocumentToUri(document: FamilyDocument, targetUri: Uri, @Suppress("UNUSED_PARAMETER") successMessage: String) {
        downloadDocumentToSafUri(document, targetUri)
    }

    suspend fun prepareShareableTempFile(document: FamilyDocument): File? {
        val bytes = loadDecryptedBytesForPreview(document) ?: return null
        return DocumentTransferManager.createTempDecryptedShareFile(appContext, document, bytes)
    }

    override fun onCleared() {
        super.onCleared()
        DocumentTransferManager.clearAllShareTempFiles(appContext)
    }

    fun renameAndUpdateDocument(
        document: FamilyDocument,
        newTitle: String,
        issueDateMillis: Long,
        expiryDateMillis: Long,
        notes: String?,
        newCategory: String? = null
    ) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.renameOrUpdateDates(
                familyId = family.familyId,
                docId = document.id,
                newTitle = newTitle,
                issueDateMillis = issueDateMillis,
                expiryDateMillis = expiryDateMillis,
                notes = notes,
                newCategory = newCategory
            ).onFailure { _statusMessage.value = it.localizedMessage }
            _isActionBusy.value = false
        }
    }

    fun updateDocumentPermissions(document: FamilyDocument, allowedUids: List<String>, successMsg: String) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            repository.updateDocumentPermissions(family, document, allowedUids)
                .onSuccess { _statusMessage.value = successMsg }
                .onFailure { _statusMessage.value = it.localizedMessage }
            _isActionBusy.value = false
        }
    }

    fun deleteDocument(document: FamilyDocument) {
        val family = familyAccount.value ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            withContext(Dispatchers.IO) {
                cryptoManager.deleteLocalEncryptedFile(document.id)
            }
            repository.deleteDocument(family.familyId, document.id, document.storagePath)
                .onFailure { _statusMessage.value = it.localizedMessage }
            _isActionBusy.value = false
        }
    }

    fun runManualBackup(completedMsg: String) {
        val family = familyAccount.value ?: return
        val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isSyncing.value = true
            var uploadedCount = 0
            var storageError: String? = null
            docs.forEach { doc ->
                val encrypted = withContext(Dispatchers.IO) {
                    runCatching {
                        cryptoManager.reencryptLegacyPayloadForFamilyIfNeeded(
                            docId = doc.id,
                            familyId = family.familyId,
                            adminUid = family.adminUid
                        )
                    }.getOrNull() ?: cryptoManager.readEncryptedPayloadBytes(doc.id)
                }
                if (encrypted != null) {
                    val res = repository.uploadEncryptedPayloadToStorage(family.familyId, doc.id, encrypted)
                    if (res.isSuccess) {
                        uploadedCount++
                    } else if (storageError == null) {
                        storageError = res.exceptionOrNull()?.localizedMessage
                    }
                }
            }
            repository.markBackupTimestamp(family.familyId)
            securityPreferences.updateLastBackupMillis(System.currentTimeMillis())
            _isSyncing.value = false
            _statusMessage.value = if (storageError != null && uploadedCount == 0) {
                "$completedMsg (Metadata synced; enable Firebase Storage bucket in Console for binary cloud sync)"
            } else {
                "$completedMsg ($uploadedCount/${docs.size})"
            }
        }
    }

    fun runCloudRestore(completedMsg: String) {
        val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isSyncing.value = true
            var restored = 0
            docs.forEach { doc ->
                if (!cryptoManager.hasLocalEncryptedFile(doc.id)) {
                    val path = doc.storagePath
                    if (!path.isNullOrBlank()) {
                        val res = repository.downloadEncryptedPayloadFromStorage(path)
                        res.getOrNull()?.let { bytes ->
                            withContext(Dispatchers.IO) {
                                cryptoManager.writeRawEncryptedPayload(doc.id, bytes)
                            }
                            restored++
                        }
                    } else if (doc.isSample) {
                        loadDecryptedBytesForPreview(doc)
                        restored++
                    }
                }
            }
            _isSyncing.value = false
            _statusMessage.value = "$completedMsg ($restored)"
        }
    }

    fun saveFamilyCloudConfig(
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
        showSampleDocs: Boolean,
        savedMessage: String
    ) {
        val family = familyAccount.value ?: return
        if (family.adminUid != currentUserId) return
        viewModelScope.launch {
            _isActionBusy.value = true
            cloudConfigRepository.saveFamilyCloudConfig(
                familyId = family.familyId,
                noticeActive = noticeActive,
                noticeTitleEn = noticeTitleEn,
                noticeTitleKn = noticeTitleKn,
                noticeBodyEn = noticeBodyEn,
                noticeBodyKn = noticeBodyKn,
                noticeLevel = noticeLevel,
                homeBannerLabelEn = homeBannerLabelEn,
                homeBannerLabelKn = homeBannerLabelKn,
                extraCategories = extraCategories,
                expiryWarningDays = expiryWarningDays,
                showSampleDocs = showSampleDocs
            ).onSuccess {
                _statusMessage.value = savedMessage
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update cloud configuration."
            }
            _isActionBusy.value = false
        }
    }

    private fun buildInitialAllowedUids(family: FamilyAccount, member: FamilyMember?): List<String> {
        val list = mutableListOf<String>()
        list.addAll(family.memberUids)
        list.add(family.adminUid)
        list.add(currentUserId)
        member?.linkedUserId?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        return list.distinct().take(25)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun compressImageUriToBase64(uri: Uri): String? {
        return runCatching {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 240
            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val scaled = Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            if (scaled != original) scaled.recycle()
            original.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun observeNetworkOnline(context: Context) = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        trySend(checkIsOnlineNow(context))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                trySend(checkIsOnlineNow(context))
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    private fun checkIsOnlineNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
        const val MAX_CLOUD_DOCUMENT_BYTES = 24L * 1024L * 1024L
    }
}
