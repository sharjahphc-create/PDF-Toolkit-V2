package com.example.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.LibraryViewMode
import com.example.core.datastore.PreferencesRepository
import com.example.core.tts.TtsEngine
import com.example.core.tts.TtsPlaybackState
import com.example.data.repository.LibraryRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.DocumentCollection
import com.example.domain.model.LibraryDocument
import com.example.domain.model.LibrarySortOrder
import com.example.domain.model.ReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinueReadingItem(
    val document: LibraryDocument,
    val progress: ReadingProgress
)

data class LibraryUiState(
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val selectedCollectionId: String? = null,
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    val viewMode: LibraryViewMode = LibraryViewMode.COVERS_GRID,
    val categories: List<DocumentCategory> = emptyList(),
    val collections: List<DocumentCollection> = emptyList(),
    val continueReading: List<ContinueReadingItem> = emptyList(),
    val progressByDocId: Map<String, ReadingProgress> = emptyMap(),
    val documents: List<LibraryDocument> = emptyList(),
    val isImporting: Boolean = false,
    val totalTtsMinutes: Int = 0,
    val totalMinutesRead: Int = 0
)

sealed interface LibraryEvent {
    data class ShowMessage(val message: String, val undoDocId: String? = null) : LibraryEvent
    data class LaunchShareIntent(val uri: Uri, val mimeType: String, val title: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val selectedCollectionId = MutableStateFlow<String?>(null)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(LibrarySortOrder.RECENT)
    private val isImporting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    val ttsState: StateFlow<TtsPlaybackState> = ttsEngine.state

    @OptIn(ExperimentalCoroutinesApi::class)
    private val collectionDocIdsFlow = selectedCollectionId.flatMapLatest { colId ->
        if (colId == null) flowOf<Set<String>?>(null)
        else libraryRepository.observeDocumentIdsInCollection(colId).map { it.toSet() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val documentsFlow = combine(
        searchQuery,
        selectedCategoryId,
        sortOrder,
        collectionDocIdsFlow,
        selectedTag
    ) { query, catId, sort, colDocIds, tag ->
        FilterParams(query, catId, sort, colDocIds, tag)
    }.flatMapLatest { params ->
        libraryRepository.observeDocuments(
            query = params.query,
            categoryId = params.categoryId,
            sortOrder = params.sortOrder,
            includeLocked = false
        ).map { docs ->
            val colFiltered = if (params.collectionDocIds == null) docs else docs.filter { it.id in params.collectionDocIds }
            if (params.tag.isNullOrBlank()) colFiltered
            else colFiltered.filter { doc -> doc.tags.any { it.equals(params.tag, ignoreCase = true) } }
        }
    }

    private val libraryViewModeFlow = preferencesRepository.preferences
        .map { it.libraryViewMode }

    private val allUnlockedDocsFlow = libraryRepository.observeDocuments(
        query = "",
        categoryId = null,
        sortOrder = LibrarySortOrder.RECENT,
        includeLocked = false
    )

    private val readingStatsAndProgressFlow = combine(
        allUnlockedDocsFlow,
        libraryRepository.observeAllReadingProgress(),
        libraryRepository.observeTotalTtsMinutes(),
        libraryRepository.observeTotalMinutesRead()
    ) { allDocs, allProgress, ttsMins, readMins ->
        val progressMap = allProgress.associateBy { it.documentId }
        val docMap = allDocs.associateBy { it.id }
        val continueList = allProgress.mapNotNull { prog ->
            val doc = docMap[prog.documentId] ?: return@mapNotNull null
            ContinueReadingItem(document = doc, progress = prog)
        }.take(8)
        val distinctTags = allDocs.flatMap { it.tags }.distinct().sorted()
        StatsBundle(continueList, progressMap, ttsMins, readMins, distinctTags)
    }

    private val filterHeaderFlow = combine(
        searchQuery,
        selectedCategoryId,
        selectedCollectionId,
        selectedTag,
        sortOrder
    ) { q, cat, col, tag, sort ->
        FilterHeader(q, cat, col, tag, sort)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        filterHeaderFlow,
        libraryViewModeFlow,
        libraryRepository.observeCategories(),
        libraryRepository.observeCollections(),
        documentsFlow,
        isImporting,
        readingStatsAndProgressFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val header = values[0] as FilterHeader
        val stats = values[6] as StatsBundle
        LibraryUiState(
            searchQuery = header.query,
            selectedCategoryId = header.categoryId,
            selectedCollectionId = header.collectionId,
            selectedTag = header.tag,
            availableTags = stats.availableTags,
            sortOrder = header.sortOrder,
            viewMode = values[1] as LibraryViewMode,
            categories = values[2] as List<DocumentCategory>,
            collections = values[3] as List<DocumentCollection>,
            documents = values[4] as List<LibraryDocument>,
            isImporting = values[5] as Boolean,
            continueReading = stats.continueReading,
            progressByDocId = stats.progressByDocId,
            totalTtsMinutes = stats.totalTtsMinutes,
            totalMinutesRead = stats.totalMinutesRead
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    init {
        ttsEngine.initializeIfNeeded()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onCategorySelected(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun onCollectionSelected(collectionId: String?) {
        selectedCollectionId.value = collectionId
    }

    fun onTagSelected(tag: String?) {
        selectedTag.value = tag
    }

    fun onSortOrderSelected(order: LibrarySortOrder) {
        sortOrder.value = order
    }

    fun toggleLibraryViewMode() {
        val next = if (uiState.value.viewMode == LibraryViewMode.COVERS_GRID) {
            LibraryViewMode.LIST
        } else {
            LibraryViewMode.COVERS_GRID
        }
        viewModelScope.launch { preferencesRepository.setLibraryViewMode(next) }
    }

    fun createCategory(name: String, iconName: String, colorHex: Long, isLocked: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val created = libraryRepository.createCategory(clean, iconName, colorHex, isLocked)
            if (!isLocked) {
                selectedCategoryId.value = created.id
            }
            val suffix = if (isLocked) " (Locked Vault category)" else ""
            _events.emit(LibraryEvent.ShowMessage("Created category \"${created.name}\"$suffix"))
        }
    }

    fun updateDocumentTags(documentId: String, tags: List<String>) {
        viewModelScope.launch {
            libraryRepository.updateDocumentTags(documentId, tags)
            _events.emit(LibraryEvent.ShowMessage("Updated tags"))
        }
    }

    fun createCollection(name: String, description: String = "") {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val created = libraryRepository.createCollection(clean, description)
            selectedCollectionId.value = created.id
            _events.emit(LibraryEvent.ShowMessage("Created collection \"${created.name}\""))
        }
    }

    fun addDocumentToCollection(document: LibraryDocument, collection: DocumentCollection) {
        viewModelScope.launch {
            libraryRepository.addDocumentToCollection(document.id, collection.id)
            _events.emit(LibraryEvent.ShowMessage("Added \"${document.title}\" to ${collection.name}"))
        }
    }

    fun importDocument(uri: Uri, categoryId: String? = selectedCategoryId.value, deleteOriginal: Boolean = false) {
        viewModelScope.launch {
            isImporting.update { true }
            val result = libraryRepository.importFromUriWithMode(
                uri = uri,
                categoryId = categoryId,
                deleteOriginal = deleteOriginal
            )
            isImporting.update { false }
            result.onSuccess { res ->
                val msg = when {
                    res.document.isLocked && deleteOriginal && res.originalDeleted ->
                        "Moved & encrypted \"${res.document.title}\" in Vault (original deleted)"
                    res.document.isLocked ->
                        "Encrypted \"${res.document.title}\" in Vault"
                    deleteOriginal && res.originalDeleted ->
                        "Moved \"${res.document.title}\" (original deleted)"
                    deleteOriginal ->
                        "Copied \"${res.document.title}\" (provider blocked deleting original)"
                    else ->
                        "Imported \"${res.document.title}\""
                }
                _events.emit(LibraryEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.emit(LibraryEvent.ShowMessage(err.localizedMessage ?: "Failed to import file"))
            }
        }
    }

    fun loadSamplePdf() {
        viewModelScope.launch {
            isImporting.update { true }
            val result = libraryRepository.importBundledSamplePdf()
            isImporting.update { false }
            result.onSuccess { doc ->
                _events.emit(LibraryEvent.ShowMessage("Loaded \"${doc.title}\""))
                listenToDocument(doc)
            }.onFailure { err ->
                _events.emit(LibraryEvent.ShowMessage(err.localizedMessage ?: "Failed to load sample PDF"))
            }
        }
    }

    fun listenToDocument(document: LibraryDocument) {
        viewModelScope.launch {
            val text = libraryRepository.extractSpeakableText(document.id)
            if (text.isBlank()) {
                _events.emit(LibraryEvent.ShowMessage("No readable text found in ${document.title}"))
                return@launch
            }
            val progress = libraryRepository.getReadingProgress(document.id)
            val resumeIndex = progress?.ttsSentenceIndex ?: 0
            ttsEngine.loadDocument(
                documentId = document.id,
                title = document.title,
                text = text,
                startSentenceIndex = resumeIndex
            )
            ttsEngine.play()
        }
    }

    fun moveToCategory(documentId: String, categoryId: String?) {
        viewModelScope.launch {
            val cat = uiState.value.categories.firstOrNull { it.id == categoryId }
            libraryRepository.moveToCategory(documentId, categoryId)
            val msg = if (cat?.isLocked == true) {
                "Encrypted & moved to locked category \"${cat.name}\""
            } else {
                "Moved to ${cat?.name ?: "Library"}"
            }
            _events.emit(LibraryEvent.ShowMessage(msg))
        }
    }

    fun deleteDocument(document: LibraryDocument) {
        viewModelScope.launch {
            if (ttsState.value.documentId == document.id) {
                ttsEngine.stop()
            }
            libraryRepository.softDeleteDocument(document.id)
            _events.emit(LibraryEvent.ShowMessage("Removed \"${document.title}\"", undoDocId = document.id))
        }
    }

    fun undoDelete(documentId: String) {
        viewModelScope.launch {
            libraryRepository.restoreDocument(documentId)
        }
    }

    fun shareDocument(document: LibraryDocument) {
        viewModelScope.launch {
            val uri = libraryRepository.getShareUri(document)
            if (uri != null) {
                _events.emit(LibraryEvent.LaunchShareIntent(uri, document.mimeType, document.title))
            } else {
                _events.emit(LibraryEvent.ShowMessage("Could not prepare file for sharing"))
            }
        }
    }

    fun moveToVault(document: LibraryDocument) {
        viewModelScope.launch {
            if (ttsState.value.documentId == document.id) {
                ttsEngine.stop()
            }
            libraryRepository.moveDocumentToVault(document.id)
                .onSuccess {
                    _events.emit(LibraryEvent.ShowMessage("Encrypted & moved \"${document.title}\" to Vault"))
                }
                .onFailure { err ->
                    _events.emit(LibraryEvent.ShowMessage(err.localizedMessage ?: "Failed to lock document"))
                }
        }
    }

    fun togglePlayPause() {
        when (ttsState.value.status) {
            com.example.core.tts.TtsStatus.SPEAKING -> ttsEngine.pause()
            com.example.core.tts.TtsStatus.PAUSED,
            com.example.core.tts.TtsStatus.READY -> ttsEngine.play()
            else -> ttsEngine.initializeIfNeeded()
        }
    }

    fun stopTts() = ttsEngine.stop()
    fun nextSentence() = ttsEngine.nextSentence()
    fun previousSentence() = ttsEngine.previousSentence()
    fun seekToSentence(index: Int) = ttsEngine.seekToSentence(index)
    fun setSpeechRate(rate: Float) = ttsEngine.setSpeechRate(rate)
    fun setPitch(pitch: Float) = ttsEngine.setPitch(pitch)
    fun selectVoice(voiceName: String) = ttsEngine.selectVoice(voiceName)
    fun setSleepTimer(minutes: Int) = ttsEngine.setSleepTimer(minutes)

    private data class FilterParams(
        val query: String,
        val categoryId: String?,
        val sortOrder: LibrarySortOrder,
        val collectionDocIds: Set<String>?,
        val tag: String?
    )

    private data class FilterHeader(
        val query: String,
        val categoryId: String?,
        val collectionId: String?,
        val tag: String?,
        val sortOrder: LibrarySortOrder
    )

    private data class StatsBundle(
        val continueReading: List<ContinueReadingItem>,
        val progressByDocId: Map<String, ReadingProgress>,
        val totalTtsMinutes: Int,
        val totalMinutesRead: Int,
        val availableTags: List<String>
    )
}
