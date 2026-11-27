package com.example.feature.documents

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
import com.example.data.repository.ToolUsageRepository
import com.example.domain.model.DocumentCategory
import com.example.feature.documents.engine.DocxDocumentModel
import com.example.feature.documents.engine.DocxParagraphItem
import com.example.feature.documents.engine.OfficeDocumentEngine
import com.example.feature.documents.engine.PptxPresentationModel
import com.example.feature.documents.engine.XlsxWorkbookModel
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
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class OfficeExportFormat { NATIVE, PDF }

data class OfficeToolUiState(
    val toolId: String = "",
    val step: ToolStep = ToolStep.PICK,
    val displayName: String = "",
    // Word (.docx)
    val docxModel: DocxDocumentModel? = null,
    // Excel (.xlsx)
    val xlsxModel: XlsxWorkbookModel? = null,
    val activeSheetIndex: Int = 0,
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    // PowerPoint (.pptx)
    val pptxModel: PptxPresentationModel? = null,
    val activeSlideIndex: Int = 0,
    val slidePreviewBitmap: Bitmap? = null,
    val includeSpeakerNotesInPdf: Boolean = true,
    // Save / Export
    val exportFormat: OfficeExportFormat = OfficeExportFormat.NATIVE,
    val progress: OperationProgress = OperationProgress(),
    val outputFile: File? = null,
    val outputMimeType: String = "application/pdf",
    val outputDocumentId: String? = null,
    val summaryMetrics: List<Pair<String, String>> = emptyList()
)

sealed interface OfficeToolEvent {
    data class ShowMessage(val message: String) : OfficeToolEvent
    data class ShareFile(val uri: Uri, val mimeType: String, val title: String) : OfficeToolEvent
}

@HiltViewModel
class OfficeToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val officeEngine: OfficeDocumentEngine,
    private val libraryRepository: LibraryRepository,
    private val toolUsageRepository: ToolUsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfficeToolUiState())
    val uiState: StateFlow<OfficeToolUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<DocumentCategory>> = libraryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<OfficeToolEvent>()
    val events: SharedFlow<OfficeToolEvent> = _events.asSharedFlow()

    private var activeJob: Job? = null

    fun initTool(toolId: String) {
        if (_uiState.value.toolId == toolId) return
        activeJob?.cancel()
        val defaultExport = if (toolId == "powerpoint") OfficeExportFormat.PDF else OfficeExportFormat.NATIVE
        _uiState.value = OfficeToolUiState(toolId = toolId, exportFormat = defaultExport)
    }

    fun openDocumentUri(uri: Uri) {
        val toolId = _uiState.value.toolId
        val ext = when (toolId) {
            "word" -> "docx"
            "excel" -> "xlsx"
            else -> "pptx"
        }
        viewModelScope.launch {
            val copied = withContext(ioDispatcher) { officeEngine.copyUriToWorkFile(uri, ext) }
            if (copied == null) {
                _events.emit(OfficeToolEvent.ShowMessage("Unable to open selected file"))
                return@launch
            }
            val (file, name) = copied
            loadLocalFile(file, name)
        }
    }

    fun createNewDocument() {
        when (_uiState.value.toolId) {
            "word" -> {
                val model = officeEngine.createNewDocxModel()
                _uiState.update {
                    it.copy(
                        step = ToolStep.CONFIGURE,
                        displayName = model.displayName,
                        docxModel = model,
                        exportFormat = OfficeExportFormat.NATIVE
                    )
                }
            }
            "excel" -> {
                val model = officeEngine.createNewXlsxModel()
                _uiState.update {
                    it.copy(
                        step = ToolStep.CONFIGURE,
                        displayName = model.displayName,
                        xlsxModel = model,
                        activeSheetIndex = 0,
                        selectedRow = 0,
                        selectedCol = 0,
                        exportFormat = OfficeExportFormat.NATIVE
                    )
                }
            }
            "powerpoint" -> {
                viewModelScope.launch {
                    val sample = withContext(ioDispatcher) { buildSamplePptxFile() }
                    loadLocalFile(sample, sample.name)
                }
            }
        }
    }

    private suspend fun loadLocalFile(file: File, name: String) {
        when (_uiState.value.toolId) {
            "word" -> {
                officeEngine.readDocx(file, name).onSuccess { model ->
                    _uiState.update {
                        it.copy(
                            step = ToolStep.CONFIGURE,
                            displayName = name,
                            docxModel = model,
                            exportFormat = OfficeExportFormat.NATIVE
                        )
                    }
                }.onFailure { err ->
                    _events.emit(OfficeToolEvent.ShowMessage(err.localizedMessage ?: "Failed to read .docx"))
                }
            }
            "excel" -> {
                officeEngine.readXlsx(file, name).onSuccess { model ->
                    _uiState.update {
                        it.copy(
                            step = ToolStep.CONFIGURE,
                            displayName = name,
                            xlsxModel = model,
                            activeSheetIndex = 0,
                            selectedRow = 0,
                            selectedCol = 0,
                            exportFormat = OfficeExportFormat.NATIVE
                        )
                    }
                }.onFailure { err ->
                    _events.emit(OfficeToolEvent.ShowMessage(err.localizedMessage ?: "Failed to read .xlsx"))
                }
            }
            "powerpoint" -> {
                officeEngine.readPptx(file, name).onSuccess { model ->
                    val firstBmp = model.slides.firstOrNull()?.let { slide ->
                        withContext(ioDispatcher) {
                            officeEngine.renderSlidePreviewBitmap(slide, model.slideWidthPt, model.slideHeightPt)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            step = ToolStep.CONFIGURE,
                            displayName = name,
                            pptxModel = model,
                            activeSlideIndex = 0,
                            slidePreviewBitmap = firstBmp,
                            exportFormat = OfficeExportFormat.PDF
                        )
                    }
                }.onFailure { err ->
                    _events.emit(OfficeToolEvent.ShowMessage(err.localizedMessage ?: "Failed to read .pptx"))
                }
            }
        }
    }

    fun setExportFormat(format: OfficeExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    // --- Word editing ---
    fun updateParagraphText(id: String, newText: String) {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            state.copy(
                docxModel = doc.copy(
                    paragraphs = doc.paragraphs.map { if (it.id == id) it.copy(text = newText) else it }
                )
            )
        }
    }

    fun toggleParagraphHeading(id: String) {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            state.copy(
                docxModel = doc.copy(
                    paragraphs = doc.paragraphs.map {
                        if (it.id == id) it.copy(isHeading = !it.isHeading, isBold = !it.isHeading || it.isBold) else it
                    }
                )
            )
        }
    }

    fun toggleParagraphBold(id: String) {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            state.copy(
                docxModel = doc.copy(
                    paragraphs = doc.paragraphs.map { if (it.id == id) it.copy(isBold = !it.isBold) else it }
                )
            )
        }
    }

    fun toggleParagraphItalic(id: String) {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            state.copy(
                docxModel = doc.copy(
                    paragraphs = doc.paragraphs.map { if (it.id == id) it.copy(isItalic = !it.isItalic) else it }
                )
            )
        }
    }

    fun addDocxParagraph() {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            state.copy(
                docxModel = doc.copy(paragraphs = doc.paragraphs + DocxParagraphItem(text = ""))
            )
        }
    }

    fun removeDocxParagraph(id: String) {
        _uiState.update { state ->
            val doc = state.docxModel ?: return@update state
            if (doc.paragraphs.size <= 1) return@update state
            state.copy(
                docxModel = doc.copy(paragraphs = doc.paragraphs.filterNot { it.id == id })
            )
        }
    }

    // --- Excel editing ---
    fun selectSheet(index: Int) {
        _uiState.update {
            it.copy(
                activeSheetIndex = index,
                selectedRow = 0,
                selectedCol = 0
            )
        }
    }

    fun selectCell(row: Int, col: Int) {
        _uiState.update { it.copy(selectedRow = row, selectedCol = col) }
    }

    fun updateSelectedCellValue(value: String) {
        _uiState.update { state ->
            val wb = state.xlsxModel ?: return@update state
            val sheetIdx = state.activeSheetIndex
            val sheets = wb.sheets.toMutableList()
            val sheet = sheets.getOrNull(sheetIdx) ?: return@update state
            val rows = sheet.rows.map { it.toMutableList() }.toMutableList()
            if (state.selectedRow in rows.indices && state.selectedCol in rows[state.selectedRow].indices) {
                rows[state.selectedRow][state.selectedCol] = value
                sheets[sheetIdx] = sheet.copy(rows = rows)
            }
            state.copy(xlsxModel = wb.copy(sheets = sheets))
        }
    }

    fun addXlsxRow() {
        _uiState.update { state ->
            val wb = state.xlsxModel ?: return@update state
            val sheetIdx = state.activeSheetIndex
            val sheets = wb.sheets.toMutableList()
            val sheet = sheets.getOrNull(sheetIdx) ?: return@update state
            val colCount = sheet.rows.firstOrNull()?.size ?: 3
            val newRow = List(colCount) { "" }
            sheets[sheetIdx] = sheet.copy(rows = sheet.rows + listOf(newRow))
            state.copy(xlsxModel = wb.copy(sheets = sheets))
        }
    }

    fun addXlsxColumn() {
        _uiState.update { state ->
            val wb = state.xlsxModel ?: return@update state
            val sheetIdx = state.activeSheetIndex
            val sheets = wb.sheets.toMutableList()
            val sheet = sheets.getOrNull(sheetIdx) ?: return@update state
            val updatedRows = sheet.rows.map { it + "" }
            sheets[sheetIdx] = sheet.copy(rows = updatedRows)
            state.copy(xlsxModel = wb.copy(sheets = sheets))
        }
    }

    // --- PowerPoint ---
    fun selectSlide(index: Int) {
        val model = _uiState.value.pptxModel ?: return
        val clamped = index.coerceIn(0, (model.slides.size - 1).coerceAtLeast(0))
        val slide = model.slides.getOrNull(clamped) ?: return
        viewModelScope.launch {
            val bmp = withContext(ioDispatcher) {
                officeEngine.renderSlidePreviewBitmap(slide, model.slideWidthPt, model.slideHeightPt)
            }
            _uiState.update { it.copy(activeSlideIndex = clamped, slidePreviewBitmap = bmp) }
        }
    }

    fun setIncludeSpeakerNotes(include: Boolean) {
        _uiState.update { it.copy(includeSpeakerNotesInPdf = include) }
    }

    // --- Save / Export ---
    fun processAndExport() {
        val state = _uiState.value
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val baseName = state.displayName.substringBeforeLast('.').ifBlank { "Document" }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.PROCESS,
                    progress = OperationProgress(0.2f, "Processing on device…", isIndeterminate = false, isCancellable = true)
                )
            }

            val outcome: Result<Triple<File, String, List<Pair<String, String>>>> = when (state.toolId) {
                "word" -> {
                    val model = state.docxModel ?: return@launch
                    if (state.exportFormat == OfficeExportFormat.PDF) {
                        val out = File(exportDir, "${baseName}_${System.currentTimeMillis()}.pdf")
                        officeEngine.exportDocxToPdf(model, out).map { f ->
                            Triple(
                                f,
                                "application/pdf",
                                listOf("Paragraphs" to "${model.paragraphs.size}", "Format" to "PDF")
                            )
                        }
                    } else {
                        val out = File(exportDir, "${baseName}_edited_${System.currentTimeMillis()}.docx")
                        officeEngine.saveDocx(model, out).map { f ->
                            Triple(
                                f,
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                listOf("Paragraphs" to "${model.paragraphs.size}", "Format" to ".docx")
                            )
                        }
                    }
                }
                "excel" -> {
                    val model = state.xlsxModel ?: return@launch
                    if (state.exportFormat == OfficeExportFormat.PDF) {
                        val out = File(exportDir, "${baseName}_${System.currentTimeMillis()}.pdf")
                        officeEngine.exportXlsxToPdf(model, out).map { f ->
                            Triple(
                                f,
                                "application/pdf",
                                listOf("Sheets" to "${model.sheets.size}", "Format" to "PDF")
                            )
                        }
                    } else {
                        val out = File(exportDir, "${baseName}_edited_${System.currentTimeMillis()}.xlsx")
                        officeEngine.saveXlsx(model, out).map { f ->
                            Triple(
                                f,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                listOf("Sheets" to "${model.sheets.size}", "Format" to ".xlsx")
                            )
                        }
                    }
                }
                "powerpoint" -> {
                    val model = state.pptxModel ?: return@launch
                    val out = File(exportDir, "${baseName}_slides_${System.currentTimeMillis()}.pdf")
                    officeEngine.exportPptxToPdf(
                        model = model,
                        includeSpeakerNotes = state.includeSpeakerNotesInPdf,
                        outputFile = out
                    ) { pct ->
                        _uiState.update {
                            it.copy(progress = OperationProgress(pct, "Rendering slides to PDF…"))
                        }
                    }.map { f ->
                        Triple(
                            f,
                            "application/pdf",
                            listOf("Slides" to "${model.slides.size}", "Size" to "${(f.length() / 1024L).coerceAtLeast(1)} KB")
                        )
                    }
                }
                else -> Result.failure(IllegalArgumentException("Unknown office tool"))
            }

            outcome.onSuccess { (file, mime, metrics) ->
                var docId: String? = null
                if (mime == "application/pdf") {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    docId = libraryRepository.importFromUri(uri).getOrNull()?.id
                }
                toolUsageRepository.recordToolLaunch(state.toolId)
                _uiState.update {
                    it.copy(
                        step = ToolStep.DONE,
                        outputFile = file,
                        outputMimeType = mime,
                        outputDocumentId = docId,
                        summaryMetrics = metrics
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
                _events.emit(OfficeToolEvent.ShowMessage(err.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun cancelProcess() {
        activeJob?.cancel()
        _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
    }

    fun shareOutput() {
        val state = _uiState.value
        val file = state.outputFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        viewModelScope.launch {
            _events.emit(OfficeToolEvent.ShareFile(uri, state.outputMimeType, file.name))
        }
    }

    fun exportOutputToSaf(destUri: Uri) {
        val out = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(destUri)?.use { os ->
                        out.inputStream().use { input -> input.copyTo(os) }
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                _events.emit(OfficeToolEvent.ShowMessage("Saved copy to device"))
            }.onFailure { err ->
                _events.emit(OfficeToolEvent.ShowMessage(err.localizedMessage ?: "Save failed"))
            }
        }
    }

    fun assignOutputToCategory(categoryId: String?) {
        val docId = _uiState.value.outputDocumentId
        if (docId == null) {
            viewModelScope.launch {
                _events.emit(OfficeToolEvent.ShowMessage("Export as PDF first to file into Library categories"))
            }
            return
        }
        viewModelScope.launch {
            libraryRepository.moveToCategory(docId, categoryId)
            _events.emit(OfficeToolEvent.ShowMessage("Saved to category"))
        }
    }

    fun stepBackOrExit(onExit: () -> Unit) {
        when (_uiState.value.step) {
            ToolStep.PICK -> onExit()
            ToolStep.CONFIGURE -> _uiState.update { it.copy(step = ToolStep.PICK) }
            ToolStep.PROCESS -> cancelProcess()
            ToolStep.DONE -> onExit()
        }
    }

    private fun buildSamplePptxFile(): File {
        val dir = File(context.cacheDir, "office_work").apply { mkdirs() }
        val file = File(dir, "Sample_Deck.pptx")
        XMLSlideShow().use { ppt ->
            val s1 = ppt.createSlide()
            s1.createTextBox().apply {
                text = "PDF Toolkit Presentation\n100% Offline Apache POI → PDF"
            }
            val s2 = ppt.createSlide()
            s2.createTextBox().apply {
                text = "Key Highlights\n• Slide previews rendered on device\n• Speaker notes preserved\n• No cloud uploads"
            }
            FileOutputStream(file).use { fos -> ppt.write(fos) }
        }
        return file
    }
}
