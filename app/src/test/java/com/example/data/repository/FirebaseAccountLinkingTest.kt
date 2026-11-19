package com.example.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.AccountAuthException
import com.example.data.firebase.AccountAuthRepository
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.firebase.GoogleSignInOutcome
import com.example.data.security.DocumentCryptoManager
import com.example.domain.model.DocumentCategory
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class FirebaseAccountLinkingTest : FirestoreEmulatorTestBase() {

    private fun buildEmulatorGoogleIdToken(
        sub: String,
        email: String,
        emailVerified: Boolean = true
    ): String {
        val header = JSONObject().put("alg", "none").put("typ", "JWT").toString()
        val payload = JSONObject()
            .put("iss", "https://accounts.google.com")
            .put("aud", "project-71ca2d4d-7b30-4952-b87")
            .put("sub", sub)
            .put("email", email)
            .put("email_verified", emailVerified)
            .put("name", "Test User")
            .toString()

        fun encodePart(raw: String): String =
            Base64.encodeToString(
                raw.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

        return "${encodePart(header)}.${encodePart(payload)}."
    }

    private fun isolatedCryptoDevice(deviceTag: String): DocumentCryptoManager {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val filesDir = File(base.filesDir, "auth_sync_$deviceTag").apply {
            deleteRecursively()
            mkdirs()
        }
        val cacheDir = File(base.cacheDir, "auth_sync_${deviceTag}_cache").apply {
            deleteRecursively()
            mkdirs()
        }
        val wrapped = object : ContextWrapper(base) {
            override fun getFilesDir(): File = filesDir
            override fun getCacheDir(): File = cacheDir
        }
        return DocumentCryptoManager(wrapped)
    }

    @Test
    fun googleSignIn_promptsPasswordCreationOnce_linksEmailProvider_andEmailLoginOpensSameUidAndVault() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "google_first_$suffix@gmail.com"
        val sub = "google_sub_$suffix"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)

        // 1. User signs in with Google
        val googleCred = GoogleAuthProvider.getCredential(buildEmulatorGoogleIdToken(sub, email), null)
        val outcome = withTimeout(TIMEOUT_MS) {
            accountRepo.completeGoogleSignIn(googleCred, email).getOrThrow()
        }
        assertTrue(outcome is GoogleSignInOutcome.Authenticated)
        val authOutcome = outcome as GoogleSignInOutcome.Authenticated
        assertTrue("Google-only user must be asked to create password once", authOutcome.needsPasswordCreation)
        assertFalse(accountRepo.hasEmailPasswordProvider())
        assertTrue(accountRepo.hasGoogleProvider())
        val originalUid = authOutcome.uid

        // 2. Link Email/Password credential to the exact same Firebase UID
        val linkedUser = withTimeout(TIMEOUT_MS) {
            accountRepo.linkPasswordToCurrentGoogleUser("VaultPass123").getOrThrow()
        }
        assertEquals(originalUid, linkedUser.uid)
        assertTrue(accountRepo.hasEmailPasswordProvider(linkedUser))
        assertTrue(accountRepo.hasGoogleProvider(linkedUser))
        assertTrue(accountRepo.refreshPasswordLinkedState())

        // Idempotent guard: once linked, linking does not re-prompt or change existing password
        withTimeout(TIMEOUT_MS) {
            accountRepo.linkPasswordToCurrentGoogleUser("SecondAttempt99").getOrThrow()
        }

        // 3. Create Family & Document under this UID, ensuring no plaintext password is in Firestore
        val familyId = withTimeout(TIMEOUT_MS) {
            vaultRepo.createFamily("Kulkarni Vault", "Sanjay").getOrThrow()
        }
        val family = withTimeout(TIMEOUT_MS) {
            vaultRepo.observeFamilyAccount(familyId).first { it != null }!!
        }
        vaultRepo.saveDocumentMetadata(
            family = family,
            docId = "doc_pan_$suffix",
            title = "PAN Card",
            category = DocumentCategory.PROPERTY.storageKey,
            memberId = originalUid,
            memberName = "Sanjay",
            allowedUids = listOf(originalUid),
            fileType = "pdf",
            fileName = "pan.pdf",
            fileSizeBytes = 1024L,
            storagePath = null,
            isEncrypted = true,
            isBackedUp = true,
            isSample = false,
            documentNumberMasked = "XXXX1234X",
            notes = null,
            issueDateMillis = 0L,
            expiryDateMillis = 0L,
            isNew = true
        ).getOrThrow()

        val rawUserDoc = withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(originalUid).get().await()
        }
        assertFalse("Firestore user doc must never contain plaintext password", rawUserDoc.contains("password"))

        // 4. Sign out and sign back in via Email & Password -> resolves to exact same UID and Vault
        auth.signOut()
        val emailUser = withTimeout(TIMEOUT_MS) {
            accountRepo.signInWithEmailPassword(email, "VaultPass123").getOrThrow()
        }
        assertEquals("Email login must resolve to exact same Firebase UID", originalUid, emailUser.uid)
        assertTrue(accountRepo.hasEmailPasswordProvider(emailUser))
        assertTrue(accountRepo.hasGoogleProvider(emailUser))

        val restoredProfile = withTimeout(TIMEOUT_MS) {
            vaultRepo.observeUserProfile(originalUid).first { it != null }!!
        }
        assertEquals(familyId, restoredProfile.familyId)

        val docs = withTimeout(TIMEOUT_MS) {
            vaultRepo.observePermittedDocuments(familyId, originalUid).first { it.isNotEmpty() }
        }
        assertTrue(docs.any { it.id == "doc_pan_$suffix" })
    }

    @Test
    fun emailSignUp_thenLinkGoogleCredential_andVerifyConflictFlow_resolvesToSameUid() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "email_first_$suffix@gmail.com"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)

        // 1. User signs up with Email & Password first
        val signUpUser = withTimeout(TIMEOUT_MS) {
            accountRepo.signUpWithEmailPassword(email, "StrongPass99", "Priya").getOrThrow()
        }
        val uid = signUpUser.uid
        assertTrue(accountRepo.hasEmailPasswordProvider(signUpUser))
        assertFalse(accountRepo.hasGoogleProvider(signUpUser))

        val familyId = withTimeout(TIMEOUT_MS) {
            vaultRepo.createFamily("Desai Family", "Priya").getOrThrow()
        }

        // Duplicate Email Sign-Up rejects cleanly with EMAIL_ALREADY_IN_USE
        val dupResult = withTimeout(TIMEOUT_MS) {
            accountRepo.signUpWithEmailPassword(email, "OtherPass88", "Dup")
        }
        assertTrue(dupResult.isFailure)
        assertEquals("EMAIL_ALREADY_IN_USE", (dupResult.exceptionOrNull() as? AccountAuthException)?.code)

        // 2. Link Google Sign-In onto the signed-in Email user (Settings flow)
        val mismatchedGoogle = GoogleAuthProvider.getCredential(
            buildEmulatorGoogleIdToken("sub_wrong_$suffix", "someone_else_$suffix@gmail.com"),
            null
        )
        val mismatchRes = withTimeout(TIMEOUT_MS) {
            accountRepo.linkGoogleCredentialToSignedInUser(mismatchedGoogle, "someone_else_$suffix@gmail.com")
        }
        assertTrue(mismatchRes.isFailure)
        assertEquals("EMAIL_MISMATCH", (mismatchRes.exceptionOrNull() as? AccountAuthException)?.code)

        val matchingGoogle = GoogleAuthProvider.getCredential(
            buildEmulatorGoogleIdToken("sub_match_$suffix", email),
            null
        )
        val linkedUser = withTimeout(TIMEOUT_MS) {
            accountRepo.linkGoogleCredentialToSignedInUser(matchingGoogle, email).getOrThrow()
        }
        assertEquals("Google link must keep exact same UID", uid, linkedUser.uid)
        assertTrue(accountRepo.hasGoogleProvider(linkedUser))
        assertTrue(accountRepo.hasEmailPasswordProvider(linkedUser))

        // 3. Also test verifyPasswordAndLinkGoogleCredential (conflict verification flow)
        auth.signOut()
        val email2 = "verify_link_$suffix@gmail.com"
        val uid2 = withTimeout(TIMEOUT_MS) {
            accountRepo.signUpWithEmailPassword(email2, "MyPass2026", "Rohan").getOrThrow().uid
        }
        auth.signOut()
        val pendingGoogleFor2 = GoogleAuthProvider.getCredential(
            buildEmulatorGoogleIdToken("sub_verify_$suffix", email2),
            null
        )

        // Wrong password refuses link
        val wrongPassAttempt = withTimeout(TIMEOUT_MS) {
            accountRepo.verifyPasswordAndLinkGoogleCredential(email2, "WrongPass1", pendingGoogleFor2)
        }
        assertTrue(wrongPassAttempt.isFailure)
        assertEquals("INVALID_CREDENTIALS", (wrongPassAttempt.exceptionOrNull() as? AccountAuthException)?.code)

        // Right password verifies ownership and links Google to exact same UID
        val verifiedAndLinked = withTimeout(TIMEOUT_MS) {
            accountRepo.verifyPasswordAndLinkGoogleCredential(email2, "MyPass2026", pendingGoogleFor2).getOrThrow()
        }
        assertEquals(uid2, verifiedAndLinked.uid)
        assertTrue(accountRepo.hasGoogleProvider(verifiedAndLinked))
        assertTrue(accountRepo.hasEmailPasswordProvider(verifiedAndLinked))

        // Attempting to reuse UID1's Google credential on a separate Email-only UID3 fails safely without duplicate account or merge
        auth.signOut()
        val email3 = "separate_uid3_$suffix@gmail.com"
        withTimeout(TIMEOUT_MS) {
            accountRepo.signUpWithEmailPassword(email3, "UniquePass3", "Kiran").getOrThrow()
        }
        auth.signOut()
        val conflictingGoogleFor1 = GoogleAuthProvider.getCredential(
            buildEmulatorGoogleIdToken("sub_match_$suffix", email),
            null
        )
        val collisionAttempt = withTimeout(TIMEOUT_MS) {
            accountRepo.verifyPasswordAndLinkGoogleCredential(email3, "UniquePass3", conflictingGoogleFor1)
        }
        assertTrue(collisionAttempt.isFailure)
        assertEquals("CREDENTIAL_ALREADY_IN_USE", (collisionAttempt.exceptionOrNull() as? AccountAuthException)?.code)

        // Confirm UID3 cannot access UID1's family
        val uid1ProfileAttempt = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("families").document(familyId).get().await()
            }
        }
        assertTrue("Separate UID must remain denied from another user's family", uid1ProfileAttempt.isFailure)
    }

    @Test
    fun passwordResetEmail_andMultiDeviceGooglePlusEmailVaultSync_workEndToEnd() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "multidevice_$suffix@gmail.com"
        val accountRepo = AccountAuthRepository(auth)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)

        val invalidReset = withTimeout(TIMEOUT_MS) { accountRepo.sendPasswordResetEmail("not-an-email") }
        assertTrue(invalidReset.isFailure)

        // Phone A signs in with Google, links password, creates family, and syncs vault_key + v3 PDF payload
        val googleCred = GoogleAuthProvider.getCredential(
            buildEmulatorGoogleIdToken("sub_md_$suffix", email),
            null
        )
        val phoneAOutcome = withTimeout(TIMEOUT_MS) {
            accountRepo.completeGoogleSignIn(googleCred, email).getOrThrow()
        } as GoogleSignInOutcome.Authenticated
        val sharedUid = phoneAOutcome.uid
        withTimeout(TIMEOUT_MS) {
            accountRepo.linkPasswordToCurrentGoogleUser("SyncVault99").getOrThrow()
        }

        // Password Reset triggers cleanly for this registered email on Auth Emulator
        withTimeout(TIMEOUT_MS) {
            accountRepo.sendPasswordResetEmail(email).getOrThrow()
        }

        val cryptoPhoneA = isolatedCryptoDevice("phoneA_$suffix")
        val familyId = withTimeout(TIMEOUT_MS) {
            vaultRepo.createFamily("Patel MultiDevice", "Aarav").getOrThrow()
        }
        val family = withTimeout(TIMEOUT_MS) {
            vaultRepo.observeFamilyAccount(familyId).first { it != null }!!
        }
        val syncedKeyA = withTimeout(TIMEOUT_MS) {
            vaultRepo.ensureAndSyncFamilyVaultKey(
                family = family,
                cryptoManager = cryptoPhoneA
            )
        }
        assertTrue(syncedKeyA)
        assertTrue(cryptoPhoneA.hasFamilyVaultKey(familyId))

        val pdfDocId = "doc_sync_$suffix"
        val pdfPlaintext = "%PDF-1.5\nMULTI-DEVICE-UNIFIED-UID-SECRET\n%%EOF".toByteArray(Charsets.UTF_8)
        cryptoPhoneA.saveEncryptedDocumentForFamily(pdfDocId, pdfPlaintext, familyId, sharedUid)
        val cloudBlob = cryptoPhoneA.readEncryptedPayloadBytes(pdfDocId)!!

        withTimeout(TIMEOUT_MS) {
            vaultRepo.saveDocumentMetadata(
                family = family,
                docId = pdfDocId,
                title = "House Deed",
                category = DocumentCategory.PROPERTY.storageKey,
                memberId = sharedUid,
                memberName = "Aarav",
                allowedUids = listOf(sharedUid),
                fileType = "pdf",
                fileName = "deed.pdf",
                fileSizeBytes = pdfPlaintext.size.toLong(),
                storagePath = "families/$familyId/documents/$pdfDocId.enc",
                isEncrypted = true,
                isBackedUp = true,
                isSample = false,
                documentNumberMasked = null,
                notes = null,
                issueDateMillis = 0L,
                expiryDateMillis = 0L,
                isNew = true
            ).getOrThrow()
        }

        // Phone B logs in with Email & Password -> accesses SAME UID, syncs vault_key, and decrypts PDF
        auth.signOut()
        val phoneBUser = withTimeout(TIMEOUT_MS) {
            accountRepo.signInWithEmailPassword(email, "SyncVault99").getOrThrow()
        }
        assertEquals(sharedUid, phoneBUser.uid)

        val cryptoPhoneB = isolatedCryptoDevice("phoneB_$suffix")
        val familyOnPhoneB = withTimeout(TIMEOUT_MS) {
            vaultRepo.observeFamilyAccount(familyId).first { it != null }!!
        }
        val syncedKeyB = withTimeout(TIMEOUT_MS) {
            vaultRepo.ensureAndSyncFamilyVaultKey(
                family = familyOnPhoneB,
                cryptoManager = cryptoPhoneB
            )
        }
        assertTrue(syncedKeyB)
        assertTrue(cryptoPhoneB.hasFamilyVaultKey(familyId))
        cryptoPhoneB.writeRawEncryptedPayload(pdfDocId, cloudBlob)
        val decryptedOnPhoneB = cryptoPhoneB.readDecryptedBytes(pdfDocId, familyId, sharedUid)
        assertArrayEquals(pdfPlaintext, decryptedOnPhoneB)

        val docsOnPhoneB = withTimeout(TIMEOUT_MS) {
            vaultRepo.observePermittedDocuments(familyId, sharedUid).first { it.isNotEmpty() }
        }
        assertEquals(1, docsOnPhoneB.size)
        assertEquals("House Deed", docsOnPhoneB.first().title)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
