package com.example.feature.standout

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@OptIn(ExperimentalLayoutApi::class)
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }

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
            "batch_tools" -> "Select PDFs"
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
            "batch_tools" -> "Process ${state.batchFiles.size} PDF(s)"
            "convert_pdf" -> if (state.convertSubMode == ConvertSubMode.PDF_TO_PNG_ZIP) "Export PNG Zip" else "Create PDF"
            "id_scanner" -> "Create ID Card PDF"
            "qr_barcode" -> "Save Decoded Report PDF"
            "autoclean_scan", "doc_scanner" -> "Build Clean Scan PDF"
            "translate_pdf" -> "Translate Offline (${state.translateLang.displayName})"
            else -> "Run"
        }
        ToolStep.PROCESS -> null
        ToolStep.DONE -> "Done"
    }

    val primaryEnabled = when (state.step) {
        ToolStep.CONFIGURE -> when (toolId) {
            "compare_pdfs" -> state.primaryFile != null && state.secondFile != null
            "batch_tools" -> state.batchFiles.isNotEmpty()
            "id_scanner", "autoclean_scan", "doc_scanner" -> state.imageUris.isNotEmpty()
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

                    if (toolId in setOf("redact", "watermark_pages", "compare_pdfs", "workflows", "convert_pdf", "translate_pdf") &&
                        libraryDocs.isNotEmpty()
                    ) {
                        item { Text("Or pick from your Library", style = MaterialTheme.typography.titleSmall) }
                        items(libraryDocs.filter { it.mimeType == "application/pdf" }, key = { it.id }) { doc ->
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
                    when (toolId) {
                        "redact" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Page ${state.currentPage + 1} of ${state.primaryPageCount}", style = MaterialTheme.typography.titleSmall)
                                            Row {
                                                IconButton(
                                                    onClick = { viewModel.changePreviewPage(state.currentPage - 1) },
                                                    enabled = state.currentPage > 0
                                                ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page") }
                                                IconButton(
                                                    onClick = { viewModel.changePreviewPage(state.currentPage + 1) },
                                                    enabled = state.currentPage < state.primaryPageCount - 1
                                                ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next page") }
                                            }
                                        }
                                        state.previewBitmap?.let { bmp ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
                                            ) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "Redaction preview",
                                                    contentScale = ContentScale.FillBounds,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    drawRect(
                                                        color = Color.Black.copy(alpha = 0.85f),
                                                        topLeft = Offset(state.redactX * size.width, state.redactY * size.height),
                                                        size = Size(state.redactW * size.width, state.redactH * size.height)
                                                    )
                                                }
                                            }
                                        }
                                        Text("Vertical Position", style = MaterialTheme.typography.labelMedium)
                                        Slider(
                                            value = state.redactY,
                                            onValueChange = { viewModel.updateRedactBox(state.redactX, it, state.redactW, state.redactH, state.redactAllPages, state.scrubDetectedPii) },
                                            valueRange = 0.02f..0.88f
                                        )
                                        Text("Box Width", style = MaterialTheme.typography.labelMedium)
                                        Slider(
                                            value = state.redactW,
                                            onValueChange = { viewModel.updateRedactBox(state.redactX, state.redactY, it, state.redactH, state.redactAllPages, state.scrubDetectedPii) },
                                            valueRange = 0.15f..0.90f
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Auto-scrub detected PII (${state.piiMatches.size} matches)")
                                            Switch(
                                                checked = state.scrubDetectedPii,
                                                onCheckedChange = { viewModel.updateRedactBox(state.redactX, state.redactY, state.redactW, state.redactH, state.redactAllPages, it) }
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Apply box to all pages")
                                            Switch(
                                                checked = state.redactAllPages,
                                                onCheckedChange = { viewModel.updateRedactBox(state.redactX, state.redactY, state.redactW, state.redactH, it, state.scrubDetectedPii) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "watermark_pages" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = state.watermarkText,
                                            onValueChange = { viewModel.updateWatermarkFields(it, state.headerText, state.footerText, state.includePageNumbers) },
                                            label = { Text("Diagonal Watermark") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.headerText,
                                            onValueChange = { viewModel.updateWatermarkFields(state.watermarkText, it, state.footerText, state.includePageNumbers) },
                                            label = { Text("Header Text") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.footerText,
                                            onValueChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, it, state.includePageNumbers) },
                                            label = { Text("Footer Text") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Stamp Page X of Y")
                                            Switch(
                                                checked = state.includePageNumbers,
                                                onCheckedChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, state.footerText, it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "compare_pdfs" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Document A: ${state.primaryName} (${state.primaryPageCount} pages)", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (state.secondFile != null) "Document B: ${state.secondName}" else "Document B: Not selected",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        OutlinedButton(onClick = { openSecondPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                            Text(if (state.secondFile == null) "Pick Second PDF" else "Change Second PDF")
                                        }
                                    }
                                }
                            }
                        }

                        "workflows" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Offline Recipe: Watermark → Page Numbers → AES-256 Lock", style = MaterialTheme.typography.titleSmall)
                                        OutlinedTextField(
                                            value = state.watermarkText,
                                            onValueChange = { viewModel.updateWorkflowFields(it, state.workflowPassword) },
                                            label = { Text("Watermark text") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = state.workflowPassword,
                                            onValueChange = { viewModel.updateWorkflowFields(state.watermarkText, it) },
                                            label = { Text("Optional AES-256 password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        "batch_tools" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Batch Queue (${state.batchFiles.size} PDFs)", style = MaterialTheme.typography.titleMedium)
                                        state.batchFiles.forEach { (_, name) ->
                                            Text("• $name", style = MaterialTheme.typography.bodySmall)
                                        }
                                        OutlinedTextField(
                                            value = state.watermarkText,
                                            onValueChange = { viewModel.updateWatermarkFields(it, state.headerText, state.footerText, state.includePageNumbers) },
                                            label = { Text("Optional Watermark") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Add Page Numbers")
                                            Switch(
                                                checked = state.includePageNumbers,
                                                onCheckedChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, state.footerText, it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "convert_pdf" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            SegmentedButton(
                                                selected = state.convertSubMode == ConvertSubMode.PDF_TO_PNG_ZIP,
                                                onClick = { viewModel.setConvertSubMode(ConvertSubMode.PDF_TO_PNG_ZIP) },
                                                enabled = state.primaryFile != null,
                                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                                            ) { Text("PDF → PNG Zip") }
                                            SegmentedButton(
                                                selected = state.convertSubMode == ConvertSubMode.TEXT_TO_PDF,
                                                onClick = { viewModel.setConvertSubMode(ConvertSubMode.TEXT_TO_PDF) },
                                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                                            ) { Text("Text → PDF") }
                                        }
                                        if (state.convertSubMode == ConvertSubMode.TEXT_TO_PDF) {
                                            OutlinedTextField(
                                                value = state.textToPdfTitle,
                                                onValueChange = { viewModel.updateTextToPdf(it, state.textToPdfBody) },
                                                label = { Text("PDF Title") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = state.textToPdfBody,
                                                onValueChange = { viewModel.updateTextToPdf(state.textToPdfTitle, it) },
                                                label = { Text("Markdown / Plain Text Content") },
                                                minLines = 6,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text("Will export all ${state.primaryPageCount} PDF page(s) as PNG images inside a .zip archive.")
                                        }
                                    }
                                }
                            }
                        }

                        "id_scanner" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Front side selected: ${state.imageUris.size} image", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (state.backIdUri != null) "Back side selected" else "Back side optional",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(onClick = {
                                            pickBackIdLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }) {
                                            Text(if (state.backIdUri == null) "Pick Back Side Photo" else "Replace Back Photo")
                                        }
                                    }
                                }
                            }
                        }

                        "qr_barcode" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Decoded Barcodes (${state.decodedBarcodes.size})", style = MaterialTheme.typography.titleMedium)
                                        if (state.decodedBarcodes.isEmpty()) {
                                            Text("No QR or barcode found in selected photo.")
                                        } else {
                                            state.decodedBarcodes.forEach { bc ->
                                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text(bc.displayType, style = MaterialTheme.typography.labelLarge)
                                                        Text(bc.rawValue, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "autoclean_scan", "doc_scanner" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${state.imageUris.size} scan page(s) ready", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Auto-Clean applies grayscale contrast boost and shadow whitening locally before packaging into A4 PDF.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        "translate_pdf" -> {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Select On-Device Target Language", style = MaterialTheme.typography.titleMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TranslateTargetLang.entries.forEach { lang ->
                                                FilterChip(
                                                    selected = state.translateLang == lang,
                                                    onClick = { viewModel.setTranslateTarget(lang) },
                                                    label = { Text(lang.displayName) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
