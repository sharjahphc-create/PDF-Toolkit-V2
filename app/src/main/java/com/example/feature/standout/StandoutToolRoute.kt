package com.example.feature.standout

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.MoveToCategoryBottomSheet
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.ToolDoneCard
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolWorkflowScaffold
import com.example.feature.tools.ToolCatalog
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

@Composable
fun StandoutToolRoute(
    toolId: String,
    onExit: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenReader: (String) -> Unit,
    viewModel: StandoutToolsViewModel = hiltViewModel()
) {
    LaunchedEffect(toolId) {
        viewModel.initTool(toolId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val libraryPdfs = remember(libraryDocs) { libraryDocs.filter { it.mimeType == "application/pdf" } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }
    val selectedBatchLibraryIds = remember { mutableStateListOf<String>() }

    val toolDef = remember(toolId) { ToolCatalog.findById(toolId) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickPrimaryPdfUri(it) } }

    val openSecondPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickSecondPdfUri(it) } }

    val openBatchPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.pickBatchUris(uris) }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> viewModel.pickImages(uris) }

    val pickSingleImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.pickImages(listOf(it)) } }

    val pickBackIdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.pickBackIdUri(it) } }

    val docScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            val pageUris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
            viewModel.onDocumentScannerResult(pdfUri, pageUris)
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(state.outputMimeType)
    ) { uri -> uri?.let { viewModel.exportOutputToSaf(it) } }

    BackHandler(enabled = state.step != ToolStep.PICK) {
        viewModel.stepBackOrExit(onExit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is StandoutEvent.ShowMessage -> snackbarHostState.showSnackbar(ev.message)
                is StandoutEvent.ShareFile -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = ev.mimeType
                        putExtra(Intent.EXTRA_STREAM, ev.uri)
                        putExtra(Intent.EXTRA_SUBJECT, ev.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, ev.title))
                }
            }
        }
    }

    val launchPrimaryPicker: () -> Unit = {
        when (toolId) {
            "batch_tools" -> openBatchPdfsLauncher.launch(arrayOf("application/pdf"))
            "id_scanner", "qr_barcode" -> pickSingleImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            "autoclean_scan" -> pickImagesLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            "doc_scanner" -> {
                val activity = context as? Activity
                if (activity != null) {
                    val opts = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setResultFormats(
                            GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                        )
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(opts)
                        .getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            docScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                        .addOnFailureListener {
                            pickImagesLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                } else {
                    pickImagesLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            }
            else -> openPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    val primaryText = when (state.step) {
        ToolStep.PICK -> when (toolId) {
            "doc_scanner" -> "Start ML Kit Scanner"
            "batch_tools" -> "Select PDFs from Device"
            "id_scanner" -> "Pick Front ID Photo"
            "qr_barcode" -> "Pick QR / Barcode Photo"
            "autoclean_scan" -> "Pick Scan Photos"
            else -> "Browse PDF"
        }
        ToolStep.CONFIGURE -> when (toolId) {
            "redact" -> "Burn Permanent Redactions"
            "watermark_pages" -> "Apply Watermark & Numbers"
            "compare_pdfs" -> "Generate Comparison Report"
            "workflows" -> "Run Offline Recipe"
            "batch_tools" -> "${state.batchMode.label} ${state.batchFiles.size} PDF(s)"
            "convert_pdf" -> when (state.convertSubMode) {
                ConvertSubMode.PDF_TO_PNG_ZIP -> "Export PNG Zip"
                ConvertSubMode.PDF_TO_TEXT -> "Export Plain Text (.txt)"
                ConvertSubMode.TEXT_TO_PDF -> "Create PDF"
            }
            "id_scanner" -> "Create ID Card PDF"
            "qr_barcode" -> "Save Decoded Report PDF"
            "autoclean_scan", "doc_scanner" -> "Build Clean Scan PDF"
            "translate_pdf" -> "Translate (${state.sourceLang.displayName} → ${state.translateLang.displayName})"
            else -> "Run"
        }
        ToolStep.PROCESS -> null
        ToolStep.DONE -> "Done"
    }

    val primaryEnabled = when (state.step) {
        ToolStep.CONFIGURE -> when (toolId) {
            "compare_pdfs" -> state.primaryFile != null && state.secondFile != null
            "batch_tools" -> state.batchFiles.isNotEmpty() &&
                (state.batchMode != BatchOperationMode.LOCK_AES256 || state.batchPassword.length >= 4)
            "id_scanner", "autoclean_scan", "doc_scanner" -> state.imageUris.isNotEmpty()
            "translate_pdf" -> state.sourceLang != state.translateLang
            else -> true
        }
        else -> true
    }

    ToolWorkflowScaffold(
        title = toolDef?.title ?: "PDF Tool",
        subtitle = state.primaryName.ifBlank { toolDef?.subtitle },
        currentStep = state.step,
        onBack = { viewModel.stepBackOrExit(onExit) },
        primaryButtonText = primaryText,
        primaryButtonEnabled = primaryEnabled,
        onPrimaryClick = {
            when (state.step) {
                ToolStep.PICK -> launchPrimaryPicker()
                ToolStep.CONFIGURE -> viewModel.runToolProcess()
                ToolStep.PROCESS -> Unit
                ToolStep.DONE -> onExit()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (state.step) {
            ToolStep.PICK -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        EmptyStateView(
                            icon = toolDef?.icon ?: Icons.Default.DocumentScanner,
                            title = toolDef?.title ?: "Select file",
                            subtitle = toolDef?.subtitle ?: "100% offline processing.",
                            primaryActionLabel = primaryText ?: "Choose",
                            onPrimaryAction = launchPrimaryPicker,
                            secondaryActionLabel = when (toolId) {
                                "convert_pdf" -> "Text / Markdown → PDF"
                                "doc_scanner" -> "Clean Gallery Photo"
                                "batch_tools", "id_scanner", "qr_barcode", "autoclean_scan" -> null
                                else -> stringResource(R.string.import_sample)
                            },
                            onSecondaryAction = when (toolId) {
                                "convert_pdf" -> viewModel::startTextToPdfMode
                                "doc_scanner" -> {
                                    {
                                        pickImagesLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                }
                                else -> viewModel::pickBundledSample
                            }
                        )
                    }

                    if (toolId == "batch_tools" && libraryPdfs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Or multi-select from Library", style = MaterialTheme.typography.titleSmall)
                                if (selectedBatchLibraryIds.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            val chosen = libraryPdfs.filter { it.id in selectedBatchLibraryIds }
                                            viewModel.addLibraryDocsToBatch(chosen)
                                            selectedBatchLibraryIds.clear()
                                        },
                                        modifier = Modifier.testTag("batch_use_selected_library_button")
                                    ) {
                                        Text("Queue ${selectedBatchLibraryIds.size}")
                                    }
                                }
                            }
                        }
                        items(libraryPdfs, key = { it.id }) { doc ->
                            val checked = doc.id in selectedBatchLibraryIds
                            Card(
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable {
                                        if (checked) selectedBatchLibraryIds.remove(doc.id)
                                        else selectedBatchLibraryIds.add(doc.id)
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (checked) selectedBatchLibraryIds.remove(doc.id)
                                            else selectedBatchLibraryIds.add(doc.id)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else if (toolId in setOf("redact", "watermark_pages", "compare_pdfs", "workflows", "convert_pdf", "translate_pdf") &&
                        libraryPdfs.isNotEmpty()
                    ) {
                        item { Text("Or pick from your Library", style = MaterialTheme.typography.titleSmall) }
                        items(libraryPdfs, key = { it.id }) { doc ->
                            Card(
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { viewModel.pickPrimaryLibraryDoc(doc) }
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ToolStep.CONFIGURE -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        when (toolId) {
                            "redact" -> RedactConfigPanel(state = state, viewModel = viewModel)
                            "watermark_pages" -> WatermarkConfigPanel(state = state, viewModel = viewModel)
                            "compare_pdfs" -> CompareConfigPanel(
                                state = state,
                                libraryPdfs = libraryPdfs,
                                onPickSecondFromSaf = { openSecondPdfLauncher.launch(arrayOf("application/pdf")) },
                                onPickSecondFromLibrary = viewModel::pickSecondLibraryDoc
                            )
                            "workflows" -> WorkflowsConfigPanel(state = state, viewModel = viewModel)
                            "batch_tools" -> BatchConfigPanel(
                                state = state,
                                viewModel = viewModel,
                                onAddMoreFromSaf = { openBatchPdfsLauncher.launch(arrayOf("application/pdf")) }
                            )
                            "convert_pdf" -> ConvertConfigPanel(state = state, viewModel = viewModel)
                            "id_scanner" -> IdCardConfigPanel(
                                state = state,
                                viewModel = viewModel,
                                onPickBackPhoto = {
                                    pickBackIdLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            "qr_barcode" -> QrBarcodeConfigPanel(
                                state = state,
                                onCopiedMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                            "autoclean_scan", "doc_scanner" -> ScanCleanConfigPanel(state = state, viewModel = viewModel)
                            "translate_pdf" -> TranslateConfigPanel(state = state, viewModel = viewModel)
                        }
                    }
                }
            }

            ToolStep.PROCESS -> {
                ProcessingProgressView(
                    progress = state.progress,
                    onCancel = viewModel::cancelProcess
                )
            }

            ToolStep.DONE -> {
                val outName = state.outputFile?.name ?: "Output"
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ToolDoneCard(
                            title = "${toolDef?.title ?: "Task"} complete",
                            summaryText = "$outName generated offline on your device.",
                            metrics = state.summaryMetrics,
                            onOpen = {
                                val docId = state.savedLibraryDocId
                                if (docId != null) onOpenReader(docId) else onOpenLibrary()
                            },
                            onShare = viewModel::shareOutput,
                            onSaveAs = { saveAsLauncher.launch(outName) },
                            onSaveToCategory = { showCategorySheet = true }
                        )
                    }
                    if (toolId == "compare_pdfs" && state.compareDiff != null) {
                        item { CompareDiffSummaryCard(diff = state.compareDiff!!) }
                    }
                }
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
}
