package com.example.feature.vault

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.CopyOrMoveImportSheet
import com.example.core.ui.components.CreateCategoryDialog
import com.example.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultRoute(
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPickDocSheet by remember { mutableStateOf(false) }
    var showCreateLockedCatDialog by remember { mutableStateOf(false) }
    var pendingDeviceImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupExportConfig by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showBackupPassDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val importDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingDeviceImportUri = uri
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val config = pendingBackupExportConfig
        pendingBackupExportConfig = null
        if (uri != null && config != null && config.first.isNotBlank()) {
            viewModel.exportEncryptedBackup(
                destUri = uri,
                passphrase = config.first,
                includeVaultFiles = config.second
            )
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    // Prevent screenshots and blank recents preview while Vault is open
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Auto-lock when app goes to background and returns past timeout
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is VaultEvent.ShowMessage -> snackbarHostState.showSnackbar(ev.message)
            }
        }
    }

    val launchBiometric: () -> Unit = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.onBiometricSuccess()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Verify your identity to access encrypted documents")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(info)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                title = {
                    Column {
                        Text("Private Vault", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "AES-256-GCM encrypted • Screen capture blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.isUnlocked) {
                        FilledTonalButton(
                            onClick = viewModel::lockVault,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("vault_lock_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lock now")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!state.isUnlocked) {
                VaultLockGate(
                    isPinConfigured = state.isPinConfigured,
                    pinError = state.pinError,
                    onCreatePin = viewModel::createOrUpdatePin,
                    onVerifyPin = viewModel::verifyPin,
                    onBiometricUnlock = launchBiometric
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isBusy) {
                        item(key = "vault_busy") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    }

                    item(key = "vault_uninstall_warning") {
                        VaultUninstallWarningCard()
                    }

                    item(key = "vault_categories_row") {
                        VaultCategoryCardsRow(
                            summaries = state.categorySummaries,
                            selectedFilterId = state.selectedCategoryFilterId,
                            onSelectFilter = viewModel::selectCategoryFilter,
                            onCreateLockedCategoryClick = { showCreateLockedCatDialog = true }
                        )
                    }

                    item(key = "vault_protection_card") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Vault & App Lock", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${state.lockedDocuments.size} file(s) • ${formatVaultStorage(state.totalVaultStorageBytes)} encrypted",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showPickDocSheet = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vault_add_doc_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("From Library")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            importDeviceLauncher.launch(
                                                arrayOf("application/pdf", "image/*", "text/plain", "*/*")
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vault_import_device_button")
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Import File")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Lock whole app", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Require biometric or PIN when opening PDF Toolkit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.prefs.appLockEnabled,
                                        onCheckedChange = viewModel::setAppLockEnabled,
                                        modifier = Modifier.testTag("vault_app_lock_switch")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Hide locked thumbnails & titles", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Masks document titles & previews in Vault list",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.prefs.hideLockedThumbnails,
                                        onCheckedChange = viewModel::setHideLockedThumbnails,
                                        modifier = Modifier.testTag("vault_hide_titles_switch")
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to "Immediate", 1 to "1 min", 5 to "5 min").forEach { (mins, label) ->
                                        FilterChip(
                                            selected = state.prefs.vaultAutoLockMinutes == mins,
                                            onClick = { viewModel.setAutoLockMinutes(mins) },
                                            label = { Text("Auto-lock: $label") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "vault_backup_card") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Encrypted Offline Backup", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Export notes, saved signatures, highlights, settings, and optionally Vault files into a passphrase-encrypted .pdftk archive via SAF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { showBackupPassDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vault_export_backup_button")
                                    ) {
                                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export Backup")
                                    }
                                    OutlinedButton(
                                        onClick = { restoreBackupLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vault_restore_backup_button")
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }

                    if (state.filteredLockedDocuments.isEmpty()) {
                        item(key = "vault_empty_state") {
                            EmptyStateView(
                                icon = Icons.Default.Lock,
                                title = "No encrypted documents here",
                                subtitle = "Encrypt files from your Library or import directly from device storage with AES-256-GCM.",
                                primaryActionLabel = "Encrypt from Library",
                                onPrimaryAction = { showPickDocSheet = true }
                            )
                        }
                    } else {
                        itemsIndexed(state.filteredLockedDocuments, key = { _, doc -> doc.id }) { idx, doc ->
                            val maskedTitle = if (state.prefs.hideLockedThumbnails) {
                                "Protected Document #${idx + 1}"
                            } else {
                                doc.title
                            }
                            Card(
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onOpenReader(doc.id) }
                                    .testTag("vault_doc_card_${doc.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (state.prefs.hideLockedThumbnails) Icons.Default.VisibilityOff else Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = maskedTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${doc.pageCount} pages • ${formatVaultStorage(doc.fileSizeBytes)} • AES-256",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.unlockDocument(doc) }) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Move back to Library")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickDocSheet) {
        PickLibraryDocToVaultDialog(
            unlockedDocuments = state.unlockedDocuments,
            onSelectDoc = { doc -> viewModel.lockDocument(doc) },
            onDismiss = { showPickDocSheet = false }
        )
    }

    if (showCreateLockedCatDialog) {
        CreateCategoryDialog(
            defaultLocked = true,
            onConfirm = { name, icon, color, _ ->
                viewModel.createLockedCategory(name, icon, color)
            },
            onDismiss = { showCreateLockedCatDialog = false }
        )
    }

    pendingDeviceImportUri?.let { uri ->
        CopyOrMoveImportSheet(
            title = "Import & Encrypt to Vault",
            subtitle = "File will be encrypted with AES-256-GCM inside app-private storage.",
            categories = state.lockedCategories,
            defaultCategoryId = state.selectedCategoryFilterId?.takeUnless { it == VaultViewModel.GENERAL_VAULT_FILTER_ID },
            onConfirmCopy = { catId ->
                viewModel.importFromDeviceToVault(uri, categoryId = catId, deleteOriginal = false)
            },
            onConfirmMove = { catId ->
                viewModel.importFromDeviceToVault(uri, categoryId = catId, deleteOriginal = true)
            },
            onDismiss = { pendingDeviceImportUri = null }
        )
    }

    if (showBackupPassDialog) {
        ExportBackupPassphraseDialog(
            onConfirm = { passphrase, includeVaultFiles ->
                pendingBackupExportConfig = passphrase to includeVaultFiles
                exportBackupLauncher.launch("pdf_toolkit_backup_${System.currentTimeMillis()}.pdftk")
            },
            onDismiss = { showBackupPassDialog = false }
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreBackupPassphraseDialog(
            onConfirm = { passphrase -> viewModel.restoreEncryptedBackup(uri, passphrase) },
            onDismiss = { pendingRestoreUri = null }
        )
    }
}
