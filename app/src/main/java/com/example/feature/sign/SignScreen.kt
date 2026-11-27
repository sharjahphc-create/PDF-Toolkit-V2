package com.example.feature.sign

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.ConfirmActionBottomSheet
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.MoveToCategoryBottomSheet
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.ToolDoneCard
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolWorkflowScaffold

@Composable
fun SignRoute(
    onExit: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: SignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.pickPdfFromUri(it) }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        destUri?.let { viewModel.exportOutputToSafUri(it) }
    }

    BackHandler(enabled = uiState.step != SignWizardStep.PICK) {
        viewModel.stepBackOrExit(onExit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SignEvent.ShowMessage -> {
                    val res = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.allowUndoPlacement) context.getString(R.string.undo) else null,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed && event.allowUndoPlacement) {
                        viewModel.undoPlacementChange()
                    }
                }
                is SignEvent.LaunchShare -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.title))
                }
            }
        }
    }

    val mappedStep = when (uiState.step) {
        SignWizardStep.PICK -> ToolStep.PICK
        SignWizardStep.DRAW, SignWizardStep.PLACE -> ToolStep.CONFIGURE
        SignWizardStep.SAVE_PROGRESS -> ToolStep.PROCESS
        SignWizardStep.DONE -> ToolStep.DONE
    }

    val hasConfirmedPlacement = uiState.placements.any { it.isConfirmed }

    val primaryText = when (uiState.step) {
        SignWizardStep.PICK -> "Choose PDF via SAF"
        SignWizardStep.DRAW -> "Continue to Place"
        SignWizardStep.PLACE -> if (hasConfirmedPlacement) "Save Signed PDF" else stringResource(R.string.sign_place_signature)
        SignWizardStep.SAVE_PROGRESS -> null
        SignWizardStep.DONE -> "Done"
    }

    ToolWorkflowScaffold(
        title = stringResource(R.string.sign_title),
        subtitle = uiState.sourceDisplayName.ifBlank { stringResource(R.string.sign_subtitle) },
        currentStep = mappedStep,
        onBack = { viewModel.stepBackOrExit(onExit) },
        primaryButtonText = primaryText,
        primaryButtonEnabled = when (uiState.step) {
            SignWizardStep.DRAW -> uiState.strokes.isNotEmpty()
            SignWizardStep.PLACE -> uiState.placements.isNotEmpty()
            else -> true
        },
        onPrimaryClick = {
            when (uiState.step) {
                SignWizardStep.PICK -> openPdfLauncher.launch(arrayOf("application/pdf"))
                SignWizardStep.DRAW -> viewModel.finishDrawingAndProceed()
                SignWizardStep.PLACE -> {
                    if (hasConfirmedPlacement) viewModel.saveSignedPdf()
                    else viewModel.openConfirmSheet()
                }
                SignWizardStep.SAVE_PROGRESS -> Unit
                SignWizardStep.DONE -> onExit()
            }
        },
        secondaryActionText = if (uiState.step == SignWizardStep.DONE) stringResource(R.string.sign_replace_original) else null,
        onSecondaryClick = if (uiState.step == SignWizardStep.DONE) {
            { viewModel.requestReplaceOriginal() }
        } else null,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (uiState.step) {
            SignWizardStep.PICK -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.Draw,
                            title = "Pick a PDF to sign",
                            subtitle = "Select any PDF via Storage Access Framework or try the bundled sample guide.",
                            primaryActionLabel = "Browse PDFs",
                            onPrimaryAction = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            secondaryActionLabel = stringResource(R.string.import_sample),
                            onSecondaryAction = viewModel::pickBundledSample
                        )
                    }
                    val pdfDocs = libraryDocs.filter { it.mimeType == "application/pdf" }
                    if (pdfDocs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Or pick from your Library",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(pdfDocs, key = { it.id }) { doc ->
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { viewModel.pickPdfFromLibrary(doc) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${doc.pageCount} pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SignWizardStep.DRAW -> {
                val activity = context as? FragmentActivity
                SignPadSection(
                    strokes = uiState.strokes,
                    selectedColor = uiState.selectedColor,
                    strokeWidthDp = uiState.strokeWidthDp,
                    showGuideLine = uiState.showGuideLine,
                    signatureKind = uiState.signatureKind,
                    saveForReuse = uiState.saveForReuse,
                    vaultUnlocked = uiState.vaultUnlocked,
                    vaultEntries = uiState.vaultEntries,
                    onColorSelected = viewModel::selectPenColor,
                    onStrokeWidthChange = viewModel::setStrokeWidth,
                    onToggleGuideLine = viewModel::toggleGuideLine,
                    onKindSelected = viewModel::setSignatureKind,
                    onSaveForReuseChange = viewModel::setSaveForReuse,
                    onStrokeCommitted = viewModel::addStroke,
                    onUndoStroke = viewModel::undoStroke,
                    onClearPad = viewModel::clearPad,
                    onUnlockVault = { activity?.let { viewModel.unlockVault(it) } },
                    onPickVaultEntry = viewModel::useVaultEntry
                )
            }

            SignWizardStep.PLACE -> {
                SignPlacementSection(
                    pageBitmap = uiState.currentPageBitmap,
                    currentPageIndex = uiState.currentPageIndex,
                    pageCount = uiState.pageCount,
                    placementsOnPage = uiState.placements.filter { it.pageIndex == uiState.currentPageIndex },
                    selectedPlacementId = uiState.selectedPlacementId,
                    canUndo = uiState.undoStack.isNotEmpty(),
                    onPreviousPage = viewModel::goToPreviousPage,
                    onNextPage = viewModel::goToNextPage,
                    onSelectPlacement = viewModel::selectPlacement,
                    onUpdatePlacementBounds = viewModel::updatePlacementBounds,
                    onUpdateStampMeta = viewModel::updateStampMeta,
                    onUndoPlacement = viewModel::undoPlacementChange,
                    onDeleteSelected = viewModel::deleteSelectedPlacement,
                    onDuplicateSelected = viewModel::duplicateSelectedPlacement,
                    onApplyToAllPages = viewModel::applySelectedToAllPages,
                    onAddAnotherSignature = viewModel::addAnotherSignature,
                    onRequestConfirmPlacement = viewModel::openConfirmSheet
                )
            }

            SignWizardStep.SAVE_PROGRESS -> {
                ProcessingProgressView(
                    progress = uiState.saveProgress,
                    onCancel = viewModel::cancelSave
                )
            }

            SignWizardStep.DONE -> {
                val outName = uiState.outputFile?.name ?: "Signed.pdf"
                val sizeKb = ((uiState.outputFile?.length() ?: 0L) / 1024L).coerceAtLeast(1)
                ToolDoneCard(
                    title = "Signed PDF ready",
                    summaryText = "$outName saved offline. Your original file was not modified.",
                    metrics = listOf(
                        "Signatures" to "${uiState.placements.count { it.isConfirmed }}",
                        "Size" to "$sizeKb KB"
                    ),
                    onOpen = onOpenLibrary,
                    onShare = viewModel::shareOutput,
                    onSaveAs = { saveAsLauncher.launch(outName) },
                    onSaveToCategory = { showCategorySheet = true }
                )
            }
        }
    }

    if (uiState.showConfirmSheet) {
        val target = uiState.placements.firstOrNull { it.id == uiState.selectedPlacementId }
            ?: uiState.placements.lastOrNull { it.pageIndex == uiState.currentPageIndex }
        if (target != null) {
            ConfirmSignaturePlacementSheet(
                pageBitmap = uiState.currentPageBitmap,
                placement = target,
                onConfirm = viewModel::confirmActivePlacement,
                onAdjust = viewModel::adjustFromConfirmSheet,
                onCancel = viewModel::cancelFromConfirmSheet
            )
        }
    }

    if (uiState.showReplaceConfirmSheet) {
        ConfirmActionBottomSheet(
            title = stringResource(R.string.sign_replace_original),
            message = stringResource(R.string.sign_replace_warning),
            confirmLabel = stringResource(R.string.sign_replace_original),
            isDestructive = true,
            onConfirm = viewModel::confirmReplaceOriginal,
            onDismiss = viewModel::dismissReplaceOriginal
        )
    }

    if (showCategorySheet) {
        MoveToCategoryBottomSheet(
            categories = categories,
            currentCategoryId = null,
            onSelectCategory = { catId -> viewModel.assignOutputToCategory(catId) },
            onDismiss = { showCategorySheet = false }
        )
    }

    if (uiState.passwordPromptFile != null) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissPasswordPrompt,
            title = { Text("Password-protected PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter password to unlock ${uiState.passwordPromptName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("PDF Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sign_pdf_password_input")
                    )
                    uiState.passwordError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.submitPdfPassword(passwordInput) }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPasswordPrompt) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
