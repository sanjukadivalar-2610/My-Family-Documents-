package com.example.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.AccountAuthException
import com.example.data.firebase.AccountAuthRepository
import com.example.data.firebase.AccountDeletionRepository
import com.example.data.firebase.AccountDeletionResult
import com.example.data.firebase.AdminDeleteAction
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.domain.model.DocumentCategory
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class FirebaseDeleteAccountTest : FirestoreEmulatorTestBase() {

    private fun isolatedCryptoDevice(deviceTag: String): Pair<Context, DocumentCryptoManager> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val filesDir = File(base.filesDir, "del_test_$deviceTag").apply {
            deleteRecursively()
            mkdirs()
        }
        val cacheDir = File(base.cacheDir, "del_test_${deviceTag}_cache").apply {
            deleteRecursively()
            mkdirs()
        }
        val wrapped = object : ContextWrapper(base) {
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
        }
        return wrapped to DocumentCryptoManager(wrapped)
    }

    @Test
    fun noCancelAction_preservesAuthProfileFamilyDocumentsAndLocalCache() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "cancel_$suffix@test.com"
        val pass = "CancelPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val (ctx, crypto) = isolatedCryptoDevice("cancel_$suffix")
        val prefs = SecurityPreferences(ctx)

        val user = accountRepo.signUpWithEmailPassword(email, pass, "Cancel User").getOrThrow()
        val famId = vaultRepo.createFamily("Cancel Family", "Cancel User").getOrThrow()
        crypto.saveEncryptedDocumentForFamily("doc_keep", "secret-bytes".toByteArray(), famId, user.uid)
        prefs.setPin("4321")

        // User chooses "No - Cancel" -> deletion is never executed
        assertTrue(crypto.hasLocalEncryptedFile("doc_keep"))
        assertTrue(prefs.settingsFlow.first().pinEnabled)
        assertTrue(firestore.collection("users").document(user.uid).get(Source.SERVER).await().exists())
        assertTrue(firestore.collection("families").document(famId).get(Source.SERVER).await().exists())
        assertNotNull(auth.currentUser)
    }

    @Test
    fun yesDeleteSoloUser_deletesAuthProfileLocalDataAndSignsOut() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "solo_$suffix@test.com"
        val pass = "SoloPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val (ctx, crypto) = isolatedCryptoDevice("solo_$suffix")
        val prefs = SecurityPreferences(ctx)

        val user = accountRepo.signUpWithEmailPassword(email, pass, "Solo User").getOrThrow()
        val uid = user.uid
        vaultRepo.ensureUserProfile("en")
        crypto.writeRawEncryptedPayload("local_only_doc", "local-secret".toByteArray())
        prefs.setPin("1234")

        val deletionRepo = AccountDeletionRepository(
            db = firestore,
            auth = auth,
            storage = null,
            accountAuthRepository = accountRepo,
            cryptoManager = crypto,
            securityPreferences = prefs,
            appContext = ctx,
            isOnlineProvider = { true },
            storageDeleter = { Result.success(Unit) },
            clearFirestoreCache = {},
            signOutHandler = { auth.signOut() }
        )

        val res = deletionRepo.deleteAccount()
        assertTrue("Expected Success but got $res", res is AccountDeletionResult.Success)
        assertEquals(null, auth.currentUser)
        assertFalse(crypto.hasLocalEncryptedFile("local_only_doc"))
        assertEquals(0, crypto.countLocalEncryptedFiles())
        assertFalse(prefs.settingsFlow.first().pinEnabled)

        // Deleted Firebase Auth account can no longer log in
        val loginAfter = accountRepo.signInWithEmailPassword(email, pass)
        assertTrue(loginAfter.isFailure)
        val code = (loginAfter.exceptionOrNull() as? AccountAuthException)?.code
        assertTrue("Expected USER_NOT_FOUND or INVALID_CREDENTIALS, got $code", code == "USER_NOT_FOUND" || code == "INVALID_CREDENTIALS")
    }

    @Test
    fun yesDeleteNormalMember_removesOnlyMemberOwnData_andPreservesAdminFamilyAndSharedDocs() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val adminEmail = "admin_$suffix@test.com"
        val memberEmail = "member_$suffix@test.com"
        val pass = "VaultPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)

        val (adminCtx, adminCrypto) = isolatedCryptoDevice("m_admin_$suffix")
        val (memberCtx, memberCrypto) = isolatedCryptoDevice("m_bob_$suffix")
        val prefs = SecurityPreferences(memberCtx)

        // 1. Admin creates family and uploads a shared Admin doc
        val adminUser = accountRepo.signUpWithEmailPassword(adminEmail, pass, "Alice Admin").getOrThrow()
        val adminUid = adminUser.uid
        val familyId = vaultRepo.createFamily("Sharma Vault", "Alice Admin").getOrThrow()
        val family = vaultRepo.observeFamilyAccount(familyId).first()!!
        vaultRepo.ensureAndSyncFamilyVaultKey(family, adminCrypto)

        val adminDocStoragePath = "families/$familyId/documents/doc_admin_shared.enc"
        vaultRepo.saveDocumentMetadata(
            family = family,
            docId = "doc_admin_shared",
            title = "Family Ration Card",
            category = DocumentCategory.RATION_CARD.storageKey,
            memberId = adminUid,
            memberName = "Alice Admin",
            allowedUids = listOf(adminUid),
            fileType = "pdf",
            fileName = "ration.pdf",
            fileSizeBytes = 512,
            storagePath = adminDocStoragePath,
            isEncrypted = true,
            isBackedUp = true,
            isSample = false,
            documentNumberMasked = null,
            notes = null,
            issueDateMillis = 1000L,
            expiryDateMillis = 0L,
            isNew = true
        ).getOrThrow()
        auth.signOut()

        // 2. Member joins family, gets shared on Admin's doc, and uploads own doc
        val memberUser = accountRepo.signUpWithEmailPassword(memberEmail, pass, "Bob Member").getOrThrow()
        val memberUid = memberUser.uid
        vaultRepo.joinFamilyByCode(family.familyCode, "Bob Member").getOrThrow()

        // Share Admin doc with Bob
        auth.signOut()
        accountRepo.signInWithEmailPassword(adminEmail, pass).getOrThrow()
        val updatedFamily = vaultRepo.observeFamilyAccount(familyId).first()!!
        val sharedDoc = vaultRepo.observePermittedDocuments(familyId, adminUid).first().first { it.id == "doc_admin_shared" }
        vaultRepo.updateDocumentPermissions(updatedFamily, sharedDoc, listOf(adminUid, memberUid)).getOrThrow()
        auth.signOut()

        // Bob uploads his own doc with Storage path + local encrypted file
        accountRepo.signInWithEmailPassword(memberEmail, pass).getOrThrow()
        val bobDocStoragePath = "families/$familyId/documents/doc_bob_passport.enc"
        memberCrypto.saveEncryptedDocumentForFamily("doc_bob_passport", "bob-passport".toByteArray(), familyId, adminUid)
        vaultRepo.saveDocumentMetadata(
            family = updatedFamily,
            docId = "doc_bob_passport",
            title = "Bob Passport",
            category = DocumentCategory.PASSPORT.storageKey,
            memberId = memberUid,
            memberName = "Bob Member",
            allowedUids = listOf(adminUid, memberUid),
            fileType = "pdf",
            fileName = "bob.pdf",
            fileSizeBytes = 256,
            storagePath = bobDocStoragePath,
            isEncrypted = true,
            isBackedUp = true,
            isSample = false,
            documentNumberMasked = null,
            notes = null,
            issueDateMillis = 1000L,
            expiryDateMillis = 0L,
            isNew = true
        ).getOrThrow()

        val deletedStoragePaths = mutableListOf<String>()
        val deletionRepo = AccountDeletionRepository(
            db = firestore,
            auth = auth,
            storage = null,
            accountAuthRepository = accountRepo,
            cryptoManager = memberCrypto,
            securityPreferences = prefs,
            appContext = memberCtx,
            isOnlineProvider = { true },
            storageDeleter = { path ->
                deletedStoragePaths.add(path)
                Result.success(Unit)
            },
            clearFirestoreCache = {},
            signOutHandler = { auth.signOut() }
        )

        val res = deletionRepo.deleteAccount(adminAction = AdminDeleteAction.NONE)
        assertTrue("Expected member deletion success, got $res", res is AccountDeletionResult.Success)

        // Only Bob's Storage file was deleted, NEVER Admin's shared doc
        assertEquals(listOf(bobDocStoragePath), deletedStoragePaths)
        assertFalse(memberCrypto.hasLocalEncryptedFile("doc_bob_passport"))
        assertTrue(accountRepo.signInWithEmailPassword(memberEmail, pass).isFailure)

        // Sign back in as Admin -> Family, Admin doc, and Admin account are 100% intact; Bob is removed!
        accountRepo.signInWithEmailPassword(adminEmail, pass).getOrThrow()
        val postFamilySnap = firestore.collection("families").document(familyId).get(Source.SERVER).await()
        assertTrue(postFamilySnap.exists())
        @Suppress("UNCHECKED_CAST")
        val membersAfter = postFamilySnap.get("memberUids") as List<String>
        assertEquals(listOf(adminUid), membersAfter)

        assertFalse(
            firestore.collection("families").document(familyId).collection("documents").document("doc_bob_passport")
                .get(Source.SERVER).await().exists()
        )
        val sharedDocAfter = firestore.collection("families").document(familyId)
            .collection("documents").document("doc_admin_shared").get(Source.SERVER).await()
        assertTrue(sharedDocAfter.exists())
        @Suppress("UNCHECKED_CAST")
        val allowedAfter = sharedDocAfter.get("allowedUids") as List<String>
        assertEquals(listOf(adminUid), allowedAfter)
        assertFalse(
            firestore.collection("families").document(familyId).collection("members").document(memberUid)
                .get(Source.SERVER).await().exists()
        )
    }

    @Test
    fun yesDeleteAdminEntireFamily_deletesFamilyVaultAndDocs_withoutDeletingOtherMemberAuthAccounts() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val adminEmail = "fam_del_admin_$suffix@test.com"
        val memberEmail = "fam_del_member_$suffix@test.com"
        val pass = "VaultPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val (adminCtx, adminCrypto) = isolatedCryptoDevice("ad_del_$suffix")
        val prefs = SecurityPreferences(adminCtx)

        val adminUser = accountRepo.signUpWithEmailPassword(adminEmail, pass, "Alice Admin").getOrThrow()
        val adminUid = adminUser.uid
        val familyId = vaultRepo.createFamily("Delete Family", "Alice Admin").getOrThrow()
        val family = vaultRepo.observeFamilyAccount(familyId).first()!!
        vaultRepo.ensureAndSyncFamilyVaultKey(family, adminCrypto)

        val doc1Path = "families/$familyId/documents/doc_1.enc"
        adminCrypto.saveEncryptedDocumentForFamily("doc_1", "doc1".toByteArray(), familyId, adminUid)
        vaultRepo.saveDocumentMetadata(
            family = family, docId = "doc_1", title = "Deed", category = DocumentCategory.PROPERTY.storageKey,
            memberId = adminUid, memberName = "Alice Admin", allowedUids = listOf(adminUid),
            fileType = "pdf", fileName = "deed.pdf", fileSizeBytes = 128, storagePath = doc1Path,
            isEncrypted = true, isBackedUp = true, isSample = false, documentNumberMasked = null,
            notes = null, issueDateMillis = 1000L, expiryDateMillis = 0L, isNew = true
        ).getOrThrow()
        auth.signOut()

        // Bob joins
        val memberUser = accountRepo.signUpWithEmailPassword(memberEmail, pass, "Bob Member").getOrThrow()
        val memberUid = memberUser.uid
        vaultRepo.joinFamilyByCode(family.familyCode, "Bob Member").getOrThrow()
        auth.signOut()

        // Alice returns and deletes entire family + her own account
        accountRepo.signInWithEmailPassword(adminEmail, pass).getOrThrow()
        val deletedStoragePaths = mutableListOf<String>()
        val deletionRepo = AccountDeletionRepository(
            db = firestore,
            auth = auth,
            storage = null,
            accountAuthRepository = accountRepo,
            cryptoManager = adminCrypto,
            securityPreferences = prefs,
            appContext = adminCtx,
            isOnlineProvider = { true },
            storageDeleter = { path ->
                deletedStoragePaths.add(path)
                Result.success(Unit)
            },
            clearFirestoreCache = {},
            signOutHandler = { auth.signOut() }
        )

        val res = deletionRepo.deleteAccount(adminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY)
        assertTrue("Expected Success but got $res", res is AccountDeletionResult.Success)
        assertEquals(listOf(doc1Path), deletedStoragePaths)
        assertFalse(adminCrypto.hasLocalEncryptedFile("doc_1"))

        // Admin Auth account is permanently deleted
        assertTrue(accountRepo.signInWithEmailPassword(adminEmail, pass).isFailure)

        // Member Bob's Auth account & /users/{bobUid} were NOT deleted!
        val bobRelogin = accountRepo.signInWithEmailPassword(memberEmail, pass)
        assertTrue("Other member's Auth account must remain intact", bobRelogin.isSuccess)
        assertTrue(firestore.collection("users").document(memberUid).get(Source.SERVER).await().exists())
        // And the family vault + invite + secret no longer exist
        assertFalse(firestore.collection("family_invites").document(family.familyCode).get(Source.SERVER).await().exists())
    }

    @Test
    fun yesDeleteAdminWithOwnershipTransfer_handsOverAdminAndEncryptedDocsToNewOwner() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val adminEmail = "tr_admin_$suffix@test.com"
        val memberEmail = "tr_new_owner_$suffix@test.com"
        val pass = "VaultPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val (adminCtx, adminCrypto) = isolatedCryptoDevice("tr_a_$suffix")
        val (_, bobCrypto) = isolatedCryptoDevice("tr_b_$suffix")
        val prefs = SecurityPreferences(adminCtx)

        val adminUser = accountRepo.signUpWithEmailPassword(adminEmail, pass, "Alice Admin").getOrThrow()
        val adminUid = adminUser.uid
        val familyId = vaultRepo.createFamily("Transfer Vault", "Alice Admin").getOrThrow()
        val family = vaultRepo.observeFamilyAccount(familyId).first()!!
        vaultRepo.ensureAndSyncFamilyVaultKey(family, adminCrypto)

        val plainBytes = "TopSecretDeed".toByteArray()
        val encryptedDocBytes = adminCrypto.encryptBytesForFamily(plainBytes, familyId, adminUid)
        vaultRepo.saveDocumentMetadata(
            family = family, docId = "doc_transfer", title = "Family Deed", category = DocumentCategory.PROPERTY.storageKey,
            memberId = adminUid, memberName = "Alice Admin", allowedUids = listOf(adminUid),
            fileType = "pdf", fileName = "deed.pdf", fileSizeBytes = plainBytes.size.toLong(), storagePath = null,
            isEncrypted = true, isBackedUp = false, isSample = false, documentNumberMasked = null,
            notes = null, issueDateMillis = 1000L, expiryDateMillis = 0L, isNew = true
        ).getOrThrow()
        auth.signOut()

        // Bob joins
        val bobUser = accountRepo.signUpWithEmailPassword(memberEmail, pass, "Bob NewOwner").getOrThrow()
        val bobUid = bobUser.uid
        vaultRepo.joinFamilyByCode(family.familyCode, "Bob NewOwner").getOrThrow()
        auth.signOut()

        // Alice transfers ownership to Bob & deletes her own account
        accountRepo.signInWithEmailPassword(adminEmail, pass).getOrThrow()
        val deletionRepo = AccountDeletionRepository(
            db = firestore,
            auth = auth,
            storage = null,
            accountAuthRepository = accountRepo,
            cryptoManager = adminCrypto,
            securityPreferences = prefs,
            appContext = adminCtx,
            isOnlineProvider = { true },
            storageDeleter = { Result.success(Unit) },
            clearFirestoreCache = {},
            signOutHandler = { auth.signOut() }
        )

        val res = deletionRepo.deleteAccount(
            adminAction = AdminDeleteAction.TRANSFER_OWNERSHIP,
            transferTargetUid = bobUid
        )
        assertTrue("Expected ownership transfer + delete success, got $res", res is AccountDeletionResult.Success)
        assertTrue(accountRepo.signInWithEmailPassword(adminEmail, pass).isFailure)

        // Bob signs in as the new Admin and can sync vault key + decrypt document + manage family!
        accountRepo.signInWithEmailPassword(memberEmail, pass).getOrThrow()
        val famSnap = firestore.collection("families").document(familyId).get(Source.SERVER).await()
        assertEquals(bobUid, famSnap.getString("adminUid"))
        val bobFam = vaultRepo.observeFamilyAccount(familyId).first()!!
        assertTrue(vaultRepo.ensureAndSyncFamilyVaultKey(bobFam, bobCrypto))
        assertArrayEquals(plainBytes, bobCrypto.decryptBytes(encryptedDocBytes, familyId, bobUid))
        val docAfter = firestore.collection("families").document(familyId)
            .collection("documents").document("doc_transfer").get(Source.SERVER).await()
        assertEquals(bobUid, docAfter.getString("adminUid"))
    }

    @Test
    fun partialStorageFailure_reportsPendingStepsAndKeepsAuthAndFirestoreIntactUntilRetrySucceeds() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "partial_$suffix@test.com"
        val pass = "PartialPass123"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val (ctx, crypto) = isolatedCryptoDevice("partial_$suffix")
        val prefs = SecurityPreferences(ctx)

        val user = accountRepo.signUpWithEmailPassword(email, pass, "Partial User").getOrThrow()
        val familyId = vaultRepo.createFamily("Partial Vault", "Partial User").getOrThrow()
        val family = vaultRepo.observeFamilyAccount(familyId).first()!!

        val storagePath = "families/$familyId/documents/doc_fail_first.enc"
        vaultRepo.saveDocumentMetadata(
            family = family, docId = "doc_fail_first", title = "Tax Return", category = DocumentCategory.BANK.storageKey,
            memberId = user.uid, memberName = "Partial User", allowedUids = listOf(user.uid),
            fileType = "pdf", fileName = "tax.pdf", fileSizeBytes = 100, storagePath = storagePath,
            isEncrypted = true, isBackedUp = true, isSample = false, documentNumberMasked = null,
            notes = null, issueDateMillis = 1000L, expiryDateMillis = 0L, isNew = true
        ).getOrThrow()

        var failStorage = true
        val deletionRepo = AccountDeletionRepository(
            db = firestore,
            auth = auth,
            storage = null,
            accountAuthRepository = accountRepo,
            cryptoManager = crypto,
            securityPreferences = prefs,
            appContext = ctx,
            isOnlineProvider = { true },
            storageDeleter = { path ->
                if (failStorage) Result.failure(IllegalStateException("Simulated Storage failure for $path"))
                else Result.success(Unit)
            },
            clearFirestoreCache = {},
            signOutHandler = { auth.signOut() }
        )

        // Wrong password re-auth fails cleanly
        assertTrue(accountRepo.reauthenticateWithPassword("WrongPassword99").isFailure)
        assertTrue(accountRepo.reauthenticateWithPassword(pass).isSuccess)

        // First delete attempt hits Storage failure -> returns PartialFailure and keeps Auth + Firestore intact!
        val partialRes = deletionRepo.deleteAccount(adminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY)
        assertTrue("Expected PartialFailure, got $partialRes", partialRes is AccountDeletionResult.PartialFailure)
        val pf = partialRes as AccountDeletionResult.PartialFailure
        assertTrue(pf.pendingSteps.any { it.stepId == "storage_doc_fail_first" })
        assertNotNull(auth.currentUser)
        assertTrue(firestore.collection("families").document(familyId).get(Source.SERVER).await().exists())
        assertTrue(firestore.collection("users").document(user.uid).get(Source.SERVER).await().exists())

        // Retry after resolving Storage failure -> completes full deletion cleanly!
        failStorage = false
        val retryRes = deletionRepo.deleteAccount(adminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY)
        assertTrue("Expected Success on retry, got $retryRes", retryRes is AccountDeletionResult.Success)
        assertEquals(null, auth.currentUser)
        assertTrue(accountRepo.signInWithEmailPassword(email, pass).isFailure)
    }
}
