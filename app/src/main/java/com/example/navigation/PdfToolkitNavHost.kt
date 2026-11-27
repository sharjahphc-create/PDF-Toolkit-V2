package com.example.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.LaunchCommand
import com.example.R
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.UserPreferences
import com.example.core.security.VaultCryptoManager
import com.example.data.repository.LibraryRepository
import com.example.feature.ai.AiAssistantRoute
import com.example.feature.designsystem.DesignSystemGalleryScreen
import com.example.feature.documents.OfficeToolRoute
import com.example.feature.feedback.FeedbackScreen
import com.example.feature.library.LibraryTtsRoute
import com.example.feature.more.MoreTabScreen
import com.example.feature.notes.NotesTabScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.pdftools.CorePdfToolRoute
import com.example.feature.reader.ReaderRoute
import com.example.feature.sign.SignRoute
import com.example.feature.standout.StandoutToolRoute
import com.example.feature.tools.ToolWorkspaceScreen
import com.example.feature.tools.ToolsHomeRoute
import com.example.feature.vault.VaultRoute
import com.example.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: AppRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = AppRoute.ToolsHome,
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tab_tools"
    ),
    TopLevelDestination(
        route = AppRoute.Notes,
        labelRes = R.string.nav_notes,
        selectedIcon = Icons.AutoMirrored.Filled.NoteAdd,
        unselectedIcon = Icons.AutoMirrored.Outlined.NoteAdd,
        testTag = "nav_tab_notes"
    ),
    TopLevelDestination(
        route = AppRoute.More,
        labelRes = R.string.nav_more,
        selectedIcon = Icons.Filled.Menu,
        unselectedIcon = Icons.Outlined.Menu,
        testTag = "nav_tab_more"
    )
)

@Composable
fun PdfToolkitAppShell(
    widthSizeClass: WindowWidthSizeClass,
    prefs: UserPreferences,
    preferencesRepository: PreferencesRepository,
    libraryRepository: LibraryRepository,
    launchCommand: LaunchCommand? = null,
    onConsumeLaunchCommand: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val shellSnackbarHostState = remember { SnackbarHostState() }
    val cryptoManager = remember(context) {
        VaultCryptoManager(context.applicationContext, kotlinx.coroutines.Dispatchers.IO)
    }

    var isAppUnlocked by rememberSaveable { mutableStateOf(true) }
    var appLockBackgroundedAt by rememberSaveable { mutableLongStateOf(0L) }
    var initialCheckDone by rememberSaveable { mutableStateOf(false) }
    var showDefaultPdfBanner by remember { mutableStateOf(false) }

    // Synchronize initial App Lock & First-Launch Onboarding after first real DataStore emission
    LaunchedEffect(Unit) {
        val initialPrefs = preferencesRepository.preferences.first()
        if (initialPrefs.appLockEnabled && !initialPrefs.vaultPinHash.isNullOrBlank()) {
            isAppUnlocked = false
        }
        if (!initialPrefs.onboardingCompleted && !initialCheckDone) {
            navController.navigate(AppRoute.Onboarding) {
                launchSingleTop = true
            }
        }
        initialCheckDone = true
    }

    // Lifecycle auto-lock for App Lock mode
    DisposableEffect(lifecycleOwner, prefs.appLockEnabled, prefs.vaultPinHash, prefs.vaultAutoLockMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            val lockActive = prefs.appLockEnabled && !prefs.vaultPinHash.isNullOrBlank()
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (lockActive) appLockBackgroundedAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    if (lockActive && appLockBackgroundedAt > 0L) {
                        val elapsed = System.currentTimeMillis() - appLockBackgroundedAt
                        if (elapsed >= prefs.vaultAutoLockMinutes * 60_000L) {
                            isAppUnlocked = false
                        }
                    }
                    appLockBackgroundedAt = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle external ACTION_VIEW, ACTION_SEND, App Shortcuts, Quick Settings Tile, and Glance Widget
    LaunchedEffect(launchCommand?.commandId) {
        val cmd = launchCommand ?: return@LaunchedEffect
        when (cmd) {
            is LaunchCommand.OpenTool -> {
                navController.navigate(AppRoute.ToolWorkspace(cmd.toolId)) {
                    launchSingleTop = true
                }
            }
            is LaunchCommand.OpenTab -> {
                when (cmd.tabKey) {
                    "notes" -> navController.navigate(AppRoute.Notes) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    "library" -> navController.navigate(AppRoute.Library) {
                        launchSingleTop = true
                    }
                }
            }
            is LaunchCommand.OpenSharedImages -> {
                navController.navigate(AppRoute.ToolWorkspace("photos_to_pdf")) {
                    launchSingleTop = true
                }
            }
            is LaunchCommand.OpenDocumentUri -> {
                val res = libraryRepository.importFromUri(cmd.uri)
                res.onSuccess { doc ->
                    navController.navigate(AppRoute.Reader(doc.id)) {
                        launchSingleTop = true
                    }
                    if (cmd.fromViewIntent && prefs.defaultPdfPromptCount < 2 && !prefs.dontAskDefaultPdf) {
                        showDefaultPdfBanner = true
                    }
                }.onFailure { err ->
                    shellSnackbarHostState.showSnackbar("Unable to open file: ${err.localizedMessage ?: "Unsupported file"}")
                }
            }
        }
        onConsumeLaunchCommand()
    }

    val showTopLevelChrome = currentDestination?.let { dest ->
        dest.hasRoute<AppRoute.ToolsHome>() ||
            dest.hasRoute<AppRoute.Notes>() ||
            dest.hasRoute<AppRoute.More>()
    } ?: true

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    val navigateToTopLevel: (AppRoute) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(shellSnackbarHostState) },
            bottomBar = {
                if (showTopLevelChrome && !useNavRail) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            val selected = when (dest.route) {
                                AppRoute.ToolsHome -> currentDestination?.hasRoute<AppRoute.ToolsHome>() == true
                                AppRoute.Notes -> currentDestination?.hasRoute<AppRoute.Notes>() == true
                                AppRoute.More -> currentDestination?.hasRoute<AppRoute.More>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelChrome && useNavRail) {
                    NavigationRail {
                        topLevelDestinations.forEach { dest ->
                            val selected = when (dest.route) {
                                AppRoute.ToolsHome -> currentDestination?.hasRoute<AppRoute.ToolsHome>() == true
                                AppRoute.Notes -> currentDestination?.hasRoute<AppRoute.Notes>() == true
                                AppRoute.More -> currentDestination?.hasRoute<AppRoute.More>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("${dest.testTag}_rail")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (showDefaultPdfBanner) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("default_pdf_viewer_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Set PDF Toolkit as default PDF viewer?",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        showDefaultPdfBanner = false
                                        scope.launch { preferencesRepository.incrementDefaultPdfPromptCount() }
                                        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }.onFailure {
                                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            runCatching { context.startActivity(fallback) }
                                        }
                                    }) {
                                        Text("Settings")
                                    }
                                    TextButton(onClick = {
                                        showDefaultPdfBanner = false
                                        scope.launch { preferencesRepository.incrementDefaultPdfPromptCount() }
                                    }) {
                                        Text("Later")
                                    }
                                    TextButton(onClick = {
                                        showDefaultPdfBanner = false
                                        scope.launch { preferencesRepository.setDontAskDefaultPdf(true) }
                                    }) {
                                        Text("Don't ask again")
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.ToolsHome,
                            enterTransition = {
                                if (reduceMotion) EnterTransition.None
                                else slideInHorizontally(tween(240)) { it / 6 } + fadeIn(tween(240))
                            },
                            exitTransition = {
                                if (reduceMotion) ExitTransition.None
                                else slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(180))
                            },
                            popEnterTransition = {
                                if (reduceMotion) EnterTransition.None
                                else slideInHorizontally(tween(240)) { -it / 6 } + fadeIn(tween(240))
                            },
                            popExitTransition = {
                                if (reduceMotion) ExitTransition.None
                                else slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(180))
                            }
                        ) {
                            composable<AppRoute.Onboarding> {
                                OnboardingScreen(
                                    preferencesRepository = preferencesRepository,
                                    libraryRepository = libraryRepository,
                                    onCompleted = { sampleDocId ->
                                        navController.navigate(AppRoute.ToolsHome) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }
                                        if (sampleDocId != null) {
                                            navController.navigate(AppRoute.Reader(sampleDocId))
                                        }
                                    }
                                )
                            }
                            composable<AppRoute.ToolsHome> {
                                ToolsHomeRoute(
                                    widthSizeClass = widthSizeClass,
                                    onOpenTool = { toolId ->
                                        if (toolId == "notes") {
                                            navigateToTopLevel(AppRoute.Notes)
                                        } else {
                                            navController.navigate(AppRoute.ToolWorkspace(toolId))
                                        }
                                    },
                                    onOpenLibrary = {
                                        navController.navigate(AppRoute.Library)
                                    },
                                    onOpenDocument = { documentId ->
                                        navController.navigate(AppRoute.Reader(documentId))
                                    }
                                )
                            }
                            composable<AppRoute.Notes> {
                                NotesTabScreen(
                                    onOpenLibrary = { navController.navigate(AppRoute.Library) }
                                )
                            }
                            composable<AppRoute.More> {
                                MoreTabScreen(
                                    prefs = prefs,
                                    preferencesRepository = preferencesRepository,
                                    onOpenLibrary = { navController.navigate(AppRoute.Library) },
                                    onOpenDesignSystem = { navController.navigate(AppRoute.DesignSystem) },
                                    onOpenVault = { navController.navigate(AppRoute.Vault) },
                                    onOpenFeedback = { navController.navigate(AppRoute.Feedback) },
                                    onOpenOnboarding = { navController.navigate(AppRoute.Onboarding) }
                                )
                            }
                            composable<AppRoute.Library> {
                                LibraryTtsRoute(
                                    onOpenReader = { documentId ->
                                        navController.navigate(AppRoute.Reader(documentId))
                                    }
                                )
                            }
                            composable<AppRoute.Vault> {
                                VaultRoute(
                                    onBack = { navController.popBackStack() },
                                    onOpenReader = { documentId ->
                                        navController.navigate(AppRoute.Reader(documentId))
                                    }
                                )
                            }
                            composable<AppRoute.Feedback> {
                                FeedbackScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<AppRoute.DesignSystem> {
                                DesignSystemGalleryScreen(
                                    prefs = prefs,
                                    preferencesRepository = preferencesRepository
                                )
                            }
                            composable<AppRoute.Reader> { entry ->
                                val route = entry.toRoute<AppRoute.Reader>()
                                ReaderRoute(
                                    documentId = route.documentId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<AppRoute.ToolWorkspace> { entry ->
                                val route = entry.toRoute<AppRoute.ToolWorkspace>()
                                when (route.toolId) {
                                    "sign" -> SignRoute(
                                        onExit = { navController.popBackStack() },
                                        onOpenLibrary = { navController.navigate(AppRoute.Library) }
                                    )
                                    "fill_form",
                                    "compress",
                                    "merge",
                                    "split",
                                    "organize_pages",
                                    "digital_signature",
                                    "password",
                                    "photos_to_pdf",
                                    "copy_text" -> CorePdfToolRoute(
                                        toolId = route.toolId,
                                        onExit = { navController.popBackStack() },
                                        onOpenLibrary = { navController.navigate(AppRoute.Library) }
                                    )
                                    "word",
                                    "excel",
                                    "powerpoint" -> OfficeToolRoute(
                                        toolId = route.toolId,
                                        onExit = { navController.popBackStack() },
                                        onOpenLibrary = { navController.navigate(AppRoute.Library) }
                                    )
                                    "ai_assistant" -> AiAssistantRoute(
                                        onExit = { navController.popBackStack() },
                                        onOpenReader = { docId -> navController.navigate(AppRoute.Reader(docId)) }
                                    )
                                    "doc_scanner",
                                    "redact",
                                    "watermark_pages",
                                    "compare_pdfs",
                                    "workflows",
                                    "batch_tools",
                                    "convert_pdf",
                                    "id_scanner",
                                    "qr_barcode",
                                    "autoclean_scan",
                                    "translate_pdf" -> StandoutToolRoute(
                                        toolId = route.toolId,
                                        onExit = { navController.popBackStack() },
                                        onOpenLibrary = { navController.navigate(AppRoute.Library) },
                                        onOpenReader = { docId -> navController.navigate(AppRoute.Reader(docId)) }
                                    )
                                    else -> ToolWorkspaceScreen(
                                        toolId = route.toolId,
                                        onBack = { navController.popBackStack() },
                                        onOpenLibrary = { navController.navigate(AppRoute.Library) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen App Lock gate when enabled
        if (!isAppUnlocked && prefs.appLockEnabled && !prefs.vaultPinHash.isNullOrBlank()) {
            AppLockGateOverlay(
                storedPinHash = prefs.vaultPinHash,
                cryptoManager = cryptoManager,
                onUnlocked = { isAppUnlocked = true }
            )
        }
    }
}

@Composable
private fun AppLockGateOverlay(
    storedPinHash: String?,
    cryptoManager: VaultCryptoManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val launchBiometric = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock PDF Toolkit")
                .setSubtitle("Authenticate to access your documents")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(info)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text("PDF Toolkit Locked", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Enter your Vault PIN or unlock with biometrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter(Char::isDigit).take(12)
                        pinError = null
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError != null,
                    supportingText = pinError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_pin_input")
                )
                Button(
                    onClick = {
                        if (cryptoManager.verifyPin(pinInput, storedPinHash)) {
                            onUnlocked()
                        } else {
                            pinError = "Incorrect PIN"
                        }
                    },
                    enabled = pinInput.length >= 4,
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_unlock_button")
                ) {
                    Text("Unlock")
                }
                OutlinedButton(
                    onClick = launchBiometric,
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_biometric_button")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics / Device Lock")
                }
            }
        }
    }
}
