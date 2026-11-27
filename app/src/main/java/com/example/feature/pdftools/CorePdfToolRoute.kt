package com.example.feature.pdftools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.feature.pdftools.ui.CompressConfigurePanel
import com.example.feature.pdftools.ui.CopyTextOcrConfigurePanel
import com.example.feature.pdftools.ui.DigitalSignatureConfigurePanel
import com.example.feature.pdftools.ui.FillFormConfigurePanel
import com.example.feature.pdftools.ui.MergeConfigurePanel
import com.example.feature.pdftools.ui.OrganizePagesConfigurePanel
import com.example.feature.pdftools.ui.PasswordConfigurePanel
import com.example.feature.pdftools.ui.PhotosToPdfConfigurePanel
import com.example.feature.pdftools.ui.SplitConfigurePanel
import com.example.feature.tools.ToolCatalog

@Composable
fun CorePdfToolRoute(
    toolId: String,
    onExit: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: CorePdfToolsViewModel = hiltViewModel()
) {
    LaunchedEffect(toolId) {
        viewModel.initTool(toolId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }

    val toolDef = remember(toolId) { ToolCatalog.findById(toolId) }

    val openSinglePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.pickPdfFromUri(it) }
    }

    val openMultiplePdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.pickMultiplePdfsForMerge(uris)
    }

    val openPkcs12Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.pickPkcs12FromUri(it) }
    }

    val pickMultiplePhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        viewModel.addGalleryPhotos(uris)
    }

    val pickSinglePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addGalleryPhotos(listOf(it)) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onCameraPhotoCaptured(success)
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        destUri?.let { viewModel.exportOutputToSaf(it) }
    }

    BackHandler(enabled = state.step != ToolStep.PICK) {
        viewModel.stepBackOrExit(onExit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CorePdfToolEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is CorePdfToolEvent.SharePdf -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
                is CorePdfToolEvent.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
                is CorePdfToolEvent.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", event.text))
                }
            }
        }
    }

    val primaryButtonText = when (state.step) {
        ToolStep.PICK -> when (toolId) {
            "photos_to_pdf" -> "Pick Photos"
            "copy_text" -> "Pick Photo for OCR"
            "merge" -> "Select PDFs to Merge"
            else -> "Browse PDFs via SAF"
        }
        ToolStep.CONFIGURE -> when (toolId) {
            "merge" -> "Merge ${state.mergeSources.size} PDFs"
            "split" -> "Extract ${state.selectedSplitPages.size} Page(s)"
            "compress" -> "Compress (${state.compressionPreset.label})"
            "organize_pages" -> "Save Organized PDF"
            "fill_form" -> "Save Filled PDF"
            "digital_signature" -> if (state.digitalSigMode == DigitalSigMode.SIGN) {
                "Sign PDF Cryptographically"
            } else null
            "password" -> if (state.passwordMode == PasswordToolMode.LOCK_AES256) {
                "Encrypt PDF (AES-256)"
            } else "Remove Password"
            "photos_to_pdf" -> "Build PDF (${state.photoPages.size} Photos)"
            "copy_text" -> "Export Searchable PDF"
            else -> "Process"
        }
        ToolStep.PROCESS -> null
        ToolStep.DONE -> "Done"
    }

    val primaryEnabled = when (state.step) {
        ToolStep.PICK -> true
        ToolStep.CONFIGURE -> when (toolId) {
            "merge" -> state.mergeSources.size >= 2
            "split" -> state.selectedSplitPages.isNotEmpty()
            "organize_pages" -> state.organizeSpecs.any { !it.isDeleted }
            "digital_signature" -> state.digitalSigMode == DigitalSigMode.SIGN &&
                state.pkcs12File != null && state.pkcs12Password.isNotBlank()
            "password" -> if (state.passwordMode == PasswordToolMode.LOCK_AES256) {
                state.lockUserPassword.isNotEmpty() || state.lockOwnerPassword.isNotEmpty()
            } else state.unlockPasswordInput.isNotEmpty()
            "photos_to_pdf" -> state.photoPages.isNotEmpty()
            "copy_text" -> !state.ocrRunning && state.ocrResult != null
            else -> true
        }
        ToolStep.PROCESS, ToolStep.DONE -> true
    }

    ToolWorkflowScaffold(
        title = toolDef?.title ?: "PDF Tool",
        subtitle = state.sourcePdf?.displayName ?: toolDef?.subtitle,
        currentStep = state.step,
        onBack = { viewModel.stepBackOrExit(onExit) },
        primaryButtonText = primaryButtonText,
        primaryButtonEnabled = primaryEnabled,
        onPrimaryClick = {
            when (state.step) {
                ToolStep.PICK -> when (toolId) {
                    "photos_to_pdf" -> pickMultiplePhotosLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    "copy_text" -> pickSinglePhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    "merge" -> openMultiplePdfsLauncher.launch(arrayOf("application/pdf"))
                    else -> openSinglePdfLauncher.launch(arrayOf("application/pdf"))
                }
                ToolStep.CONFIGURE -> viewModel.runToolProcess()
                ToolStep.PROCESS -> Unit
                ToolStep.DONE -> onExit()
            }
        },
        secondaryActionText = if (state.step == ToolStep.DONE && state.sourcePdf?.sourceUri != null) {
            stringResource(R.string.sign_replace_original)
        } else null,
        onSecondaryClick = if (state.step == ToolStep.DONE && state.sourcePdf?.sourceUri != null) {
            { viewModel.requestReplaceOriginal() }
        } else null,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (state.step) {
            ToolStep.PICK -> {
                CoreToolPickStep(
                    toolId = toolId,
                    state = state,
                    libraryDocs = libraryDocs.filter { it.mimeType == "application/pdf" },
                    onBrowsePdf = {
                        if (toolId == "merge") {
                            openMultiplePdfsLauncher.launch(arrayOf("application/pdf"))
                        } else {
                            openSinglePdfLauncher.launch(arrayOf("application/pdf"))
                        }
                    },
                    onPickLibraryDoc = { doc ->
                        if (toolId == "merge") viewModel.addLibraryDocToMerge(doc)
                        else viewModel.pickPdfFromLibrary(doc)
                    },
                    onPickSample = viewModel::pickBundledSamplePdf,
                    onPickPhotos = {
                        if (toolId == "copy_text") {
                            pickSinglePhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            pickMultiplePhotosLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onCaptureCamera = {
                        runCatching {
                            val uri = viewModel.prepareCameraPhotoUri()
                            takePictureLauncher.launch(uri)
                        }
                    },
                    onPasswordModeChange = viewModel::setPasswordMode,
                    onDigitalSigModeChange = viewModel::setDigitalSigMode
                )
            }

            ToolStep.CONFIGURE -> {
                when (toolId) {
                    "merge" -> MergeConfigurePanel(
                        state = state,
                        onAddMorePdfs = { openMultiplePdfsLauncher.launch(arrayOf("application/pdf")) },
                        onMoveSource = viewModel::moveMergeSource,
                        onRemoveSource = viewModel::removeMergeSource
                    )
                    "split" -> SplitConfigurePanel(
                        state = state,
                        onRangeChange = viewModel::updateSplitRangeInput,
                        onTogglePage = viewModel::toggleSplitPage,
                        onRequestThumbnail = viewModel::requestThumbnail
                    )
                    "compress" -> CompressConfigurePanel(
                        state = state,
                        onSelectPreset = viewModel::selectCompressionPreset
                    )
                    "organize_pages" -> OrganizePagesConfigurePanel(
                        state = state,
                        onRotate = { idx -> viewModel.rotateOrganizePage(idx) },
                        onToggleDelete = viewModel::toggleDeleteOrganizePage,
                        onMove = viewModel::moveOrganizePage,
                        onCropChange = viewModel::setOrganizeCropInset,
                        onRequestThumbnail = viewModel::requestThumbnail
                    )
                    "fill_form" -> FillFormConfigurePanel(
                        state = state,
                        onUpdateAcroText = viewModel::updateAcroFieldText,
                        onUpdateAcroCheckbox = viewModel::updateAcroCheckbox,
                        onFlattenToggle = viewModel::setFlattenForm,
                        onAddFreeText = viewModel::addFreeTextOverlay,
                        onRemoveFreeText = viewModel::removeFreeTextOverlay,
                        onChangePage = viewModel::changePreviewPage
                    )
                    "digital_signature" -> DigitalSignatureConfigurePanel(
                        state = state,
                        onModeChange = viewModel::setDigitalSigMode,
                        onPickPkcs12 = {
                            openPkcs12Launcher.launch(
                                arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")
                            )
                        },
                        onGeneratePkcs12 = viewModel::generateLocalPkcs12,
                        onUpdateInputs = viewModel::updateDigitalSigInputs,
                        onRunVerify = viewModel::verifyDigitalSignatures
                    )
                    "password" -> PasswordConfigurePanel(
                        state = state,
                        onModeChange = viewModel::setPasswordMode,
                        onFieldsChange = viewModel::updatePasswordFields
                    )
                    "photos_to_pdf" -> PhotosToPdfConfigurePanel(
                        state = state,
                        onAddGalleryPhotos = {
                            pickMultiplePhotosLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCaptureCameraPhoto = {
                            runCatching {
                                val uri = viewModel.prepareCameraPhotoUri()
                                takePictureLauncher.launch(uri)
                            }
                        },
                        onPageSizeSelected = viewModel::setPdfPageSize,
                        onToggleAutoCrop = viewModel::togglePhotoAutoCrop,
                        onSetManualInset = viewModel::setPhotoManualInset,
                        onMovePhoto = viewModel::movePhoto,
                        onRemovePhoto = viewModel::removePhoto
                    )
                    "copy_text" -> CopyTextOcrConfigurePanel(
                        state = state,
                        onEditableTextChange = viewModel::updateOcrEditableText,
                        onCopyText = viewModel::copyOcrText,
                        onShareText = viewModel::shareOcrText,
                        onChangePdfPage = viewModel::changePreviewPage
                    )
                }
            }

            ToolStep.PROCESS -> {
                ProcessingProgressView(
                    progress = state.progress,
                    onCancel = viewModel::cancelProcess
                )
            }

            ToolStep.DONE -> {
                val outName = state.outputFile?.name ?: "Output.pdf"
                ToolDoneCard(
                    title = "${toolDef?.title ?: "PDF"} complete",
                    summaryText = "$outName created offline. Original files remain untouched.",
                    metrics = state.summaryMetrics,
                    onOpen = onOpenLibrary,
                    onShare = viewModel::shareOutputPdf,
                    onSaveAs = { saveAsLauncher.launch(outName) },
                    onSaveToCategory = { showCategorySheet = true }
                )
            }
        }
    }

    if (showCategorySheet) {
        MoveToCategoryBottomSheet(
            categories = categories,
            currentCategoryId = null,
            onSelectCategory = { catId -> viewModel.assignOutputToCategory(catId) },
            onDismiss = { showCategorySheet = false }
        )
    }

    if (state.showReplaceOriginalSheet) {
        ConfirmActionBottomSheet(
            title = stringResource(R.string.sign_replace_original),
            message = stringResource(R.string.sign_replace_warning),
            confirmLabel = stringResource(R.string.sign_replace_original),
            isDestructive = true,
            onConfirm = viewModel::confirmReplaceOriginal,
            onDismiss = viewModel::dismissReplaceOriginal
        )
    }

    if (state.passwordPromptFile != null) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissPasswordPrompt,
            title = { Text("Password-protected PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter password to open ${state.passwordPromptName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("PDF Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.passwordPromptError?.let { err ->
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

@Composable
private fun CoreToolPickStep(
    toolId: String,
    state: CorePdfToolUiState,
    libraryDocs: List<com.example.domain.model.LibraryDocument>,
    onBrowsePdf: () -> Unit,
    onPickLibraryDoc: (com.example.domain.model.LibraryDocument) -> Unit,
    onPickSample: () -> Unit,
    onPickPhotos: () -> Unit,
    onCaptureCamera: () -> Unit,
    onPasswordModeChange: (PasswordToolMode) -> Unit,
    onDigitalSigModeChange: (DigitalSigMode) -> Unit
) {
    val toolDef = ToolCatalog.findById(toolId)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (toolId == "digital_signature") {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.digital_sig_distinct_notice),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.digitalSigMode == DigitalSigMode.SIGN,
                        onClick = { onDigitalSigModeChange(DigitalSigMode.SIGN) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Default.Verified, contentDescription = null) }
                    ) { Text("Sign PDF") }
                    SegmentedButton(
                        selected = state.digitalSigMode == DigitalSigMode.VERIFY,
                        onClick = { onDigitalSigModeChange(DigitalSigMode.VERIFY) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Verify PDF") }
                }
            }
        }

        if (toolId == "password") {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.passwordMode == PasswordToolMode.LOCK_AES256,
                        onClick = { onPasswordModeChange(PasswordToolMode.LOCK_AES256) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    ) { Text("Lock (AES-256)") }
                    SegmentedButton(
                        selected = state.passwordMode == PasswordToolMode.UNLOCK,
                        onClick = { onPasswordModeChange(PasswordToolMode.UNLOCK) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                    ) { Text("Unlock PDF") }
                }
            }
        }

        if (toolId == "photos_to_pdf" || toolId == "copy_text") {
            item {
                EmptyStateView(
                    icon = if (toolId == "photos_to_pdf") Icons.Default.PhotoLibrary else Icons.Default.CameraAlt,
                    title = if (toolId == "photos_to_pdf") "Turn photos into a clean PDF" else "Extract text offline with ML Kit OCR",
                    subtitle = if (toolId == "photos_to_pdf") {
                        "Pick photos from your gallery or shoot documents with the camera."
                    } else {
                        "Pick a photo, snap a page, or choose a PDF below to copy text and export a searchable PDF."
                    },
                    primaryActionLabel = if (toolId == "photos_to_pdf") "Pick Photos" else "Pick Photo",
                    onPrimaryAction = onPickPhotos,
                    secondaryActionLabel = "Use Camera",
                    onSecondaryAction = onCaptureCamera
                )
            }
            if (toolId == "copy_text") {
                item {
                    OutlinedButton(
                        onClick = onBrowsePdf,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Or OCR a PDF file")
                    }
                }
            }
        } else {
            item {
                EmptyStateView(
                    icon = toolDef?.icon ?: Icons.Default.PictureAsPdf,
                    title = toolDef?.title ?: "Select PDF",
                    subtitle = toolDef?.subtitle ?: "Everything runs 100% offline on your device.",
                    primaryActionLabel = if (toolId == "merge") "Pick Multiple PDFs" else "Browse PDFs",
                    onPrimaryAction = onBrowsePdf,
                    secondaryActionLabel = stringResource(R.string.import_sample),
                    onSecondaryAction = onPickSample
                )
            }
        }

        if (toolId != "photos_to_pdf" && libraryDocs.isNotEmpty()) {
            item {
                Text(
                    "Or pick from your Library",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(libraryDocs, key = { it.id }) { doc ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onPickLibraryDoc(doc) }
                        .testTag("pick_library_doc_${doc.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
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
