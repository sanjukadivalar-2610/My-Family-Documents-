package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyCredentialsOverview
import com.example.domain.model.MemberCredentialSummary
import com.example.localization.LocalAppStrings
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkySoft

private val USERNAME_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,31}$")

private fun isStrongPassword(password: String): Boolean =
    password.length in 8..128 &&
        password.any { it.isLetter() } &&
        password.any { it.isDigit() }

@Composable
fun FamilyCredentialsCard(
    family: FamilyAccount,
    credentialsOverview: FamilyCredentialsOverview?,
    isActionBusy: Boolean,
    onSetupClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onCopiedUsername: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val activeUsername = credentialsOverview?.familyUsername?.takeIf { it.isNotBlank() }
        ?: family.familyUsername.orEmpty()
    val hasConfigured = activeUsername.isNotBlank() &&
        (credentialsOverview?.hasFamilyPassword == true || family.hasFamilyPassword)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.familyCredentialsSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.familyCredentialsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                if (hasConfigured) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SkySoft,
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(activeUsername))
                                onCopiedUsername("@$activeUsername")
                            }
                            .testTag("admin_family_username_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${strings.familyUsernameConfiguredPrefix}$activeUsername",
                                style = MaterialTheme.typography.labelLarge,
                                color = NavyPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = activeUsername,
                                tint = NavyPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = strings.familyCredentialsNotSet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSetupClick,
                        enabled = !isActionBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_setup_family_credentials_button")
                    ) {
                        Text(
                            text = if (hasConfigured) strings.updateFamilyLoginButton else strings.setFamilyLoginButton,
                            maxLines = 1
                        )
                    }
                    if (hasConfigured) {
                        OutlinedButton(
                            onClick = onResetPasswordClick,
                            enabled = !isActionBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_reset_family_password_button")
                        ) {
                            Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.resetFamilyPasswordButton, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberLoginsCard(
    credentialsOverview: FamilyCredentialsOverview?,
    isActionBusy: Boolean,
    onRefresh: () -> Unit,
    onCreateMemberLoginClick: () -> Unit,
    onResetMemberPasswordClick: (MemberCredentialSummary) -> Unit,
    onRevokeMemberClick: (MemberCredentialSummary) -> Unit
) {
    val strings = LocalAppStrings.current
    val memberAccounts = credentialsOverview?.memberAccounts.orEmpty()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ManageAccounts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${strings.memberLoginsSection} (${memberAccounts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isActionBusy,
                        modifier = Modifier.testTag("admin_refresh_credentials_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = strings.refreshList)
                    }
                }

                Text(
                    text = strings.memberLoginsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCreateMemberLoginClick,
                    enabled = !isActionBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyPrimary,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_create_member_login_button")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.createMemberLoginButton)
                }

                if (memberAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    memberAccounts.forEachIndexed { idx, acct ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${acct.memberName} (${acct.relation})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "@${acct.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (acct.active) SkySoft else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = if (acct.active) strings.statusActive else strings.statusRevoked,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (acct.active) NavyPrimary else MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (acct.active) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onResetMemberPasswordClick(acct) },
                                        enabled = !isActionBusy,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("member_reset_pwd_${acct.username}")
                                    ) {
                                        Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.resetPasswordShort)
                                    }
                                    OutlinedButton(
                                        onClick = { onRevokeMemberClick(acct) },
                                        enabled = !isActionBusy,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("member_revoke_${acct.username}")
                                    ) {
                                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.revokeAccessShort)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupFamilyCredentialsDialog(
    initialUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val cleanUser = username.trim().lowercase()
    val userValid = USERNAME_PATTERN.matches(cleanUser)
    val pwdValid = isStrongPassword(password)
    val matches = password == confirmPassword && confirmPassword.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.familyCredentialsSection) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.familyOrMemberUsernameLabel) },
                    supportingText = { Text(strings.usernameFormatHint) },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                    isError = username.isNotEmpty() && !userValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("setup_family_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.passwordLabel) },
                    supportingText = { Text(strings.passwordPolicyHint) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) strings.hidePasswordDesc else strings.showPasswordDesc
                            )
                        }
                    },
                    isError = password.isNotEmpty() && !pwdValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("setup_family_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(strings.confirmPasswordLabel) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("setup_family_confirm_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(cleanUser, password) },
                enabled = userValid && pwdValid && matches,
                modifier = Modifier.testTag("confirm_setup_family_credentials_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun CreateMemberCredentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, relation: String, username: String, password: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var memberName by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Member") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val cleanUser = username.trim().lowercase()
    val valid = memberName.trim().isNotBlank() &&
        USERNAME_PATTERN.matches(cleanUser) &&
        isStrongPassword(password) &&
        password == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.createMemberLoginButton) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    label = { Text(strings.memberNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_member_name_input")
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text(strings.relationLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_member_relation_input")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.memberUsernameLabel) },
                    supportingText = { Text(strings.usernameFormatHint) },
                    isError = username.isNotEmpty() && !USERNAME_PATTERN.matches(cleanUser),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().testTag("create_member_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.passwordLabel) },
                    supportingText = { Text(strings.passwordPolicyHint) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) strings.hidePasswordDesc else strings.showPasswordDesc
                            )
                        }
                    },
                    isError = password.isNotEmpty() && !isStrongPassword(password),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("create_member_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(strings.confirmPasswordLabel) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("create_member_confirm_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        memberName.trim(),
                        relation.trim().ifBlank { "Member" },
                        cleanUser,
                        password
                    )
                },
                enabled = valid,
                modifier = Modifier.testTag("confirm_create_member_login_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun ResetCredentialPasswordDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val valid = isStrongPassword(newPassword) && newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.resetFamilyPasswordButton}: $targetLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(strings.newPasswordLabel) },
                    supportingText = { Text(strings.passwordPolicyHint) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) strings.hidePasswordDesc else strings.showPasswordDesc
                            )
                        }
                    },
                    isError = newPassword.isNotEmpty() && !isStrongPassword(newPassword),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("reset_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(strings.confirmPasswordLabel) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("reset_confirm_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPassword) },
                enabled = valid,
                modifier = Modifier.testTag("confirm_reset_password_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
