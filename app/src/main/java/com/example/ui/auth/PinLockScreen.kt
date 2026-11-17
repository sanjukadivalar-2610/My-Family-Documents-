package com.example.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
    onCancelSetup: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var enteredPin by remember { mutableStateOf("") }
    var firstPinForSetup by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingVerify by remember { mutableStateOf<String?>(null) }

    val canUseBiometric = remember(activity, biometricEnabled) {
        if (!biometricEnabled || activity == null || mode != PinMode.UNLOCK) return@remember false
        val mgr = BiometricManager.from(activity)
        mgr.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val launchBiometric: () -> Unit = remember(activity) {
        {
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onPinVerifiedOrSaved("")
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(strings.biometricPromptTitle)
                    .setSubtitle(strings.biometricPromptSubtitle)
                    .setNegativeButtonText(strings.cancel)
                    .build()
                prompt.authenticate(promptInfo)
            }
        }
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) {
            launchBiometric()
        }
    }

    LaunchedEffect(pendingVerify) {
        val candidate = pendingVerify ?: return@LaunchedEffect
        val valid = verifyPin(candidate)
        if (valid) {
            onPinVerifiedOrSaved(candidate)
        } else {
            errorMsg = strings.wrongPinError
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
                    onPinVerifiedOrSaved(updated)
                } else {
                    errorMsg = strings.pinMismatchError
                    firstPinForSetup = null
                    enteredPin = ""
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
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
                    imageVector = Icons.Rounded.Lock,
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
                text = strings.pinSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4-dot PIN indicator
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

            if (!errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(34.dp))
            }

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
                                            onClick = launchBiometric,
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
                    onClick = launchBiometric,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("unlock_biometric_cta")
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.unlockWithBiometric)
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
