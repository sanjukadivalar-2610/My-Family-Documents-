package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.update.AppUpdateState
import com.example.data.update.RemoteVersionInfo
import com.example.data.update.UpdatePhase
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.CloudCategoryItem
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.UserProfile
import com.example.localization.EnglishStrings
import com.example.localization.LocalAppStrings
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.UpdateScreen
import com.example.ui.theme.MyFamilyDocTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class UpdateScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pkgId = "com.aistudio.myfamilydoc.k7m2px"
    private val sampleFamily = FamilyAccount(
        familyId = "fam_1",
        familyName = "Kadivalar Family",
        familyCode = "MFD-4821-AA",
        adminUid = "uid_1",
        memberUids = listOf("uid_1")
    )

    @Test
    fun sameVersion_hidesUpdateButtonOnHomeAndUpdateScreen() {
        val sameVersionState = AppUpdateState(
            installedPackageId = pkgId,
            installedVersionCode = 1L,
            installedVersionName = "1.0",
            remoteVersion = RemoteVersionInfo(
                versionCode = 1L,
                versionName = "1.0",
                packageId = pkgId,
                apkUrl = "https://github.com/example/app-debug.apk"
            ),
            hasUpdate = false,
            phase = UpdatePhase.UP_TO_DATE
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    UpdateScreen(
                        updateState = sameVersionState,
                        cloudConfig = EffectiveCloudConfig(),
                        isAdmin = true,
                        checkOnOpen = false,
                        onCheckForUpdates = {},
                        onStartDownloadAndInstall = {},
                        onCancelDownload = {},
                        onLaunchVerifiedInstaller = {},
                        onRequestInstallPermission = {},
                        onSaveFamilyCloudConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Your app is up to date.").assertIsDisplayed()
        composeRule.onNodeWithTag("update_install_button").assertDoesNotExist()
        composeRule.onNodeWithTag("update_check_again_button").assertIsDisplayed()
    }

    @Test
    fun sameVersion_homeDashboardShowsNoUpdateBannerOrButton() {
        val sameVersionState = AppUpdateState(
            installedPackageId = pkgId,
            installedVersionCode = 1L,
            installedVersionName = "1.0",
            hasUpdate = false,
            phase = UpdatePhase.UP_TO_DATE
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    HomeDashboardScreen(
                        userProfile = UserProfile(userId = "uid_1", displayName = "Sanju", familyId = "fam_1"),
                        family = sampleFamily,
                        members = emptyList(),
                        documents = emptyList(),
                        backupStatus = BackupStatusInfo(),
                        isAdmin = true,
                        isWideScreen = false,
                        languageCode = "en",
                        onSelectLanguage = {},
                        onQuickUpload = {},
                        onQuickScan = {},
                        onQuickAddMember = {},
                        onLoadSamples = {},
                        onOpenDocuments = {},
                        onOpenBackup = {},
                        onOpenAdminPanel = {},
                        onOpenDocumentDetail = {},
                        onShowMessage = {},
                        updateState = sameVersionState,
                        cloudConfig = EffectiveCloudConfig()
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_update_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("home_update_button").assertDoesNotExist()
    }

    @Test
    fun newerVersion_displaysNewUpdateAvailableAndUpdateButton() {
        var updateClicked = false
        val newerVersionState = AppUpdateState(
            installedPackageId = pkgId,
            installedVersionCode = 1L,
            installedVersionName = "1.0",
            remoteVersion = RemoteVersionInfo(
                versionCode = 2L,
                versionName = "1.1",
                packageId = pkgId,
                apkUrl = "https://github.com/example/app-debug.apk",
                releaseNotes = "Faster vault search and offline PDF rendering"
            ),
            hasUpdate = true,
            phase = UpdatePhase.UPDATE_AVAILABLE
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    UpdateScreen(
                        updateState = newerVersionState,
                        cloudConfig = EffectiveCloudConfig(),
                        isAdmin = false,
                        checkOnOpen = false,
                        onCheckForUpdates = {},
                        onStartDownloadAndInstall = { updateClicked = true },
                        onCancelDownload = {},
                        onLaunchVerifiedInstaller = {},
                        onRequestInstallPermission = {},
                        onSaveFamilyCloudConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("New Update Available").assertIsDisplayed()
        composeRule.onNodeWithText("Faster vault search and offline PDF rendering").assertIsDisplayed()
        composeRule.onNodeWithTag("update_install_button").assertIsDisplayed().performClick()
        assertTrue(updateClicked)
    }

    @Test
    fun homeDashboard_showsUpdateBannerAndCloudNoticeAndCustomBannerLabel() {
        var startUpdateCalled = false
        val updateAvailableState = AppUpdateState(
            installedPackageId = pkgId,
            installedVersionCode = 1L,
            installedVersionName = "1.0",
            remoteVersion = RemoteVersionInfo(
                versionCode = 3L,
                versionName = "1.2",
                packageId = pkgId,
                apkUrl = "https://github.com/example/app-debug.apk"
            ),
            hasUpdate = true,
            phase = UpdatePhase.UPDATE_AVAILABLE
        )
        val cloudConfig = EffectiveCloudConfig(
            isSyncedFromCloud = true,
            noticeActive = true,
            noticeTitleEn = "Cloud Policy Notice",
            noticeBodyEn = "Renew health insurance cards before March 31.",
            homeBannerLabelEn = "FY 2026 Vault Ready"
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    HomeDashboardScreen(
                        userProfile = UserProfile(userId = "uid_1", displayName = "Sanju", familyId = "fam_1"),
                        family = sampleFamily,
                        members = emptyList(),
                        documents = emptyList<FamilyDocument>(),
                        backupStatus = BackupStatusInfo(),
                        isAdmin = true,
                        isWideScreen = false,
                        languageCode = "en",
                        onSelectLanguage = {},
                        onQuickUpload = {},
                        onQuickScan = {},
                        onQuickAddMember = {},
                        onLoadSamples = {},
                        onOpenDocuments = {},
                        onOpenBackup = {},
                        onOpenAdminPanel = {},
                        onOpenDocumentDetail = {},
                        onShowMessage = {},
                        updateState = updateAvailableState,
                        cloudConfig = cloudConfig,
                        onOpenUpdates = {},
                        onStartAppUpdate = { startUpdateCalled = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_cloud_banner_label").assertIsDisplayed()
        composeRule.onNodeWithText("FY 2026 Vault Ready").assertIsDisplayed()
        composeRule.onNodeWithTag("home_update_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("home_update_button").assertIsDisplayed().performClick()
        assertTrue(startUpdateCalled)
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_cloud_notice_banner"))
        composeRule.onNodeWithTag("home_cloud_notice_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Renew health insurance cards before March 31.").assertIsDisplayed()
    }

    @Test
    fun installCanceledAndApkVsCloudDistinction_renderClearly() {
        var retryCalled = false
        val canceledState = AppUpdateState(
            installedPackageId = pkgId,
            installedVersionCode = 1L,
            installedVersionName = "1.0",
            remoteVersion = RemoteVersionInfo(
                versionCode = 2L,
                versionName = "1.1",
                packageId = pkgId,
                apkUrl = "https://github.com/example/app-debug.apk"
            ),
            hasUpdate = true,
            phase = UpdatePhase.INSTALL_CANCELED,
            verifiedApkFile = File("/tmp/dummy-v2.apk"),
            packageVerified = true,
            signatureVerified = true,
            versionVerified = true
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    UpdateScreen(
                        updateState = canceledState,
                        cloudConfig = EffectiveCloudConfig(),
                        isAdmin = false,
                        checkOnOpen = false,
                        onCheckForUpdates = {},
                        onStartDownloadAndInstall = {},
                        onCancelDownload = {},
                        onLaunchVerifiedInstaller = { retryCalled = true },
                        onRequestInstallPermission = {},
                        onSaveFamilyCloudConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(EnglishStrings.installCanceledTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("update_retry_install_button").assertIsDisplayed().performClick()
        assertTrue(retryCalled)

        composeRule.onNodeWithTag("update_lazy_column").performScrollToNode(hasTestTag("apk_vs_cloud_card"))
        composeRule.onNodeWithTag("apk_vs_cloud_card").assertIsDisplayed()
        composeRule.onNodeWithText(EnglishStrings.apkVsCloudTitle).assertIsDisplayed()
    }

    @Test
    fun documentsScreen_dynamicallyDisplaysCloudAddedCategoryWithoutApkRebuild() {
        val cloudConfig = EffectiveCloudConfig(
            isSyncedFromCloud = true,
            extraCategories = listOf(
                CloudCategoryItem(
                    key = "insurance_policy",
                    labelEn = "Insurance Policy",
                    labelKn = "ವಿಮಾ ಪಾಲಿಸಿ"
                )
            ),
            showSampleDocs = false
        )

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    DocumentsScreen(
                        documents = emptyList(),
                        members = emptyList(),
                        initialMemberFilterId = null,
                        openUploadInitially = false,
                        onConsumeInitialState = {},
                        onOpenDocumentDetail = {},
                        onOpenCameraScanner = {},
                        onLoadSampleDocs = {},
                        onUploadDocumentUri = { _, _, _, _, _, _, _, _, _ -> },
                        cloudConfig = cloudConfig
                    )
                }
            }
        }

        composeRule.onNodeWithTag("documents_category_row")
            .performScrollToNode(hasTestTag("category_chip_insurance_policy"))
        composeRule.onNodeWithTag("category_chip_insurance_policy").assertIsDisplayed()
        composeRule.onNodeWithText("Insurance Policy").assertIsDisplayed()
        composeRule.onNodeWithText("Load Sample Docs").assertDoesNotExist()
    }
}
