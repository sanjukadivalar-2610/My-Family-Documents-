package com.example.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.Coil
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.FamilyMember
import com.example.domain.model.UserProfile
import com.example.worker.VaultWorkScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

enum class AdminDeleteAction {
    NONE,
    DELETE_ENTIRE_FAMILY,
    TRANSFER_OWNERSHIP
}

data class PendingCleanupStep(
    val stepId: String,
    val label: String,
    val detail: String
)

sealed interface AccountDeletionResult {
    data class Success(
        val completedSteps: List<String>
    ) : AccountDeletionResult

    data class RequiresReauthentication(
        val hasPasswordProvider: Boolean,
        val hasGoogleProvider: Boolean,
        val email: String,
        val reason: String
    ) : AccountDeletionResult

    data class PartialFailure(
        val completedSteps: List<String>,
        val pendingSteps: List<PendingCleanupStep>,
        val errorMessage: String
    ) : AccountDeletionResult

    data class Failed(
        val errorMessage: String
    ) : AccountDeletionResult
}

class AccountDeletionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage? = null,
    private val accountAuthRepository: AccountAuthRepository = AccountAuthRepository(auth),
    private val cryptoManager: DocumentCryptoManager,
    private val securityPreferences: SecurityPreferences,
    private val appContext: Context,
    private val isOnlineProvider: () -> Boolean = { isNetworkConnected(appContext) },
    private val storageDeleter: suspend (String) -> Result<Unit> = { path ->
        deleteStorageObjectSafely(storage, path)
    },
    private val clearFirestoreCache: suspend () -> Unit = {
        runCatching { db.terminate().await() }
        runCatching { db.clearPersistence().await() }
    },
    private val signOutHandler: suspend () -> Unit = {
        auth.signOut()
    }
) {
    constructor(context: Context) : this(
        db = FamilyVaultRepository.getConfiguredFirestore(context.applicationContext),
        auth = Firebase.auth,
        storage = FamilyVaultRepository.createStorageSafely(),
        accountAuthRepository = AccountAuthRepository(Firebase.auth),
        cryptoManager = DocumentCryptoManager(context.applicationContext),
        securityPreferences = SecurityPreferences(context.applicationContext),
        appContext = context.applicationContext
    )

    suspend fun deleteAccount(
        adminAction: AdminDeleteAction = AdminDeleteAction.NONE,
        transferTargetUid: String? = null,
        recentAuthVerifiedInSession: Boolean = false
    ): AccountDeletionResult {
        val user = auth.currentUser
            ?: return AccountDeletionResult.Failed("No authenticated user session found.")
        val uid = user.uid

        if (!isOnlineProvider()) {
            return AccountDeletionResult.Failed(
                "An active internet connection is required to permanently delete your account and cloud data."
            )
        }

        // Load latest server state before mutating anything
        val userRef = db.collection("users").document(uid)
        val userProfile = try {
            val userSnap = userRef.get(Source.SERVER).await()
            if (userSnap.exists()) userSnap.toObject(UserProfile::class.java) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path, auth)
            return AccountDeletionResult.Failed(
                e.localizedMessage ?: "Unable to verify account state from server."
            )
        }

        val resolvedFamilyId = userProfile?.familyId?.takeIf { it.isNotBlank() }
            ?: findFamilyIdFromServer(uid).orEmpty()

        val family: FamilyAccount? = if (resolvedFamilyId.isNotBlank()) {
            val famRef = db.collection("families").document(resolvedFamilyId)
            try {
                val snap = famRef.get(Source.SERVER).await()
                if (snap.exists()) snap.toObject(FamilyAccount::class.java)?.copy(familyId = snap.id) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                // If permission denied, this user is no longer a member (e.g., retry after transfer/removal)
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null
                else return AccountDeletionResult.Failed(e.localizedMessage ?: "Unable to load family state.")
            } catch (e: Exception) {
                return AccountDeletionResult.Failed(e.localizedMessage ?: "Unable to load family state.")
            }
        } else {
            null
        }

        val isAdmin = family != null && family.adminUid == uid
        if (isAdmin) {
            if (adminAction == AdminDeleteAction.NONE) {
                return AccountDeletionResult.Failed(
                    "Family Admin must either confirm permanent family deletion or transfer ownership before deleting their account."
                )
            }
            if (adminAction == AdminDeleteAction.TRANSFER_OWNERSHIP) {
                val target = transferTargetUid?.trim().orEmpty()
                if (target.isBlank() || target == uid || !family!!.memberUids.contains(target)) {
                    return AccountDeletionResult.Failed(
                        "Select a valid linked family member to transfer Admin ownership before deleting your account."
                    )
                }
            }
        } else if (adminAction != AdminDeleteAction.NONE) {
            return AccountDeletionResult.Failed(
                "Only the Family Admin can delete the family or transfer family ownership."
            )
        }

        // Enforce recent authentication BEFORE deleting any cloud records
        if (!recentAuthVerifiedInSession && !accountAuthRepository.isRecentLoginFresh()) {
            return AccountDeletionResult.RequiresReauthentication(
                hasPasswordProvider = accountAuthRepository.hasEmailPasswordProvider(user),
                hasGoogleProvider = accountAuthRepository.hasGoogleProvider(user),
                email = user.email.orEmpty(),
                reason = "Recent sign-in verification is required before permanently deleting your account."
            )
        }

        val completedSteps = mutableListOf<String>()
        val pendingSteps = mutableListOf<PendingCleanupStep>()

        when {
            family == null -> {
                deleteUserProfileStep(uid, completedSteps, pendingSteps)
            }

            isAdmin && adminAction == AdminDeleteAction.TRANSFER_OWNERSHIP -> {
                executeAdminTransferOwnership(
                    family = family,
                    oldAdminUid = uid,
                    newAdminUid = transferTargetUid!!.trim(),
                    completedSteps = completedSteps,
                    pendingSteps = pendingSteps
                )
                if (pendingSteps.isEmpty()) {
                    deleteUserProfileStep(uid, completedSteps, pendingSteps)
                }
            }

            isAdmin && adminAction == AdminDeleteAction.DELETE_ENTIRE_FAMILY -> {
                executeAdminDeleteEntireFamily(
                    family = family,
                    adminUid = uid,
                    completedSteps = completedSteps,
                    pendingSteps = pendingSteps
                )
                if (pendingSteps.isEmpty()) {
                    deleteUserProfileStep(uid, completedSteps, pendingSteps)
                }
            }

            else -> {
                // Normal family member: delete ONLY own uploaded files/docs & self-membership;
                // never delete shared docs uploaded by others or other members' accounts.
                executeMemberLeaveAndCleanup(
                    family = family,
                    memberUid = uid,
                    completedSteps = completedSteps,
                    pendingSteps = pendingSteps
                )
                if (pendingSteps.isEmpty()) {
                    deleteUserProfileStep(uid, completedSteps, pendingSteps)
                }
            }
        }

        // Never proceed to delete Firebase Auth or report success if any cloud data remains
        if (pendingSteps.isNotEmpty()) {
            return AccountDeletionResult.PartialFailure(
                completedSteps = completedSteps.toList(),
                pendingSteps = pendingSteps.toList(),
                errorMessage = pendingSteps.first().detail
            )
        }

        // Permanently delete Firebase Authentication account
        val authRes = accountAuthRepository.deleteCurrentFirebaseAuthUser()
        if (authRes.isFailure) {
            val err = authRes.exceptionOrNull()
            if ((err as? AccountAuthException)?.code == "REQUIRES_RECENT_LOGIN") {
                return AccountDeletionResult.RequiresReauthentication(
                    hasPasswordProvider = accountAuthRepository.hasEmailPasswordProvider(auth.currentUser),
                    hasGoogleProvider = accountAuthRepository.hasGoogleProvider(auth.currentUser),
                    email = auth.currentUser?.email.orEmpty(),
                    reason = err.message
                )
            }
            val msg = err?.localizedMessage ?: "Failed to delete Firebase Authentication account."
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "firebase_auth_account",
                    label = "Delete Firebase Authentication account",
                    detail = msg
                )
            )
            return AccountDeletionResult.PartialFailure(
                completedSteps = completedSteps.toList(),
                pendingSteps = pendingSteps.toList(),
                errorMessage = msg
            )
        }
        completedSteps.add("Delete Firebase Authentication account")

        // Clear all local encrypted documents, keys, preferences, workers, and caches
        val localError = clearLocalDeviceData()
        if (localError != null) {
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "local_device_cleanup",
                    label = "Clear local vault cache on this device",
                    detail = localError
                )
            )
            return AccountDeletionResult.PartialFailure(
                completedSteps = completedSteps.toList(),
                pendingSteps = pendingSteps.toList(),
                errorMessage = localError
            )
        }
        completedSteps.add("Clear local vault data & cached documents")

        withContext(NonCancellable) {
            runCatching { signOutHandler() }
        }
        completedSteps.add("Sign out of session")

        return AccountDeletionResult.Success(completedSteps = completedSteps.toList())
    }

    private suspend fun executeMemberLeaveAndCleanup(
        family: FamilyAccount,
        memberUid: String,
        completedSteps: MutableList<String>,
        pendingSteps: MutableList<PendingCleanupStep>
    ) {
        val famRef = db.collection("families").document(family.familyId)

        // 1. Query only documents visible to this member
        val visibleDocs = try {
            famRef.collection("documents")
                .whereArrayContains("allowedUids", memberUid)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { snap ->
                    snap.toObject(FamilyDocument::class.java)?.copy(id = snap.id)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/documents", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "query_member_docs",
                    label = "Load your uploaded documents",
                    detail = e.localizedMessage ?: "Unable to query documents."
                )
            )
            return
        }

        val ownedDocs = visibleDocs.filter { it.uploadedByUid == memberUid }
        val sharedOtherDocs = visibleDocs.filter { it.uploadedByUid != memberUid && it.allowedUids.contains(memberUid) }

        // 2. Delete Storage file FIRST for each doc uploaded by this member, then Firestore metadata
        var ownedDeletedCount = 0
        for (doc in ownedDocs) {
            val storagePath = doc.storagePath
            var storageCleaned = true
            if (!storagePath.isNullOrBlank()) {
                val storageRes = storageDeleter(storagePath)
                if (storageRes.isFailure) {
                    storageCleaned = false
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "storage_${doc.id}",
                            label = "Delete Storage file for \"${doc.title}\"",
                            detail = storageRes.exceptionOrNull()?.localizedMessage
                                ?: "Failed to delete $storagePath from Firebase Storage."
                        )
                    )
                }
            }
            if (storageCleaned) {
                val docRef = famRef.collection("documents").document(doc.id)
                try {
                    docRef.delete().await()
                    ownedDeletedCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "firestore_doc_${doc.id}",
                            label = "Delete document record \"${doc.title}\"",
                            detail = e.localizedMessage ?: "Failed to delete Firestore document."
                        )
                    )
                }
            } else {
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "firestore_doc_${doc.id}",
                        label = "Delete document record \"${doc.title}\"",
                        detail = "Waiting for encrypted Storage file deletion first."
                    )
                )
            }
        }
        if (ownedDocs.isNotEmpty() && pendingSteps.isEmpty()) {
            completedSteps.add("Delete $ownedDeletedCount personal uploaded document(s) & Storage file(s)")
        }

        // Stop before altering permissions/membership if own file cleanup failed
        if (pendingSteps.isNotEmpty()) return

        // 3. Remove only self from shared documents uploaded by other family members
        for (doc in sharedOtherDocs) {
            val docRef = famRef.collection("documents").document(doc.id)
            try {
                docRef.update(
                    mapOf(
                        "allowedUids" to FieldValue.arrayRemove(memberUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "unshare_doc_${doc.id}",
                        label = "Remove your access from shared doc \"${doc.title}\"",
                        detail = e.localizedMessage ?: "Failed to update shared document access."
                    )
                )
            }
        }
        if (sharedOtherDocs.isNotEmpty() && pendingSteps.isEmpty()) {
            completedSteps.add("Revoke self access from ${sharedOtherDocs.size} shared family document(s)")
        }

        if (pendingSteps.isNotEmpty()) return

        // Best-effort update to family totalDocuments if own docs were deleted
        if (ownedDeletedCount > 0) {
            runCatching {
                val updatedCount = (family.totalDocuments - ownedDeletedCount).coerceAtLeast(0L)
                famRef.update(
                    mapOf(
                        "totalDocuments" to updatedCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        // 4. Delete ONLY member's own linked member profile(s) in family
        val memberSnaps = try {
            famRef.collection("members")
                .whereArrayContains("memberUids", memberUid)
                .get(Source.SERVER)
                .await()
                .documents
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/members", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "query_own_member_doc",
                    label = "Locate your family member profile",
                    detail = e.localizedMessage ?: "Failed to query family member profiles."
                )
            )
            return
        }

        val ownMemberDocs = memberSnaps.filter { snap ->
            val m = snap.toObject(FamilyMember::class.java)
            m != null && m.linkedUserId == memberUid && m.adminUid != memberUid
        }
        for (snap in ownMemberDocs) {
            try {
                snap.reference.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, snap.reference.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "delete_member_${snap.id}",
                        label = "Delete your family member card",
                        detail = e.localizedMessage ?: "Failed to delete member profile."
                    )
                )
            }
        }
        if (ownMemberDocs.isNotEmpty() && pendingSteps.isEmpty()) {
            completedSteps.add("Delete your member profile from family")
        }

        if (pendingSteps.isNotEmpty()) return

        // 5. Remove ONLY self UID from families/{familyId}.memberUids
        if (family.memberUids.contains(memberUid)) {
            try {
                famRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayRemove(memberUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                completedSteps.add("Leave family membership list")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "leave_family_member_uids",
                        label = "Remove account from family membership",
                        detail = e.localizedMessage ?: "Failed to update family membership."
                    )
                )
            }
        }
    }

    private suspend fun executeAdminDeleteEntireFamily(
        family: FamilyAccount,
        adminUid: String,
        completedSteps: MutableList<String>,
        pendingSteps: MutableList<PendingCleanupStep>
    ) {
        val famRef = db.collection("families").document(family.familyId)

        // 1. Load all family documents owned by Admin
        val docs = try {
            famRef.collection("documents")
                .whereEqualTo("adminUid", adminUid)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { snap -> snap.toObject(FamilyDocument::class.java)?.copy(id = snap.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/documents", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "query_family_docs",
                    label = "Load family documents for deletion",
                    detail = e.localizedMessage ?: "Unable to list family documents."
                )
            )
            return
        }

        // 2. Delete ALL Storage files FIRST while Firestore metadata & rules references exist
        val storageFailedDocIds = mutableSetOf<String>()
        var storageDeletedCount = 0
        for (doc in docs) {
            val path = doc.storagePath
            if (!path.isNullOrBlank()) {
                val res = storageDeleter(path)
                if (res.isFailure) {
                    storageFailedDocIds.add(doc.id)
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "storage_${doc.id}",
                            label = "Delete Storage file for \"${doc.title}\"",
                            detail = res.exceptionOrNull()?.localizedMessage
                                ?: "Failed to delete $path from Firebase Storage."
                        )
                    )
                } else {
                    storageDeletedCount++
                }
            }
        }
        if (storageFailedDocIds.isNotEmpty()) {
            // Do NOT delete Firestore metadata/family so retry keeps rule authorization intact
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "pending_family_cleanup",
                    label = "Complete family & Firestore record deletion",
                    detail = "Waiting for Storage file cleanup before removing family records."
                )
            )
            return
        }
        if (storageDeletedCount > 0) {
            completedSteps.add("Delete $storageDeletedCount encrypted file(s) from Firebase Storage")
        }

        // 3. Delete all Firestore document records
        var deletedDocs = 0
        for (doc in docs) {
            val docRef = famRef.collection("documents").document(doc.id)
            try {
                docRef.delete().await()
                deletedDocs++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "doc_${doc.id}",
                        label = "Delete document \"${doc.title}\"",
                        detail = e.localizedMessage ?: "Failed to delete document record."
                    )
                )
            }
        }
        if (deletedDocs > 0 && pendingSteps.isEmpty()) {
            completedSteps.add("Delete $deletedDocs family document record(s)")
        }
        if (pendingSteps.isNotEmpty()) return

        // 4. Delete all member records inside the family subcollection (does NOT touch other members' /users or Auth accounts)
        val memberSnaps = try {
            famRef.collection("members")
                .whereEqualTo("adminUid", adminUid)
                .get(Source.SERVER)
                .await()
                .documents
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/members", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "query_family_members",
                    label = "Load family member profiles",
                    detail = e.localizedMessage ?: "Unable to query family member records."
                )
            )
            return
        }
        for (snap in memberSnaps) {
            try {
                snap.reference.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, snap.reference.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "member_${snap.id}",
                        label = "Delete family member card (${snap.id})",
                        detail = e.localizedMessage ?: "Failed to delete family member record."
                    )
                )
            }
        }
        if (memberSnaps.isNotEmpty() && pendingSteps.isEmpty()) {
            completedSteps.add("Remove ${memberSnaps.size} family member card(s)")
        }
        if (pendingSteps.isNotEmpty()) return

        // 5. Delete shared encryption vault_key secret BEFORE deleting families/{familyId}
        val secretRef = famRef.collection("secrets").document("vault_key")
        try {
            val secretSnap = secretRef.get(Source.SERVER).await()
            if (secretSnap.exists()) {
                secretRef.delete().await()
                completedSteps.add("Delete family encryption key secret")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, secretRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "secret_vault_key",
                    label = "Delete family encryption secret",
                    detail = e.localizedMessage ?: "Failed to delete vault_key secret."
                )
            )
            return
        }

        // 6. Delete family invite code record
        if (family.familyCode.isNotBlank()) {
            val inviteRef = db.collection("family_invites").document(family.familyCode)
            try {
                val inviteSnap = inviteRef.get(Source.SERVER).await()
                if (inviteSnap.exists()) {
                    inviteRef.delete().await()
                    completedSteps.add("Deactivate & delete Family ID invite")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, inviteRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "family_invite",
                        label = "Delete Family ID invite (${family.familyCode})",
                        detail = e.localizedMessage ?: "Failed to delete Family ID invite."
                    )
                )
                return
            }
        }

        // 7. Delete the family account document
        try {
            famRef.delete().await()
            completedSteps.add("Delete family vault (${family.familyName})")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, famRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "family_doc",
                    label = "Delete family vault record",
                    detail = e.localizedMessage ?: "Failed to delete family record."
                )
            )
        }
    }

    private suspend fun executeAdminTransferOwnership(
        family: FamilyAccount,
        oldAdminUid: String,
        newAdminUid: String,
        completedSteps: MutableList<String>,
        pendingSteps: MutableList<PendingCleanupStep>
    ) {
        val famRef = db.collection("families").document(family.familyId)

        // Ensure v3 vault_key exists & upgrade any locally cached legacy v2 docs before adminUid changes
        val vaultRepo = FamilyVaultRepository(
            db = db,
            auth = auth,
            storage = storage,
            isOnlineProvider = isOnlineProvider
        )
        runCatching { vaultRepo.ensureAndSyncFamilyVaultKey(family, cryptoManager) }

        val docs = try {
            famRef.collection("documents")
                .whereEqualTo("adminUid", oldAdminUid)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { snap -> snap.toObject(FamilyDocument::class.java)?.copy(id = snap.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/documents", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "transfer_load_docs",
                    label = "Load family documents for ownership transfer",
                    detail = e.localizedMessage ?: "Failed to list documents."
                )
            )
            return
        }

        val memberSnaps = try {
            famRef.collection("members")
                .whereEqualTo("adminUid", oldAdminUid)
                .get(Source.SERVER)
                .await()
                .documents
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${famRef.path}/members", auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "transfer_load_members",
                    label = "Load family members for ownership transfer",
                    detail = e.localizedMessage ?: "Failed to list family members."
                )
            )
            return
        }

        // Upgrade any v2 encrypted payloads to v3 before changing adminUid so new admin can decrypt all docs
        if (cryptoManager.hasFamilyVaultKey(family.familyId)) {
            for (doc in docs) {
                runCatching {
                    val upgraded = cryptoManager.reencryptLegacyPayloadForFamilyIfNeeded(
                        docId = doc.id,
                        familyId = family.familyId,
                        adminUid = oldAdminUid
                    )
                    if (upgraded != null && !doc.storagePath.isNullOrBlank() && storage != null) {
                        storage.reference.child(doc.storagePath).putBytes(upgraded).await()
                    }
                }
            }
        }

        // 1. Transfer secrets/vault_key adminUid while oldAdminUid is still Family Admin
        val secretRef = famRef.collection("secrets").document("vault_key")
        try {
            val secretSnap = secretRef.get(Source.SERVER).await()
            if (secretSnap.exists()) {
                secretRef.update(
                    mapOf(
                        "adminUid" to newAdminUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, secretRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "transfer_vault_key",
                    label = "Transfer family encryption secret ownership",
                    detail = e.localizedMessage ?: "Failed to update vault_key ownership."
                )
            )
            return
        }

        // 2. Transfer family_invites adminUid
        if (family.familyCode.isNotBlank()) {
            val inviteRef = db.collection("family_invites").document(family.familyCode)
            try {
                val inviteSnap = inviteRef.get(Source.SERVER).await()
                if (inviteSnap.exists()) {
                    inviteRef.update(mapOf("adminUid" to newAdminUid)).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, inviteRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "transfer_invite",
                        label = "Transfer Family ID invite ownership",
                        detail = e.localizedMessage ?: "Failed to update invite ownership."
                    )
                )
                return
            }
        }

        // 3. Update remaining member cards (promote newAdminUid to admin) & delete outgoing admin's own card
        val remainingMemberUids = (family.memberUids - oldAdminUid + newAdminUid).distinct()
        for (snap in memberSnaps) {
            val member = snap.toObject(FamilyMember::class.java) ?: continue
            if (member.linkedUserId == oldAdminUid) {
                try {
                    snap.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.DELETE, snap.reference.path, auth)
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "transfer_delete_old_admin_member",
                            label = "Remove outgoing admin member card",
                            detail = e.localizedMessage ?: "Failed to delete outgoing admin member profile."
                        )
                    )
                }
            } else {
                val updatedRole = if (member.linkedUserId == newAdminUid) "admin" else member.role
                try {
                    snap.reference.update(
                        mapOf(
                            "adminUid" to newAdminUid,
                            "role" to updatedRole,
                            "memberUids" to remainingMemberUids,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, snap.reference.path, auth)
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "transfer_member_${snap.id}",
                            label = "Transfer member record (${member.name})",
                            detail = e.localizedMessage ?: "Failed to update member ownership."
                        )
                    )
                }
            }
        }
        if (pendingSteps.isNotEmpty()) return

        // 4. Update all family documents' adminUid -> newAdminUid, ensuring newAdminUid in allowedUids
        for (doc in docs) {
            val updatedAllowed = (doc.allowedUids - oldAdminUid + newAdminUid).distinct().take(25)
            val docRef = famRef.collection("documents").document(doc.id)
            try {
                docRef.update(
                    mapOf(
                        "adminUid" to newAdminUid,
                        "allowedUids" to updatedAllowed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                pendingSteps.add(
                    PendingCleanupStep(
                        stepId = "transfer_doc_${doc.id}",
                        label = "Transfer ownership of \"${doc.title}\"",
                        detail = e.localizedMessage ?: "Failed to update document ownership."
                    )
                )
            }
        }
        if (pendingSteps.isNotEmpty()) return

        // 5. Switch families/{familyId}.adminUid -> newAdminUid
        try {
            famRef.update(
                mapOf(
                    "adminUid" to newAdminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "transfer_family_admin",
                    label = "Transfer family Admin ownership",
                    detail = e.localizedMessage ?: "Failed to transfer family ownership."
                )
            )
            return
        }

        // 6. Remove outgoing admin UID from families/{familyId}.memberUids
        try {
            famRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(oldAdminUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, famRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "transfer_leave_member_uids",
                    label = "Remove previous admin from family membership",
                    detail = e.localizedMessage ?: "Failed to remove previous admin UID."
                )
            )
            return
        }

        completedSteps.add("Transfer Family Admin ownership to new owner & leave family")
    }

    private suspend fun deleteUserProfileStep(
        uid: String,
        completedSteps: MutableList<String>,
        pendingSteps: MutableList<PendingCleanupStep>
    ) {
        val userRef = db.collection("users").document(uid)
        try {
            val snap = userRef.get(Source.SERVER).await()
            if (snap.exists()) {
                userRef.delete().await()
                val after = runCatching { userRef.get(Source.SERVER).await() }.getOrNull()
                if (after?.exists() == true) {
                    pendingSteps.add(
                        PendingCleanupStep(
                            stepId = "user_profile",
                            label = "Delete user profile record",
                            detail = "User profile still exists after deletion attempt."
                        )
                    )
                    return
                }
            }
            completedSteps.add("Delete personal user profile")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path, auth)
            pendingSteps.add(
                PendingCleanupStep(
                    stepId = "user_profile",
                    label = "Delete personal user profile",
                    detail = e.localizedMessage ?: "Failed to delete Firestore user profile."
                )
            )
        }
    }

    private suspend fun findFamilyIdFromServer(uid: String): String? {
        return runCatching {
            val snap = db.collection("families")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get(Source.SERVER)
                .await()
            snap.documents.firstOrNull()?.id
        }.getOrNull()
    }

    private suspend fun clearLocalDeviceData(): String? = withContext(NonCancellable + Dispatchers.IO) {
        try {
            VaultWorkScheduler.cancelAll(appContext)
            cryptoManager.clearAllLocalVaultData()
            securityPreferences.clearAccountLocalData()
            runCatching {
                val imageCacheDir = File(appContext.cacheDir, "image_cache")
                if (imageCacheDir.exists()) imageCacheDir.deleteRecursively()
                Coil.imageLoader(appContext).memoryCache?.clear()
            }
            clearFirestoreCache()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to clear local encrypted files and cached documents."
        }
    }

    companion object {
        private suspend fun deleteStorageObjectSafely(
            storage: FirebaseStorage?,
            storagePath: String
        ): Result<Unit> {
            val client = storage
                ?: return Result.failure(
                    IllegalStateException("Firebase Storage unavailable to delete $storagePath")
                )
            return try {
                client.reference.child(storagePath).delete().await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                    Result.success(Unit)
                } else {
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
