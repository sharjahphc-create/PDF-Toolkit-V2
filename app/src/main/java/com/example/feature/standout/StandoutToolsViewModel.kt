package com.example.feature.standout

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.IoDispatcher
import com.example.core.common.OperationProgress
import com.example.core.ui.components.ToolStep
import com.example.data.repository.LibraryRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import com.example.feature.sign.PdfOpenResult
import com.example.feature.sign.PdfSignatureFlattener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class StandoutUiState(
    val toolId: String = "redact",
    val step: ToolStep = ToolStep.PICK,
    val primaryFile: File? = null,
    val primaryName: String = "",
    val primaryPageCount: Int = 1,
    val previewBitmap: Bitmap? = null,
    val currentPage: Int = 0,
    // Redact
    val piiMatches: List<PiiMatchSummary> = emptyList(),
    val redactionBoxes: List<RedactionBox> = emptyList(),
    val draftRedactX: Float = 0.08f,
    val draftRedactY: Float = 0.18f,
    val draftRedactW: Float = 0.65f,
    val draftRedactH: Float = 0.07f,
    val redactAllPages: Boolean = false,
    val scrubDetectedPii: Boolean = true,
    val stripMetadata: Boolean = true,
    // Watermark & Headers
    val watermarkText: String = "CONFIDENTIAL",
    val headerText: String = "",
    val footerText: String = "",
    val includePageNumbers: Boolean = true,
    val watermarkOpacity: Float = 0.22f,
    val watermarkStyle: WatermarkStyle = WatermarkStyle.DIAGONAL,
    val pageNumberFormat: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
    // Compare
    val secondFile: File? = null,
    val secondName: String = "",
    val compareDiff: PdfCompareDiff? = null,
    // Workflows
    val workflowConfig: WorkflowRecipeConfig = WorkflowRecipeConfig(),
    // Batch
    val batchFiles: List<Pair<File, String>> = emptyList(),
    val batchMode: BatchOperationMode = BatchOperationMode.COMPRESS,
    val batchCompressionPreset: BatchCompressionPreset = BatchCompressionPreset.BALANCED,
    val batchPassword: String = "",
    val batchSummary: BatchRunSummary? = null,
    // Convert
    val convertSubMode: ConvertSubMode = ConvertSubMode.PDF_TO_PNG_ZIP,
    val pngExportHighDpi: Boolean = false,
    val textToPdfTitle: String = "Offline Notes Export",
    val textToPdfBody: String = "# Summary\nType or paste text/markdown here to convert into an offline PDF.",
    // Photos / ID Card / AutoClean / Scan
    val imageUris: List<Uri> = emptyList(),
    val backIdUri: Uri? = null,
    val idGrayscaleClean: Boolean = false,
    val idVerificationWatermark: Boolean = true,
    val scanCleanMode: ScanCleanMode = ScanCleanMode.BLACK_AND_WHITE,
    // QR / Barcode
    val decodedBarcodes: List<DecodedBarcodeItem> = emptyList(),
    // Translate
    val sourceLang: TranslateTargetLang = TranslateTargetLang.ENGLISH,
    val translateLang: TranslateTargetLang = TranslateTargetLang.SPANISH,
    val bilingualTranslate: Boolean = false,
    // Progress & Output
    val progress: OperationProgress = OperationProgress(),
    val outputFile: File? = null,
    val outputMimeType: String = "application/pdf",
    val savedLibraryDocId: String? = null,
    val summaryMetrics: List<Pair<String, String>> = emptyList()
)

sealed interface StandoutEvent {
    data class ShowMessage(val message: String) : StandoutEvent
    data class ShareFile(val uri: Uri, val mimeType: String, val title: String) : StandoutEvent
}

@HiltViewModel
class StandoutToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: StandoutPdfEngine,
    private val pdfFlattener: PdfSignatureFlattener,
    private val libraryRepository: LibraryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandoutUiState())
    val uiState: StateFlow<StandoutUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StandoutEvent>()
    val events: SharedFlow<StandoutEvent> = _events.asSharedFlow()

    val categories: StateFlow<List<DocumentCategory>> = libraryRepository
        .observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryDocuments: StateFlow<List<LibraryDocument>> = libraryRepository
        .observeDocuments(includeLocked = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeJob: Job? = null

    fun initTool(toolId: String) {
        if (_uiState.value.toolId != toolId || _uiState.value.step == ToolStep.DONE) {
            _uiState.value = StandoutUiState(toolId = toolId)
        }
    }

    fun pickPrimaryPdfUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = pdfFlattener.preparePdfFromUri(uri)) {
                is PdfOpenResult.Ready -> onPrimaryPdfReady(res.workingCopy, res.displayName, res.pageCount)
                is PdfOpenResult.PasswordRequired -> _events.emit(StandoutEvent.ShowMessage("Unlock PDF password first"))
                is PdfOpenResult.Error -> _events.emit(StandoutEvent.ShowMessage(res.message))
            }
        }
    }

    fun pickPrimaryLibraryDoc(doc: LibraryDocument) {
        viewModelScope.launch {
            val file = File(doc.localCachePath)
            when (val res = pdfFlattener.preparePdfFromFile(file, doc.fileName)) {
                is PdfOpenResult.Ready -> onPrimaryPdfReady(res.workingCopy, res.displayName, res.pageCount)
                is PdfOpenResult.PasswordRequired -> _events.emit(StandoutEvent.ShowMessage("Unlock PDF password first"))
                is PdfOpenResult.Error -> _events.emit(StandoutEvent.ShowMessage(res.message))
            }
        }
    }

    fun pickBundledSample() {
        viewModelScope.launch {
            libraryRepository.importBundledSamplePdf().onSuccess { pickPrimaryLibraryDoc(it) }
        }
    }

    private suspend fun onPrimaryPdfReady(file: File, name: String, pageCount: Int) {
        val preview = pdfFlattener.renderPageBitmap(file, 0, 900)
        val pii = if (_uiState.value.toolId == "redact") engine.scanPii(file) else emptyList()
        _uiState.update {
            it.copy(
                step = ToolStep.CONFIGURE,
                primaryFile = file,
                primaryName = name,
                primaryPageCount = pageCount,
                previewBitmap = preview,
                currentPage = 0,
                piiMatches = pii,
                redactionBoxes = emptyList()
            )
        }
    }

    fun pickSecondPdfUri(uri: Uri) {
        viewModelScope.launch {
            when (val res = pdfFlattener.preparePdfFromUri(uri)) {
                is PdfOpenResult.Ready -> {
                    _uiState.update { it.copy(secondFile = res.workingCopy, secondName = res.displayName) }
                }
                else -> _events.emit(StandoutEvent.ShowMessage("Could not open second PDF"))
            }
        }
    }

    fun pickSecondLibraryDoc(doc: LibraryDocument) {
        viewModelScope.launch {
            val file = File(doc.localCachePath)
            when (val res = pdfFlattener.preparePdfFromFile(file, doc.fileName)) {
                is PdfOpenResult.Ready -> {
                    _uiState.update { it.copy(secondFile = res.workingCopy, secondName = res.displayName) }
                }
                else -> _events.emit(StandoutEvent.ShowMessage("Could not open selected Library PDF"))
            }
        }
    }

    fun pickBatchUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<Pair<File, String>>()
            for (u in uris) {
                when (val res = pdfFlattener.preparePdfFromUri(u)) {
                    is PdfOpenResult.Ready -> added.add(res.workingCopy to res.displayName)
                    else -> Unit
                }
            }
            if (added.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        step = ToolStep.CONFIGURE,
                        batchFiles = it.batchFiles + added
                    )
                }
            }
        }
    }

    fun addLibraryDocsToBatch(docs: List<LibraryDocument>) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<Pair<File, String>>()
            for (doc in docs) {
                val file = File(doc.localCachePath)
                if (!file.exists()) continue
                when (val res = pdfFlattener.preparePdfFromFile(file, doc.fileName)) {
                    is PdfOpenResult.Ready -> added.add(res.workingCopy to res.displayName)
                    else -> Unit
                }
            }
            if (added.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        step = ToolStep.CONFIGURE,
                        batchFiles = it.batchFiles + added
                    )
                }
            }
        }
    }

    fun removeBatchItem(index: Int) {
        _uiState.update { state ->
            val updated = state.batchFiles.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(batchFiles = updated)
        }
    }

    fun setBatchMode(mode: BatchOperationMode) {
        _uiState.update { it.copy(batchMode = mode) }
    }

    fun setBatchCompressionPreset(preset: BatchCompressionPreset) {
        _uiState.update { it.copy(batchCompressionPreset = preset) }
    }

    fun setBatchPassword(password: String) {
        _uiState.update { it.copy(batchPassword = password) }
    }

    fun pickImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val state = _uiState.value
        if (state.toolId == "qr_barcode") {
            viewModelScope.launch {
                val res = engine.scanBarcodes(uris.first())
                res.onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            step = ToolStep.CONFIGURE,
                            imageUris = listOf(uris.first()),
                            decodedBarcodes = items
                        )
                    }
                    if (items.isEmpty()) {
                        _events.emit(StandoutEvent.ShowMessage("No QR or barcode detected in that image"))
                    }
                }.onFailure { err ->
                    _events.emit(StandoutEvent.ShowMessage(err.localizedMessage ?: "Barcode scan failed"))
                }
            }
        } else {
            _uiState.update { it.copy(step = ToolStep.CONFIGURE, imageUris = uris) }
        }
    }

    fun onDocumentScannerResult(pdfUri: Uri?, pageUris: List<Uri>) {
        if (pdfUri != null) {
            pickPrimaryPdfUri(pdfUri)
            viewModelScope.launch {
                val doc = libraryRepository.importFromUri(pdfUri).getOrNull()
                if (doc != null) {
                    _uiState.update {
                        it.copy(
                            step = ToolStep.DONE,
                            outputFile = File(doc.localCachePath),
                            outputMimeType = "application/pdf",
                            savedLibraryDocId = doc.id,
                            summaryMetrics = listOf("Pages" to doc.pageCount.toString(), "Scanner" to "ML Kit")
                        )
                    }
                }
            }
        } else if (pageUris.isNotEmpty()) {
            pickImages(pageUris)
        }
    }

    fun pickBackIdUri(uri: Uri) {
        _uiState.update { it.copy(backIdUri = uri) }
    }

    fun updateIdCardOptions(grayscale: Boolean, verificationWatermark: Boolean) {
        _uiState.update {
            it.copy(idGrayscaleClean = grayscale, idVerificationWatermark = verificationWatermark)
        }
    }

    fun setScanCleanMode(mode: ScanCleanMode) {
        _uiState.update { it.copy(scanCleanMode = mode) }
    }

    fun startTextToPdfMode() {
        _uiState.update {
            it.copy(step = ToolStep.CONFIGURE, convertSubMode = ConvertSubMode.TEXT_TO_PDF)
        }
    }

    fun setConvertSubMode(mode: ConvertSubMode) {
        _uiState.update { it.copy(convertSubMode = mode) }
    }

    fun setPngExportHighDpi(highDpi: Boolean) {
        _uiState.update { it.copy(pngExportHighDpi = highDpi) }
    }

    fun updateTextToPdf(title: String, body: String) {
        _uiState.update { it.copy(textToPdfTitle = title, textToPdfBody = body) }
    }

    // Redaction multi-box controls
    fun updateDraftRedactBox(x: Float, y: Float, w: Float, h: Float) {
        _uiState.update {
            it.copy(
                draftRedactX = x.coerceIn(0f, 0.92f),
                draftRedactY = y.coerceIn(0f, 0.92f),
                draftRedactW = w.coerceIn(0.08f, 0.96f),
                draftRedactH = h.coerceIn(0.03f, 0.45f)
            )
        }
    }

    fun addCurrentRedactBox() {
        val s = _uiState.value
        val newBox = RedactionBox(
            pageIndex = s.currentPage,
            x = s.draftRedactX,
            y = s.draftRedactY,
            w = s.draftRedactW,
            h = s.draftRedactH
        )
        _uiState.update { it.copy(redactionBoxes = it.redactionBoxes + newBox) }
    }

    fun undoLastRedactBox() {
        _uiState.update { s ->
            if (s.redactionBoxes.isEmpty()) s
            else s.copy(redactionBoxes = s.redactionBoxes.dropLast(1))
        }
    }

    fun clearPageRedactBoxes() {
        _uiState.update { s ->
            s.copy(redactionBoxes = s.redactionBoxes.filterNot { it.pageIndex == s.currentPage })
        }
    }

    fun updateRedactToggles(allPages: Boolean, scrubPii: Boolean, stripMeta: Boolean) {
        _uiState.update {
            it.copy(redactAllPages = allPages, scrubDetectedPii = scrubPii, stripMetadata = stripMeta)
        }
    }

    fun updateWatermarkFields(
        watermark: String,
        header: String,
        footer: String,
        pageNums: Boolean,
        opacity: Float = _uiState.value.watermarkOpacity,
        style: WatermarkStyle = _uiState.value.watermarkStyle,
        format: PageNumberFormat = _uiState.value.pageNumberFormat
    ) {
        _uiState.update {
            it.copy(
                watermarkText = watermark,
                headerText = header,
                footerText = footer,
                includePageNumbers = pageNums,
                watermarkOpacity = opacity.coerceIn(0.08f, 0.65f),
                watermarkStyle = style,
                pageNumberFormat = format
            )
        }
    }

    fun updateWorkflowConfig(transform: (WorkflowRecipeConfig) -> WorkflowRecipeConfig) {
        _uiState.update { it.copy(workflowConfig = transform(it.workflowConfig)) }
    }

    fun applyWorkflowPreset(presetIndex: Int) {
        val recipe = when (presetIndex) {
            0 -> WorkflowRecipeConfig(
                compress = true,
                compressionPreset = BatchCompressionPreset.BALANCED,
                stripMetadata = true,
                watermarkText = "CONFIDENTIAL",
                includePageNumbers = true,
                password = _uiState.value.workflowConfig.password
            )
            1 -> WorkflowRecipeConfig(
                compress = true,
                compressionPreset = BatchCompressionPreset.LIGHT,
                stripMetadata = true,
                watermarkText = "",
                includePageNumbers = true,
                password = ""
            )
            else -> WorkflowRecipeConfig(
                compress = false,
                stripMetadata = true,
                watermarkText = "INTERNAL COPY",
                includePageNumbers = false,
                password = _uiState.value.workflowConfig.password
            )
        }
        _uiState.update { it.copy(workflowConfig = recipe) }
    }

    fun setTranslateLanguages(
        source: TranslateTargetLang = _uiState.value.sourceLang,
        target: TranslateTargetLang = _uiState.value.translateLang,
        bilingual: Boolean = _uiState.value.bilingualTranslate
    ) {
        _uiState.update {
            it.copy(sourceLang = source, translateLang = target, bilingualTranslate = bilingual)
        }
    }

    fun changePreviewPage(pageIndex: Int) {
        val state = _uiState.value
        val file = state.primaryFile ?: return
        val clamped = pageIndex.coerceIn(0, (state.primaryPageCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            val bmp = pdfFlattener.renderPageBitmap(file, clamped, 900)
            _uiState.update { it.copy(currentPage = clamped, previewBitmap = bmp) }
        }
    }

    fun runToolProcess() {
        val state = _uiState.value
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.PROCESS,
                    progress = OperationProgress(0.1f, "Processing on your device…")
                )
            }
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val result: Result<Triple<File, String, List<Pair<String, String>>>> = when (state.toolId) {
                "redact" -> {
                    val src = state.primaryFile ?: return@launch
                    val out = File(exportsDir, "${src.nameWithoutExtension}_redacted_${System.currentTimeMillis()}.pdf")
                    val allBoxes = state.redactionBoxes.ifEmpty {
                        listOf(
                            RedactionBox(
                                pageIndex = state.currentPage,
                                x = state.draftRedactX,
                                y = state.draftRedactY,
                                w = state.draftRedactW,
                                h = state.draftRedactH
                            )
                        )
                    }
                    engine.redactPdf(
                        sourceFile = src,
                        outputFile = out,
                        boxes = allBoxes,
                        redactAllPagesWithBoxes = state.redactAllPages,
                        scrubPiiBand = state.scrubDetectedPii,
                        stripMetadata = state.stripMetadata,
                        onProgress = { p -> updateProgress(p, "Burning flat redactions…") }
                    ).map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Boxes burned" to allBoxes.size.toString(),
                                "PII matches" to state.piiMatches.size.toString(),
                                "Metadata" to if (state.stripMetadata) "Stripped" else "Kept"
                            )
                        )
                    }
                }

                "watermark_pages" -> {
                    val src = state.primaryFile ?: return@launch
                    val out = File(exportsDir, "${src.nameWithoutExtension}_stamped_${System.currentTimeMillis()}.pdf")
                    engine.stampWatermarkAndNumbers(
                        sourceFile = src,
                        outputFile = out,
                        watermarkText = state.watermarkText,
                        headerText = state.headerText,
                        footerText = state.footerText,
                        includePageNumbers = state.includePageNumbers,
                        watermarkOpacity = state.watermarkOpacity,
                        watermarkStyle = state.watermarkStyle,
                        pageNumberFormat = state.pageNumberFormat,
                        onProgress = { p -> updateProgress(p, "Stamping pages…") }
                    ).map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Pages" to state.primaryPageCount.toString(),
                                "Style" to state.watermarkStyle.label
                            )
                        )
                    }
                }

                "compare_pdfs" -> {
                    val left = state.primaryFile ?: return@launch
                    val right = state.secondFile ?: return@launch
                    val out = File(exportsDir, "Compare_Report_${System.currentTimeMillis()}.pdf")
                    engine.comparePdfs(left, right, out).map { diff ->
                        _uiState.update { it.copy(compareDiff = diff) }
                        Triple(
                            out,
                            "application/pdf",
                            listOf(
                                "Changed pages" to diff.changedPageCount.toString(),
                                "Added lines" to diff.addedLines.size.toString(),
                                "Removed lines" to diff.removedLines.size.toString()
                            )
                        )
                    }
                }

                "workflows" -> {
                    val src = state.primaryFile ?: return@launch
                    val out = File(exportsDir, "${src.nameWithoutExtension}_workflow_${System.currentTimeMillis()}.pdf")
                    val cfg = state.workflowConfig
                    engine.runCustomWorkflowRecipe(
                        sourceFile = src,
                        outputFile = out,
                        config = cfg,
                        onProgress = { p, msg -> updateProgress(p, msg) }
                    ).map {
                        val activeSteps = buildList {
                            if (cfg.compress) add("Compress")
                            if (cfg.stripMetadata) add("Clean Meta")
                            if (cfg.watermarkText.isNotBlank()) add("Watermark")
                            if (cfg.includePageNumbers) add("Numbers")
                            if (cfg.password.isNotBlank()) add("AES-256")
                        }.joinToString(" → ").ifBlank { "Copy" }
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Pipeline" to activeSteps,
                                "Output size" to formatBytes(it.length())
                            )
                        )
                    }
                }

                "batch_tools" -> {
                    val out = File(exportsDir, "Batch_${state.batchMode.name.lowercase()}_${System.currentTimeMillis()}.zip")
                    engine.runBatchOperation(
                        sources = state.batchFiles,
                        outputZip = out,
                        mode = state.batchMode,
                        compressionPreset = state.batchCompressionPreset,
                        watermarkText = state.watermarkText,
                        includePageNumbers = state.includePageNumbers,
                        password = state.batchPassword,
                        onProgress = { p, msg -> updateProgress(p, msg) }
                    ).map { summary ->
                        _uiState.update { it.copy(batchSummary = summary) }
                        Triple(
                            out,
                            "application/zip",
                            listOf(
                                "Processed" to "${summary.succeededCount}/${summary.totalFiles}",
                                "Operation" to state.batchMode.label,
                                "Input → ZIP" to "${formatBytes(summary.inputTotalBytes)} → ${formatBytes(summary.outputTotalBytes)}"
                            )
                        )
                    }
                }

                "convert_pdf" -> {
                    when (state.convertSubMode) {
                        ConvertSubMode.PDF_TO_PNG_ZIP -> {
                            val src = state.primaryFile ?: return@launch
                            val out = File(exportsDir, "${src.nameWithoutExtension}_pngs.zip")
                            val widthPx = if (state.pngExportHighDpi) 1920 else 1280
                            engine.exportPdfPagesAsPngZip(src, out, targetWidthPx = widthPx) { p ->
                                updateProgress(p, "Rendering PNG pages…")
                            }.map { count ->
                                Triple(
                                    out,
                                    "application/zip",
                                    listOf("PNG pages" to count.toString(), "Quality" to if (state.pngExportHighDpi) "High (240 DPI)" else "Standard")
                                )
                            }
                        }
                        ConvertSubMode.PDF_TO_TEXT -> {
                            val src = state.primaryFile ?: return@launch
                            val out = File(exportsDir, "${src.nameWithoutExtension}_extracted.txt")
                            engine.extractPdfToTextFile(src, out) { p ->
                                updateProgress(p, "Extracting text…")
                            }.map { pages ->
                                Triple(out, "text/plain", listOf("Pages extracted" to pages.toString(), "Format" to "Plain Text (.txt)"))
                            }
                        }
                        ConvertSubMode.TEXT_TO_PDF -> {
                            val safeName = state.textToPdfTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Text_Export" }
                            val out = File(exportsDir, "$safeName.pdf")
                            engine.createTextPdf(state.textToPdfBody, out, state.textToPdfTitle).map {
                                Triple(it, "application/pdf", listOf("Format" to "Text/Markdown → PDF"))
                            }
                        }
                    }
                }

                "id_scanner" -> {
                    val front = state.imageUris.firstOrNull() ?: return@launch
                    val out = File(exportsDir, "ID_Card_${System.currentTimeMillis()}.pdf")
                    engine.buildIdCardPdf(
                        frontUri = front,
                        backUri = state.backIdUri,
                        outputFile = out,
                        grayscaleClean = state.idGrayscaleClean,
                        addVerificationWatermark = state.idVerificationWatermark
                    ).map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Sides" to if (state.backIdUri != null) "Front + Back" else "Front",
                                "Watermark" to if (state.idVerificationWatermark) "Verification stamp" else "None"
                            )
                        )
                    }
                }

                "qr_barcode" -> {
                    val out = File(exportsDir, "Scanned_Codes_${System.currentTimeMillis()}.pdf")
                    val body = state.decodedBarcodes.joinToString("\n\n") {
                        "[${it.displayType} • ${it.formatLabel}]\n${it.rawValue}"
                    }.ifBlank { "No barcode values found." }
                    engine.createTextPdf(body, out, "QR & Barcode Report").map {
                        Triple(it, "application/pdf", listOf("Codes decoded" to state.decodedBarcodes.size.toString()))
                    }
                }

                "autoclean_scan", "doc_scanner" -> {
                    val out = File(exportsDir, "Clean_Scan_${System.currentTimeMillis()}.pdf")
                    engine.autoCleanImagesToPdf(state.imageUris, out, cleanMode = state.scanCleanMode) { p ->
                        updateProgress(p, "Enhancing scan (${state.scanCleanMode.label})…")
                    }.map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf("Pages cleaned" to state.imageUris.size.toString(), "Filter" to state.scanCleanMode.label)
                        )
                    }
                }

                "translate_pdf" -> {
                    val src = state.primaryFile ?: return@launch
                    val out = File(exportsDir, "${src.nameWithoutExtension}_${state.translateLang.name.lowercase()}.pdf")
                    engine.translatePdfOffline(
                        sourceFile = src,
                        sourceLang = state.sourceLang,
                        targetLang = state.translateLang,
                        bilingualMode = state.bilingualTranslate,
                        outputFile = out
                    ) { p ->
                        updateProgress(p, "Translating offline (${state.translateLang.displayName})…")
                    }.map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Languages" to "${state.sourceLang.displayName} → ${state.translateLang.displayName}",
                                "Layout" to if (state.bilingualTranslate) "Bilingual" else "Translated only"
                            )
                        )
                    }
                }

                else -> Result.failure(IllegalStateException("Unsupported tool"))
            }

            result.onSuccess { (file, mime, metrics) ->
                val importedDoc = if (mime == "application/pdf") {
                    libraryRepository.importLocalFile(file, file.name, mime).getOrNull()
                } else null
                _uiState.update {
                    it.copy(
                        step = ToolStep.DONE,
                        outputFile = file,
                        outputMimeType = mime,
                        savedLibraryDocId = importedDoc?.id,
                        summaryMetrics = metrics
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
                _events.emit(StandoutEvent.ShowMessage(err.localizedMessage ?: "Operation failed"))
            }
        }
    }

    private fun updateProgress(fraction: Float, message: String) {
        _uiState.update {
            it.copy(progress = OperationProgress(fraction, message, isIndeterminate = false))
        }
    }

    fun cancelProcess() {
        activeJob?.cancel()
        _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
    }

    fun shareOutput() {
        val out = _uiState.value.outputFile ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        }.getOrNull() ?: return
        viewModelScope.launch {
            _events.emit(StandoutEvent.ShareFile(uri, _uiState.value.outputMimeType, out.name))
        }
    }

    fun exportOutputToSaf(destUri: Uri) {
        val out = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            val res = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openOutputStream(destUri)?.use { os ->
                        out.inputStream().use { it.copyTo(os) }
                    } ?: error("Unable to write to destination")
                }
            }
            res.onSuccess { _events.emit(StandoutEvent.ShowMessage("Saved to device via SAF")) }
                .onFailure { _events.emit(StandoutEvent.ShowMessage(it.localizedMessage ?: "Export failed")) }
        }
    }

    fun assignOutputToCategory(categoryId: String?) {
        val docId = _uiState.value.savedLibraryDocId ?: return
        viewModelScope.launch {
            libraryRepository.moveToCategory(docId, categoryId)
            _events.emit(StandoutEvent.ShowMessage("Saved to category"))
        }
    }

    fun stepBackOrExit(onExit: () -> Unit) {
        when (_uiState.value.step) {
            ToolStep.PICK -> onExit()
            ToolStep.CONFIGURE -> _uiState.update { StandoutUiState(toolId = it.toolId) }
            ToolStep.PROCESS -> cancelProcess()
            ToolStep.DONE -> _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}
