package com.example.feature.standout

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

enum class ConvertSubMode { PDF_TO_PNG_ZIP, TEXT_TO_PDF }

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
    val redactX: Float = 0.1f,
    val redactY: Float = 0.18f,
    val redactW: Float = 0.65f,
    val redactH: Float = 0.08f,
    val redactAllPages: Boolean = false,
    val scrubDetectedPii: Boolean = true,
    // Watermark & Headers
    val watermarkText: String = "CONFIDENTIAL",
    val headerText: String = "",
    val footerText: String = "",
    val includePageNumbers: Boolean = true,
    // Compare
    val secondFile: File? = null,
    val secondName: String = "",
    val compareDiff: PdfCompareDiff? = null,
    // Workflows
    val workflowPassword: String = "",
    // Batch
    val batchFiles: List<Pair<File, String>> = emptyList(),
    // Convert
    val convertSubMode: ConvertSubMode = ConvertSubMode.PDF_TO_PNG_ZIP,
    val textToPdfTitle: String = "Offline Notes Export",
    val textToPdfBody: String = "# Summary\nType or paste text/markdown here to convert into an offline PDF.",
    // Photos / ID Card / AutoClean / Scan
    val imageUris: List<Uri> = emptyList(),
    val backIdUri: Uri? = null,
    // QR / Barcode
    val decodedBarcodes: List<DecodedBarcodeItem> = emptyList(),
    // Translate
    val translateLang: TranslateTargetLang = TranslateTargetLang.SPANISH,
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
                piiMatches = pii
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

    fun pickBatchUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = mutableListOf<Pair<File, String>>()
            for (u in uris) {
                when (val res = pdfFlattener.preparePdfFromUri(u)) {
                    is PdfOpenResult.Ready -> items.add(res.workingCopy to res.displayName)
                    else -> Unit
                }
            }
            if (items.isNotEmpty()) {
                _uiState.update { it.copy(step = ToolStep.CONFIGURE, batchFiles = items) }
            }
        }
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

    fun startTextToPdfMode() {
        _uiState.update {
            it.copy(step = ToolStep.CONFIGURE, convertSubMode = ConvertSubMode.TEXT_TO_PDF)
        }
    }

    fun setConvertSubMode(mode: ConvertSubMode) {
        _uiState.update { it.copy(convertSubMode = mode) }
    }

    fun updateTextToPdf(title: String, body: String) {
        _uiState.update { it.copy(textToPdfTitle = title, textToPdfBody = body) }
    }

    fun updateRedactBox(x: Float, y: Float, w: Float, h: Float, allPages: Boolean, scrubPii: Boolean) {
        _uiState.update {
            it.copy(
                redactX = x,
                redactY = y,
                redactW = w,
                redactH = h,
                redactAllPages = allPages,
                scrubDetectedPii = scrubPii
            )
        }
    }

    fun updateWatermarkFields(watermark: String, header: String, footer: String, pageNums: Boolean) {
        _uiState.update {
            it.copy(
                watermarkText = watermark,
                headerText = header,
                footerText = footer,
                includePageNumbers = pageNums
            )
        }
    }

    fun updateWorkflowFields(watermark: String, password: String) {
        _uiState.update { it.copy(watermarkText = watermark, workflowPassword = password) }
    }

    fun setTranslateTarget(lang: TranslateTargetLang) {
        _uiState.update { it.copy(translateLang = lang) }
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
                    engine.redactPdf(
                        sourceFile = src,
                        outputFile = out,
                        boxNormalizedX = state.redactX,
                        boxNormalizedY = state.redactY,
                        boxNormalizedW = state.redactW,
                        boxNormalizedH = state.redactH,
                        targetPageIndex = state.currentPage,
                        redactAllPagesWithBox = state.redactAllPages,
                        scrubPiiBand = state.scrubDetectedPii,
                        onProgress = { p -> updateProgress(p, "Burning flat redactions…") }
                    ).map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf("PII findings" to state.piiMatches.size.toString(), "Mode" to "Rasterized permanent")
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
                        onProgress = { p -> updateProgress(p, "Stamping pages…") }
                    ).map {
                        Triple(it, "application/pdf", listOf("Pages" to state.primaryPageCount.toString()))
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
                    engine.runSecureWorkflowRecipe(
                        sourceFile = src,
                        outputFile = out,
                        watermarkText = state.watermarkText,
                        password = state.workflowPassword,
                        onProgress = { p -> updateProgress(p, "Running workflow steps…") }
                    ).map {
                        Triple(
                            it,
                            "application/pdf",
                            listOf(
                                "Steps" to "Stamp → Numbers → AES-256",
                                "Encrypted" to if (state.workflowPassword.isNotBlank()) "Yes" else "No"
                            )
                        )
                    }
                }

                "batch_tools" -> {
                    val out = File(exportsDir, "Batch_PDFs_${System.currentTimeMillis()}.zip")
                    engine.batchStampAndZip(
                        sources = state.batchFiles,
                        outputZip = out,
                        includePageNumbers = state.includePageNumbers,
                        watermarkText = state.watermarkText,
                        onProgress = { p -> updateProgress(p, "Processing batch…") }
                    ).map {
                        Triple(it, "application/zip", listOf("Files processed" to state.batchFiles.size.toString()))
                    }
                }

                "convert_pdf" -> {
                    if (state.convertSubMode == ConvertSubMode.PDF_TO_PNG_ZIP && state.primaryFile != null) {
                        val out = File(exportsDir, "${state.primaryFile.nameWithoutExtension}_pngs.zip")
                        engine.exportPdfPagesAsPngZip(state.primaryFile, out) { p ->
                            updateProgress(p, "Rendering PNG pages…")
                        }.map { count ->
                            Triple(out, "application/zip", listOf("PNG pages" to count.toString()))
                        }
                    } else {
                        val out = File(exportsDir, "${state.textToPdfTitle.replace(' ', '_')}.pdf")
                        engine.createTextPdf(state.textToPdfBody, out, state.textToPdfTitle).map {
                            Triple(it, "application/pdf", listOf("Format" to "Text/Markdown → PDF"))
                        }
                    }
                }

                "id_scanner" -> {
                    val front = state.imageUris.firstOrNull() ?: return@launch
                    val out = File(exportsDir, "ID_Card_${System.currentTimeMillis()}.pdf")
                    engine.buildIdCardPdf(front, state.backIdUri, out).map {
                        Triple(it, "application/pdf", listOf("Sides" to if (state.backIdUri != null) "Front + Back" else "Front"))
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
                    engine.autoCleanImagesToPdf(state.imageUris, out, highContrastScan = true) { p ->
                        updateProgress(p, "Enhancing scan contrast…")
                    }.map {
                        Triple(it, "application/pdf", listOf("Pages cleaned" to state.imageUris.size.toString()))
                    }
                }

                "translate_pdf" -> {
                    val src = state.primaryFile ?: return@launch
                    val out = File(exportsDir, "${src.nameWithoutExtension}_${state.translateLang.name.lowercase()}.pdf")
                    engine.translatePdfOffline(src, state.translateLang, out) { p ->
                        updateProgress(p, "Translating offline (${state.translateLang.displayName})…")
                    }.map {
                        Triple(it, "application/pdf", listOf("Language" to state.translateLang.displayName))
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
}
