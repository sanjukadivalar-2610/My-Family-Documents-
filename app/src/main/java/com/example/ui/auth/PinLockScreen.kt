package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.security.AndroidBiometricGateway
import com.example.data.security.BiometricGateway
import com.example.data.security.BiometricPromptOutcome
import com.example.data.security.BiometricSupportStatus
import com.example.data.security.findFragmentActivity
import com.example.data.security.toFeedback
import com.example.localization.LocalAppStrings
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent

enum class PinMode {
    UNLOCK,
    SETUP_OR_CHANGE
}

@Composable
fun PinLockScreen(
    mode: PinMode,
    biometricEnabled: Boolean,
    verifyPin: suspend (String) -> Boolean,
    onPinVerifiedOrSaved: (String) -> Unit,
    onCancelSetup: (() -> Unit)? = null,
    pinEnabled: Boolean = true,
    biometricGateway: BiometricGateway = AndroidBiometricGateway,
    onSignOut: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    val currentStrings by rememberUpdatedState(strings)
    val currentOnVerified by rememberUpdatedState(onPinVerifiedOrSaved)

    var enteredPin by remember { mutableStateOf("") }
    var firstPinForSetup by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingVerify by remember { mutableStateOf<String?>(null) }
    var autoPromptLaunched by remember(mode, biometricEnabled) { mutableStateOf(false) }

    val supportStatus = remember(activity, biometricEnabled, mode, biometricGateway) {
        if (!biometricEnabled || activity == null || mode != PinMode.UNLOCK) {
            BiometricSupportStatus.NO_HARDWARE
        } else {
            biometricGateway.checkSupport(activity)
        }
    }
    val canUseBiometric = supportStatus == BiometricSupportStatus.AVAILABLE
    val canUseDeviceCredential = remember(context, biometricEnabled, mode, pinEnabled, biometricGateway) {
        mode == PinMode.UNLOCK && biometricEnabled && !pinEnabled && biometricGateway.isDeviceCredentialSecure(context)
    }

    val launchBiometric: (Boolean) -> Unit = remember(
        activity,
        pinEnabled,
        biometricEnabled,
        supportStatus,
        biometricGateway
    ) {
        { allowDeviceCredential ->
            if (activity == null) {
                errorMsg = currentStrings.biometricNotSupportedMessage
            } else if (!allowDeviceCredential && supportStatus != BiometricSupportStatus.AVAILABLE) {
                errorMsg = supportStatus.toFeedback(currentStrings)?.message
                    ?: currentStrings.biometricNotSupportedMessage
            } else {
                biometricGateway.authenticate(
                    activity = activity,
                    title = currentStrings.biometricPromptTitle,
                    subtitle = currentStrings.biometricPromptSubtitle,
                    negativeButtonText = if (pinEnabled) currentStrings.useAppPinFallback else currentStrings.cancel,
                    allowDeviceCredentialFallback = allowDeviceCredential
                ) { outcome ->
                    when (outcome) {
                        BiometricPromptOutcome.Succeeded -> {
                            errorMsg = null
                            currentOnVerified("")
                        }

                        BiometricPromptOutcome.FailedAttempt -> {
                            errorMsg = currentStrings.biometricAuthFailedMessage
                        }

                        BiometricPromptOutcome.Cancelled -> {
                            if (!pinEnabled) {
                                errorMsg = currentStrings.biometricAuthCancelledMessage
                            }
                        }

                        is BiometricPromptOutcome.Error -> {
                            errorMsg = currentStrings.biometricAuthFailedMessage
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(
        mode,
        biometricEnabled,
        pinEnabled,
        canUseBiometric,
        canUseDeviceCredential,
        supportStatus
    ) {
        if (!autoPromptLaunched && mode == PinMode.UNLOCK && biometricEnabled) {
            if (canUseBiometric) {
                autoPromptLaunched = true
                launchBiometric(false)
            } else if (!pinEnabled && canUseDeviceCredential) {
                autoPromptLaunched = true
                launchBiometric(true)
            } else if (!pinEnabled && supportStatus != BiometricSupportStatus.AVAILABLE) {
                errorMsg = supportStatus.toFeedback(currentStrings)?.message
            }
        }
    }

    LaunchedEffect(pendingVerify) {
        val candidate = pendingVerify ?: return@LaunchedEffect
        val valid = verifyPin(candidate)
        if (valid) {
            errorMsg = null
            currentOnVerified(candidate)
        } else {
            errorMsg = currentStrings.wrongPinError
            enteredPin = ""
        }
        pendingVerify = null
    }

    fun onDigit(digit: String) {
        if (enteredPin.length >= 4) return
        errorMsg = null
        val updated = enteredPin + digit
        enteredPin = updated
        if (updated.length == 4) {
            if (mode == PinMode.UNLOCK) {
                pendingVerify = updated
            } else {
                val first = firstPinForSetup
                if (first == null) {
                    firstPinForSetup = updated
                    enteredPin = ""
                } else if (first == updated) {
                    currentOnVerified(updated)
                } else {
                    errorMsg = currentStrings.pinMismatchError
                    firstPinForSetup = null
                    enteredPin = ""
                }
            }
        }
    }

    val showPinKeypad = mode == PinMode.SETUP_OR_CHANGE || pinEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .testTag("vault_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NavyPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showPinKeypad) Icons.Rounded.Lock else Icons.Rounded.Fingerprint,
                    contentDescription = strings.appPinLock,
                    tint = SkyAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when {
                    mode == PinMode.UNLOCK -> strings.enterPinTitle
                    firstPinForSetup == null -> strings.setupPinTitle
                    else -> strings.confirmPinTitle
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (showPinKeypad) strings.pinSubtitle else strings.biometricLockSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (showPinKeypad) {
                // 4-dot PIN indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.testTag("pin_dots_row")
                ) {
                    repeat(4) { index ->
                        val filled = index < enteredPin.length
                        Surface(
                            modifier = Modifier.size(18.dp),
                            shape = CircleShape,
                            color = if (filled) NavyPrimary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                2.dp,
                                if (filled) SkyAccent else MaterialTheme.colorScheme.outline
                            )
                        ) {}
                    }
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("pin_lock_error_text")
                )
            } else if (showPinKeypad) {
                Spacer(modifier = Modifier.height(34.dp))
            }

            if (showPinKeypad) {
                // Keypad 1..9, Biometric/Empty, 0, Backspace
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "BIO" -> {
                                        if (canUseBiometric) {
                                            PinKeyButton(
                                                onClick = { launchBiometric(false) },
                                                tag = "pin_biometric_button"
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Fingerprint,
                                                    contentDescription = strings.unlockWithBiometric,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(72.dp))
                                        }
                                    }

                                    "DEL" -> {
                                        PinKeyButton(
                                            onClick = {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            },
                                            tag = "pin_backspace_button"
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                contentDescription = "Backspace",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    else -> {
                                        PinKeyButton(
                                            onClick = { onDigit(key) },
                                            tag = "pin_key_$key"
                                        ) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canUseBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { launchBiometric(false) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("unlock_biometric_cta")
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.unlockWithBiometric)
                    }
                }
            } else {
                // Biometric-only lock screen (when App PIN is not yet configured)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("biometric_lock_only_container"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (canUseBiometric) {
                        Button(
                            onClick = { launchBiometric(false) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyPrimary,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("unlock_biometric_cta")
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.unlockWithBiometric, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (canUseDeviceCredential) {
                        OutlinedButton(
                            onClick = { launchBiometric(true) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_device_credential_button")
                        ) {
                            Icon(Icons.Rounded.PhonelinkLock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.unlockWithDeviceCredential, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (onSignOut != null) {
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("lock_sign_out_button")
                        ) {
                            Text(strings.signOutDifferentAccount)
                        }
                    }
                }
            }

            if (onCancelSetup != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onCancelSetup,
                    modifier = Modifier.testTag("pin_cancel_button")
                ) {
                    Text(strings.cancel)
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    onClick: () -> Unit,
    tag: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
