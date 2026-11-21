package com.example.security

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.BiometricGateway
import com.example.data.security.BiometricPromptOutcome
import com.example.data.security.BiometricSupportStatus
import com.example.data.security.BiometricUiFeedback
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.domain.model.FamilyAccount
import com.example.domain.model.UserProfile
import com.example.localization.EnglishStrings
import com.example.localization.LocalAppStrings
import com.example.ui.auth.PinLockScreen
import com.example.ui.auth.PinMode
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyFamilyDocTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class BiometricLockUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var appContext: Application
    private lateinit var firebaseApp: FirebaseApp
    private lateinit var securityPreferences: SecurityPreferences
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        firebaseApp = if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.myfamilydoc.k7m2px")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-ui-test")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }
        securityPreferences = SecurityPreferences(appContext)
        runBlocking {
            securityPreferences.clearAccountLocalData()
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        runBlocking {
            securityPreferences.clearAccountLocalData()
        }
    }

    private fun buildFragmentActivity(): FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    private fun createTestViewModel(): MainViewModel {
        val repo = FamilyVaultRepository(
            db = FirebaseFirestore.getInstance(firebaseApp),
            auth = FirebaseAuth.getInstance(firebaseApp),
            storage = null,
            isOnlineProvider = { false }
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    repository = repo,
                    cryptoManager = DocumentCryptoManager(appContext),
                    securityPreferences = securityPreferences,
                    appContext = appContext,
                    currentUserId = "uid_bio_ui_test"
                ) as T
            }
        }
        return ViewModelProvider(viewModelStore, factory)["vm_ui", MainViewModel::class.java]
    }

    private class FakeBiometricGateway(
        var supportStatus: BiometricSupportStatus = BiometricSupportStatus.AVAILABLE,
        var deviceCredentialSecure: Boolean = true,
        var nextOutcome: BiometricPromptOutcome? = null
    ) : BiometricGateway {
        var authCalls = 0
        private var pendingCallback: ((BiometricPromptOutcome) -> Unit)? = null

        override fun checkSupport(context: Context): BiometricSupportStatus = supportStatus

        override fun isDeviceCredentialSecure(context: Context): Boolean = deviceCredentialSecure

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            negativeButtonText: String?,
            allowDeviceCredentialFallback: Boolean,
            onOutcome: (BiometricPromptOutcome) -> Unit
        ) {
            authCalls++
            pendingCallback = onOutcome
            nextOutcome?.let { onOutcome(it) }
        }

        fun dispatchOutcome(outcome: BiometricPromptOutcome) {
            pendingCallback?.invoke(outcome)
        }
    }

    @Test
    fun `settings screen allows enabling biometric without PIN and shows enrollment feedback`() {
        var toggledTo: Boolean? = null
        var openSettingsClicks = 0

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
                    SettingsScreen(
                        userProfile = UserProfile(userId = "u1", displayName = "Sanju", email = "s@test.com"),
                        family = FamilyAccount(familyId = "f1", familyName = "Family", familyCode = "MFD-1", adminUid = "u1"),
                        isAdmin = true,
                        securitySettings = SecuritySettings(pinEnabled = false, biometricEnabled = false),
                        userEmail = "s@test.com",
                        firebaseUid = "u1",
                        hasGoogleLinked = true,
                        hasEmailPasswordLinked = true,
                        onLinkGoogleAccount = {},
                        onOpenCreatePassword = {},
                        onSendPasswordResetEmail = {},
                        onSelectLanguage = {},
                        onSelectThemeMode = {},
                        onSetupOrChangePin = {},
                        onDisablePin = {},
                        onToggleBiometric = { toggledTo = it },
                        onToggleExpiryReminders = {},
                        onCheckExpiriesNow = {},
                        onOpenBackupCenter = {},
                        onOpenAdminPanel = {},
                        onLogout = {},
                        biometricFeedback = BiometricUiFeedback(
                            message = EnglishStrings.biometricNoneEnrolledMessage,
                            isError = true,
                            needsEnrollmentAction = true
                        ),
                        onOpenDeviceBiometricSettings = { openSettingsClicks++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_biometric_switch"))
        composeRule.onNodeWithTag("settings_biometric_switch")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(true, toggledTo)

        composeRule.onNodeWithTag("settings_biometric_feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Please set up biometric authentication in Android Settings first.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_biometric_enroll_button")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, openSettingsClicks)
    }

    @Test
    fun `pin lock screen supports biometric unlock, error feedback, and PIN fallback`() {
        val fragmentActivity = buildFragmentActivity()
        val gateway = FakeBiometricGateway(supportStatus = BiometricSupportStatus.AVAILABLE)
        var unlockedCount = 0

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(
                    LocalAppStrings provides EnglishStrings,
                    LocalContext provides fragmentActivity
                ) {
                    PinLockScreen(
                        mode = PinMode.UNLOCK,
                        pinEnabled = true,
                        biometricEnabled = true,
                        verifyPin = { it == "1234" },
                        onPinVerifiedOrSaved = { unlockedCount++ },
                        biometricGateway = gateway
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals("BiometricPrompt must auto-launch on lock screen", 1, gateway.authCalls)

        composeRule.runOnIdle { gateway.dispatchOutcome(BiometricPromptOutcome.FailedAttempt) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pin_lock_error_text").assertIsDisplayed()
        composeRule.onNodeWithText("Authentication failed. Please try again.").assertIsDisplayed()
        assertEquals(0, unlockedCount)

        listOf("9", "9", "9", "9").forEach { digit ->
            composeRule.onNodeWithTag("pin_key_$digit").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(EnglishStrings.wrongPinError).assertIsDisplayed()
        assertEquals(0, unlockedCount)

        listOf("1", "2", "3", "4").forEach { digit ->
            composeRule.onNodeWithTag("pin_key_$digit").performClick()
        }
        composeRule.waitForIdle()
        assertEquals(1, unlockedCount)
    }

    @Test
    fun `documents are never exposed before security settings load and biometric auth succeeds`() {
        val fragmentActivity = buildFragmentActivity()
        val gateway = FakeBiometricGateway(supportStatus = BiometricSupportStatus.AVAILABLE)
        runBlocking {
            securityPreferences.setBiometricEnabled(true)
        }
        val viewModel = createTestViewModel()

        composeRule.setContent {
            MyFamilyDocTheme {
                CompositionLocalProvider(
                    LocalAppStrings provides EnglishStrings,
                    LocalContext provides fragmentActivity
                ) {
                    val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
                    val loaded by viewModel.isSecuritySettingsLoaded.collectAsStateWithLifecycle()
                    val unlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()

                    if (!loaded) {
                        Text("Loading", modifier = Modifier.testTag("security_loading"))
                    } else if ((settings.pinEnabled || settings.biometricEnabled) && !unlocked) {
                        PinLockScreen(
                            mode = PinMode.UNLOCK,
                            pinEnabled = settings.pinEnabled,
                            biometricEnabled = settings.biometricEnabled,
                            verifyPin = viewModel::verifyPin,
                            onPinVerifiedOrSaved = { viewModel.unlockVault() },
                            biometricGateway = gateway
                        )
                    } else {
                        Text(
                            text = "Secret Aadhaar Doc",
                            modifier = Modifier.testTag("protected_family_doc_title")
                        )
                    }
                }
            }
        }

        val deadline = System.currentTimeMillis() + 5_000L
        while (!viewModel.isSecuritySettingsLoaded.value && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            Thread.sleep(15)
        }
        assertTrue(viewModel.isSecuritySettingsLoaded.value)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("vault_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("biometric_lock_only_container").assertIsDisplayed()
        composeRule.onNodeWithText("Secret Aadhaar Doc").assertDoesNotExist()

        composeRule.runOnIdle { gateway.dispatchOutcome(BiometricPromptOutcome.Cancelled) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(EnglishStrings.biometricAuthCancelledMessage).assertIsDisplayed()
        composeRule.onNodeWithText("Secret Aadhaar Doc").assertDoesNotExist()

        composeRule.onNodeWithTag("unlock_biometric_cta").performClick()
        composeRule.runOnIdle { gateway.dispatchOutcome(BiometricPromptOutcome.Succeeded) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("protected_family_doc_title").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_lock_screen").assertDoesNotExist()

        composeRule.runOnIdle { viewModel.lockVault() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vault_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Secret Aadhaar Doc").assertDoesNotExist()
    }
}
