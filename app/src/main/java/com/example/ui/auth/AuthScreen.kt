package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.firebase.AccountAuthException
import com.example.data.firebase.AccountAuthRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.LanguageSwitcherPill
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.google.firebase.auth.AuthCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EmailAuthMode {
    LOGIN,
    SIGN_UP
}

@Composable
fun AuthScreen(
    languageCode: String,
    onSelectLanguage: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    accountAuthRepository: AccountAuthRepository = remember { AccountAuthRepository() },
    initialInfoMessage: String? = null,
    onConsumeInitialInfoMessage: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    val isBusy = isGoogleLoading || isEmailLoading

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf(initialInfoMessage) }
    var showSwitchToLoginHint by remember { mutableStateOf(false) }

    LaunchedEffect(initialInfoMessage) {
        if (!initialInfoMessage.isNullOrBlank()) {
            infoMessage = initialInfoMessage
            onConsumeInitialInfoMessage()
        }
    }

    var emailMode by rememberSaveable { mutableStateOf(EmailAuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    // Forgot Password Dialog state
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by rememberSaveable { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }
    var isResetBusy by remember { mutableStateOf(false) }

    // Account collision verification dialog (Google Sign-In encountered existing Email/Password account)
    var pendingLinkEmail by remember { mutableStateOf<String?>(null) }
    var pendingGoogleCred by remember { mutableStateOf<AuthCredential?>(null) }
    var pendingLinkMessage by remember { mutableStateOf<String?>(null) }
    var verifyLinkPassword by remember { mutableStateOf("") }
    var verifyLinkVisible by remember { mutableStateOf(false) }
    var verifyLinkError by remember { mutableStateOf<String?>(null) }
    var isVerifyLinkBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Wait until the initial window transaction settles before contacting CredentialManager
        withFrameNanos { }
        delay(750L)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    fun launchGoogleSignIn(forceAccountChooser: Boolean) {
        errorMessage = null
        infoMessage = null
        showSwitchToLoginHint = false
        isGoogleLoading = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            accountAuthRepository = accountAuthRepository,
            onAuthSuccess = { _ ->
                isGoogleLoading = false
                onAuthSuccess()
            },
            onPasswordVerificationNeeded = { conflictEmail, pendingCred, msg ->
                isGoogleLoading = false
                pendingLinkEmail = conflictEmail
                pendingGoogleCred = pendingCred
                pendingLinkMessage = msg
                verifyLinkPassword = ""
                verifyLinkError = null
            },
            onAuthError = { msg ->
                isGoogleLoading = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isGoogleLoading = false
            },
            forceAccountChooser = forceAccountChooser
        )
    }

    fun submitEmailAuth() {
        val cleanEmail = email.trim()
        errorMessage = null
        infoMessage = null
        showSwitchToLoginHint = false

        if (!AccountAuthRepository.isValidEmail(cleanEmail)) {
            errorMessage = strings.invalidEmailClientError
            return
        }

        if (emailMode == EmailAuthMode.SIGN_UP) {
            if (!AccountAuthRepository.isStrongPassword(password)) {
                errorMessage = strings.weakPasswordClientError
                return
            }
            if (password != confirmPassword) {
                errorMessage = strings.passwordMismatchError
                return
            }
            focusManager.clearFocus()
            isEmailLoading = true
            scope.launch {
                accountAuthRepository.signUpWithEmailPassword(
                    email = cleanEmail,
                    password = password,
                    displayName = fullName.takeIf { it.isNotBlank() }
                ).onSuccess {
                    isEmailLoading = false
                    password = ""
                    confirmPassword = ""
                    onAuthSuccess()
                }.onFailure { err ->
                    isEmailLoading = false
                    showSwitchToLoginHint = (err as? AccountAuthException)?.code == "EMAIL_ALREADY_IN_USE"
                    errorMessage = err.localizedMessage ?: "Unable to create account."
                }
            }
        } else {
            if (password.isEmpty()) {
                errorMessage = strings.weakPasswordClientError
                return
            }
            focusManager.clearFocus()
            isEmailLoading = true
            scope.launch {
                accountAuthRepository.signInWithEmailPassword(
                    email = cleanEmail,
                    password = password
                ).onSuccess {
                    isEmailLoading = false
                    password = ""
                    onAuthSuccess()
                }.onFailure { err ->
                    isEmailLoading = false
                    errorMessage = err.localizedMessage ?: "Incorrect email or password."
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        AsyncImage(
                            model = remember(context) {
                                ImageRequest.Builder(context)
                                    .data(R.drawable.img_vault_hero)
                                    .size(960, 540)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = strings.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            NavyDeep.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SkyAccent.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = SkyAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.appName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.tagline,
                                style = MaterialTheme.typography.headlineSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.welcomeTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.welcomeSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecurityPill(
                                icon = Icons.Rounded.Lock,
                                label = strings.securityEncryptedBadge,
                                modifier = Modifier.weight(1f)
                            )
                            SecurityPill(
                                icon = Icons.Rounded.VerifiedUser,
                                label = strings.securityFamilyIsolatedBadge,
                                modifier = Modifier.weight(1f)
                            )
                            SecurityPill(
                                icon = Icons.Rounded.Fingerprint,
                                label = strings.securityBiometricBadge,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!infoMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SkySoft,
                                border = BorderStroke(1.dp, SkyAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_info_banner")
                            ) {
                                Text(
                                    text = infoMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NavyPrimary,
                                    modifier = Modifier.padding(12.dp)
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
                                    .testTag("auth_error_banner")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = errorMessage!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    if (showSwitchToLoginHint) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TextButton(
                                            onClick = {
                                                emailMode = EmailAuthMode.LOGIN
                                                errorMessage = null
                                                showSwitchToLoginHint = false
                                            },
                                            modifier = Modifier.testTag("switch_to_login_action")
                                        ) {
                                            Text(strings.switchToEmailLoginHint)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Google Sign-In Primary Button
                        Button(
                            onClick = { launchGoogleSignIn(forceAccountChooser = false) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyPrimary,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = PureWhite,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(strings.signingIn, style = MaterialTheme.typography.labelLarge)
                            } else {
                                Text(
                                    text = strings.signInWithGoogle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Choose Another Google Account Button
                        OutlinedButton(
                            onClick = { launchGoogleSignIn(forceAccountChooser = true) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_login_existing_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = strings.loginWithExistingGoogle,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.loginWithExistingGoogle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = strings.orContinueWith,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Unified Email Login / Create Account Card
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    val modes = listOf(
                                        EmailAuthMode.LOGIN to strings.emailLoginTab,
                                        EmailAuthMode.SIGN_UP to strings.createAccountTab
                                    )
                                    modes.forEachIndexed { idx, (mode, label) ->
                                        SegmentedButton(
                                            selected = emailMode == mode,
                                            onClick = {
                                                emailMode = mode
                                                errorMessage = null
                                                infoMessage = null
                                                showSwitchToLoginHint = false
                                            },
                                            enabled = !isBusy,
                                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                                            modifier = Modifier.testTag(
                                                if (mode == EmailAuthMode.LOGIN) "auth_mode_email_login_tab"
                                                else "auth_mode_email_signup_tab"
                                            )
                                        ) {
                                            Text(label, maxLines = 1)
                                        }
                                    }
                                }

                                Text(
                                    text = if (emailMode == EmailAuthMode.LOGIN) {
                                        strings.emailAuthLoginSubtitle
                                    } else {
                                        strings.emailAuthSignUpSubtitle
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                AnimatedVisibility(visible = emailMode == EmailAuthMode.SIGN_UP) {
                                    OutlinedTextField(
                                        value = fullName,
                                        onValueChange = { fullName = it },
                                        label = { Text(strings.fullNameOptionalLabel) },
                                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                        singleLine = true,
                                        enabled = !isBusy,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_display_name_input")
                                    )
                                }

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text(strings.emailLabel) },
                                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                                    singleLine = true,
                                    enabled = !isBusy,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_email_input")
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(strings.passwordLabel) },
                                    supportingText = if (emailMode == EmailAuthMode.SIGN_UP) {
                                        { Text(strings.passwordPolicyHint) }
                                    } else {
                                        null
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("auth_password_visibility_toggle")
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
                                        imeAction = if (emailMode == EmailAuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (emailMode == EmailAuthMode.LOGIN) submitEmailAuth()
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input")
                                )

                                AnimatedVisibility(visible = emailMode == EmailAuthMode.SIGN_UP) {
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
                                                    .testTag("auth_confirm_password_visibility_toggle")
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
                                        keyboardActions = KeyboardActions(onDone = { submitEmailAuth() }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_confirm_password_input")
                                    )
                                }

                                if (emailMode == EmailAuthMode.LOGIN) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                resetEmail = email.trim()
                                                resetError = null
                                                showResetDialog = true
                                            },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("forgot_password_button")
                                        ) {
                                            Text(strings.forgotPasswordButton)
                                        }
                                    }
                                }

                                Button(
                                    onClick = { submitEmailAuth() },
                                    enabled = !isBusy && email.isNotBlank() && password.isNotEmpty() &&
                                        (emailMode == EmailAuthMode.LOGIN || confirmPassword.isNotEmpty()),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NavyPrimary,
                                        contentColor = PureWhite
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("email_auth_submit_button")
                                ) {
                                    if (isEmailLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = PureWhite,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (emailMode == EmailAuthMode.LOGIN) strings.loggingIn else strings.creatingAccount,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (emailMode == EmailAuthMode.LOGIN) {
                                                Icons.AutoMirrored.Rounded.Login
                                            } else {
                                                Icons.Rounded.PersonAdd
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (emailMode == EmailAuthMode.LOGIN) {
                                                strings.loginWithEmailButton
                                            } else {
                                                strings.createAccountButton
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = strings.authMultiDeviceHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Forgot Password Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetBusy) showResetDialog = false },
            title = { Text(strings.resetPasswordTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.resetPasswordBody,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(strings.emailLabel) },
                        singleLine = true,
                        enabled = !isResetBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )
                    if (!resetError.isNullOrBlank()) {
                        Text(
                            text = resetError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetError = null
                        isResetBusy = true
                        scope.launch {
                            accountAuthRepository.sendPasswordResetEmail(resetEmail)
                                .onSuccess {
                                    isResetBusy = false
                                    showResetDialog = false
                                    infoMessage = strings.resetEmailSentMessage
                                }
                                .onFailure { err ->
                                    isResetBusy = false
                                    resetError = err.localizedMessage ?: "Unable to send reset email."
                                }
                        }
                    },
                    enabled = !isResetBusy && AccountAuthRepository.isValidEmail(resetEmail),
                    modifier = Modifier.testTag("send_password_reset_button")
                ) {
                    Text(strings.sendResetEmailButton)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !isResetBusy
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Credential Conflict Verification Dialog (verify Email/Password ownership before linking Google)
    val linkEmail = pendingLinkEmail
    val linkCred = pendingGoogleCred
    if (linkEmail != null && linkCred != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyLinkBusy) {
                    pendingLinkEmail = null
                    pendingGoogleCred = null
                }
            },
            title = { Text(strings.verifyLinkGoogleTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = pendingLinkMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = linkEmail,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.emailLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = verifyLinkPassword,
                        onValueChange = { verifyLinkPassword = it },
                        label = { Text(strings.passwordLabel) },
                        trailingIcon = {
                            IconButton(
                                onClick = { verifyLinkVisible = !verifyLinkVisible },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (verifyLinkVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (verifyLinkVisible) strings.hidePasswordDesc else strings.showPasswordDesc
                                )
                            }
                        },
                        visualTransformation = if (verifyLinkVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isVerifyLinkBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_link_password_input")
                    )
                    if (!verifyLinkError.isNullOrBlank()) {
                        Text(
                            text = verifyLinkError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        verifyLinkError = null
                        isVerifyLinkBusy = true
                        scope.launch {
                            accountAuthRepository.verifyPasswordAndLinkGoogleCredential(
                                email = linkEmail,
                                password = verifyLinkPassword,
                                pendingGoogleCredential = linkCred
                            ).onSuccess {
                                isVerifyLinkBusy = false
                                verifyLinkPassword = ""
                                pendingLinkEmail = null
                                pendingGoogleCred = null
                                onAuthSuccess()
                            }.onFailure { err ->
                                isVerifyLinkBusy = false
                                verifyLinkError = err.localizedMessage ?: "Verification failed."
                            }
                        }
                    },
                    enabled = !isVerifyLinkBusy && verifyLinkPassword.isNotEmpty(),
                    modifier = Modifier.testTag("verify_link_submit_button")
                ) {
                    Text(strings.verifyLinkGoogleButton)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingLinkEmail = null
                        pendingGoogleCred = null
                    },
                    enabled = !isVerifyLinkBusy
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SecurityPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SkySoft.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NavyPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NavyPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
