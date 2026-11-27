package com.example.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.tts.TtsEngine
import com.example.core.tts.TtsPlaybackState
import com.example.data.repository.LibraryRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import com.example.domain.model.LibrarySortOrder
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    val categories: List<DocumentCategory> = emptyList(),
    val documents: List<LibraryDocument> = emptyList(),
    val isImporting: Boolean = false,
    val totalTtsMinutes: Int = 0
)

sealed interface LibraryEvent {
    data class ShowMessage(val message: String, val undoDocId: String? = null) : LibraryEvent
    data class LaunchShareIntent(val uri: Uri, val mimeType: String, val title: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(LibrarySortOrder.RECENT)
    private val isImporting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    val ttsState: StateFlow<TtsPlaybackState> = ttsEngine.state

    @OptIn(ExperimentalCoroutinesApi::class)
    private val documentsFlow = combine(
        searchQuery,
        selectedCategoryId,
        sortOrder
    ) { query, catId, sort ->
        Triple(query, catId, sort)
    }.flatMapLatest { (query, catId, sort) ->
        libraryRepository.observeDocuments(
            query = query,
            categoryId = catId,
            sortOrder = sort,
            includeLocked = false
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        searchQuery,
        selectedCategoryId,
        sortOrder,
        libraryRepository.observeCategories(),
        documentsFlow,
        isImporting,
        libraryRepository.observeTotalTtsMinutes()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LibraryUiState(
            searchQuery = values[0] as String,
            selectedCategoryId = values[1] as String?,
            sortOrder = values[2] as LibrarySortOrder,
            categories = values[3] as List<DocumentCategory>,
            documents = values[4] as List<LibraryDocument>,
            isImporting = values[5] as Boolean,
            totalTtsMinutes = values[6] as Int
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

    fun onSortOrderSelected(order: LibrarySortOrder) {
        sortOrder.value = order
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            isImporting.update { true }
            val result = libraryRepository.importFromUri(uri, selectedCategoryId.value)
            isImporting.update { false }
            result.onSuccess { doc ->
                _events.emit(LibraryEvent.ShowMessage("Imported \"${doc.title}\""))
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
            libraryRepository.moveToCategory(documentId, categoryId)
            _events.emit(LibraryEvent.ShowMessage("Updated document category"))
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
}
