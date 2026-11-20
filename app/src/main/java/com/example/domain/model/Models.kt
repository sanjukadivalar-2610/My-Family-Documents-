package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val familyId: String = "",
    val preferredLanguage: String = "en",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.ifBlank { "Family Member" },
        "email" to email,
        "photoUrl" to photoUrl,
        "familyId" to familyId,
        "preferredLanguage" to preferredLanguage,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@IgnoreExtraProperties
data class FamilyAccount(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val familyCode: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    val totalDocuments: Long = 0L,
    val lastBackupAt: Timestamp? = null,
    val autoBackupEnabled: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyInvite(
    @DocumentId val id: String = "",
    val familyCode: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val adminUid: String = "",
    val active: Boolean = true,
    val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMember(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val name: String = "",
    val relation: String = "Member",
    val role: String = "member",
    val linkedUserId: String = "",
    val email: String = "",
    val photoBase64: String? = null,
    val memberUids: List<String> = emptyList(),
    val adminUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isAdmin: Boolean get() = role == "admin"
}

@IgnoreExtraProperties
data class FamilyDocument(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val title: String = "",
    val category: String = DocumentCategory.OTHER.storageKey,
    val memberId: String = "",
    val memberName: String = "",
    val uploadedByUid: String = "",
    val adminUid: String = "",
    val allowedUids: List<String> = emptyList(),
    val fileType: String = "pdf",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val storagePath: String? = null,
    val isEncrypted: Boolean = true,
    val isBackedUp: Boolean = false,
    val isSample: Boolean = false,
    val documentNumberMasked: String? = null,
    val notes: String? = null,
    val issueDateMillis: Long = 0L,
    val expiryDateMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun expiryStatus(
        nowMillis: Long = System.currentTimeMillis(),
        warningDays: Long = 30L
    ): ExpiryStatus {
        if (expiryDateMillis <= 0L) return ExpiryStatus.NO_EXPIRY
        val daysLeft = (expiryDateMillis - nowMillis) / MILLIS_PER_DAY
        return when {
            expiryDateMillis < nowMillis -> ExpiryStatus.EXPIRED
            daysLeft <= warningDays.coerceIn(1L, 365L) -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.VALID
        }
    }

    fun daysUntilExpiry(nowMillis: Long = System.currentTimeMillis()): Long {
        if (expiryDateMillis <= 0L) return Long.MAX_VALUE
        return (expiryDateMillis - nowMillis) / MILLIS_PER_DAY
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}

enum class ExpiryStatus {
    NO_EXPIRY,
    VALID,
    EXPIRING_SOON,
    EXPIRED
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SyncState {
    SYNCED,
    SYNCING,
    OFFLINE,
    ATTENTION_NEEDED
}

data class BackupStatusInfo(
    val syncState: SyncState = SyncState.SYNCED,
    val isOnline: Boolean = true,
    val totalDocs: Int = 0,
    val backedUpDocs: Int = 0,
    val localEncryptedDocs: Int = 0,
    val lastBackupAtMillis: Long = 0L,
    val autoBackupEnabled: Boolean = true,
    val backupIntervalDays: Int = 1,
    val message: String? = null
)
