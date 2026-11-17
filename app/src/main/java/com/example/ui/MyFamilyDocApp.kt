package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.domain.model.UiState
import com.example.localization.LocalAppStrings
import com.example.localization.stringsForLanguage
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.FamilyOnboardingScreen
import com.example.ui.auth.PinLockScreen
import com.example.ui.auth.PinMode
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.FamilyScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private enum class VaultTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", Icons.Rounded.Home, Icons.Outlined.Home),
    FAMILY("family", Icons.Rounded.Group, Icons.Outlined.Group),
    DOCUMENTS("documents", Icons.Rounded.Description, Icons.Outlined.Description),
    BACKUP("backup", Icons.Rounded.Backup, Icons.Outlined.Backup),
    SETTINGS("settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@Composable
fun MyFamilyDocApp(
    securityPreferences: SecurityPreferences,
    securitySettings: SecuritySettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val appStrings = remember(securitySettings.languageCode) {
        stringsForLanguage(securitySettings.languageCode)
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                languageCode = securitySettings.languageCode,
                onSelectLanguage = { code ->
                    scope.launch { securityPreferences.setLanguage(code) }
                },
                onGetDeviceId = { securityPreferences.getOrCreateDeviceInstallationId() },
                onAuthSuccess = {}
            )
        } else {
            key(user.uid) {
                AuthenticatedVaultShell(
                    userId = user.uid,
                    fallbackDisplayName = user.displayName ?: "Member",
                    securityPreferences = securityPreferences,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedVaultShell(
    userId: String,
    fallbackDisplayName: String,
    securityPreferences: SecurityPreferences,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val strings = LocalAppStrings.current

    val viewModel: MainViewModel = viewModel(
        key = userId,
        factory = remember(userId, application) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        repository = FamilyVaultRepository(application),
                        cryptoManager = DocumentCryptoManager(application),
                        securityPreferences = securityPreferences,
                        appContext = application,
                        currentUserId = userId
                    ) as T
                }
            }
        }
    )

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val familyAccount by viewModel.familyAccount.collectAsStateWithLifecycle()
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val isActionBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val credentialsOverview by viewModel.credentialsOverview.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    var currentTab by rememberSaveable { mutableStateOf(VaultTab.HOME) }
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCameraScanner by rememberSaveable { mutableStateOf(false) }
    var showAdminPanel by rememberSaveable { mutableStateOf(false) }
    var showPinSetup by rememberSaveable { mutableStateOf(false) }
    var openAddMemberInitially by rememberSaveable { mutableStateOf(false) }
    var openUploadDocInitially by rememberSaveable { mutableStateOf(false) }
    var memberFilterId by rememberSaveable { mutableStateOf<String?>(null) }

    when (val profileRes = userProfileState) {
        UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = profileRes.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = onSignOut) {
                        Text(strings.logout)
                    }
                }
            }
        }

        is UiState.Success -> {
            val userProfile = profileRes.data

            if (settings.pinEnabled && !isVaultUnlocked) {
                PinLockScreen(
                    mode = PinMode.UNLOCK,
                    biometricEnabled = settings.biometricEnabled,
                    verifyPin = viewModel::verifyPin,
                    onPinVerifiedOrSaved = { viewModel.unlockVault() }
                )
                return
            }

            if (userProfile == null || userProfile.familyId.isBlank()) {
                FamilyOnboardingScreen(
                    defaultDisplayName = userProfile?.displayName?.ifBlank { fallbackDisplayName }
                        ?: fallbackDisplayName,
                    languageCode = settings.languageCode,
                    isBusy = isActionBusy,
                    errorMessage = statusMessage,
                    onSelectLanguage = viewModel::setLanguage,
                    onCreateFamily = { familyName, creatorName ->
                        viewModel.createFamily(familyName, creatorName)
                    },
                    onJoinFamily = { familyCode, memberName ->
                        viewModel.joinFamily(familyCode, memberName)
                    },
                    onSignOut = onSignOut
                )
                return
            }

            val family = familyAccount
            if (family == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NavyPrimary)
                }
                return
            }

            val documents = (documentsState as? UiState.Success)?.data.orEmpty()
            val isAdmin = family.adminUid == userId
            val selectedDocument = selectedDocumentId?.let { id ->
                documents.firstOrNull { it.id == id }
            }

            if (showPinSetup) {
                BackHandler { showPinSetup = false }
                PinLockScreen(
                    mode = PinMode.SETUP_OR_CHANGE,
                    biometricEnabled = false,
                    verifyPin = { true },
                    onPinVerifiedOrSaved = { newPin ->
                        viewModel.savePin(newPin)
                        showPinSetup = false
                    },
                    onCancelSetup = { showPinSetup = false }
                )
                return
            }

            if (showCameraScanner) {
                CameraScannerScreen(
                    members = familyMembers,
                    onScanCapturedAndSaved = { file, title, categoryKey, member, maskedId, notes, issueMs, expiryMs ->
                        viewModel.saveScannedImageFile(
                            imageFile = file,
                            title = title,
                            categoryKey = categoryKey,
                            member = member,
                            maskedId = maskedId,
                            notes = notes,
                            issueDateMillis = issueMs,
                            expiryDateMillis = expiryMs
                        )
                    },
                    onBack = { showCameraScanner = false }
                )
                return
            }

            if (selectedDocument != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DocumentDetailScreen(
                        document = selectedDocument,
                        members = familyMembers,
                        isAdmin = isAdmin,
                        loadDecryptedBytes = viewModel::loadDecryptedBytesForPreview,
                        prepareShareFile = viewModel::prepareShareableTempFile,
                        onExportToUri = { doc, uri ->
                            viewModel.exportDocumentToUri(doc, uri, strings.downloadSuccess)
                        },
                        onRenameAndUpdateDates = viewModel::renameAndUpdateDocument,
                        onUpdatePermissions = { doc, allowed ->
                            viewModel.updateDocumentPermissions(doc, allowed, strings.permissionsSaved)
                        },
                        onDeleteDocument = viewModel::deleteDocument,
                        onBack = { selectedDocumentId = null }
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                return
            }

            val scope = rememberCoroutineScope()

            if (showAdminPanel && isAdmin) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AdminPanelScreen(
                        family = family,
                        members = familyMembers,
                        documents = documents,
                        backupStatus = backupStatus,
                        credentialsOverview = credentialsOverview,
                        isActionBusy = isActionBusy,
                        onRenameFamily = viewModel::renameFamily,
                        onAddOrUpdateMember = viewModel::addOrUpdateMember,
                        onDeleteMember = viewModel::deleteMember,
                        onUpdateDocumentPermissions = { doc, allowed ->
                            viewModel.updateDocumentPermissions(doc, allowed, strings.permissionsSaved)
                        },
                        onBackupNow = { viewModel.runManualBackup(strings.backupCompletedMsg) },
                        onRefreshCredentials = viewModel::loadFamilyCredentialsOverview,
                        onSetupFamilyCredentials = { username, password ->
                            viewModel.setupFamilyCredentials(username, password, strings.familyUsernameSavedMsg)
                        },
                        onResetFamilyPassword = { newPassword ->
                            viewModel.resetFamilyPassword(newPassword, strings.familyPasswordResetMsg)
                        },
                        onCreateMemberCredential = { name, relation, username, password ->
                            viewModel.createMemberCredential(
                                memberName = name,
                                relation = relation,
                                memberUsername = username,
                                password = password,
                                successMessage = strings.memberLoginCreatedMsg
                            )
                        },
                        onResetMemberPassword = { memberUsername, newPassword ->
                            viewModel.resetMemberPassword(
                                memberUsername = memberUsername,
                                newPassword = newPassword,
                                successMessage = strings.memberPasswordResetMsg
                            )
                        },
                        onRevokeMemberAccess = { memberUsername, memberUid ->
                            viewModel.revokeMemberAccess(
                                memberUsername = memberUsername,
                                memberUid = memberUid,
                                successMessage = strings.memberRevokedMsg
                            )
                        },
                        onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        onBack = { showAdminPanel = false }
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                return
            }

            val tabLabels = mapOf(
                VaultTab.HOME to strings.navHome,
                VaultTab.FAMILY to strings.navFamily,
                VaultTab.DOCUMENTS to strings.navDocuments,
                VaultTab.BACKUP to strings.navBackup,
                VaultTab.SETTINGS to strings.navSettings
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp

                val tabContent: @Composable (Modifier) -> Unit = { contentModifier ->
                    Box(modifier = contentModifier.fillMaxSize()) {
                        when (currentTab) {
                            VaultTab.HOME -> {
                                HomeDashboardScreen(
                                    userProfile = userProfile,
                                    family = family,
                                    members = familyMembers,
                                    documents = documents,
                                    backupStatus = backupStatus,
                                    isAdmin = isAdmin,
                                    isWideScreen = isWideScreen,
                                    languageCode = settings.languageCode,
                                    onSelectLanguage = viewModel::setLanguage,
                                    onQuickUpload = {
                                        openUploadDocInitially = true
                                        currentTab = VaultTab.DOCUMENTS
                                    },
                                    onQuickScan = { showCameraScanner = true },
                                    onQuickAddMember = {
                                        openAddMemberInitially = true
                                        currentTab = VaultTab.FAMILY
                                    },
                                    onLoadSamples = { viewModel.loadSampleDocuments() },
                                    onOpenDocuments = { currentTab = VaultTab.DOCUMENTS },
                                    onOpenBackup = { currentTab = VaultTab.BACKUP },
                                    onOpenAdminPanel = { showAdminPanel = true },
                                    onOpenDocumentDetail = { doc -> selectedDocumentId = doc.id },
                                    onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                            }

                            VaultTab.FAMILY -> {
                                FamilyScreen(
                                    family = family,
                                    members = familyMembers,
                                    documents = documents,
                                    currentUserId = userId,
                                    isAdmin = isAdmin,
                                    openAddDialogInitially = openAddMemberInitially,
                                    onConsumeInitialAdd = { openAddMemberInitially = false },
                                    onAddOrUpdateMember = viewModel::addOrUpdateMember,
                                    onDeleteMember = viewModel::deleteMember,
                                    onOpenMemberDocuments = { memberId ->
                                        memberFilterId = memberId
                                        currentTab = VaultTab.DOCUMENTS
                                    },
                                    onOpenAdminPanel = { showAdminPanel = true }
                                )
                            }

                            VaultTab.DOCUMENTS -> {
                                DocumentsScreen(
                                    documents = documents,
                                    members = familyMembers,
                                    initialMemberFilterId = memberFilterId,
                                    openUploadInitially = openUploadDocInitially,
                                    onConsumeInitialState = {
                                        memberFilterId = null
                                        openUploadDocInitially = false
                                    },
                                    onOpenDocumentDetail = { doc -> selectedDocumentId = doc.id },
                                    onOpenCameraScanner = { showCameraScanner = true },
                                    onLoadSampleDocs = { viewModel.loadSampleDocuments() },
                                    onUploadDocumentUri = { uri, title, catKey, member, fileTypeOverride, maskedId, notes, issueMs, expiryMs ->
                                        viewModel.uploadDocumentFromUri(
                                            uri = uri,
                                            title = title,
                                            categoryKey = catKey,
                                            member = member,
                                            fileTypeOverride = fileTypeOverride,
                                            maskedId = maskedId,
                                            notes = notes,
                                            issueDateMillis = issueMs,
                                            expiryDateMillis = expiryMs
                                        )
                                    }
                                )
                            }

                            VaultTab.BACKUP -> {
                                BackupScreen(
                                    backupStatus = backupStatus,
                                    documents = documents,
                                    onBackupNow = { viewModel.runManualBackup(strings.backupCompletedMsg) },
                                    onRestoreFromCloud = { viewModel.runCloudRestore(strings.restoreCompletedMsg) },
                                    onToggleAutoBackup = viewModel::setAutoBackupEnabled,
                                    onSelectIntervalDays = viewModel::setBackupIntervalDays,
                                    onOpenDocumentDetail = { doc -> selectedDocumentId = doc.id }
                                )
                            }

                            VaultTab.SETTINGS -> {
                                SettingsScreen(
                                    userProfile = userProfile,
                                    family = family,
                                    isAdmin = isAdmin,
                                    securitySettings = settings,
                                    onSelectLanguage = viewModel::setLanguage,
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSetupOrChangePin = { showPinSetup = true },
                                    onDisablePin = { viewModel.disablePin() },
                                    onToggleBiometric = viewModel::setBiometricEnabled,
                                    onToggleExpiryReminders = viewModel::setExpiryRemindersEnabled,
                                    onCheckExpiriesNow = viewModel::checkExpiriesAndNotifyNow,
                                    onOpenBackupCenter = { currentTab = VaultTab.BACKUP },
                                    onOpenAdminPanel = { showAdminPanel = true },
                                    onLogout = onSignOut
                                )
                            }
                        }
                    }
                }

                if (isWideScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = NavyPrimary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shield,
                                                contentDescription = strings.appName,
                                                tint = SkyAccent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            VaultTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                val label = tabLabels[tab].orEmpty()
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = NavyPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = SkySoft
                                    ),
                                    modifier = Modifier.testTag("nav_rail_${tab.route}")
                                )
                            }
                        }

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            tabContent(
                                Modifier
                                    .padding(innerPadding)
                                    .consumeWindowInsets(innerPadding)
                            )
                        }
                    }
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars,
                                tonalElevation = 4.dp
                            ) {
                                VaultTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    val label = tabLabels[tab].orEmpty()
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = NavyPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = SkySoft
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        tabContent(
                            Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
