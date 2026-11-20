package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.CloudConfigRepository
import com.example.data.firebase.FamilyVaultRepository
import com.example.domain.model.CloudCategoryItem
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

class FirebaseCloudConfigSyncTest : FirestoreEmulatorTestBase() {

    @Test
    fun adminCloudConfigUpdate_syncsLiveToOtherFamilyMembersWithoutApkRebuild() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val adminEmail = "cfg_admin_$suffix@test.com"
        val memberEmail = "cfg_member_$suffix@test.com"

        // 1. Admin creates family
        signInTestUser(adminEmail)
        val vaultRepo = FamilyVaultRepository(firestore, auth, null)
        val familyId = withTimeout(5000L) {
            vaultRepo.createFamily("Cloud Test Family", "Admin User").getOrThrow()
        }
        val family = withTimeout(5000L) {
            vaultRepo.observeFamilyAccount(familyId).first { it != null }!!
        }

        // 2. Member joins family
        signInTestUser(memberEmail)
        withTimeout(5000L) {
            vaultRepo.joinFamilyByCode(family.familyCode, "Member User").getOrThrow()
        }

        // 3. Admin publishes live cloud categories, notice, banner label, and expiry setting
        signInTestUser(adminEmail)
        val adminCloudRepo = CloudConfigRepository(firestore, auth)
        withTimeout(5000L) {
            adminCloudRepo.saveFamilyCloudConfig(
                familyId = familyId,
                noticeActive = true,
                noticeTitleEn = "Tax Season Alert",
                noticeTitleKn = "ತೆರಿಗೆ ಎಚ್ಚರಿಕೆ",
                noticeBodyEn = "Keep PAN and Form 16 updated.",
                noticeBodyKn = "ಪ್ಯಾನ್ ಮತ್ತು ಫಾರ್ಮ್ 16 ನವೀಕರಿಸಿ.",
                noticeLevel = "info",
                homeBannerLabelEn = "Family Sync Active",
                homeBannerLabelKn = "ಕುಟುಂಬ ಸಿಂಕ್ ಸಕ್ರಿಯ",
                extraCategories = listOf(
                    CloudCategoryItem(
                        key = "insurance_policy",
                        labelEn = "Insurance Policy",
                        labelKn = "ವಿಮಾ ಪಾಲಿಸಿ"
                    )
                ),
                expiryWarningDays = 60L,
                showSampleDocs = false
            ).getOrThrow()
        }

        // 4. Member immediately receives live config without APK rebuild
        signInTestUser(memberEmail)
        val memberCloudRepo = CloudConfigRepository(firestore, auth)
        val synced = withTimeout(5000L) {
            memberCloudRepo.observeEffectiveCloudConfig(familyId).first {
                it.isSyncedFromCloud && it.noticeActive && it.extraCategories.isNotEmpty()
            }
        }

        assertEquals("Tax Season Alert", synced.localizedNoticeTitle("en"))
        assertEquals("ತೆರಿಗೆ ಎಚ್ಚರಿಕೆ", synced.localizedNoticeTitle("kn"))
        assertEquals("Keep PAN and Form 16 updated.", synced.localizedNoticeBody("en"))
        assertEquals("Family Sync Active", synced.localizedHomeBannerLabel("en"))
        assertEquals(60L, synced.expiryWarningDays)
        assertFalse(synced.showSampleDocs)
        assertTrue(synced.allCategoryOptions().any { it.storageKey == "insurance_policy" && it.isCloudSynced })

        // 5. Non-admin member cannot overwrite family cloud config directly
        var memberWriteDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("families").document(familyId)
                    .collection("config").document("live")
                    .update("noticeActive", false)
                    .await()
            }
        } catch (e: Throwable) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
            memberWriteDenied = true
        }
        assertTrue("Non-admin must be denied writing family cloud config", memberWriteDenied)
    }
}
