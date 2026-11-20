package com.example.ui

import android.net.Uri
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
import com.example.data.documents.DocumentTransferState
import com.example.data.security.SecuritySettings
import com.example.data.update.AppUpdateState
import com.example.data.update.UpdatePhase
import com.example.domain.model.BackupStatusInfo
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyDocument
import com.example.domain.model.UserProfile
import com.example.localization.EnglishStrings
import com.example.localization.KannadaStrings
import com.example.localization.LocalAppStrings
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdateScreen
import com.example.ui.theme.MyFamilyDocTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class BrandingAndShareDownloadUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val family = FamilyAccount(
        familyId = "fam_ui",
        familyName = "Kadivalar Family",
        familyCode = "MFD-9911-XX",
        adminUid = "uid_admin",
        memberUids = listOf("uid_admin")
    )

    private val sampleDoc1 = FamilyDocument(
        id = "doc_1",
        familyId = "fam_ui",
        title = "Aadhaar Card",
        category = "AADHAAR",
        memberId = "m1",
        memberName = "Sanju",
        uploadedByUid = "uid_admin",
        adminUid = "uid_admin",
        allowedUids = listOf("uid_admin"),
        fileType = "pdf",
        fileName = "aadhaar_sanju.pdf",
        fileSizeBytes = 102400L,
        isBackedUp = true
    )

    private val sampleDoc2 = sampleDoc1.copy(
        id = "doc_2",
        title = "Passport Scan",
        category = "PASSPORT",
        fileType = "jpg",
        fileName = "passport_sanju.jpg"
    )

    @Test
    fun splashOverlay_rendersFamvioraAndKannadaPronunciationAndTagline() {
        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides KannadaStrings) {
                    FamvioraSplashOverlay()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("famviora_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithText("FAMVIORA").assertIsDisplayed()
        composeRule.onNodeWithText("ಫ್ಯಾಮ್ವಿಯೋರಾ").assertIsDisplayed()
        composeRule.onNodeWithText("ನಿಮ್ಮ ಕುಟುಂಬ. ನಿಮ್ಮ ದಾಖಲೆಗಳು. ನಿಮ್ಮ ಕಾಳಜಿ.").assertIsDisplayed()
    }

    @Test
    fun homeDashboard_displaysFamvioraBrandTaglineAndCardShareDownloadActions() {
        var sharedDocId: String? = null
        var downloadDocId: String? = null

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    HomeDashboardScreen(
                        userProfile = UserProfile(userId = "uid_admin", displayName = "Sanju", familyId = "fam_ui"),
                        family = family,
                        members = emptyList(),
                        documents = listOf(sampleDoc1),
                        backupStatus = BackupStatusInfo(totalDocs = 1, backedUpDocs = 1, localEncryptedDocs = 1),
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
                        onShareDocument = { sharedDocId = it.id },
                        onDownloadDocument = { downloadDocId = it.id }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_brand_header").assertIsDisplayed()
        composeRule.onNodeWithText("FAMVIORA").assertIsDisplayed()
        composeRule.onNodeWithText("Your Family. Your Records. Your Care.").assertIsDisplayed()

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("document_card_doc_1"))
        composeRule.onNodeWithTag("doc_card_share_doc_1").assertIsDisplayed().performClick()
        assertEquals("doc_1", sharedDocId)
        composeRule.onNodeWithTag("doc_card_download_doc_1").assertIsDisplayed().performClick()
        assertEquals("doc_1", downloadDocId)
    }

    @Test
    fun documentsScreen_supportsSingleAndBatchShareDownloadDestinationChooserAndTransferBanner() {
        var batchSharedIds: List<String> = emptyList()
        var batchDownloadIds: List<String> = emptyList()
        var openClickedUri: Uri? = null
        val completedUri = Uri.parse("content://downloads/aadhaar_sanju.pdf")

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    DocumentsScreen(
                        documents = listOf(sampleDoc1, sampleDoc2),
                        members = emptyList(),
                        initialMemberFilterId = null,
                        openUploadInitially = false,
                        onConsumeInitialState = {},
                        onOpenDocumentDetail = {},
                        onOpenCameraScanner = {},
                        onLoadSampleDocs = {},
                        onUploadDocumentUri = { _, _, _, _, _, _, _, _, _ -> },
                        transferState = DocumentTransferState.Completed(
                            fileName = "aadhaar_sanju.pdf",
                            savedUri = completedUri,
                            mimeType = "application/pdf",
                            count = 1,
                            isBatch = false
                        ),
                        onShareSingle = {},
                        onShareMultiple = { docs -> batchSharedIds = docs.map { it.id } },
                        onDownloadToDownloads = { docs -> batchDownloadIds = docs.map { it.id } },
                        onDownloadSingleToSafUri = { _, _ -> },
                        onDownloadMultipleToTreeUri = { _, _ -> },
                        onOpenSavedFile = { uri, _ -> openClickedUri = uri }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Completed transfer banner with Open button is visible
        composeRule.onNodeWithTag("transfer_completed_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_open_file_button").assertIsDisplayed().performClick()
        assertEquals(completedUri, openClickedUri)

        // Single-card Download opens Destination Chooser -> Save to Downloads
        composeRule.onNodeWithTag("doc_card_download_doc_1").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("download_destination_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("download_dest_downloads_button").assertIsDisplayed().performClick()
        assertEquals(listOf("doc_1"), batchDownloadIds)

        // Enter multi-select mode, select both documents, and trigger batch share
        composeRule.onNodeWithTag("documents_select_mode_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("documents_multi_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("documents_lazy_column")
            .performScrollToNode(hasTestTag("doc_card_checkbox_doc_1"))
        composeRule.onNodeWithTag("doc_card_checkbox_doc_1").performClick()
        composeRule.onNodeWithTag("documents_lazy_column")
            .performScrollToNode(hasTestTag("doc_card_checkbox_doc_2"))
        composeRule.onNodeWithTag("doc_card_checkbox_doc_2").performClick()
        composeRule.onNodeWithTag("documents_lazy_column")
            .performScrollToNode(hasTestTag("multi_share_button"))
        composeRule.onNodeWithTag("multi_share_button").performClick()
        assertEquals(listOf("doc_1", "doc_2"), batchSharedIds)
    }

    @Test
    fun documentDetailScreen_moreActionsSheetExposesShareDownloadOpenWithAndCopyLink() {
        var shareTriggered = false
        var downloadsTriggered = false
        var openWithTriggered = false
        var copyLinkTriggered = false

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    DocumentDetailScreen(
                        document = sampleDoc1,
                        members = emptyList(),
                        isAdmin = true,
                        loadDecryptedBytes = { null },
                        onRenameAndUpdateDates = { _, _, _, _, _, _ -> },
                        onUpdatePermissions = { _, _ -> },
                        onDeleteDocument = {},
                        onBack = {},
                        onShare = { shareTriggered = true },
                        onDownloadToDownloads = { downloadsTriggered = true },
                        onDownloadToSafUri = {},
                        onOpenWith = { openWithTriggered = true },
                        onCopyLink = { copyLinkTriggered = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Open More Actions bottom sheet & test Copy Link
        composeRule.onNodeWithTag("detail_more_actions_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("document_actions_bottom_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("doc_sheet_share_button").assertIsDisplayed()
        composeRule.onNodeWithTag("doc_sheet_download_button").assertIsDisplayed()
        composeRule.onNodeWithTag("doc_sheet_open_with_button").assertIsDisplayed()
        composeRule.onNodeWithTag("doc_sheet_copy_link_button").assertIsDisplayed().performClick()
        assertTrue(copyLinkTriggered)

        // Re-open sheet and test Open With
        composeRule.onNodeWithTag("detail_more_actions_button").performClick()
        composeRule.onNodeWithTag("doc_sheet_open_with_button").performClick()
        assertTrue(openWithTriggered)

        // Test Share button
        composeRule.onNodeWithTag("detail_share_button").performClick()
        assertTrue(shareTriggered)

        // Test Download destination chooser -> Save to Downloads
        composeRule.onNodeWithTag("detail_download_button").performClick()
        composeRule.onNodeWithTag("download_dest_downloads_button").performClick()
        assertTrue(downloadsTriggered)
    }

    @Test
    fun settingsScreen_displaysFamvioraBranding() {
        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    SettingsScreen(
                        userProfile = UserProfile(userId = "uid_admin", displayName = "Sanju", familyId = "fam_ui"),
                        family = family,
                        isAdmin = true,
                        securitySettings = SecuritySettings(),
                        userEmail = "sanju@example.com",
                        firebaseUid = "uid_admin",
                        hasGoogleLinked = true,
                        hasEmailPasswordLinked = true,
                        onLinkGoogleAccount = {},
                        onOpenCreatePassword = {},
                        onSendPasswordResetEmail = {},
                        onSelectLanguage = {},
                        onSelectThemeMode = {},
                        onSetupOrChangePin = {},
                        onDisablePin = {},
                        onToggleBiometric = {},
                        onToggleExpiryReminders = {},
                        onCheckExpiriesNow = {},
                        onOpenBackupCenter = {},
                        onOpenAdminPanel = {},
                        onLogout = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_brand_subtitle").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_about_button"))
        composeRule.onNodeWithText("About FAMVIORA").assertIsDisplayed()
    }

    @Test
    fun updateScreen_displaysFamvioraBranding() {
        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    UpdateScreen(
                        updateState = AppUpdateState(
                            installedPackageId = "com.aistudio.myfamilydoc.k7m2px",
                            installedVersionCode = 1L,
                            installedVersionName = "1.0",
                            phase = UpdatePhase.UP_TO_DATE
                        ),
                        cloudConfig = EffectiveCloudConfig(),
                        isAdmin = false,
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_brand_label").assertIsDisplayed()
        composeRule.onNodeWithText("FAMVIORA • Your Family. Your Records. Your Care.").assertIsDisplayed()
    }
}
