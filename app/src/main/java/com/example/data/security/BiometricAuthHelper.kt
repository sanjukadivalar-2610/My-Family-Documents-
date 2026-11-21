package com.example.data.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.localization.AppStrings

enum class BiometricSupportStatus {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNKNOWN
}

data class BiometricUiFeedback(
    val message: String,
    val isError: Boolean = false,
    val needsEnrollmentAction: Boolean = false
)

sealed interface BiometricPromptOutcome {
    data object Succeeded : BiometricPromptOutcome
    data object FailedAttempt : BiometricPromptOutcome
    data object Cancelled : BiometricPromptOutcome
    data class Error(val errorCode: Int, val message: String) : BiometricPromptOutcome
}

interface BiometricGateway {
    fun checkSupport(context: Context): BiometricSupportStatus
    fun isDeviceCredentialSecure(context: Context): Boolean
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String?,
        allowDeviceCredentialFallback: Boolean = false,
        onOutcome: (BiometricPromptOutcome) -> Unit
    )
}

object AndroidBiometricGateway : BiometricGateway {
    private const val TAG = "AndroidBiometricGateway"

    const val BIOMETRIC_AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    override fun checkSupport(context: Context): BiometricSupportStatus {
        return try {
            val manager = BiometricManager.from(context)
            mapCanAuthenticateResult(manager.canAuthenticate(BIOMETRIC_AUTHENTICATORS))
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to query BiometricManager.canAuthenticate()", t)
            BiometricSupportStatus.UNKNOWN
        }
    }

    override fun isDeviceCredentialSecure(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure == true
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String?,
        allowDeviceCredentialFallback: Boolean,
        onOutcome: (BiometricPromptOutcome) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onOutcome(BiometricPromptOutcome.Error(BiometricPrompt.ERROR_CANCELED, "Activity unavailable"))
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            var hadFailedAttempt = false
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onOutcome(BiometricPromptOutcome.Succeeded)
                    }

                    override fun onAuthenticationFailed() {
                        hadFailedAttempt = true
                        onOutcome(BiometricPromptOutcome.FailedAttempt)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val isCancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (isCancel && !hadFailedAttempt) {
                            onOutcome(BiometricPromptOutcome.Cancelled)
                        } else {
                            onOutcome(
                                BiometricPromptOutcome.Error(
                                    errorCode = errorCode,
                                    message = errString.toString()
                                )
                            )
                        }
                    }
                }
            )

            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (allowDeviceCredentialFallback) {
                val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    BIOMETRIC_AUTHENTICATORS or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                }
                builder.setAllowedAuthenticators(authenticators)
            } else {
                builder.setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
                builder.setNegativeButtonText(
                    negativeButtonText?.takeIf { it.isNotBlank() } ?: "Cancel"
                )
            }

            prompt.authenticate(builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch BiometricPrompt", t)
            onOutcome(
                BiometricPromptOutcome.Error(
                    errorCode = BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    message = t.localizedMessage ?: "Biometric prompt error"
                )
            )
        }
    }

    fun mapCanAuthenticateResult(result: Int): BiometricSupportStatus = when (result) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricSupportStatus.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricSupportStatus.NO_HARDWARE
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricSupportStatus.HW_UNAVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricSupportStatus.NONE_ENROLLED
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            BiometricSupportStatus.SECURITY_UPDATE_REQUIRED
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricSupportStatus.NO_HARDWARE
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricSupportStatus.UNKNOWN
        else -> BiometricSupportStatus.UNKNOWN
    }
}

fun BiometricSupportStatus.toFeedback(strings: AppStrings): BiometricUiFeedback? = when (this) {
    BiometricSupportStatus.AVAILABLE -> null
    BiometricSupportStatus.NO_HARDWARE -> BiometricUiFeedback(
        message = strings.biometricNotSupportedMessage,
        isError = true,
        needsEnrollmentAction = false
    )
    BiometricSupportStatus.HW_UNAVAILABLE -> BiometricUiFeedback(
        message = strings.biometricHwUnavailableMessage,
        isError = true,
        needsEnrollmentAction = false
    )
    BiometricSupportStatus.NONE_ENROLLED -> BiometricUiFeedback(
        message = strings.biometricNoneEnrolledMessage,
        isError = true,
        needsEnrollmentAction = true
    )
    BiometricSupportStatus.SECURITY_UPDATE_REQUIRED -> BiometricUiFeedback(
        message = strings.biometricSecurityUpdateRequiredMessage,
        isError = true,
        needsEnrollmentAction = false
    )
    BiometricSupportStatus.UNKNOWN -> BiometricUiFeedback(
        message = strings.biometricStatusUnknownMessage,
        isError = true,
        needsEnrollmentAction = false
    )
}

fun buildBiometricEnrollIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                AndroidBiometricGateway.BIOMETRIC_AUTHENTICATORS
            )
        }
        if (enrollIntent.resolveActivity(context.packageManager) != null) {
            return enrollIntent
        }
    }
    val securityIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
    return if (securityIntent.resolveActivity(context.packageManager) != null) {
        securityIntent
    } else {
        Intent(Settings.ACTION_SETTINGS)
    }
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
