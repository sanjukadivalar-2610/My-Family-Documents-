package com.example.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.R
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyInvite
import com.example.domain.model.FamilyMember
import com.example.domain.model.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.random.Random

class FamilyVaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage? = null,
    private val isOnlineProvider: () -> Boolean = { true }
) {
    constructor(context: Context) : this(
        db = getConfiguredFirestore(context.applicationContext),
        auth = Firebase.auth,
        storage = createStorageSafely(),
        isOnlineProvider = { isNetworkConnected(context.applicationContext) }
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(preferredLanguage: String = "en"): UserProfile {
        val uid = requireUserId()
        val user = auth.currentUser!!
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid)
                if (existing.familyId.isBlank()) {
                    val recoveredFamilyId = findExistingFamilyIdForUser(uid)
                    if (!recoveredFamilyId.isNullOrBlank()) {
                        runCatching {
                            docRef.update(
                                mapOf(
                                    "familyId" to recoveredFamilyId,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        return existing.copy(familyId = recoveredFamilyId)
                    }
                }
                existing
            } else {
                val recoveredFamilyId = findExistingFamilyIdForUser(uid).orEmpty()
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "Family Member").take(100),
                    "email" to (user.email ?: "").take(160),
                    "familyId" to recoveredFamilyId,
                    "preferredLanguage" to if (preferredLanguage == "kn") "kn" else "en",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                user.photoUrl?.toString()?.takeIf { it.length <= 500 }?.let {
                    payload["photoUrl"] = it
                }
                docRef.set(payload).await()
                docRef.get().await()
                    .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = payload["displayName"] as String,
                        familyId = recoveredFamilyId
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    /**
     * Checks Firestore for any FamilyAccount that already lists [uid] in `memberUids` and repairs
     * the `/users/{uid}.familyId` pointer if missing (e.g., after interrupted creation or multi-device login).
     */
    suspend fun recoverFamilyLinkIfNeeded(uid: String): String? {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return null
        val foundFamilyId = findExistingFamilyIdForUser(uid) ?: return null
        runCatching {
            db.collection("users").document(uid).update(
                mapOf(
                    "familyId" to foundFamilyId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        return foundFamilyId
    }

    /**
     * Fetches the member-only 256-bit family AES vault key from `/families/{familyId}/secrets/vault_key`
     * and caches it securely on the device. If missing and the caller is the Family Admin, provisions
     * a new cryptographically random 256-bit key.
     */
    suspend fun ensureAndSyncFamilyVaultKey(
        family: FamilyAccount,
        cryptoManager: com.example.data.security.DocumentCryptoManager
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val secretRef = db.collection("families").document(family.familyId)
            .collection("secrets").document("vault_key")
        return try {
            val snap = secretRef.get().await()
            if (snap.exists()) {
                val keyBase64 = snap.getString("keyBase64").orEmpty()
                if (keyBase64.isNotBlank()) {
                    cryptoManager.storeFamilyVaultKeyBase64(family.familyId, keyBase64)
                    return true
                }
            }
            if (family.adminUid == uid) {
                val newKeyBase64 = cryptoManager.generateFamilyVaultKeyBase64()
                cryptoManager.storeFamilyVaultKeyBase64(family.familyId, newKeyBase64)
                val payload = mapOf(
                    "familyId" to family.familyId,
                    "adminUid" to family.adminUid,
                    "keyBase64" to newKeyBase64,
                    "keyVersion" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitWriteOfflineSafe(secretRef.set(payload), OperationType.CREATE, secretRef.path)
                return true
            }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, secretRef.path, auth)
            false
        }
    }

    private suspend fun findExistingFamilyIdForUser(uid: String): String? {
        return runCatching {
            val querySnap = db.collection("families")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()
            querySnap.documents.firstOrNull()?.id
        }.getOrNull()
    }

    suspend fun updateUserLanguage(languageCode: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            awaitWriteOfflineSafe(
                task = docRef.update(
                    mapOf(
                        "preferredLanguage" to if (languageCode == "kn") "kn" else "en",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                operation = OperationType.UPDATE,
                path = docRef.path
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeFamilyAccount(familyId: String): Flow<FamilyAccount?> = flow {
        val uid = requireUserId()
        val path = "families/$familyId"
        emitAll(
            db.collection("families").document(familyId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(FamilyAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeFamilyMembers(familyId: String, uid: String): Flow<List<FamilyMember>> = flow {
        val path = "families/$familyId/members"
        emitAll(
            db.collection("families").document(familyId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FamilyMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observePermittedDocuments(familyId: String, uid: String): Flow<List<FamilyDocument>> = flow {
        val path = "families/$familyId/documents"
        emitAll(
            db.collection("families").document(familyId).collection("documents")
                .whereArrayContains("allowedUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FamilyDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createFamily(
        familyName: String,
        creatorName: String,
        preferredLanguage: String = "en"
    ): Result<String> {
        val uid = requireUserId()
        val user = auth.currentUser!!
        val cleanFamilyName = familyName.trim().take(80)
        val cleanCreatorName = creatorName.trim().ifBlank { user.displayName ?: "Admin" }.take(80)
        if (cleanFamilyName.length < 2) {
            return Result.failure(IllegalArgumentException("Family name must be at least 2 characters."))
        }

        val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val familyCode = generateFamilyCode()
        val familyRef = db.collection("families").document(familyId)
        val inviteRef = db.collection("family_invites").document(familyCode)
        val memberRef = familyRef.collection("members").document(uid)
        val userRef = db.collection("users").document(uid)

        return try {
            val profile = ensureUserProfile(preferredLanguage)
            if (profile.familyId.isNotBlank()) {
                return Result.success(profile.familyId)
            }

            val familyPayload = mapOf(
                "familyId" to familyId,
                "familyName" to cleanFamilyName,
                "familyCode" to familyCode,
                "adminUid" to uid,
                "memberUids" to listOf(uid),
                "totalDocuments" to 0L,
                "autoBackupEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            awaitWriteOfflineSafe(familyRef.set(familyPayload), OperationType.CREATE, familyRef.path)

            val invitePayload = mapOf(
                "familyCode" to familyCode,
                "familyId" to familyId,
                "familyName" to cleanFamilyName,
                "adminUid" to uid,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            awaitWriteOfflineSafe(inviteRef.set(invitePayload), OperationType.CREATE, inviteRef.path)

            val memberPayload = mapOf(
                "familyId" to familyId,
                "name" to cleanCreatorName,
                "relation" to "Self",
                "role" to "admin",
                "linkedUserId" to uid,
                "email" to (user.email ?: "").take(160),
                "memberUids" to listOf(uid),
                "adminUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            awaitWriteOfflineSafe(memberRef.set(memberPayload), OperationType.CREATE, memberRef.path)

            awaitWriteOfflineSafe(
                userRef.update(
                    mapOf(
                        "familyId" to familyId,
                        "displayName" to cleanCreatorName.take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                userRef.path
            )

            Result.success(familyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, familyRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun joinFamilyByCode(
        rawCode: String,
        memberName: String,
        preferredLanguage: String = "en"
    ): Result<String> {
        val uid = requireUserId()
        val user = auth.currentUser!!
        val code = rawCode.trim().uppercase()
        if (!code.matches(Regex("^[A-Za-z0-9_\\-]{6,24}$"))) {
            return Result.failure(IllegalArgumentException("Invalid Family ID format."))
        }
        val inviteRef = db.collection("family_invites").document(code)

        return try {
            ensureUserProfile(preferredLanguage)
            val inviteSnap = inviteRef.get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Family ID not found."))
            }
            val invite = inviteSnap.toObject(FamilyInvite::class.java)
                ?: return Result.failure(IllegalArgumentException("Invalid Family ID record."))
            if (!invite.active) {
                return Result.failure(IllegalStateException("This Family ID is no longer active."))
            }

            val familyRef = db.collection("families").document(invite.familyId)
            familyRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val familySnap = familyRef.get().await()
            val family = familySnap.toObject(FamilyAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Unable to load family after joining."))

            val cleanName = memberName.trim().ifBlank { user.displayName ?: "Family Member" }.take(80)
            val memberRef = familyRef.collection("members").document(uid)
            val existingMember = memberRef.get().await()
            if (!existingMember.exists()) {
                val memberPayload = mapOf(
                    "familyId" to family.familyId,
                    "name" to cleanName,
                    "relation" to "Member",
                    "role" to "member",
                    "linkedUserId" to uid,
                    "email" to (user.email ?: "").take(160),
                    "memberUids" to family.memberUids.ifEmpty { listOf(family.adminUid, uid).distinct() },
                    "adminUid" to family.adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                memberRef.set(memberPayload).await()
            }

            db.collection("users").document(uid).update(
                mapOf(
                    "familyId" to family.familyId,
                    "displayName" to cleanName.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(family.familyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, inviteRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncMemberUidsIfAdmin(family: FamilyAccount, members: List<FamilyMember>) {
        val uid = auth.currentUser?.uid ?: return
        if (family.adminUid != uid) return
        val expected = family.memberUids
        members.forEach { member ->
            if (member.id.isNotBlank() && member.memberUids.toSet() != expected.toSet()) {
                runCatching {
                    val memberRef = db.collection("families").document(family.familyId)
                        .collection("members").document(member.id)
                    awaitWriteOfflineSafe(
                        memberRef.update(
                            mapOf(
                                "memberUids" to expected,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        OperationType.UPDATE,
                        memberRef.path
                    )
                }
            }
        }
    }

    suspend fun addOrUpdateMember(
        family: FamilyAccount,
        existingMember: FamilyMember? = null,
        name: String,
        relation: String,
        role: String,
        email: String,
        photoBase64: String?
    ): Result<String> {
        requireUserId()
        val cleanName = name.trim().take(80)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Member name is required."))
        }
        val cleanRelation = relation.trim().ifBlank { "Member" }.take(40)
        val cleanRole = if (role == "admin") "admin" else "member"
        val cleanEmail = email.trim().take(160)
        val memberId = existingMember?.id?.takeIf { it.isNotBlank() }
            ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val docRef = db.collection("families").document(family.familyId)
            .collection("members").document(memberId)

        return try {
            if (existingMember == null) {
                val payload = mutableMapOf<String, Any>(
                    "familyId" to family.familyId,
                    "name" to cleanName,
                    "relation" to cleanRelation,
                    "role" to cleanRole,
                    "linkedUserId" to "",
                    "email" to cleanEmail,
                    "memberUids" to family.memberUids,
                    "adminUid" to family.adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                photoBase64?.takeIf { it.isNotBlank() && it.length <= 120_000 }?.let {
                    payload["photoBase64"] = it
                }
                awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, docRef.path)
            } else {
                val updates = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "relation" to cleanRelation,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (family.adminUid == auth.currentUser?.uid) {
                    updates["role"] = if (existingMember.linkedUserId == family.adminUid) "admin" else cleanRole
                    updates["email"] = cleanEmail
                    updates["memberUids"] = family.memberUids
                }
                photoBase64?.takeIf { it.isNotBlank() && it.length <= 120_000 }?.let {
                    updates["photoBase64"] = it
                }
                awaitWriteOfflineSafe(docRef.update(updates), OperationType.UPDATE, docRef.path)
            }
            Result.success(memberId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMember(familyId: String, memberId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("families").document(familyId)
            .collection("members").document(memberId)
        return try {
            awaitWriteOfflineSafe(docRef.delete(), OperationType.DELETE, docRef.path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveDocumentMetadata(
        family: FamilyAccount,
        docId: String,
        title: String,
        category: String,
        memberId: String,
        memberName: String,
        allowedUids: List<String>,
        fileType: String,
        fileName: String,
        fileSizeBytes: Long,
        storagePath: String?,
        isEncrypted: Boolean,
        isBackedUp: Boolean,
        isSample: Boolean,
        documentNumberMasked: String?,
        notes: String?,
        issueDateMillis: Long,
        expiryDateMillis: Long,
        isNew: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("families").document(family.familyId)
            .collection("documents").document(docId)

        val finalAllowed = (allowedUids + family.adminUid + uid).distinct().take(25)

        return try {
            if (isNew) {
                val payload = mutableMapOf<String, Any>(
                    "familyId" to family.familyId,
                    "title" to title.trim().ifBlank { "Untitled Document" }.take(120),
                    "category" to category.take(60),
                    "memberId" to memberId,
                    "memberName" to memberName.trim().ifBlank { "Family" }.take(80),
                    "uploadedByUid" to uid,
                    "adminUid" to family.adminUid,
                    "allowedUids" to finalAllowed,
                    "fileType" to fileType,
                    "fileName" to fileName.take(160),
                    "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                    "isEncrypted" to isEncrypted,
                    "isBackedUp" to isBackedUp,
                    "isSample" to isSample,
                    "issueDateMillis" to issueDateMillis.coerceAtLeast(0L),
                    "expiryDateMillis" to expiryDateMillis.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                storagePath?.takeIf { it.isNotBlank() }?.let { payload["storagePath"] = it.take(300) }
                documentNumberMasked?.takeIf { it.isNotBlank() }?.let { payload["documentNumberMasked"] = it.take(40) }
                notes?.takeIf { it.isNotBlank() }?.let { payload["notes"] = it.take(500) }

                awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, docRef.path)
                val famRef = db.collection("families").document(family.familyId)
                runCatching {
                    awaitWriteOfflineSafe(
                        famRef.update(
                            mapOf(
                                "totalDocuments" to FieldValue.increment(1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        OperationType.UPDATE,
                        famRef.path
                    )
                }
            } else {
                val updates = mutableMapOf<String, Any>(
                    "title" to title.trim().ifBlank { "Untitled Document" }.take(120),
                    "category" to category.take(60),
                    "issueDateMillis" to issueDateMillis.coerceAtLeast(0L),
                    "expiryDateMillis" to expiryDateMillis.coerceAtLeast(0L),
                    "isBackedUp" to isBackedUp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (family.adminUid == uid) {
                    updates["allowedUids"] = finalAllowed
                    updates["memberId"] = memberId
                    updates["memberName"] = memberName.trim().ifBlank { "Family" }.take(80)
                }
                storagePath?.takeIf { it.isNotBlank() }?.let { updates["storagePath"] = it.take(300) }
                documentNumberMasked?.let { updates["documentNumberMasked"] = it.take(40) }
                notes?.let { updates["notes"] = it.take(500) }
                awaitWriteOfflineSafe(docRef.update(updates), OperationType.UPDATE, docRef.path)
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateDocumentPermissions(
        family: FamilyAccount,
        document: FamilyDocument,
        allowedMemberUids: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        if (family.adminUid != uid) {
            return Result.failure(SecurityException("Only the Family Admin can change permissions."))
        }
        val docRef = db.collection("families").document(family.familyId)
            .collection("documents").document(document.id)
        val merged = (allowedMemberUids + family.adminUid + document.uploadedByUid).distinct().take(25)
        return try {
            awaitWriteOfflineSafe(
                docRef.update(
                    mapOf(
                        "allowedUids" to merged,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                docRef.path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun renameOrUpdateDates(
        familyId: String,
        docId: String,
        newTitle: String,
        issueDateMillis: Long,
        expiryDateMillis: Long,
        notes: String?,
        newCategory: String? = null
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("families").document(familyId)
            .collection("documents").document(docId)
        val updates = mutableMapOf<String, Any>(
            "title" to newTitle.trim().ifBlank { "Untitled Document" }.take(120),
            "issueDateMillis" to issueDateMillis.coerceAtLeast(0L),
            "expiryDateMillis" to expiryDateMillis.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        newCategory?.takeIf { it.isNotBlank() }?.let { updates["category"] = it.take(60) }
        notes?.let { updates["notes"] = it.take(500) }
        return try {
            awaitWriteOfflineSafe(docRef.update(updates), OperationType.UPDATE, docRef.path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(familyId: String, docId: String, storagePath: String?): Result<Unit> {
        requireUserId()
        val docRef = db.collection("families").document(familyId)
            .collection("documents").document(docId)
        return try {
            awaitWriteOfflineSafe(docRef.delete(), OperationType.DELETE, docRef.path)
            if (!storagePath.isNullOrBlank()) {
                storage?.let { s -> runCatching { s.reference.child(storagePath).delete().await() } }
            }
            runCatching {
                val famRef = db.collection("families").document(familyId)
                val snap = famRef.get().await()
                val current = snap.getLong("totalDocuments") ?: 0L
                if (current > 0L) {
                    awaitWriteOfflineSafe(
                        famRef.update(
                            mapOf(
                                "totalDocuments" to (current - 1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        OperationType.UPDATE,
                        famRef.path
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun renameFamily(family: FamilyAccount, newName: String): Result<Unit> {
        requireUserId()
        val clean = newName.trim().take(80)
        if (clean.length < 2) return Result.failure(IllegalArgumentException("Family name too short."))
        val famRef = db.collection("families").document(family.familyId)
        return try {
            awaitWriteOfflineSafe(
                famRef.update(
                    mapOf(
                        "familyName" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                famRef.path
            )
            runCatching {
                val inviteRef = db.collection("family_invites").document(family.familyCode)
                awaitWriteOfflineSafe(
                    inviteRef.update(mapOf("familyName" to clean)),
                    OperationType.UPDATE,
                    inviteRef.path
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateFamilyAutoBackup(familyId: String, enabled: Boolean): Result<Unit> {
        requireUserId()
        val famRef = db.collection("families").document(familyId)
        return try {
            awaitWriteOfflineSafe(
                famRef.update(
                    mapOf(
                        "autoBackupEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                famRef.path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun uploadEncryptedPayloadToStorage(
        familyId: String,
        docId: String,
        encryptedBytes: ByteArray
    ): Result<String> {
        requireUserId()
        val storageClient = storage
            ?: return Result.failure(IllegalStateException("Firebase Storage is not initialized."))
        val path = "families/$familyId/documents/$docId.enc"
        val docRef = db.collection("families").document(familyId)
            .collection("documents").document(docId)
        return try {
            // Ensure pending Firestore metadata writes reach the server before Storage security rules
            // evaluate firestore.get(.../families/{familyId}).
            runCatching { withTimeoutOrNull(3000L) { db.waitForPendingWrites().await() } }

            storageClient.reference.child(path).putBytes(encryptedBytes).await()
            awaitWriteOfflineSafe(
                docRef.update(
                    mapOf(
                        "storagePath" to path,
                        "isBackedUp" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                docRef.path
            )
            Result.success(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadEncryptedPayloadFromStorage(storagePath: String): Result<ByteArray> {
        requireUserId()
        val storageClient = storage
            ?: return Result.failure(IllegalStateException("Firebase Storage is not initialized."))
        return try {
            val maxDownloadBytes = 26L * 1024L * 1024L
            val bytes = storageClient.reference.child(storagePath).getBytes(maxDownloadBytes).await()
            Result.success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadEncryptedPayloadWithProgress(
        storagePath: String,
        tempDir: java.io.File,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<ByteArray> {
        requireUserId()
        val storageClient = storage
            ?: return Result.failure(IllegalStateException("Firebase Storage is not initialized."))
        val tempFile = java.io.File(tempDir, "dl_${UUID.randomUUID()}.enc.part")
        return try {
            tempDir.mkdirs()
            val task = storageClient.reference.child(storagePath).getFile(tempFile)
            task.addOnProgressListener { snap ->
                onProgress(snap.bytesTransferred, snap.totalByteCount)
            }
            task.await()
            Result.success(tempFile.readBytes())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    suspend fun markBackupTimestamp(familyId: String): Result<Unit> {
        requireUserId()
        val famRef = db.collection("families").document(familyId)
        return try {
            awaitWriteOfflineSafe(
                famRef.update(
                    mapOf(
                        "lastBackupAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                famRef.path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
            Result.failure(e)
        }
    }

    private suspend fun awaitWriteOfflineSafe(
        task: Task<Void>,
        operation: OperationType,
        path: String
    ) {
        if (!isOnlineProvider()) {
            task.addOnFailureListener { err ->
                handleFirestoreError(err, operation, path, auth)
            }
            delay(120L)
            return
        }
        val completed = withTimeoutOrNull(WRITE_SERVER_ACK_TIMEOUT_MS) {
            task.await()
            true
        }
        if (completed == null) {
            task.addOnFailureListener { err ->
                handleFirestoreError(err, operation, path, auth)
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): com.google.firebase.firestore.FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun generateFamilyCode(): String {
        val digits = Random.nextInt(1000, 9999)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val suffix = "${chars[Random.nextInt(chars.length)]}${chars[Random.nextInt(chars.length)]}"
        return "MFD-$digits-$suffix"
    }

    internal companion object {
        const val WRITE_SERVER_ACK_TIMEOUT_MS = 4000L
        val configuredDatabases: MutableSet<FirebaseFirestore> =
            Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

        internal fun getConfiguredFirestore(appContext: Context): FirebaseFirestore {
            val dbId = appContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(dbId)
            if (configuredDatabases.add(firestore)) {
                runCatching {
                    firestore.firestoreSettings = firestoreSettings {
                        setLocalCacheSettings(
                            persistentCacheSettings {
                                setSizeBytes(100L * 1024L * 1024L)
                            }
                        )
                    }
                }
            }
            return firestore
        }

        internal fun createStorageSafely(): FirebaseStorage? =
            try {
                FirebaseStorage.getInstance()
            } catch (_: Throwable) {
                null
            }

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
