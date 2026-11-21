package com.example.security

import android.app.Application
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.AndroidBiometricGateway
import com.example.data.security.BiometricGateway
import com.example.data.security.BiometricPromptOutcome
import com.example.data.security.BiometricSupportStatus
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.data.security.buildBiometricEnrollIntent
import com.example.data.security.toFeedback
import com.example.localization.EnglishStrings
import com.example.localization.KannadaStrings
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiometricGatewayAndViewModelTest {

    private lateinit var appContext: Application
    private lateinit var firebaseApp: FirebaseApp
    private lateinit var securityPreferences: SecurityPreferences
    private val viewModelStore = ViewModelStore()
    private val activityControllers = mutableListOf<ActivityController<FragmentActivity>>()
    private var vmCounter = 0

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        firebaseApp = if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.myfamilydoc.k7m2px")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-test")
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
        activityControllers.forEach { controller ->
            runCatching { controller.pause().stop().destroy() }
        }
        activityControllers.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking {
            withTimeout(3_000L) {
                securityPreferences.clearAccountLocalData()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createTestViewModel(
        prefs: SecurityPreferences = securityPreferences,
        uid: String = "uid_bio_test"
    ): MainViewModel {
        val repo = FamilyVaultRepository(
            db = FirebaseFirestore.getInstance(firebaseApp),
            auth = FirebaseAuth.getInstance(firebaseApp),
            storage = null,
            isOnlineProvider = { false }
        )
        vmCounter++
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    repository = repo,
                    cryptoManager = DocumentCryptoManager(appContext),
                    securityPreferences = prefs,
                    appContext = appContext,
                    currentUserId = uid
                ) as T
            }
        }
        return ViewModelProvider(viewModelStore, factory)["vm_$vmCounter", MainViewModel::class.java]
    }

    private fun buildFragmentActivity(): FragmentActivity {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activityControllers.add(controller)
        return controller.get()
    }

    private class FakeBiometricGateway(
        var supportStatus: BiometricSupportStatus = BiometricSupportStatus.AVAILABLE,
        var deviceCredentialSecure: Boolean = true,
        var nextOutcome: BiometricPromptOutcome? = BiometricPromptOutcome.Succeeded
    ) : BiometricGateway {
        var authCalls = 0
        var lastAllowDeviceCredentialFallback: Boolean? = null
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
            lastAllowDeviceCredentialFallback = allowDeviceCredentialFallback
            pendingCallback = onOutcome
            nextOutcome?.let { onOutcome(it) }
        }

        fun dispatchOutcome(outcome: BiometricPromptOutcome) {
            pendingCallback?.invoke(outcome)
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition not met within ${timeoutMs}ms", predicate())
    }

    private fun readPersistedBiometricEnabled(prefs: SecurityPreferences = securityPreferences): Boolean {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return runBlocking {
            withTimeout(3_000L) {
                prefs.settingsFlow.first().biometricEnabled
            }
        }
    }

    @Test
    fun `biometric manager status constants map accurately and produce localized feedback`() {
        assertEquals(
            BiometricSupportStatus.AVAILABLE,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_SUCCESS)
        )
        assertEquals(
            BiometricSupportStatus.NO_HARDWARE,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
        )
        assertEquals(
            BiometricSupportStatus.HW_UNAVAILABLE,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
        )
        assertEquals(
            BiometricSupportStatus.NONE_ENROLLED,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
        )
        assertEquals(
            BiometricSupportStatus.SECURITY_UPDATE_REQUIRED,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED)
        )
        assertEquals(
            BiometricSupportStatus.UNKNOWN,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_STATUS_UNKNOWN)
        )
        assertEquals(
            BiometricSupportStatus.NO_HARDWARE,
            AndroidBiometricGateway.mapCanAuthenticateResult(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)
        )

        assertNull(BiometricSupportStatus.AVAILABLE.toFeedback(EnglishStrings))
        assertEquals(
            "Biometric authentication is not supported on this device.",
            BiometricSupportStatus.NO_HARDWARE.toFeedback(EnglishStrings)?.message
        )
        val noneEnrolled = BiometricSupportStatus.NONE_ENROLLED.toFeedback(EnglishStrings)
        assertEquals(
            "Please set up biometric authentication in Android Settings first.",
            noneEnrolled?.message
        )
        assertTrue(noneEnrolled?.needsEnrollmentAction == true)
        assertNotNull(BiometricSupportStatus.HW_UNAVAILABLE.toFeedback(KannadaStrings)?.message)
        assertNotNull(BiometricSupportStatus.SECURITY_UPDATE_REQUIRED.toFeedback(KannadaStrings)?.message)
        assertNotNull(BiometricSupportStatus.UNKNOWN.toFeedback(KannadaStrings)?.message)

        val enrollIntent = buildBiometricEnrollIntent(appContext)
        assertTrue(
            enrollIntent.action == Settings.ACTION_BIOMETRIC_ENROLL ||
                enrollIntent.action == Settings.ACTION_SECURITY_SETTINGS ||
                enrollIntent.action == Settings.ACTION_SETTINGS
        )
    }

    @Test
    fun `unsupported or unenrolled hardware keeps biometric OFF and surfaces exact message`() {
        val activity = buildFragmentActivity()
        val viewModel = createTestViewModel()
        val statuses = listOf(
            BiometricSupportStatus.NO_HARDWARE to "Biometric authentication is not supported on this device.",
            BiometricSupportStatus.NONE_ENROLLED to "Please set up biometric authentication in Android Settings first.",
            BiometricSupportStatus.HW_UNAVAILABLE to EnglishStrings.biometricHwUnavailableMessage,
            BiometricSupportStatus.SECURITY_UPDATE_REQUIRED to EnglishStrings.biometricSecurityUpdateRequiredMessage,
            BiometricSupportStatus.UNKNOWN to EnglishStrings.biometricStatusUnknownMessage
        )

        statuses.forEach { (status, expectedMsg) ->
            val gateway = FakeBiometricGateway(supportStatus = status)
            viewModel.handleBiometricToggleRequest(
                requestedEnabled = true,
                activity = activity,
                strings = EnglishStrings,
                gateway = gateway
            )
            assertEquals("Prompt must not launch for $status", 0, gateway.authCalls)
            assertEquals(expectedMsg, viewModel.biometricUiFeedback.value?.message)
            assertTrue(viewModel.biometricUiFeedback.value?.isError == true)
            assertEquals(
                status == BiometricSupportStatus.NONE_ENROLLED,
                viewModel.biometricUiFeedback.value?.needsEnrollmentAction
            )
            assertFalse(readPersistedBiometricEnabled())
        }

        // Null activity also fails safely without crashing
        viewModel.handleBiometricToggleRequest(
            requestedEnabled = true,
            activity = null,
            strings = EnglishStrings,
            gateway = FakeBiometricGateway(supportStatus = BiometricSupportStatus.AVAILABLE)
        )
        assertTrue(viewModel.biometricUiFeedback.value?.isError == true)
        assertFalse(readPersistedBiometricEnabled())
    }

    @Test
    fun `enabling biometric succeeds only after prompt authentication and persists across restarts`() {
        val activity = buildFragmentActivity()
        val viewModel = createTestViewModel()
        val gateway = FakeBiometricGateway(
            supportStatus = BiometricSupportStatus.AVAILABLE,
            nextOutcome = BiometricPromptOutcome.Succeeded
        )

        viewModel.handleBiometricToggleRequest(
            requestedEnabled = true,
            activity = activity,
            strings = EnglishStrings,
            gateway = gateway
        )

        assertEquals(1, gateway.authCalls)
        assertEquals(false, gateway.lastAllowDeviceCredentialFallback)
        assertFalse(viewModel.isBiometricPromptActive)
        assertEquals(
            "Biometric authentication enabled successfully.",
            viewModel.biometricUiFeedback.value?.message
        )
        assertFalse(viewModel.biometricUiFeedback.value?.isError == true)
        assertTrue(viewModel.isVaultUnlocked.value)

        waitUntil { viewModel.securitySettings.value.biometricEnabled }
        assertTrue(readPersistedBiometricEnabled(SecurityPreferences(appContext)))

        // Simulate closing and reopening the app with a fresh SecurityPreferences & ViewModel
        val reopenedViewModel = createTestViewModel(prefs = SecurityPreferences(appContext))
        waitUntil { reopenedViewModel.isSecuritySettingsLoaded.value }
        assertTrue(reopenedViewModel.securitySettings.value.biometricEnabled)
        assertFalse("Reopened app must start locked when biometric is enabled", reopenedViewModel.isVaultUnlocked.value)
    }

    @Test
    fun `biometric failure or cancellation keeps setting OFF without crashing`() {
        val activity = buildFragmentActivity()
        val viewModel = createTestViewModel()

        // 1. FailedAttempt then Error
        val failGateway = FakeBiometricGateway(
            supportStatus = BiometricSupportStatus.AVAILABLE,
            nextOutcome = null
        )
        viewModel.handleBiometricToggleRequest(
            requestedEnabled = true,
            activity = activity,
            strings = EnglishStrings,
            gateway = failGateway
        )
        assertTrue(viewModel.isBiometricPromptActive)

        failGateway.dispatchOutcome(BiometricPromptOutcome.FailedAttempt)
        assertEquals("Authentication failed. Please try again.", viewModel.biometricUiFeedback.value?.message)
        assertTrue(viewModel.biometricUiFeedback.value?.isError == true)
        assertFalse(readPersistedBiometricEnabled())

        failGateway.dispatchOutcome(BiometricPromptOutcome.Error(7, "Locked out"))
        assertFalse(viewModel.isBiometricPromptActive)
        assertEquals("Authentication failed. Please try again.", viewModel.biometricUiFeedback.value?.message)
        assertFalse(readPersistedBiometricEnabled())

        // 2. Clean cancellation
        val cancelGateway = FakeBiometricGateway(
            supportStatus = BiometricSupportStatus.AVAILABLE,
            nextOutcome = BiometricPromptOutcome.Cancelled
        )
        viewModel.handleBiometricToggleRequest(
            requestedEnabled = true,
            activity = activity,
            strings = EnglishStrings,
            gateway = cancelGateway
        )
        assertFalse(viewModel.isBiometricPromptActive)
        assertEquals(EnglishStrings.biometricAuthCancelledMessage, viewModel.biometricUiFeedback.value?.message)
        assertFalse(readPersistedBiometricEnabled())
    }

    @Test
    fun `disabling biometric turns OFF biometric lock while preserving existing App PIN`() {
        val activity = buildFragmentActivity()
        val viewModel = createTestViewModel()
        runBlocking {
            securityPreferences.setPin("1234")
            securityPreferences.setBiometricEnabled(true)
        }
        waitUntil { viewModel.securitySettings.value.biometricEnabled && viewModel.securitySettings.value.pinEnabled }

        viewModel.handleBiometricToggleRequest(
            requestedEnabled = false,
            activity = activity,
            strings = EnglishStrings,
            gateway = FakeBiometricGateway()
        )

        waitUntil { !viewModel.securitySettings.value.biometricEnabled }
        assertTrue("Disabling biometric must preserve PIN", viewModel.securitySettings.value.pinEnabled)
        assertEquals(EnglishStrings.biometricDisabledSuccessMessage, viewModel.biometricUiFeedback.value?.message)

        // Conversely, disabling PIN must preserve biometricEnabled if enabled
        runBlocking {
            securityPreferences.setBiometricEnabled(true)
            securityPreferences.disablePin()
        }
        waitUntil { !viewModel.securitySettings.value.pinEnabled }
        assertTrue(viewModel.securitySettings.value.biometricEnabled)
    }
}
