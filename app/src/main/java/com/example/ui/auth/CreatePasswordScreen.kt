package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.firebase.AccountAuthException
import com.example.data.firebase.AccountAuthRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.LanguageSwitcherPill
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun CreatePasswordScreen(
    user: FirebaseUser,
    languageCode: String,
    onSelectLanguage: (String) -> Unit,
    onPasswordLinked: () -> Unit,
    onSignOut: () -> Unit,
    accountAuthRepository: AccountAuthRepository = remember { AccountAuthRepository() }
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var requiresRecentLogin by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitLinkPassword() {
        if (!AccountAuthRepository.isStrongPassword(password)) {
            errorMessage = strings.weakPasswordClientError
            return
        }
        if (password != confirmPassword) {
            errorMessage = strings.passwordMismatchError
            return
        }
        focusManager.clearFocus()
        errorMessage = null
        requiresRecentLogin = false
        isBusy = true
        scope.launch {
            accountAuthRepository.linkPasswordToCurrentGoogleUser(password)
                .onSuccess {
                    isBusy = false
                    password = ""
                    confirmPassword = ""
                    onPasswordLinked()
                }
                .onFailure { err ->
                    isBusy = false
                    requiresRecentLogin = (err as? AccountAuthException)?.code == "REQUIRES_RECENT_LOGIN"
                    errorMessage = err.localizedMessage ?: "Unable to link password."
                }
        }
    }

    fun reverifyGoogleAndRetry() {
        focusManager.clearFocus()
        errorMessage = null
        isBusy = true
        scope.launch {
            when (val fetch = obtainGoogleCredentialFromManager(context, credentialManager)) {
                is CredentialFetchResult.Cancelled -> {
                    isBusy = false
                }
                is CredentialFetchResult.Error -> {
                    isBusy = false
                    errorMessage = fetch.message
                }
                is CredentialFetchResult.Success -> {
                    accountAuthRepository.reauthenticateCurrentUserWithGoogle(fetch.credential)
                        .onSuccess {
                            accountAuthRepository.linkPasswordToCurrentGoogleUser(password)
                                .onSuccess {
                                    isBusy = false
                                    requiresRecentLogin = false
                                    password = ""
                                    confirmPassword = ""
                                    onPasswordLinked()
                                }
                                .onFailure { linkErr ->
                                    isBusy = false
                                    errorMessage = linkErr.localizedMessage ?: "Unable to link password."
                                }
                        }
                        .onFailure { reauthErr ->
                            isBusy = false
                            errorMessage = reauthErr.localizedMessage ?: "Google re-verification failed."
                        }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyDeep, NavyPrimary, MaterialTheme.colorScheme.background)
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.End
            ) {
                LanguageSwitcherPill(
                    currentLanguage = languageCode,
                    onSelectLanguage = onSelectLanguage
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SkySoft,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = strings.createVaultPasswordTitle,
                                tint = NavyPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = strings.createVaultPasswordTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = strings.createVaultPasswordSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Verified Google Account Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SkySoft.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = strings.googleAccountVerifiedBadge,
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.googleAccountVerifiedBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.AlternateEmail,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = user.email.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = NavyPrimary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_password_error_banner")
                        ) {
                            SelectionContainer {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.passwordLabel) },
                        supportingText = { Text(strings.passwordPolicyHint) },
                        leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("create_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) strings.hidePasswordDesc else strings.showPasswordDesc
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isBusy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_password_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(strings.confirmPasswordLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { confirmVisible = !confirmVisible },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("create_confirm_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (confirmVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (confirmVisible) strings.hidePasswordDesc else strings.showPasswordDesc
                                )
                            }
                        },
                        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isBusy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitLinkPassword() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submitLinkPassword() },
                        enabled = !isBusy && password.isNotEmpty() && confirmPassword.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("create_password_submit_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(strings.linkingPasswordProgress, style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text(
                                text = strings.linkPasswordContinueButton,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (requiresRecentLogin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { reverifyGoogleAndRetry() },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_password_reverify_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.reverifyGoogleButton)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onSignOut,
                        enabled = !isBusy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("create_password_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.signOutDifferentAccount)
                    }
                }
            }
        }
    }
}
