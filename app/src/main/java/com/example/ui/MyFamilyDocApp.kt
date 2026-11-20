package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.documents.DocumentTransferManager
import com.example.data.firebase.AccountAuthRepository
import com.example.data.firebase.AccountDeletionRepository
import com.example.data.firebase.AccountDeletionResult
import com.example.data.firebase.AdminDeleteAction
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.data.update.AppUpdateRepository
import com.example.data.update.AppUpdateState
import com.example.data.update.UpdatePhase
import com.example.domain.model.FamilyDocument
import com.example.domain.model.UiState
import com.example.localization.LocalAppStrings
import com.example.localization.stringsForLanguage
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CreatePasswordScreen
import com.example.ui.auth.CredentialFetchResult
import com.example.ui.auth.FamilyOnboardingScreen
import com.example.ui.auth.PinLockScreen
import com.example.ui.auth.PinMode
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.obtainGoogleCredentialFromManager
import com.example.ui.auth.signOutUser
import com.example.ui.components.CreateDocumentWithMimeContract
import com.example.ui.components.DeleteAccountDialogState
import com.example.ui.components.DeleteAccountFlowDialogs
import com.example.ui.components.DocumentTransferBanner
import com.example.ui.components.DownloadDestinationDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.FamilyScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdateScreen
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    securitySettings: SecuritySettings,
    pendingVaultLinkUri: Uri? = null,
    onConsumeVaultLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val accountAuthRepository = remember { AccountAuthRepository() }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val appStrings = remember(securitySettings.languageCode) {
        stringsForLanguage(securitySettings.languageCode)
    }

    var showSplashOverlay by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(550L)
        showSplashOverlay = false
    }

    var providerRefreshCounter by remember(currentUser?.uid) { mutableIntStateOf(0) }
    var hasPasswordLinked by remember(currentUser?.uid) {
        mutableStateOf(accountAuthRepository.hasEmailPasswordProvider(currentUser))
    }
    var hasGoogleLinked by remember(currentUser?.uid) {
        mutableStateOf(accountAuthRepository.hasGoogleProvider(currentUser))
    }
    var pendingLoginInfoMessage by remember { mutableStateOf<String?>(null) }
    var deleteAccountDialogState by remember {
        mutableStateOf<DeleteAccountDialogState>(DeleteAccountDialogState.Idle)
    }

    val deletionRepository = remember(context.applicationContext, credentialManager) {
        val appCtx = context.applicationContext
        AccountDeletionRepository(
            db = FamilyVaultRepository.getConfiguredFirestore(appCtx),
            auth = Firebase.auth,
            storage = FamilyVaultRepository.createStorageSafely(),
            accountAuthRepository = accountAuthRepository,
            cryptoManager = DocumentCryptoManager(appCtx),
            securityPreferences = securityPreferences,
            appContext = appCtx,
            signOutHandler = {
                withContext(NonCancellable) {
                    runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                    Firebase.auth.signOut()
                }
            }
        )
    }

    val appUpdateRepository = remember(context.applicationContext) {
        AppUpdateRepository(context.applicationContext)
    }
    val updateState by appUpdateRepository.state.collectAsStateWithLifecycle()

    LaunchedEffect(appUpdateRepository) {
        appUpdateRepository.checkForUpdates()
    }

    val packageInstallerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        appUpdateRepository.markInstallCanceledOrReturned()
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        appUpdateRepository.refreshInstallPermissionState()
        val readyFile = appUpdateRepository.state.value.verifiedApkFile
        if (readyFile != null && appUpdateRepository.canRequestPackageInstalls()) {
            runCatching {
                appUpdateRepository.markInstallingStarted()
                packageInstallerLauncher.launch(appUpdateRepository.buildPackageInstallerIntent(readyFile))
            }.onFailure { err ->
                appUpdateRepository.markInstallLaunchFailed(
                    err.localizedMessage ?: "Unable to launch Android Package Installer."
                )
            }
        }
    }

    fun launchVerifiedApkInstaller(apkFile: java.io.File) {
        if (!appUpdateRepository.canRequestPackageInstalls()) {
            appUpdateRepository.refreshInstallPermissionState()
            runCatching {
                installPermissionLauncher.launch(appUpdateRepository.buildInstallUnknownSourcesSettingsIntent())
            }.onFailure { err ->
                appUpdateRepository.markInstallLaunchFailed(
                    err.localizedMessage ?: "Unable to open install permission settings."
                )
            }
            return
        }
        runCatching {
            appUpdateRepository.markInstallingStarted()
            packageInstallerLauncher.launch(appUpdateRepository.buildPackageInstallerIntent(apkFile))
        }.onFailure { err ->
            appUpdateRepository.markInstallLaunchFailed(
                err.localizedMessage ?: "Unable to launch Android Package Installer."
            )
        }
    }

    fun startAppUpdateDownloadAndInstall() {
        scope.launch {
            val result = appUpdateRepository.downloadAndVerifyLatestApk()
            result.getOrNull()?.let { verifiedFile ->
                if (appUpdateRepository.canRequestPackageInstalls()) {
                    launchVerifiedApkInstaller(verifiedFile)
                }
            }
        }
    }

    fun launchAccountDeletion(
        adminAction: AdminDeleteAction,
        transferTargetUid: String?,
        recentAuthVerified: Boolean
    ) {
        deleteAccountDialogState = DeleteAccountDialogState.DeletingInProgress
        scope.launch {
            when (
                val res = deletionRepository.deleteAccount(
                    adminAction = adminAction,
                    transferTargetUid = transferTargetUid,
                    recentAuthVerifiedInSession = recentAuthVerified
                )
            ) {
                is AccountDeletionResult.Success -> {
                    pendingLoginInfoMessage = appStrings.accountDeletedSuccessMessage
                    deleteAccountDialogState = DeleteAccountDialogState.Idle
                }

                is AccountDeletionResult.RequiresReauthentication -> {
                    deleteAccountDialogState = DeleteAccountDialogState.NeedsReauthentication(
                        adminAction = adminAction,
                        transferTargetUid = transferTargetUid,
                        hasPasswordProvider = res.hasPasswordProvider,
                        hasGoogleProvider = res.hasGoogleProvider,
                        email = res.email,
                        reason = res.reason
                    )
                }

                is AccountDeletionResult.PartialFailure -> {
                    deleteAccountDialogState = DeleteAccountDialogState.PartialFailure(
                        adminAction = adminAction,
                        transferTargetUid = transferTargetUid,
                        completedSteps = res.completedSteps,
                        pendingSteps = res.pendingSteps,
                        errorMessage = res.errorMessage
                    )
                }

                is AccountDeletionResult.Failed -> {
                    deleteAccountDialogState = DeleteAccountDialogState.Failed(
                        errorMessage = res.errorMessage
                    )
                }
            }
        }
    }

    LaunchedEffect(currentUser?.uid, providerRefreshCounter) {
        val activeUser = currentUser
        if (activeUser != null) {
            hasPasswordLinked = accountAuthRepository.refreshPasswordLinkedState()
            hasGoogleLinked = accountAuthRepository.hasGoogleProvider(accountAuthRepository.currentUser() ?: activeUser)
        }
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        val user = currentUser
        if (user == null && deleteAccountDialogState == DeleteAccountDialogState.DeletingInProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        } else if (user == null) {
            AuthScreen(
                languageCode = securitySettings.languageCode,
                onSelectLanguage = { code ->
                    scope.launch { securityPreferences.setLanguage(code) }
                },
                onAuthSuccess = {
                    providerRefreshCounter++
                },
                initialInfoMessage = pendingLoginInfoMessage,
                onConsumeInitialInfoMessage = { pendingLoginInfoMessage = null }
            )
        } else if (!hasPasswordLinked) {
            CreatePasswordScreen(
                user = user,
                languageCode = securitySettings.languageCode,
                onSelectLanguage = { code ->
                    scope.launch { securityPreferences.setLanguage(code) }
                },
                onPasswordLinked = {
                    hasPasswordLinked = true
                    providerRefreshCounter++
                },
                onSignOut = {
                    DocumentTransferManager.clearAllShareTempFiles(context.applicationContext)
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                },
                accountAuthRepository = accountAuthRepository
            )
        } else {
            key(user.uid) {
                AuthenticatedVaultShell(
                    firebaseUser = user,
                    userId = user.uid,
                    fallbackDisplayName = user.displayName ?: "Member",
                    hasGoogleLinked = hasGoogleLinked,
                    hasEmailPasswordLinked = hasPasswordLinked,
                    isDeletingAccount = deleteAccountDialogState != DeleteAccountDialogState.Idle,
                    onProvidersUpdated = { providerRefreshCounter++ },
                    securityPreferences = securityPreferences,
                    accountAuthRepository = accountAuthRepository,
                    credentialManager = credentialManager,
                    onRequestDeleteAccount = { adminAction, targetUid ->
                        launchAccountDeletion(
                            adminAction = adminAction,
                            transferTargetUid = targetUid,
                            recentAuthVerified = false
                        )
                    },
                    onSignOut = {
                        DocumentTransferManager.clearAllShareTempFiles(context.applicationContext)
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    pendingVaultLinkUri = pendingVaultLinkUri,
                    onConsumeVaultLink = onConsumeVaultLink,
                    updateState = updateState,
                    onCheckForUpdates = { scope.launch { appUpdateRepository.checkForUpdates() } },
                    onStartDownloadAndInstall = ::startAppUpdateDownloadAndInstall,
                    onCancelDownload = { appUpdateRepository.cancelActiveDownload() },
                    onLaunchVerifiedInstaller = ::launchVerifiedApkInstaller,
                    onRequestInstallPermission = {
                        runCatching {
                            installPermissionLauncher.launch(
                                appUpdateRepository.buildInstallUnknownSourcesSettingsIntent()
                            )
                        }.onFailure { err ->
                            appUpdateRepository.markInstallLaunchFailed(
                                err.localizedMessage ?: "Unable to open install permission settings."
                            )
                        }
                    }
                )
            }
        }

        DeleteAccountFlowDialogs(
            state = deleteAccountDialogState,
            onVerifyPasswordAndRetry = { enteredPassword ->
                val reauthState = deleteAccountDialogState as? DeleteAccountDialogState.NeedsReauthentication
                    ?: return@DeleteAccountFlowDialogs
                deleteAccountDialogState = reauthState.copy(isVerifying = true, errorMessage = null)
                scope.launch {
                    accountAuthRepository.reauthenticateWithPassword(enteredPassword)
                        .onSuccess {
                            launchAccountDeletion(
                                adminAction = reauthState.adminAction,
                                transferTargetUid = reauthState.transferTargetUid,
                                recentAuthVerified = true
                            )
                        }
                        .onFailure { err ->
                            deleteAccountDialogState = reauthState.copy(
                                isVerifying = false,
                                errorMessage = err.localizedMessage ?: "Verification failed."
                            )
                        }
                }
            },
            onVerifyGoogleAndRetry = {
                val reauthState = deleteAccountDialogState as? DeleteAccountDialogState.NeedsReauthentication
                    ?: return@DeleteAccountFlowDialogs
                deleteAccountDialogState = reauthState.copy(isVerifying = true, errorMessage = null)
                scope.launch {
                    when (
                        val fetch = obtainGoogleCredentialFromManager(
                            context = context,
                            credentialManager = credentialManager,
                            forceAccountChooser = false
                        )
                    ) {
                        is CredentialFetchResult.Cancelled -> {
                            deleteAccountDialogState = reauthState.copy(isVerifying = false)
                        }
                        is CredentialFetchResult.Error -> {
                            deleteAccountDialogState = reauthState.copy(
                                isVerifying = false,
                                errorMessage = fetch.message
                            )
                        }
                        is CredentialFetchResult.Success -> {
                            accountAuthRepository.reauthenticateCurrentUserWithGoogle(fetch.credential)
                                .onSuccess {
                                    launchAccountDeletion(
                                        adminAction = reauthState.adminAction,
                                        transferTargetUid = reauthState.transferTargetUid,
                                        recentAuthVerified = true
                                    )
                                }
                                .onFailure { err ->
                                    deleteAccountDialogState = reauthState.copy(
                                        isVerifying = false,
                                        errorMessage = err.localizedMessage ?: "Google re-verification failed."
                                    )
                                }
                        }
                    }
                }
            },
            onRetryPartialCleanup = {
                val partialState = deleteAccountDialogState as? DeleteAccountDialogState.PartialFailure
                    ?: return@DeleteAccountFlowDialogs
                launchAccountDeletion(
                    adminAction = partialState.adminAction,
                    transferTargetUid = partialState.transferTargetUid,
                    recentAuthVerified = false
                )
            },
            onDismiss = {
                deleteAccountDialogState = DeleteAccountDialogState.Idle
            }
        )

        AnimatedVisibility(
            visible = showSplashOverlay,
            exit = fadeOut()
        ) {
            FamvioraSplashOverlay()
        }
    }
}

@Composable
fun FamvioraSplashOverlay() {
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyDeep, NavyPrimary)
                )
            )
            .testTag("famviora_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PureWhite.copy(alpha = 0.14f),
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = strings.appName,
                        tint = SkyAccent,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineLarge,
                color = PureWhite,
                fontWeight = FontWeight.ExtraBold
            )
            if (strings.languageCode == "kn") {
                Text(
                    text = strings.docActions.brandPronunciationKn,
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.titleSmall,
                color = SkySoft,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.docActions.splashSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SkySoft.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthenticatedVaultShell(
    firebaseUser: FirebaseUser,
    userId: String,
    fallbackDisplayName: String,
    hasGoogleLinked: Boolean,
    hasEmailPasswordLinked: Boolean,
    isDeletingAccount: Boolean,
    onProvidersUpdated: () -> Unit,
    securityPreferences: SecurityPreferences,
    accountAuthRepository: AccountAuthRepository,
    credentialManager: CredentialManager,
    onRequestDeleteAccount: (AdminDeleteAction, String?) -> Unit,
    onSignOut: () -> Unit,
    pendingVaultLinkUri: Uri?,
    onConsumeVaultLink: () -> Unit,
    updateState: AppUpdateState,
    onCheckForUpdates: () -> Unit,
    onStartDownloadAndInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onLaunchVerifiedInstaller: (java.io.File) -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current

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
    val effectiveCloudConfig by viewModel.effectiveCloudConfig.collectAsStateWithLifecycle()
    val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val isActionBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val shellScope = rememberCoroutineScope()
    var isAccountActionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    var currentTab by rememberSaveable { mutableStateOf(VaultTab.HOME) }
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCameraScanner by rememberSaveable { mutableStateOf(false) }
    var showAdminPanel by rememberSaveable { mutableStateOf(false) }
    var showUpdateScreen by rememberSaveable { mutableStateOf(false) }
    var showPinSetup by rememberSaveable { mutableStateOf(false) }
    var openAddMemberInitially by rememberSaveable { mutableStateOf(false) }
    var openUploadDocInitially by rememberSaveable { mutableStateOf(false) }
    var memberFilterId by rememberSaveable { mutableStateOf<String?>(null) }

    var homePendingDownloadDoc by remember { mutableStateOf<FamilyDocument?>(null) }
    var homePendingSafDoc by remember { mutableStateOf<FamilyDocument?>(null) }
    val homeSafCreateLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentWithMimeContract()
    ) { uri ->
        val doc = homePendingSafDoc
        homePendingSafDoc = null
        if (uri != null && doc != null) {
            viewModel.downloadDocumentToSafUri(doc, uri)
        }
    }

    fun openSavedDocumentUri(uri: Uri, mimeType: String) {
        val openIntent = DocumentTransferManager.buildOpenWithIntent(
            context = context,
            contentUri = uri,
            mimeType = mimeType,
            chooserTitle = strings.docActions.openWithLabel
        )
        runCatching {
            context.startActivity(openIntent)
        }.onFailure {
            shellScope.launch {
                snackbarHostState.showSnackbar(strings.docActions.noAppAvailableMessage)
            }
        }
    }

    fun shareSingleDocument(doc: FamilyDocument) {
        shellScope.launch {
            viewModel.prepareShareSingle(doc, strings.shareDocument)
                .onSuccess { intent ->
                    runCatching { context.startActivity(intent) }.onFailure {
                        snackbarHostState.showSnackbar(strings.docActions.noAppAvailableMessage)
                    }
                }
        }
    }

    fun shareMultipleDocuments(docs: List<FamilyDocument>) {
        shellScope.launch {
            viewModel.prepareShareMultiple(docs, strings.docActions.shareMultipleAction)
                .onSuccess { intent ->
                    runCatching { context.startActivity(intent) }.onFailure {
                        snackbarHostState.showSnackbar(strings.docActions.noAppAvailableMessage)
                    }
                }
        }
    }

    fun openWithDocument(doc: FamilyDocument) {
        shellScope.launch {
            viewModel.prepareOpenWith(doc, strings.docActions.openWithLabel)
                .onSuccess { intent ->
                    runCatching { context.startActivity(intent) }.onFailure {
                        snackbarHostState.showSnackbar(strings.docActions.noAppAvailableMessage)
                    }
                }
        }
    }

    fun copyAuthorizedLink(doc: FamilyDocument) {
        val link = viewModel.buildAuthorizedLinkIfAllowed(doc)
        if (link == null) {
            shellScope.launch {
                snackbarHostState.showSnackbar(strings.docActions.unauthorizedAccessMessage)
            }
        } else {
            clipboard.setText(AnnotatedString(link))
            shellScope.launch {
                snackbarHostState.showSnackbar(strings.docActions.linkCopiedMessage)
            }
        }
    }

    LaunchedEffect(
        pendingVaultLinkUri,
        userProfileState,
        familyAccount,
        documentsState,
        settings.pinEnabled,
        isVaultUnlocked
    ) {
        val uri = pendingVaultLinkUri ?: return@LaunchedEffect
        val target = DocumentTransferManager.parseAuthorizedVaultLink(uri)
        if (target == null) {
            onConsumeVaultLink()
            return@LaunchedEffect
        }
        val profileOk = userProfileState as? UiState.Success ?: return@LaunchedEffect
        val profile = profileOk.data
        if (profile == null || profile.familyId.isBlank()) {
            onConsumeVaultLink()
            return@LaunchedEffect
        }
        if (settings.pinEnabled && !isVaultUnlocked) return@LaunchedEffect
        val family = familyAccount ?: return@LaunchedEffect
        val docsOk = documentsState as? UiState.Success ?: return@LaunchedEffect

        if (family.familyId != target.familyId) {
            onConsumeVaultLink()
            snackbarHostState.showSnackbar(strings.docActions.deepLinkUnauthorizedMessage)
            return@LaunchedEffect
        }

        val authorizedDoc = docsOk.data.firstOrNull { doc ->
            doc.id == target.docId && DocumentTransferManager.verifyDocumentAccess(doc, userId, family)
        }
        onConsumeVaultLink()
        if (authorizedDoc != null) {
            showPinSetup = false
            showCameraScanner = false
            showAdminPanel = false
            showUpdateScreen = false
            currentTab = VaultTab.DOCUMENTS
            selectedDocumentId = authorizedDoc.id
        } else {
            snackbarHostState.showSnackbar(strings.docActions.deepLinkUnauthorizedMessage)
        }
    }

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
                        onBack = { selectedDocumentId = null },
                        cloudConfig = effectiveCloudConfig,
                        transferState = transferState,
                        onShare = { shareSingleDocument(selectedDocument) },
                        onDownloadToDownloads = { viewModel.downloadDocumentToDownloads(selectedDocument) },
                        onDownloadToSafUri = { uri -> viewModel.downloadDocumentToSafUri(selectedDocument, uri) },
                        onOpenWith = { openWithDocument(selectedDocument) },
                        onCopyLink = { copyAuthorizedLink(selectedDocument) },
                        onOpenSavedFile = ::openSavedDocumentUri,
                        onDismissTransferState = viewModel::dismissTransferState
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

            if (showUpdateScreen) {
                Box(modifier = Modifier.fillMaxSize()) {
                    UpdateScreen(
                        updateState = updateState,
                        cloudConfig = effectiveCloudConfig,
                        isAdmin = isAdmin,
                        checkOnOpen = true,
                        onCheckForUpdates = onCheckForUpdates,
                        onStartDownloadAndInstall = onStartDownloadAndInstall,
                        onCancelDownload = onCancelDownload,
                        onLaunchVerifiedInstaller = onLaunchVerifiedInstaller,
                        onRequestInstallPermission = onRequestInstallPermission,
                        onSaveFamilyCloudConfig = { noticeActive, titleEn, titleKn, bodyEn, bodyKn, level, bannerEn, bannerKn, categories, expiryDays, showSamples ->
                            viewModel.saveFamilyCloudConfig(
                                noticeActive = noticeActive,
                                noticeTitleEn = titleEn,
                                noticeTitleKn = titleKn,
                                noticeBodyEn = bodyEn,
                                noticeBodyKn = bodyKn,
                                noticeLevel = level,
                                homeBannerLabelEn = bannerEn,
                                homeBannerLabelKn = bannerKn,
                                extraCategories = categories,
                                expiryWarningDays = expiryDays,
                                showSampleDocs = showSamples,
                                savedMessage = strings.cloudConfigSavedMsg
                            )
                        },
                        onBack = { showUpdateScreen = false }
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

            if (showAdminPanel && isAdmin) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AdminPanelScreen(
                        family = family,
                        members = familyMembers,
                        documents = documents,
                        backupStatus = backupStatus,
                        onRenameFamily = viewModel::renameFamily,
                        onAddOrUpdateMember = viewModel::addOrUpdateMember,
                        onDeleteMember = viewModel::deleteMember,
                        onUpdateDocumentPermissions = { doc, allowed ->
                            viewModel.updateDocumentPermissions(doc, allowed, strings.permissionsSaved)
                        },
                        onBackupNow = { viewModel.runManualBackup(strings.backupCompletedMsg) },
                        onShowMessage = { msg -> shellScope.launch { snackbarHostState.showSnackbar(msg) } },
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
                                    onShowMessage = { msg -> shellScope.launch { snackbarHostState.showSnackbar(msg) } },
                                    updateState = updateState,
                                    cloudConfig = effectiveCloudConfig,
                                    onOpenUpdates = { showUpdateScreen = true },
                                    onStartAppUpdate = {
                                        showUpdateScreen = true
                                        if (updateState.phase != UpdatePhase.DOWNLOADING) {
                                            onStartDownloadAndInstall()
                                        }
                                    },
                                    onShareDocument = ::shareSingleDocument,
                                    onDownloadDocument = { doc -> homePendingDownloadDoc = doc }
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
                                    },
                                    cloudConfig = effectiveCloudConfig,
                                    transferState = transferState,
                                    onShareSingle = ::shareSingleDocument,
                                    onShareMultiple = ::shareMultipleDocuments,
                                    onDownloadToDownloads = { docsToSave ->
                                        if (docsToSave.size == 1) {
                                            viewModel.downloadDocumentToDownloads(docsToSave.first())
                                        } else {
                                            viewModel.downloadMultipleToDownloads(docsToSave)
                                        }
                                    },
                                    onDownloadSingleToSafUri = { doc, uri ->
                                        viewModel.downloadDocumentToSafUri(doc, uri)
                                    },
                                    onDownloadMultipleToTreeUri = { docsToSave, treeUri ->
                                        viewModel.downloadMultipleToTreeUri(docsToSave, treeUri)
                                    },
                                    onOpenSavedFile = ::openSavedDocumentUri,
                                    onDismissTransferState = viewModel::dismissTransferState
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
                                val resolvedEmail = firebaseUser.email?.takeIf { it.isNotBlank() }
                                    ?: userProfile.email
                                SettingsScreen(
                                    userProfile = userProfile,
                                    family = family,
                                    isAdmin = isAdmin,
                                    familyMembers = familyMembers,
                                    securitySettings = settings,
                                    userEmail = resolvedEmail,
                                    firebaseUid = userId,
                                    hasGoogleLinked = hasGoogleLinked,
                                    hasEmailPasswordLinked = hasEmailPasswordLinked,
                                    isAccountActionBusy = isAccountActionBusy || isDeletingAccount,
                                    onLinkGoogleAccount = {
                                        isAccountActionBusy = true
                                        shellScope.launch {
                                            when (
                                                val fetch = obtainGoogleCredentialFromManager(
                                                    context = context,
                                                    credentialManager = credentialManager,
                                                    forceAccountChooser = true
                                                )
                                            ) {
                                                is CredentialFetchResult.Cancelled -> {
                                                    isAccountActionBusy = false
                                                }
                                                is CredentialFetchResult.Error -> {
                                                    isAccountActionBusy = false
                                                    snackbarHostState.showSnackbar(fetch.message)
                                                }
                                                is CredentialFetchResult.Success -> {
                                                    accountAuthRepository.linkGoogleCredentialToSignedInUser(
                                                        googleCredential = fetch.credential,
                                                        googleEmail = fetch.email
                                                    ).onSuccess {
                                                        isAccountActionBusy = false
                                                        onProvidersUpdated()
                                                        snackbarHostState.showSnackbar(strings.googleLinkedSuccessMsg)
                                                    }.onFailure { err ->
                                                        isAccountActionBusy = false
                                                        snackbarHostState.showSnackbar(
                                                            err.localizedMessage ?: "Unable to link Google account."
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onOpenCreatePassword = {
                                        onProvidersUpdated()
                                    },
                                    onSendPasswordResetEmail = {
                                        if (resolvedEmail.isNotBlank()) {
                                            isAccountActionBusy = true
                                            shellScope.launch {
                                                accountAuthRepository.sendPasswordResetEmail(resolvedEmail)
                                                    .onSuccess {
                                                        isAccountActionBusy = false
                                                        snackbarHostState.showSnackbar(strings.resetEmailSentMessage)
                                                    }
                                                    .onFailure { err ->
                                                        isAccountActionBusy = false
                                                        snackbarHostState.showSnackbar(
                                                            err.localizedMessage ?: "Unable to send password reset email."
                                                        )
                                                    }
                                            }
                                        }
                                    },
                                    onSelectLanguage = viewModel::setLanguage,
                                    onSelectThemeMode = viewModel::setThemeMode,
                                    onSetupOrChangePin = { showPinSetup = true },
                                    onDisablePin = { viewModel.disablePin() },
                                    onToggleBiometric = viewModel::setBiometricEnabled,
                                    onToggleExpiryReminders = viewModel::setExpiryRemindersEnabled,
                                    onCheckExpiriesNow = viewModel::checkExpiriesAndNotifyNow,
                                    onOpenBackupCenter = { currentTab = VaultTab.BACKUP },
                                    onOpenAdminPanel = { showAdminPanel = true },
                                    onLogout = onSignOut,
                                    onConfirmDeleteAccount = onRequestDeleteAccount,
                                    hasUpdateAvailable = updateState.hasUpdate,
                                    installedVersionLabel = "v${updateState.installedVersionName} (${updateState.installedVersionCode})",
                                    onOpenUpdates = { showUpdateScreen = true }
                                )
                            }
                        }
                        if (currentTab != VaultTab.DOCUMENTS && transferState != null) {
                            DocumentTransferBanner(
                                transferState = transferState,
                                onOpenFile = ::openSavedDocumentUri,
                                onDismiss = viewModel::dismissTransferState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                    }

                    homePendingDownloadDoc?.let { targetDoc ->
                        DownloadDestinationDialog(
                            targetLabel = DocumentTransferManager.buildSafeFileName(targetDoc),
                            onSaveToDownloads = {
                                homePendingDownloadDoc = null
                                viewModel.downloadDocumentToDownloads(targetDoc)
                            },
                            onChooseFolder = {
                                homePendingDownloadDoc = null
                                homePendingSafDoc = targetDoc
                                homeSafCreateLauncher.launch(
                                    DocumentTransferManager.buildSafeFileName(targetDoc) to
                                        DocumentTransferManager.resolveMimeType(targetDoc)
                                )
                            },
                            onDismiss = { homePendingDownloadDoc = null }
                        )
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
