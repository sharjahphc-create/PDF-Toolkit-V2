package com.example.feature.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.IoDispatcher
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ReaderBookPreset
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TtsEngine
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.LibraryRepository
import com.example.domain.model.LibraryDocument
import com.example.feature.documents.engine.OfficeDocumentEngine
import com.example.feature.sign.PdfOpenResult
import com.example.feature.sign.PdfSignatureFlattener
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlin.math.roundToInt

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfFlattener: PdfSignatureFlattener,
    private val pdfTextExtractor: PdfTextExtractor,
    private val epubParser: EpubParser,
    private val officeEngine: OfficeDocumentEngine,
    private val readerHelper: ReaderPdfAndExportHelper,
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
    private var statsJob: Job? = null
    private var scrollSaveJob: Job? = null

    init {
        statsJob = viewModelScope.launch {
            launch {
                libraryRepository.observeTotalMinutesRead().collectLatest { mins ->
                    _uiState.update { it.copy(stats = it.stats.copy(totalMinutesRead = mins)) }
                }
            }
            launch {
                libraryRepository.observeTotalTtsMinutes().collectLatest { ttsMins ->
                    _uiState.update { it.copy(stats = it.stats.copy(totalTtsMinutes = ttsMins)) }
                }
            }
        }
    }

    fun openDocument(documentId: String) {
        if (activeDocumentId == documentId && _uiState.value.document != null && !_uiState.value.isLoading) {
            return
        }
        activeDocumentId = documentId
        _uiState.update {
            ReaderUiState(
                isLoading = true,
                stats = it.stats.copy(sessionMinutes = 0)
            )
        }

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

            loadWorkingFile(
                doc = doc,
                workingFile = workingFile,
                initialPage = savedProgress?.currentPage ?: 0,
                initialScrollFraction = savedProgress?.scrollOffsetFraction ?: 0f
            )
            startReadingStatsTicker(documentId)
        }
    }

    private suspend fun loadWorkingFile(
        doc: LibraryDocument,
        workingFile: File,
        initialPage: Int,
        initialScrollFraction: Float = 0f,
        password: String? = null
    ) {
        val lowerName = doc.fileName.lowercase()
        when {
            doc.mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                when (val openRes = pdfFlattener.preparePdfFromFile(workingFile, doc.fileName, password)) {
                    is PdfOpenResult.Ready -> {
                        val safePage = initialPage.coerceIn(0, (openRes.pageCount - 1).coerceAtLeast(0))
                        val initialText = buildPdfPageSections(doc, openRes.workingCopy, openRes.pageCount)
                        val toc = readerHelper.extractPdfToc(openRes.workingCopy, openRes.password, initialText)
                        val (estMins, wordCount) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
                            sections = initialText,
                            currentPage = safePage,
                            totalPages = openRes.pageCount
                        )
                        val pct = (((safePage + 1).toFloat() / openRes.pageCount.coerceAtLeast(1)) * 100f).roundToInt()
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
                                initialScrollFraction = initialScrollFraction,
                                textSections = initialText,
                                tocEntries = toc,
                                stats = it.stats.copy(
                                    percentComplete = pct,
                                    estimatedMinutesLeft = estMins,
                                    totalWordCount = wordCount
                                )
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
                    val toc = sections.map { ReaderTocEntry(title = it.title, pageIndex = it.pageIndex, level = 0) }
                    val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                    val total = sections.size.coerceAtLeast(1)
                    val (estMins, wordCount) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
                        sections = sections,
                        currentPage = safePage,
                        totalPages = total
                    )
                    val pct = (((safePage + 1).toFloat() / total) * 100f).roundToInt()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            document = doc,
                            contentKind = ReaderContentKind.EPUB,
                            totalPages = total,
                            currentPage = safePage,
                            initialScrollFraction = initialScrollFraction,
                            textSections = sections,
                            tocEntries = toc,
                            reflowTextMode = true,
                            stats = it.stats.copy(
                                percentComplete = pct,
                                estimatedMinutesLeft = estMins,
                                totalWordCount = wordCount
                            )
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
                    val toc = sections.map { ReaderTocEntry(title = it.title, pageIndex = it.pageIndex, level = 0) }
                    val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                    val total = sections.size.coerceAtLeast(1)
                    val (estMins, wordCount) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
                        sections = sections,
                        currentPage = safePage,
                        totalPages = total
                    )
                    val pct = (((safePage + 1).toFloat() / total) * 100f).roundToInt()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            document = doc,
                            contentKind = ReaderContentKind.TEXT,
                            totalPages = total,
                            currentPage = safePage,
                            initialScrollFraction = initialScrollFraction,
                            textSections = sections,
                            tocEntries = toc,
                            reflowTextMode = true,
                            stats = it.stats.copy(
                                percentComplete = pct,
                                estimatedMinutesLeft = estMins,
                                totalWordCount = wordCount
                            )
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
                val toc = sections.map { ReaderTocEntry(title = it.title, pageIndex = it.pageIndex, level = 0) }
                val safePage = initialPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
                val total = sections.size.coerceAtLeast(1)
                val (estMins, wordCount) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
                    sections = sections,
                    currentPage = safePage,
                    totalPages = total
                )
                val pct = (((safePage + 1).toFloat() / total) * 100f).roundToInt()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        document = doc,
                        contentKind = ReaderContentKind.TEXT,
                        totalPages = total,
                        currentPage = safePage,
                        initialScrollFraction = initialScrollFraction,
                        textSections = sections,
                        tocEntries = toc,
                        reflowTextMode = true,
                        stats = it.stats.copy(
                            percentComplete = pct,
                            estimatedMinutesLeft = estMins,
                            totalWordCount = wordCount
                        )
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
            loadWorkingFile(doc, lockedFile, _uiState.value.currentPage, 0f, password)
        }
    }

    fun goToPage(pageIndex: Int) {
        val state = _uiState.value
        val clamped = pageIndex.coerceIn(0, (state.totalPages - 1).coerceAtLeast(0))
        val (estMins, wordCount) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
            sections = state.textSections,
            currentPage = clamped,
            totalPages = state.totalPages
        )
        val pct = (((clamped + 1).toFloat() / state.totalPages.coerceAtLeast(1)) * 100f).roundToInt()
        _uiState.update {
            it.copy(
                currentPage = clamped,
                initialScrollFraction = 0f,
                stats = it.stats.copy(
                    percentComplete = pct,
                    estimatedMinutesLeft = estMins,
                    totalWordCount = wordCount
                )
            )
        }
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

    fun saveScrollFraction(fraction: Float) {
        val doc = _uiState.value.document ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        scrollSaveJob?.cancel()
        scrollSaveJob = viewModelScope.launch {
            delay(400L)
            libraryRepository.saveReadingProgress(
                documentId = doc.id,
                currentPage = _uiState.value.currentPage,
                totalPages = _uiState.value.totalPages,
                scrollOffsetFraction = clamped
            )
        }
    }

    fun ensurePageRendered(pageIndex: Int) {
        val state = _uiState.value
        val file = state.pdfWorkingFile ?: return
        if (state.pageBitmaps[pageIndex]?.isRecycled == false) return
        viewModelScope.launch {
            preloadPdfWindow(file, pageIndex, state.totalPages)
        }
    }

    private suspend fun preloadPdfWindow(file: File, centerPage: Int, totalPages: Int) {
        val window = ((centerPage - 1).coerceAtLeast(0)..(centerPage + 1).coerceAtMost(totalPages - 1)).toList()
        val updated = _uiState.value.pageBitmaps.toMutableMap()
        val cropped = _uiState.value.croppedPageBitmaps.toMutableMap()
        val preset = preferences.value.effectiveReaderPreset(_uiState.value.document?.id)
        val wantsAutoCrop = preset.autoCropMargins || preset.pdfFitMode == com.example.core.datastore.PdfFitMode.AUTO_CROP

        for (pageIdx in window) {
            var bmp = updated[pageIdx]
            if (bmp == null || bmp.isRecycled) {
                bmp = pdfFlattener.renderPageBitmap(file, pageIdx, targetWidthPx = 1280)
                if (bmp != null) {
                    updated[pageIdx] = bmp
                }
            }
            if (wantsAutoCrop && bmp != null && !bmp.isRecycled && cropped[pageIdx]?.isRecycled != false) {
                cropped[pageIdx] = readerHelper.autoCropWhiteMargins(bmp)
            }
        }
        val pruned = updated.filterKeys { kotlin.math.abs(it - centerPage) <= 3 }
        val prunedCropped = cropped.filterKeys { kotlin.math.abs(it - centerPage) <= 3 }
        _uiState.update { it.copy(pageBitmaps = pruned, croppedPageBitmaps = prunedCropped) }
    }

    private suspend fun buildPdfPageSections(
        doc: LibraryDocument,
        workingFile: File,
        pageCount: Int
    ): List<ReaderPageText> = withContext(ioDispatcher) {
        val cachedPages = doc.ocrText.split('\u000C')
        if (cachedPages.size >= pageCount && doc.ocrText.isNotBlank()) {
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
                val heading = buffer.toString().lineSequence().firstOrNull { it.isNotBlank() }?.take(48)
                    ?: "Section ${idx + 1}"
                pages.add(ReaderPageText(idx, heading, buffer.toString().trim()))
                buffer.clear()
            }
            buffer.append(para).append("\n\n")
        }
        if (buffer.isNotBlank()) {
            val idx = pages.size
            val heading = buffer.toString().lineSequence().firstOrNull { it.isNotBlank() }?.take(48)
                ?: "Section ${idx + 1}"
            pages.add(ReaderPageText(idx, heading, buffer.toString().trim()))
        }
        return pages
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun toggleReflowTextMode() {
        _uiState.update { it.copy(reflowTextMode = !it.reflowTextMode) }
    }

    fun dismissLayoutWarning() {
        _uiState.update { it.copy(layoutWarningDismissed = true) }
    }

    fun searchInDocument(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchQuery = "", searchHits = emptyList(), totalSearchMatches = 0) }
            return
        }
        var totalCount = 0
        val hits = _uiState.value.textSections.mapNotNull { sec ->
            val firstIdx = sec.body.indexOf(trimmed, ignoreCase = true)
            if (firstIdx >= 0) {
                var pageCount = 0
                var cursor = firstIdx
                while (cursor >= 0) {
                    pageCount++
                    cursor = sec.body.indexOf(trimmed, cursor + trimmed.length, ignoreCase = true)
                }
                totalCount += pageCount
                val start = (firstIdx - 32).coerceAtLeast(0)
                val end = (firstIdx + trimmed.length + 58).coerceAtMost(sec.body.length)
                ReaderSearchHit(
                    pageIndex = sec.pageIndex,
                    pageLabel = sec.title,
                    snippet = sec.body.substring(start, end).replace('\n', ' ').trim(),
                    matchCountOnPage = pageCount
                )
            } else null
        }
        _uiState.update { it.copy(searchQuery = query, searchHits = hits, totalSearchMatches = totalCount) }
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

    fun exportHighlightsAsMarkdown() {
        val state = _uiState.value
        if (state.highlights.isEmpty()) {
            viewModelScope.launch { _events.emit(ReaderEvent.ShowMessage("No highlights to export yet")) }
            return
        }
        val title = state.document?.title ?: "Document"
        viewModelScope.launch {
            readerHelper.exportHighlightsToMarkdown(title, state.highlights)
                .onSuccess { file ->
                    val uri = readerHelper.getShareUri(file)
                    if (uri != null) {
                        _events.emit(ReaderEvent.ShareExportedFile(uri, "text/markdown", "${title}_highlights.md"))
                    } else {
                        _events.emit(ReaderEvent.ShowMessage("Exported Markdown: ${file.name}"))
                    }
                }
                .onFailure { err ->
                    _events.emit(ReaderEvent.ShowMessage(err.localizedMessage ?: "Markdown export failed"))
                }
        }
    }

    fun exportHighlightsAsPdf() {
        val state = _uiState.value
        if (state.highlights.isEmpty()) {
            viewModelScope.launch { _events.emit(ReaderEvent.ShowMessage("No highlights to export yet")) }
            return
        }
        val title = state.document?.title ?: "Document"
        viewModelScope.launch {
            readerHelper.exportHighlightsToPdf(title, state.highlights)
                .onSuccess { file ->
                    libraryRepository.importLocalFile(file, file.name, "application/pdf")
                    val uri = readerHelper.getShareUri(file)
                    if (uri != null) {
                        _events.emit(ReaderEvent.ShareExportedFile(uri, "application/pdf", file.name))
                    } else {
                        _events.emit(ReaderEvent.ShowMessage("Exported PDF: ${file.name}"))
                    }
                }
                .onFailure { err ->
                    _events.emit(ReaderEvent.ShowMessage(err.localizedMessage ?: "PDF export failed"))
                }
        }
    }

    // Long-press word lookup: Offline Dictionary + ML Kit Offline Translate
    fun openWordLookup(initialText: String) {
        val clean = initialText.trim().take(280)
        val firstWord = clean.split(Regex("\\s+")).firstOrNull().orEmpty()
        viewModelScope.launch {
            val (term, def, meta) = readerHelper.lookupOfflineWord(firstWord)
            val defaultLang = ReaderTranslateLanguage.SPANISH
            _uiState.update {
                it.copy(
                    wordLookup = WordLookupState(
                        queryText = clean,
                        dictionaryTerm = term,
                        dictionaryDefinition = def,
                        dictionarySourceLabel = meta.first,
                        customPackWordCount = meta.second,
                        targetLanguage = defaultLang,
                        isCheckingPack = true
                    )
                )
            }
            val downloaded = readerHelper.isTranslationPackDownloaded(defaultLang)
            _uiState.update { st ->
                st.copy(
                    wordLookup = st.wordLookup?.copy(
                        isCheckingPack = false,
                        isLanguagePackDownloaded = downloaded
                    )
                )
            }
        }
    }

    fun updateLookupQuery(text: String) {
        viewModelScope.launch {
            val firstWord = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            val (term, def, meta) = readerHelper.lookupOfflineWord(firstWord)
            _uiState.update { st ->
                st.copy(
                    wordLookup = st.wordLookup?.copy(
                        queryText = text,
                        dictionaryTerm = term,
                        dictionaryDefinition = def,
                        dictionarySourceLabel = meta.first,
                        customPackWordCount = meta.second,
                        translatedText = null,
                        translationError = null
                    )
                )
            }
        }
    }

    fun selectTranslateLanguage(language: ReaderTranslateLanguage) {
        viewModelScope.launch {
            _uiState.update { st ->
                st.copy(
                    wordLookup = st.wordLookup?.copy(
                        targetLanguage = language,
                        isCheckingPack = true,
                        translatedText = null,
                        translationError = null
                    )
                )
            }
            val downloaded = readerHelper.isTranslationPackDownloaded(language)
            _uiState.update { st ->
                st.copy(
                    wordLookup = st.wordLookup?.copy(
                        isCheckingPack = false,
                        isLanguagePackDownloaded = downloaded
                    )
                )
            }
        }
    }

    fun downloadTranslatePack() {
        val lang = _uiState.value.wordLookup?.targetLanguage ?: return
        viewModelScope.launch {
            _uiState.update { st ->
                st.copy(wordLookup = st.wordLookup?.copy(isDownloadingPack = true, translationError = null))
            }
            readerHelper.downloadTranslationPack(lang)
                .onSuccess {
                    _uiState.update { st ->
                        st.copy(
                            wordLookup = st.wordLookup?.copy(
                                isDownloadingPack = false,
                                isLanguagePackDownloaded = true
                            )
                        )
                    }
                    _events.emit(ReaderEvent.ShowMessage("${lang.displayName} offline pack ready"))
                }
                .onFailure { err ->
                    _uiState.update { st ->
                        st.copy(
                            wordLookup = st.wordLookup?.copy(
                                isDownloadingPack = false,
                                translationError = err.localizedMessage ?: "Download unavailable offline"
                            )
                        )
                    }
                }
        }
    }

    fun runOfflineTranslation() {
        val lookup = _uiState.value.wordLookup ?: return
        viewModelScope.launch {
            _uiState.update { st ->
                st.copy(wordLookup = st.wordLookup?.copy(isTranslating = true, translationError = null))
            }
            readerHelper.translateOffline(lookup.queryText, lookup.targetLanguage)
                .onSuccess { translated ->
                    _uiState.update { st ->
                        st.copy(
                            wordLookup = st.wordLookup?.copy(
                                isTranslating = false,
                                isLanguagePackDownloaded = true,
                                translatedText = translated
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { st ->
                        st.copy(
                            wordLookup = st.wordLookup?.copy(
                                isTranslating = false,
                                translationError = err.localizedMessage ?: "Offline translation failed"
                            )
                        )
                    }
                }
        }
    }

    fun importOfflineDictionaryPack(uri: Uri) {
        viewModelScope.launch {
            readerHelper.importOfflineDictionaryPack(uri)
                .onSuccess { count ->
                    _events.emit(ReaderEvent.ShowMessage("Loaded $count offline dictionary entries"))
                    _uiState.value.wordLookup?.queryText?.let { updateLookupQuery(it) }
                }
                .onFailure { err ->
                    _events.emit(ReaderEvent.ShowMessage(err.localizedMessage ?: "Could not import dictionary pack"))
                }
        }
    }

    fun dismissWordLookup() {
        _uiState.update { it.copy(wordLookup = null) }
    }

    // TTS Controls
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
            // Start from first sentence on current page if possible
            val segmented = com.example.core.tts.TtsTextSegmenter.segment(fullText)
            val targetPageNum = state.currentPage + 1
            val startIdx = segmented.indexOfFirst { it.pageNumber >= targetPageNum }.coerceAtLeast(0)
            ttsEngine.loadDocument(
                documentId = doc.id,
                title = doc.title,
                text = fullText,
                startSentenceIndex = startIdx
            )
            ttsEngine.play()
        }
    }

    fun setTtsRate(rate: Float) = ttsEngine.setSpeechRate(rate)
    fun setTtsSleepTimer(minutes: Int) = ttsEngine.setSleepTimer(minutes)
    fun nextTtsSentence() = ttsEngine.nextSentence()
    fun prevTtsSentence() = ttsEngine.previousSentence()
    fun stopTts() = ttsEngine.stop()

    // Reader Global vs Per-Book Presets
    fun updateReaderPreset(usePerBook: Boolean, transform: (ReaderBookPreset) -> ReaderBookPreset) {
        val docId = _uiState.value.document?.id
        val current = preferences.value.effectiveReaderPreset(docId)
        val updated = transform(current)
        viewModelScope.launch {
            if (usePerBook && docId != null) {
                preferencesRepository.savePerBookReaderPreset(docId, updated)
            } else {
                preferencesRepository.saveGlobalReaderPreset(updated)
            }
            val needsCrop = updated.autoCropMargins || updated.pdfFitMode == com.example.core.datastore.PdfFitMode.AUTO_CROP
            if (needsCrop) {
                _uiState.value.pdfWorkingFile?.let { file ->
                    preloadPdfWindow(file, _uiState.value.currentPage, _uiState.value.totalPages)
                }
            }
        }
    }

    fun togglePerBookPresetMode(enablePerBook: Boolean) {
        val docId = _uiState.value.document?.id ?: return
        viewModelScope.launch {
            if (enablePerBook) {
                val base = preferences.value.effectiveReaderPreset(docId)
                preferencesRepository.savePerBookReaderPreset(docId, base)
                _events.emit(ReaderEvent.ShowMessage("Using custom preset for this book"))
            } else {
                preferencesRepository.clearPerBookReaderPreset(docId)
                _events.emit(ReaderEvent.ShowMessage("Switched back to global reader settings"))
            }
        }
    }

    private fun startReadingStatsTicker(documentId: String) {
        readingTimerJob?.cancel()
        readingTimerJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                libraryRepository.recordReadingMinute(documentId, pagesReadDelta = 1)
                _uiState.update { it.copy(stats = it.stats.copy(sessionMinutes = it.stats.sessionMinutes + 1)) }
            }
        }
    }

    override fun onCleared() {
        readingTimerJob?.cancel()
        scrollSaveJob?.cancel()
        statsJob?.cancel()
        decryptedTempFile?.delete()
        super.onCleared()
    }
}
