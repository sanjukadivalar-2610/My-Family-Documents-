package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.firebase.AdminDeleteAction
import com.example.data.security.SecuritySettings
import com.example.data.security.ThemeMode
import com.example.domain.model.FamilyAccount
import com.example.domain.model.FamilyMember
import com.example.domain.model.UserProfile
import com.example.localization.LocalAppStrings
import com.example.ui.components.LanguageSwitcherPill
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft

@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    family: FamilyAccount?,
    isAdmin: Boolean,
    familyMembers: List<FamilyMember> = emptyList(),
    securitySettings: SecuritySettings,
    userEmail: String,
    firebaseUid: String,
    hasGoogleLinked: Boolean,
    hasEmailPasswordLinked: Boolean,
    isAccountActionBusy: Boolean = false,
    onLinkGoogleAccount: () -> Unit,
    onOpenCreatePassword: () -> Unit,
    onSendPasswordResetEmail: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetupOrChangePin: () -> Unit,
    onDisablePin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleExpiryReminders: (Boolean) -> Unit,
    onCheckExpiriesNow: () -> Unit,
    onOpenBackupCenter: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    onConfirmDeleteAccount: (AdminDeleteAction, String?) -> Unit = { _, _ -> },
    hasUpdateAvailable: Boolean = false,
    installedVersionLabel: String = "v1.0 (1)",
    onOpenUpdates: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAdminConsequenceDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleExpiryReminders(true)
            onCheckExpiriesNow()
        }
    }

    fun requestNotificationPermissionIfNeeded(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onGranted()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    Text(
                        text = strings.settingsTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${strings.appName} (${strings.docActions.brandPronunciationKn}) • ${strings.tagline}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("settings_brand_subtitle")
                    )
                }
            }
        }

        // User Profile Summary Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            name = userProfile?.displayName ?: "U",
                            photoBase64 = null,
                            size = 52.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val displayedEmail = userEmail.ifBlank { userProfile?.email.orEmpty() }
                            if (displayedEmail.isNotEmpty()) {
                                Text(
                                    text = displayedEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (family != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SkySoft
                                ) {
                                    Text(
                                        text = "${family.familyName} • ${if (isAdmin) strings.roleAdmin else strings.roleMember}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unified Sign-In Methods & Account Linking Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = strings.signInMethodsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.signInMethodsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("settings_firebase_uid_badge")
                        ) {
                            Text(
                                text = "${strings.firebaseUidPrefix} ${firebaseUid.take(12)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        ProviderStatusRow(
                            icon = Icons.Rounded.AccountCircle,
                            label = strings.googleProviderLabel,
                            isLinked = hasGoogleLinked,
                            linkedText = strings.linkedBadge,
                            notLinkedText = strings.notLinkedBadge
                        )

                        if (!hasGoogleLinked) {
                            Button(
                                onClick = onLinkGoogleAccount,
                                enabled = !isAccountActionBusy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_link_google_button")
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.linkGoogleAccountButton, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider()

                        ProviderStatusRow(
                            icon = Icons.Rounded.Key,
                            label = strings.emailPasswordProviderLabel,
                            isLinked = hasEmailPasswordLinked,
                            linkedText = strings.linkedBadge,
                            notLinkedText = strings.notLinkedBadge
                        )

                        if (!hasEmailPasswordLinked) {
                            Button(
                                onClick = onOpenCreatePassword,
                                enabled = !isAccountActionBusy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_link_password_button")
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.linkPasswordButton, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSendPasswordResetEmail,
                                enabled = !isAccountActionBusy && userEmail.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_reset_password_email_button")
                            ) {
                                Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.sendPasswordResetButton)
                            }
                        }
                    }
                }
            }
        }

        // Language & Theme Card
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.languageSetting,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LanguageSwitcherPill(
                                currentLanguage = securitySettings.languageCode,
                                onSelectLanguage = onSelectLanguage
                            )
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.themeSetting,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val modes = listOf(
                                    ThemeMode.LIGHT to strings.lightMode,
                                    ThemeMode.DARK to strings.darkMode,
                                    ThemeMode.SYSTEM to strings.systemMode
                                )
                                modes.forEachIndexed { idx, (mode, label) ->
                                    SegmentedButton(
                                        selected = securitySettings.themeMode == mode,
                                        onClick = { onSelectThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                                        modifier = Modifier.testTag("theme_mode_${mode.key}")
                                    ) {
                                        Text(label, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Security (PIN & Biometrics)
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.appPinLock,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = onSetupOrChangePin,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = PureWhite),
                                modifier = Modifier.testTag("settings_pin_button")
                            ) {
                                Text(if (securitySettings.pinEnabled) strings.changePin else strings.appPinLock)
                            }
                        }

                        if (securitySettings.pinEnabled) {
                            OutlinedButton(
                                onClick = onDisablePin,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_disable_pin_button")
                            ) {
                                Text(strings.disablePin)
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.biometricUnlock,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = securitySettings.biometricEnabled,
                                enabled = securitySettings.pinEnabled,
                                onCheckedChange = onToggleBiometric,
                                modifier = Modifier.testTag("settings_biometric_switch")
                            )
                        }
                    }
                }
            }
        }

        // Expiry Reminders & Navigation Links
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = strings.expiryRemindersSetting,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.expiryRemindersSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = securitySettings.expiryRemindersEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        requestNotificationPermissionIfNeeded { onToggleExpiryReminders(true) }
                                    } else {
                                        onToggleExpiryReminders(false)
                                    }
                                },
                                modifier = Modifier.testTag("settings_expiry_switch")
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                requestNotificationPermissionIfNeeded { onCheckExpiriesNow() }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_check_expiries_button")
                        ) {
                            Text(strings.checkExpiriesNow)
                        }

                        HorizontalDivider()

                        SettingsLinkRow(
                            icon = Icons.Rounded.SystemUpdate,
                            label = strings.settingsUpdatesLink,
                            tag = "settings_updates_link",
                            badgeText = if (hasUpdateAvailable) strings.newUpdateAvailableTitle else installedVersionLabel,
                            highlightBadge = hasUpdateAvailable,
                            onClick = onOpenUpdates
                        )

                        HorizontalDivider()

                        SettingsLinkRow(
                            icon = Icons.Rounded.Backup,
                            label = strings.backupSettingsLink,
                            tag = "settings_backup_link",
                            onClick = onOpenBackupCenter
                        )

                        if (isAdmin) {
                            HorizontalDivider()
                            SettingsLinkRow(
                                icon = Icons.Rounded.AdminPanelSettings,
                                label = strings.adminPanelTitle,
                                tag = "settings_admin_link",
                                onClick = onOpenAdminPanel
                            )
                        }
                    }
                }
            }
        }

        // Legal, About & Logout
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsLinkRow(
                            icon = Icons.Rounded.Policy,
                            label = strings.privacyPolicy,
                            tag = "settings_privacy_button",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider()
                        SettingsLinkRow(
                            icon = Icons.Rounded.Info,
                            label = strings.aboutApp,
                            tag = "settings_about_button",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showLogoutDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.logout, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showDeleteAccountDialog = true },
                        enabled = !isAccountActionBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_delete_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = strings.deleteAccountButton,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.deleteAccountButton, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.privacyPolicy) },
            text = { Text(strings.privacyPolicyBody) },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(strings.aboutApp) },
            text = { Text(strings.aboutAppBody) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(strings.logout) },
            text = { Text(strings.logoutConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text(strings.logout)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = strings.deleteAccountDialogTitle,
                    modifier = Modifier.testTag("delete_account_dialog_title")
                )
            },
            text = {
                Text(
                    text = strings.deleteAccountDialogMessage,
                    modifier = Modifier.testTag("delete_account_dialog_message")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        if (isAdmin && family != null) {
                            showAdminConsequenceDialog = true
                        } else {
                            onConfirmDeleteAccount(AdminDeleteAction.NONE, null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("delete_account_yes_button")
                ) {
                    Text(strings.deleteAccountYesDelete)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    modifier = Modifier.testTag("delete_account_no_button")
                ) {
                    Text(strings.deleteAccountNoCancel)
                }
            }
        )
    }

    if (showAdminConsequenceDialog && isAdmin && family != null) {
        val transferableMembers = remember(familyMembers, firebaseUid) {
            familyMembers.filter { it.linkedUserId.isNotBlank() && it.linkedUserId != firebaseUid }
        }
        var selectedAdminAction by remember(transferableMembers) {
            mutableStateOf(
                if (transferableMembers.isNotEmpty()) AdminDeleteAction.TRANSFER_OWNERSHIP
                else AdminDeleteAction.DELETE_ENTIRE_FAMILY
            )
        }
        var selectedNewOwnerUid by remember(transferableMembers) {
            mutableStateOf(transferableMembers.firstOrNull()?.linkedUserId)
        }
        var extraFamilyDeleteConfirmed by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdminConsequenceDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = strings.adminDeleteWarningTitle,
                    modifier = Modifier.testTag("admin_delete_warning_title")
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.adminDeleteWarningBody,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = strings.adminDeleteOthersAuthNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (transferableMembers.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedAdminAction == AdminDeleteAction.TRANSFER_OWNERSHIP) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAdminAction = AdminDeleteAction.TRANSFER_OWNERSHIP
                                    extraFamilyDeleteConfirmed = false
                                }
                                .testTag("admin_option_transfer_ownership")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = selectedAdminAction == AdminDeleteAction.TRANSFER_OWNERSHIP,
                                    onClick = {
                                        selectedAdminAction = AdminDeleteAction.TRANSFER_OWNERSHIP
                                        extraFamilyDeleteConfirmed = false
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.adminOptionTransferOwnership,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.adminOptionTransferSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (selectedAdminAction == AdminDeleteAction.TRANSFER_OWNERSHIP) {
                            Text(
                                text = strings.adminSelectNewOwnerLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            transferableMembers.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedNewOwnerUid = member.linkedUserId }
                                        .padding(vertical = 4.dp)
                                        .testTag("transfer_member_${member.linkedUserId}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedNewOwnerUid == member.linkedUserId,
                                        onClick = { selectedNewOwnerUid = member.linkedUserId }
                                    )
                                    Column {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (member.email.isNotBlank()) {
                                            Text(
                                                text = member.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = strings.adminNoTransferableMembersNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedAdminAction == AdminDeleteAction.DELETE_ENTIRE_FAMILY) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAdminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY
                            }
                            .testTag("admin_option_delete_family")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selectedAdminAction == AdminDeleteAction.DELETE_ENTIRE_FAMILY,
                                onClick = {
                                    selectedAdminAction = AdminDeleteAction.DELETE_ENTIRE_FAMILY
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.adminOptionDeleteFamily,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = strings.adminOptionDeleteFamilySubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (selectedAdminAction == AdminDeleteAction.DELETE_ENTIRE_FAMILY) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { extraFamilyDeleteConfirmed = !extraFamilyDeleteConfirmed }
                                .padding(top = 4.dp)
                                .testTag("admin_delete_family_extra_confirm_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = extraFamilyDeleteConfirmed,
                                onCheckedChange = { extraFamilyDeleteConfirmed = it },
                                modifier = Modifier.testTag("admin_delete_family_extra_confirm_checkbox")
                            )
                            Text(
                                text = strings.adminExtraConfirmCheckbox,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val canProceed = when (selectedAdminAction) {
                    AdminDeleteAction.TRANSFER_OWNERSHIP -> !selectedNewOwnerUid.isNullOrBlank()
                    AdminDeleteAction.DELETE_ENTIRE_FAMILY -> extraFamilyDeleteConfirmed
                    AdminDeleteAction.NONE -> false
                }
                Button(
                    onClick = {
                        showAdminConsequenceDialog = false
                        val targetUid = if (selectedAdminAction == AdminDeleteAction.TRANSFER_OWNERSHIP) {
                            selectedNewOwnerUid
                        } else {
                            null
                        }
                        onConfirmDeleteAccount(selectedAdminAction, targetUid)
                    },
                    enabled = canProceed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedAdminAction == AdminDeleteAction.DELETE_ENTIRE_FAMILY) {
                            MaterialTheme.colorScheme.error
                        } else {
                            NavyPrimary
                        },
                        contentColor = PureWhite
                    ),
                    modifier = Modifier.testTag("admin_delete_confirm_button")
                ) {
                    Text(
                        if (selectedAdminAction == AdminDeleteAction.TRANSFER_OWNERSHIP) {
                            strings.adminConfirmTransferAndDelete
                        } else {
                            strings.adminConfirmDeleteFamilyAndAccount
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAdminConsequenceDialog = false },
                    modifier = Modifier.testTag("admin_delete_cancel_button")
                ) {
                    Text(strings.deleteAccountNoCancel)
                }
            }
        )
    }
}

@Composable
private fun ProviderStatusRow(
    icon: ImageVector,
    label: String,
    isLinked: Boolean,
    linkedText: String,
    notLinkedText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isLinked) SkySoft else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isLinked) BorderStroke(1.dp, SkyAccent) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLinked) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isLinked) linkedText else notLinkedText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLinked) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    label: String,
    tag: String,
    badgeText: String? = null,
    highlightBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (highlightBadge) SkyAccent else SkySoft
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NavyPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
