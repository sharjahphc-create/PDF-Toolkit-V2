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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
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
    var pendingBackupExportPass by remember { mutableStateOf<String?>(null) }
    var showBackupPassDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pass = pendingBackupExportPass
        pendingBackupExportPass = null
        if (uri != null && !pass.isNullOrBlank()) {
            viewModel.exportEncryptedBackup(uri, pass)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    // Prevent screenshots and recents thumbnails while Vault is open
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
                        IconButton(
                            onClick = viewModel::lockVault,
                            modifier = Modifier.testTag("vault_lock_now_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault now")
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
                        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    }

                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
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
                                        Text("Vault Protection Active", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${state.lockedDocuments.size} encrypted document(s)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = { showPickDocSheet = true },
                                        modifier = Modifier.testTag("vault_add_doc_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Encrypt File")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Hide locked thumbnails", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = state.prefs.hideLockedThumbnails,
                                        onCheckedChange = viewModel::setHideLockedThumbnails
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

                    item {
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
                                    "Bundle documents, notes, bookmarks, highlights, and Vault files into a passphrase-encrypted .pdftk file via Storage Access Framework.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { showBackupPassDialog = true },
                                        modifier = Modifier.weight(1f).testTag("vault_export_backup_button")
                                    ) {
                                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export Backup")
                                    }
                                    OutlinedButton(
                                        onClick = { restoreBackupLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                        modifier = Modifier.weight(1f).testTag("vault_restore_backup_button")
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }

                    if (state.lockedDocuments.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.Lock,
                                title = "No encrypted documents yet",
                                subtitle = "Move sensitive IDs, tax forms, or contracts here to encrypt them with Android Keystore AES-256-GCM.",
                                primaryActionLabel = "Encrypt from Library",
                                onPrimaryAction = { showPickDocSheet = true }
                            )
                        }
                    } else {
                        items(state.lockedDocuments, key = { it.id }) { doc ->
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
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${doc.pageCount} pages • AES-256 encrypted",
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
        AlertDialog(
            onDismissRequest = { showPickDocSheet = false },
            title = { Text("Move Document to Vault") },
            text = {
                if (state.unlockedDocuments.isEmpty()) {
                    Text("All documents in your Library are already encrypted or none imported yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.unlockedDocuments, key = { it.id }) { doc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.lockDocument(doc)
                                        showPickDocSheet = false
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(doc.title, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickDocSheet = false }) { Text("Close") }
            }
        )
    }

    if (showBackupPassDialog) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBackupPassDialog = false },
            title = { Text("Encrypt Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a strong passphrase (min 4 characters). You will need it to restore this backup.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingBackupExportPass = passphrase
                        showBackupPassDialog = false
                        exportBackupLauncher.launch("pdf_toolkit_backup_${System.currentTimeMillis()}.pdftk")
                    },
                    enabled = passphrase.length >= 4
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPassDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Decrypt Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when creating this .pdftk backup.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreEncryptedBackup(uri, passphrase)
                        pendingRestoreUri = null
                    },
                    enabled = passphrase.length >= 4
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun VaultLockGate(
    isPinConfigured: Boolean,
    pinError: String?,
    onCreatePin: (String) -> Unit,
    onVerifyPin: (String) -> Unit,
    onBiometricUnlock: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 460.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isPinConfigured) "Enter Vault PIN" else "Set Up Private Vault PIN",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isPinConfigured) {
                "Documents inside the Vault stay hidden from Recent Files and Search."
            } else {
                "Create a 4+ digit PIN to protect encrypted documents and backups."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 12) pinInput = it },
            label = { Text(if (isPinConfigured) "PIN" else "New PIN (4+ digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("vault_pin_input")
        )
        if (pinError != null) {
            Spacer(Modifier.height(6.dp))
            Text(pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (isPinConfigured) onVerifyPin(pinInput) else onCreatePin(pinInput)
            },
            enabled = pinInput.length >= 4,
            modifier = Modifier.fillMaxWidth().testTag("vault_unlock_button")
        ) {
            Text(if (isPinConfigured) "Unlock Vault" else "Save PIN & Unlock")
        }
        if (isPinConfigured) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBiometricUnlock,
                modifier = Modifier.fillMaxWidth().testTag("vault_biometric_button")
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with Biometrics")
            }
        }
    }
}
