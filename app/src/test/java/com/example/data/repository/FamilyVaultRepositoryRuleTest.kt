package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.FamilyVaultRepository
import com.example.domain.model.DocumentCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyVaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun familyIsolation_memberPermissions_andUnauthenticatedRejection() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val adminEmail = "admin_$suffix@test.com"
        val memberEmail = "member_$suffix@test.com"
        val outsiderEmail = "outsider_$suffix@test.com"

        val adminUid = signInTestUser(adminEmail)
        val repo = FamilyVaultRepository(firestore, auth, null)
        val familyId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createFamily("Rao Family", "Ananya Rao").getOrThrow()
        }
        val family = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFamilyAccount(familyId).first { it != null }!!
        }

        // Member joins via Family ID code
        val memberUid = signInTestUser(memberEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinFamilyByCode(family.familyCode, "Kiran Rao").getOrThrow()
        }

        // Admin creates two documents: one admin-only, one shared with member
        signInTestUser(adminEmail)
        val updatedFamily = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFamilyAccount(familyId).first { it != null && it.memberUids.contains(memberUid) }!!
        }
        val adminOnlyDocId = "doc_admin_$suffix"
        val sharedDocId = "doc_shared_$suffix"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveDocumentMetadata(
                family = updatedFamily,
                docId = adminOnlyDocId,
                title = "Sample Property Deed",
                category = DocumentCategory.PROPERTY.storageKey,
                memberId = adminUid,
                memberName = "Ananya Rao",
                allowedUids = listOf(adminUid),
                fileType = "sample",
                fileName = "deed_sample.pdf",
                fileSizeBytes = 2048L,
                storagePath = null,
                isEncrypted = true,
                isBackedUp = false,
                isSample = true,
                documentNumberMasked = "DEMO-9001",
                notes = "Synthetic demo record",
                issueDateMillis = 1700000000000L,
                expiryDateMillis = 0L,
                isNew = true
            ).getOrThrow()

            repo.saveDocumentMetadata(
                family = updatedFamily,
                docId = sharedDocId,
                title = "Sample Ration Card",
                category = DocumentCategory.RATION_CARD.storageKey,
                memberId = adminUid,
                memberName = "Ananya Rao",
                allowedUids = listOf(adminUid, memberUid),
                fileType = "sample",
                fileName = "ration_sample.pdf",
                fileSizeBytes = 1024L,
                storagePath = null,
                isEncrypted = true,
                isBackedUp = false,
                isSample = true,
                documentNumberMasked = "DEMO-1122",
                notes = null,
                issueDateMillis = 1700000000000L,
                expiryDateMillis = 1900000000000L,
                isNew = true
            ).getOrThrow()
        }

        // Member observes permitted docs -> sees ONLY sharedDocId, NOT adminOnlyDocId
        signInTestUser(memberEmail)
        val memberDocs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePermittedDocuments(familyId, memberUid).first { it.isNotEmpty() }
        }
        assertTrue(memberDocs.any { it.id == sharedDocId })
        assertFalse(memberDocs.any { it.id == adminOnlyDocId })

        // Member cannot get admin-only doc directly
        var memberDeniedOnAdminDoc = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("families").document(familyId)
                    .collection("documents").document(adminOnlyDocId).get().await()
            }
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
            memberDeniedOnAdminDoc = true
        }
        assertTrue("Member must be denied direct read on admin-only doc", memberDeniedOnAdminDoc)

        // Outsider from another family sees 0 docs on own filter and fails on admin filter
        val outsiderUid = signInTestUser(outsiderEmail)
        val outsiderDocs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePermittedDocuments(familyId, outsiderUid).first()
        }
        assertTrue(outsiderDocs.isEmpty())

        var outsiderFailed = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePermittedDocuments(familyId, adminUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
            outsiderFailed = true
        }
        assertTrue("Expected outsider access to fail with PERMISSION_DENIED", outsiderFailed)

        // Unauthenticated caller fails with PERMISSION_DENIED
        auth.signOut()
        var unauthFailed = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePermittedDocuments(familyId, adminUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
            unauthFailed = true
        }
        assertTrue("Expected unauthenticated access to fail with PERMISSION_DENIED", unauthFailed)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
