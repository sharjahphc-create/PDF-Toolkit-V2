package com.example.feature.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.IoDispatcher
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderThemeMode
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TtsEngine
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.LibraryRepository
import com.example.domain.model.Bookmark
import com.example.domain.model.Highlight
import com.example.domain.model.LibraryDocument
import com.example.feature.documents.engine.OfficeDocumentEngine
import com.example.feature.sign.PdfOpenResult
import com.example.feature.sign.PdfSignatureFlattener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ReaderContentKind {
    PDF,
    EPUB,
    TEXT
}

data class ReaderPageText(
    val pageIndex: Int,
    val title: String,
    val body: String
)

data class ReaderSearchHit(
    val pageIndex: Int,
    val pageLabel: String,
    val snippet: String
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val document: LibraryDocument? = null,
    val contentKind: ReaderContentKind = ReaderContentKind.PDF,
    val pdfWorkingFile: File? = null,
    val pdfPassword: String? = null,
    val passwordRequiredFile: File? = null,
    val passwordError: String? = null,
    val totalPages: Int = 1,
    val currentPage: Int = 0,
    val pageBitmaps: Map<Int, Bitmap> = emptyMap(),
    val textSections: List<ReaderPageText> = emptyList(),
    val reflowTextMode: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<ReaderSearchHit> = emptyList(),
    val isControlsVisible: Boolean = true
)

sealed interface ReaderEvent {
    data class ShowMessage(val message: String) : ReaderEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfFlattener: PdfSignatureFlattener,
    private val pdfTextExtractor: PdfTextExtractor,
    private val epubParser: EpubParser,
    private val officeEngine: OfficeDocumentEngine,
    private val ttsEngine: TtsEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEvent>()
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    val ttsState: StateFlow<TtsPlaybackState> = ttsEngine.state

    private var activeDocumentId: String? = null
    private var decryptedTempFile: File? = null
    private var readingTimerJob: Job? = null
    private var annotationsJob: Job? = null

    fun openDocument(documentId: String) {
        if (activeDocumentId == documentId && _uiState.value.document != null && !_uiState.value.isLoading) {
            return
        }
        activeDocumentId = documentId
        _uiState.update { ReaderUiState(isLoading = true) }

        annotationsJob?.cancel()
        annotationsJob = viewModelScope.launch {
            launch {
                libraryRepository.observeBookmarks(documentId).collectLatest { list ->
                    _uiState.update { it.copy(bookmarks = list) }
                }
            }
            launch {
                libraryRepository.observeHighlights(documentId).collectLatest { list ->
                    _uiState.update { it.copy(highlights = list) }
                }
            }
        }

        viewModelScope.launch {
            val doc = libraryRepository.getDocumentById(documentId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Document not found in library.") }
                return@launch
            }
            val savedProgress = libraryRepository.getReadingProgress(documentId)
            val workingFile = if (doc.isLocked && doc.localCachePath.endsWith(".enc")) {
                val res = libraryRepository.openVaultDecryptedCopy(doc)
                if (res.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            document = doc,
                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to decrypt vault document"
                        )
                    }
                    return@launch
                }
                res.getOrThrow().also { decryptedTempFile = it }
            } else {
                File(doc.localCachePath)
            }

            if (!workingFile.exists()) {
                _uiState.update {
                    it.copy(isLoading = false, document = doc, errorMessage = "File missing on disk.")
                }
                return@launch
            }

            loadWorkingFile(doc, workingFile, savedProgress?.currentPage ?: 0)
            startReadingStatsTicker(documentId)
        }
    }

    private suspend fun loadWorkingFile(
        doc: LibraryDocument,
        workingFile: File,
        initialPage: Int,
        password: String? = null
    ) {
        val lowerName = doc.fileName.lowercase()
        when {
            doc.mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                when (val openRes = pdfFlattener.preparePdfFromFile(workingFile, doc.fileName, password)) {
                    is PdfOpenResult.Ready -> {
                        val safePage = initialPage.coerceIn(0, (openRes.pageCount - 1).coerceAtLeast(0))
                        val initialText = buildPdfPageSections(doc, openRes.workingCopy, openRes.pageCount)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                document = doc,
                                contentKind = ReaderContentKind.PDF,
                                pdfWorkingFile = openRes.workingCopy,
                                pdfPassword = openRes.password,
                                passwordRequiredFile = null,
                                passwordError = null,
                                totalPages = openRes.pageCount,
                                currentPage = safePage,
                                textSections = initialText
                            )
                        }
                        preloadPdfWindow(openRes.workingCopy, safePage, openRes.pageCount)
                    }
                    is PdfOpenResult.PasswordRequired -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                document = doc,
                                passwordRequiredFile = openRes.tempLockedFile,
                                passwordError = if (password != null) "Incorrect PDF password" else null
                            )
                        }
                    }
                    is PdfOpenResult.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, document = doc, errorMessage = openRes.message)
                        }
                    }
                }
            }

            doc.mimeType == "application/epub+zip" || lowerName.endsWith(".epub") -> {
                val parsed = epubParser.parse(workingFile)
                parsed.onSuccess { book ->
                    val sections = book.chapters.map { ch ->
                        ReaderPageText(pageIndex = ch.index, title = ch.title, body = ch.text)
                    }
                    val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            document = doc,
                            contentKind = ReaderContentKind.EPUB,
                            totalPages = sections.size.coerceAtLeast(1),
                            currentPage = safePage,
                            textSections = sections,
                            reflowTextMode = true
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            document = doc,
                            errorMessage = err.localizedMessage ?: "Failed to parse EPUB"
                        )
                    }
                }
            }

            lowerName.endsWith(".docx") -> {
                val docxRes = officeEngine.readDocx(workingFile, doc.fileName)
                docxRes.onSuccess { model ->
                    val fullText = model.paragraphs.joinToString("\n\n") { it.text }
                    val sections = paginatePlainText(doc.title, fullText)
                    val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            document = doc,
                            contentKind = ReaderContentKind.TEXT,
                            totalPages = sections.size.coerceAtLeast(1),
                            currentPage = safePage,
                            textSections = sections,
                            reflowTextMode = true
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, document = doc, errorMessage = err.localizedMessage ?: "Unable to open .docx")
                    }
                }
            }

            else -> {
                val rawText = withContext(ioDispatcher) {
                    runCatching { workingFile.readText() }.getOrDefault(doc.ocrText)
                }
                val sections = paginatePlainText(doc.title, rawText)
                val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        document = doc,
                        contentKind = ReaderContentKind.TEXT,
                        totalPages = sections.size.coerceAtLeast(1),
                        currentPage = safePage,
                        textSections = sections,
                        reflowTextMode = true
                    )
                }
            }
        }
    }

    fun submitPdfPassword(password: String) {
        val doc = _uiState.value.document ?: return
        val lockedFile = _uiState.value.passwordRequiredFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, passwordError = null) }
            loadWorkingFile(doc, lockedFile, _uiState.value.currentPage, password)
        }
    }

    fun goToPage(pageIndex: Int) {
        val state = _uiState.value
        val clamped = pageIndex.coerceIn(0, (state.totalPages - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentPage = clamped) }
        val doc = state.document ?: return
        viewModelScope.launch {
            libraryRepository.saveReadingProgress(
                documentId = doc.id,
                currentPage = clamped,
                totalPages = state.totalPages,
                scrollOffsetFraction = 0f
            )
        }
        state.pdfWorkingFile?.let { file ->
            viewModelScope.launch {
                preloadPdfWindow(file, clamped, state.totalPages)
            }
        }
    }

    private suspend fun preloadPdfWindow(file: File, centerPage: Int, totalPages: Int) {
        val window = ((centerPage - 1).coerceAtLeast(0)..(centerPage + 1).coerceAtMost(totalPages - 1)).toList()
        val updated = _uiState.value.pageBitmaps.toMutableMap()
        for (pageIdx in window) {
            if (updated[pageIdx]?.isRecycled != false) {
                val bmp = pdfFlattener.renderPageBitmap(file, pageIdx, targetWidthPx = 1240)
                if (bmp != null) {
                    updated[pageIdx] = bmp
                }
            }
        }
        // Keep bounded window in UI state so memory remains low on 200+ page documents
        val pruned = updated.filterKeys { kotlin.math.abs(it - centerPage) <= 3 }
        _uiState.update { it.copy(pageBitmaps = pruned) }
    }

    private suspend fun buildPdfPageSections(
        doc: LibraryDocument,
        workingFile: File,
        pageCount: Int
    ): List<ReaderPageText> = withContext(ioDispatcher) {
        val cachedPages = doc.ocrText.split('\u000C')
        val sections = if (cachedPages.size >= pageCount && doc.ocrText.isNotBlank()) {
            cachedPages.take(pageCount).mapIndexed { index, text ->
                ReaderPageText(index, "Page ${index + 1}", text.trim())
            }
        } else {
            val extracted = pdfTextExtractor.extractSpeakableText(workingFile, "application/pdf")
                .split('\u000C')
            (0 until pageCount).map { idx ->
                ReaderPageText(idx, "Page ${idx + 1}", extracted.getOrNull(idx).orEmpty().trim())
            }
        }
        sections
    }

    private fun paginatePlainText(title: String, rawText: String): List<ReaderPageText> {
        if (rawText.isBlank()) return listOf(ReaderPageText(0, title, "Empty document."))
        val chunkSize = 2200
        val paragraphs = rawText.split(Regex("\n{2,}"))
        val pages = mutableListOf<ReaderPageText>()
        val buffer = StringBuilder()
        for (para in paragraphs) {
            if (buffer.length + para.length > chunkSize && buffer.isNotEmpty()) {
                val idx = pages.size
                pages.add(ReaderPageText(idx, "Section ${idx + 1}", buffer.toString().trim()))
                buffer.clear()
            }
            buffer.append(para).append("\n\n")
        }
        if (buffer.isNotBlank()) {
            val idx = pages.size
            pages.add(ReaderPageText(idx, "Section ${idx + 1}", buffer.toString().trim()))
        }
        return pages
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun toggleReflowTextMode() {
        _uiState.update { it.copy(reflowTextMode = !it.reflowTextMode) }
    }

    fun searchInDocument(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchQuery = "", searchHits = emptyList()) }
            return
        }
        val hits = _uiState.value.textSections.mapNotNull { sec ->
            val matchIdx = sec.body.indexOf(trimmed, ignoreCase = true)
            if (matchIdx >= 0) {
                val start = (matchIdx - 28).coerceAtLeast(0)
                val end = (matchIdx + trimmed.length + 52).coerceAtMost(sec.body.length)
                ReaderSearchHit(
                    pageIndex = sec.pageIndex,
                    pageLabel = sec.title,
                    snippet = sec.body.substring(start, end).replace('\n', ' ').trim()
                )
            } else null
        }
        _uiState.update { it.copy(searchQuery = query, searchHits = hits) }
    }

    fun toggleCurrentPageBookmark() {
        val state = _uiState.value
        val doc = state.document ?: return
        val pageNumber = state.currentPage + 1
        val existing = state.bookmarks.firstOrNull { it.pageNumber == pageNumber }
        viewModelScope.launch {
            if (existing != null) {
                libraryRepository.softDeleteBookmark(existing.id)
                _events.emit(ReaderEvent.ShowMessage("Bookmark removed (page $pageNumber)"))
            } else {
                val snippet = state.textSections.getOrNull(state.currentPage)?.body?.take(80).orEmpty()
                libraryRepository.addBookmark(
                    documentId = doc.id,
                    pageNumber = pageNumber,
                    label = "Page $pageNumber",
                    excerpt = snippet
                )
                _events.emit(ReaderEvent.ShowMessage("Bookmarked page $pageNumber"))
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch { libraryRepository.softDeleteBookmark(bookmarkId) }
    }

    fun addHighlight(colorKey: String, quotedText: String, noteText: String) {
        val state = _uiState.value
        val doc = state.document ?: return
        val cleanQuote = quotedText.trim().ifBlank {
            state.textSections.getOrNull(state.currentPage)?.body?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(140)
                ?: "Page ${state.currentPage + 1} highlight"
        }
        viewModelScope.launch {
            libraryRepository.addHighlight(
                documentId = doc.id,
                pageNumber = state.currentPage + 1,
                colorKey = colorKey,
                quotedText = cleanQuote,
                noteText = noteText.trim()
            )
            _events.emit(ReaderEvent.ShowMessage("Highlight saved on page ${state.currentPage + 1}"))
        }
    }

    fun deleteHighlight(highlightId: String) {
        viewModelScope.launch { libraryRepository.softDeleteHighlight(highlightId) }
    }

    fun startOrToggleTtsFromCurrentPage() {
        val state = _uiState.value
        val doc = state.document ?: return
        val activeTts = ttsState.value
        if (activeTts.documentId == doc.id && activeTts.sentences.isNotEmpty()) {
            when (activeTts.status) {
                TtsStatus.SPEAKING -> ttsEngine.pause()
                TtsStatus.PAUSED, TtsStatus.READY -> ttsEngine.play()
                else -> ttsEngine.initializeIfNeeded()
            }
            return
        }
        viewModelScope.launch {
            val fullText = state.textSections.joinToString("\u000C") { it.body }
                .ifBlank { libraryRepository.extractSpeakableText(doc.id) }
            if (fullText.isBlank()) {
                _events.emit(ReaderEvent.ShowMessage("No speakable text found in this document"))
                return@launch
            }
            ttsEngine.loadDocument(
                documentId = doc.id,
                title = doc.title,
                text = fullText,
                startSentenceIndex = 0
            )
            ttsEngine.play()
        }
    }

    fun setTtsRate(rate: Float) = ttsEngine.setSpeechRate(rate)
    fun nextTtsSentence() = ttsEngine.nextSentence()
    fun prevTtsSentence() = ttsEngine.previousSentence()
    fun stopTts() = ttsEngine.stop()

    fun setReaderTheme(mode: ReaderThemeMode) {
        viewModelScope.launch { preferencesRepository.setReaderTheme(mode) }
    }

    fun setReaderFont(font: ReaderFontChoice) {
        viewModelScope.launch { preferencesRepository.setReaderFont(font) }
    }

    fun setReaderFontScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setReaderFontScale(scale) }
    }

    fun setReaderBrightness(brightness: Float) {
        viewModelScope.launch { preferencesRepository.setReaderBrightness(brightness) }
    }

    fun setReaderWarmLight(warmLight: Float) {
        viewModelScope.launch { preferencesRepository.setReaderWarmLight(warmLight) }
    }

    private fun startReadingStatsTicker(documentId: String) {
        readingTimerJob?.cancel()
        readingTimerJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                libraryRepository.recordReadingMinute(documentId, pagesReadDelta = 1)
            }
        }
    }

    override fun onCleared() {
        readingTimerJob?.cancel()
        decryptedTempFile?.delete()
        super.onCleared()
    }
}
