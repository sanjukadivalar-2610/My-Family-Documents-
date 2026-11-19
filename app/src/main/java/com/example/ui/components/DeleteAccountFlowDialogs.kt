package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.firebase.AdminDeleteAction
import com.example.data.firebase.PendingCleanupStep
import com.example.localization.LocalAppStrings
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite

sealed interface DeleteAccountDialogState {
    data object Idle : DeleteAccountDialogState

    data object DeletingInProgress : DeleteAccountDialogState

    data class NeedsReauthentication(
        val adminAction: AdminDeleteAction,
        val transferTargetUid: String?,
        val hasPasswordProvider: Boolean,
        val hasGoogleProvider: Boolean,
        val email: String,
        val reason: String,
        val errorMessage: String? = null,
        val isVerifying: Boolean = false
    ) : DeleteAccountDialogState

    data class PartialFailure(
        val adminAction: AdminDeleteAction,
        val transferTargetUid: String?,
        val completedSteps: List<String>,
        val pendingSteps: List<PendingCleanupStep>,
        val errorMessage: String
    ) : DeleteAccountDialogState

    data class Failed(
        val errorMessage: String
    ) : DeleteAccountDialogState
}

@Composable
fun DeleteAccountFlowDialogs(
    state: DeleteAccountDialogState,
    onVerifyPasswordAndRetry: (password: String) -> Unit,
    onVerifyGoogleAndRetry: () -> Unit,
    onRetryPartialCleanup: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current

    when (state) {
        DeleteAccountDialogState.Idle -> Unit

        DeleteAccountDialogState.DeletingInProgress -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(strings.deleteAccountDialogTitle) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = strings.deletingAccountProgress,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {},
                modifier = Modifier.testTag("delete_account_progress_dialog")
            )
        }

        is DeleteAccountDialogState.NeedsReauthentication -> {
            var password by remember(state.email) { mutableStateOf("") }
            var visible by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!state.isVerifying) onDismiss() },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(strings.reauthRequiredTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = state.reason,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.email.isNotBlank()) {
                            OutlinedTextField(
                                value = state.email,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strings.emailLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (state.hasPasswordProvider) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(strings.passwordLabel) },
                                leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { visible = !visible },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (visible) strings.hidePasswordDesc else strings.showPasswordDesc
                                        )
                                    }
                                },
                                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !state.isVerifying,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reauth_password_input")
                            )
                        }
                        if (!state.errorMessage.isNullOrBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("reauth_error_text")
                            )
                        }
                        if (state.hasGoogleProvider) {
                            OutlinedButton(
                                onClick = onVerifyGoogleAndRetry,
                                enabled = !state.isVerifying,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reauth_google_button")
                            ) {
                                Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.reauthVerifyGoogleButton)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (state.hasPasswordProvider) {
                        Button(
                            onClick = { onVerifyPasswordAndRetry(password) },
                            enabled = !state.isVerifying && password.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("reauth_password_submit_button")
                        ) {
                            Text(strings.reauthVerifyPasswordButton)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isVerifying,
                        modifier = Modifier.testTag("reauth_cancel_button")
                    ) {
                        Text(strings.cancel)
                    }
                },
                modifier = Modifier.testTag("reauth_delete_dialog")
            )
        }

        is DeleteAccountDialogState.PartialFailure -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = strings.partialCleanupTitle,
                        modifier = Modifier.testTag("partial_failure_title")
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.partialCleanupBody,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (state.completedSteps.isNotEmpty()) {
                            Text(
                                text = strings.completedStepsHeading,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            state.completedSteps.forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(text = step, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Text(
                            text = strings.pendingStepsHeading,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        state.pendingSteps.forEach { pending ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pending_cleanup_step_${pending.stepId}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = pending.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = pending.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onRetryPartialCleanup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.testTag("retry_cleanup_button")
                    ) {
                        Text(strings.retryCleanupButton)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("partial_failure_close_button")
                    ) {
                        Text(strings.close)
                    }
                },
                modifier = Modifier.testTag("partial_failure_dialog")
            )
        }

        is DeleteAccountDialogState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(strings.deleteAccountDialogTitle) },
                text = {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("delete_error_message")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("delete_error_close_button")
                    ) {
                        Text(strings.close)
                    }
                },
                modifier = Modifier.testTag("delete_error_dialog")
            )
        }
    }
}
