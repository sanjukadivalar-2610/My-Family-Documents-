package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.firebase.AccountAuthRepository
import com.example.data.firebase.AdminDeleteAction
import com.example.data.firebase.PendingCleanupStep
import com.example.data.security.SecuritySettings
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyMember
import com.example.domain.model.UserProfile
import com.example.localization.EnglishStrings
import com.example.localization.LocalAppStrings
import com.example.ui.auth.AuthScreen
import com.example.ui.components.DeleteAccountDialogState
import com.example.ui.components.DeleteAccountFlowDialogs
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyFamilyDocTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsDeleteAccountUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleFamily = FamilyAccount(
        familyId = "fam_test_1",
        familyName = "Sharma Family",
        familyCode = "MFD-1234-XY",
        adminUid = "uid_admin",
        memberUids = listOf("uid_admin", "uid_member")
    )

    private val sampleMembers = listOf(
        FamilyMember(
            id = "uid_admin",
            familyId = "fam_test_1",
            name = "Alice Admin",
            relation = "Self",
            role = "admin",
            linkedUserId = "uid_admin",
            email = "alice@test.com",
            memberUids = listOf("uid_admin", "uid_member"),
            adminUid = "uid_admin"
        ),
        FamilyMember(
            id = "uid_member",
            familyId = "fam_test_1",
            name = "Bob Member",
            relation = "Spouse",
            role = "member",
            linkedUserId = "uid_member",
            email = "bob@test.com",
            memberUids = listOf("uid_admin", "uid_member"),
            adminUid = "uid_admin"
        )
    )

    @Test
    fun deleteAccountButtonAppearsBelowLogout_andNoCancelKeepsUserLoggedIn() {
        var logoutClicks = 0
        var deleteCallbacks = 0

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    SettingsScreen(
                        userProfile = UserProfile(userId = "uid_member", displayName = "Bob Member", email = "bob@test.com"),
                        family = sampleFamily,
                        isAdmin = false,
                        familyMembers = sampleMembers,
                        securitySettings = SecuritySettings(),
                        userEmail = "bob@test.com",
                        firebaseUid = "uid_member",
                        hasGoogleLinked = false,
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
                        onLogout = { logoutClicks++ },
                        onConfirmDeleteAccount = { _, _ -> deleteCallbacks++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_delete_account_button"))

        val logoutNode = composeRule.onNodeWithTag("settings_logout_button")
        val deleteNode = composeRule.onNodeWithTag("settings_delete_account_button")

        logoutNode.assertIsDisplayed()
        deleteNode.assertIsDisplayed()

        val logoutBounds = logoutNode.getUnclippedBoundsInRoot()
        val deleteBounds = deleteNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Delete Account button must be placed immediately below Logout button",
            deleteBounds.top >= logoutBounds.bottom
        )

        // Click Delete Account -> verify exact dialog copy
        deleteNode.performClick()
        composeRule.onNodeWithText("Delete Account?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Are you sure you want to permanently delete your account and associated data? This action cannot be undone."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("No – Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Yes – Delete Account").assertIsDisplayed()

        // Clicking No - Cancel closes dialog and does NOT delete or sign out
        composeRule.onNodeWithTag("delete_account_no_button").performClick()
        composeRule.onNodeWithText("Delete Account?").assertDoesNotExist()
        assertEquals(0, deleteCallbacks)
        assertEquals(0, logoutClicks)
        deleteNode.assertIsDisplayed()
    }

    @Test
    fun memberYesDeleteAccount_invokesDeletionFlowDirectly() {
        var capturedAction: AdminDeleteAction? = null
        var capturedTarget: String? = "unset"

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    SettingsScreen(
                        userProfile = UserProfile(userId = "uid_member", displayName = "Bob Member", email = "bob@test.com"),
                        family = sampleFamily,
                        isAdmin = false,
                        familyMembers = sampleMembers,
                        securitySettings = SecuritySettings(),
                        userEmail = "bob@test.com",
                        firebaseUid = "uid_member",
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
                        onLogout = {},
                        onConfirmDeleteAccount = { action, target ->
                            capturedAction = action
                            capturedTarget = target
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_delete_account_button"))
        composeRule.onNodeWithTag("settings_delete_account_button").performClick()
        composeRule.onNodeWithTag("delete_account_yes_button").performClick()

        assertEquals(AdminDeleteAction.NONE, capturedAction)
        assertEquals(null, capturedTarget)
    }

    @Test
    fun adminYesDeleteAccount_requiresAdditionalConsequenceConfirmationOrOwnershipTransfer() {
        var capturedAction: AdminDeleteAction? = null
        var capturedTarget: String? = null

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    SettingsScreen(
                        userProfile = UserProfile(userId = "uid_admin", displayName = "Alice Admin", email = "alice@test.com"),
                        family = sampleFamily,
                        isAdmin = true,
                        familyMembers = sampleMembers,
                        securitySettings = SecuritySettings(),
                        userEmail = "alice@test.com",
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
                        onLogout = {},
                        onConfirmDeleteAccount = { action, target ->
                            capturedAction = action
                            capturedTarget = target
                        }
                    )
                }
            }
        }

        // Step 1: initial dialog -> Yes - Delete Account
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_delete_account_button"))
        composeRule.onNodeWithTag("settings_delete_account_button").performClick()
        composeRule.onNodeWithTag("delete_account_yes_button").performClick()

        // Step 2: Admin consequence dialog appears instead of immediate delete
        composeRule.onNodeWithTag("admin_delete_warning_title").assertIsDisplayed()
        assertEquals(null, capturedAction)

        // Switching to "Delete Entire Family" requires extra confirmation checkbox
        composeRule.onNodeWithTag("admin_option_delete_family").performScrollTo().performClick()
        composeRule.onNodeWithTag("admin_delete_confirm_button").assertIsNotEnabled()

        // Check extra confirmation -> button enables and triggers DELETE_ENTIRE_FAMILY
        composeRule.onNodeWithTag("admin_delete_family_extra_confirm_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("admin_delete_confirm_button").assertIsEnabled()

        // Now test switching to Ownership Transfer -> selects Bob and triggers TRANSFER_OWNERSHIP
        composeRule.onNodeWithTag("admin_option_transfer_ownership").performScrollTo().performClick()
        composeRule.onNodeWithTag("transfer_member_uid_member").performScrollTo().performClick()
        composeRule.onNodeWithTag("admin_delete_confirm_button").assertIsEnabled().performClick()

        assertEquals(AdminDeleteAction.TRANSFER_OWNERSHIP, capturedAction)
        assertEquals("uid_member", capturedTarget)
    }

    @Test
    fun partialFailureDialog_surfacesPendingCleanupStepsAndAllowsRetry() {
        var retried = false
        val partialState = DeleteAccountDialogState.PartialFailure(
            adminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY,
            transferTargetUid = null,
            completedSteps = listOf("Delete 1 encrypted file(s) from Firebase Storage"),
            pendingSteps = listOf(
                PendingCleanupStep(
                    stepId = "storage_doc_2",
                    label = "Delete Storage file for \"Passport\"",
                    detail = "Network timeout during Storage cleanup"
                )
            ),
            errorMessage = "Network timeout during Storage cleanup"
        )

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    DeleteAccountFlowDialogs(
                        state = partialState,
                        onVerifyPasswordAndRetry = {},
                        onVerifyGoogleAndRetry = {},
                        onRetryPartialCleanup = { retried = true },
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("partial_failure_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("pending_cleanup_step_storage_doc_2").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_cleanup_button").performClick()
        assertTrue(retried)
    }

    @Test
    fun loginScreenDisplaysSuccessfulAccountDeletionBanner() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.myfamilydoc.k7m2px")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-ui-test")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }
        val repo = AccountAuthRepository(FirebaseAuth.getInstance(app))

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    AuthScreen(
                        languageCode = "en",
                        onSelectLanguage = {},
                        onAuthSuccess = {},
                        accountAuthRepository = repo,
                        initialInfoMessage = EnglishStrings.accountDeletedSuccessMessage
                    )
                }
            }
        }

        composeRule.onNodeWithTag("auth_info_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(EnglishStrings.accountDeletedSuccessMessage).assertIsDisplayed()
    }
}
