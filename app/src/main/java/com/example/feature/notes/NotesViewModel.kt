package com.example.feature.notes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.NoteType
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ToolUsageRepository
import com.example.feature.documents.engine.DocxDocumentModel
import com.example.feature.documents.engine.DocxParagraphItem
import com.example.feature.documents.engine.OfficeDocumentEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class NoteEditorDraft(
    val existingId: String? = null,
    val existingCreatedAt: Long? = null,
    val title: String = "",
    val content: String = "",
    val noteType: NoteType = NoteType.TEXT,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val isPinned: Boolean = false,
    val colorKey: String = "default"
)

sealed interface NotesEvent {
    data class ShowMessage(val message: String, val undoNote: NoteModel? = null) : NotesEvent
    data class ShareText(val title: String, val body: String) : NotesEvent
    data class SharePdf(val uri: Uri, val title: String) : NotesEvent
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notesRepository: NotesRepository,
    private val officeEngine: OfficeDocumentEngine,
    private val libraryRepository: LibraryRepository,
    private val toolUsageRepository: ToolUsageRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<NoteType?>(null)
    val filterType: StateFlow<NoteType?> = _filterType.asStateFlow()

    private val _editorDraft = MutableStateFlow<NoteEditorDraft?>(null)
    val editorDraft: StateFlow<NoteEditorDraft?> = _editorDraft.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialVoiceText = MutableStateFlow("")
    val partialVoiceText: StateFlow<String> = _partialVoiceText.asStateFlow()

    private val _events = MutableSharedFlow<NotesEvent>()
    val events: SharedFlow<NotesEvent> = _events.asSharedFlow()

    val notes: StateFlow<List<NoteModel>> = combine(
        notesRepository.observeNotes(),
        _searchQuery,
        _filterType
    ) { all, query, typeFilter ->
        val q = query.trim().lowercase()
        all.filter { note ->
            val matchesType = typeFilter == null || note.noteType == typeFilter
            val matchesQuery = q.isEmpty() ||
                note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.checklistItems.any { it.text.lowercase().contains(q) }
            matchesType && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: NoteType?) {
        _filterType.value = type
    }

    fun startNewNote(type: NoteType) {
        val defaultItems = when (type) {
            NoteType.CHECKLIST -> listOf(ChecklistItem(text = ""))
            NoteType.SHOPPING -> listOf(ChecklistItem(text = ""))
            else -> emptyList()
        }
        _editorDraft.value = NoteEditorDraft(
            noteType = type,
            checklistItems = defaultItems
        )
    }

    fun openExistingNote(note: NoteModel) {
        _editorDraft.value = NoteEditorDraft(
            existingId = note.id,
            existingCreatedAt = note.createdAt,
            title = note.title,
            content = note.content,
            noteType = note.noteType,
            checklistItems = note.checklistItems,
            isPinned = note.isPinned,
            colorKey = note.colorKey
        )
    }

    fun closeEditor() {
        _isListening.value = false
        _partialVoiceText.value = ""
        _editorDraft.value = null
    }

    fun updateDraftTitle(title: String) {
        _editorDraft.update { it?.copy(title = title) }
    }

    fun updateDraftContent(content: String) {
        _editorDraft.update { it?.copy(content = content) }
    }

    fun updateDraftColor(colorKey: String) {
        _editorDraft.update { it?.copy(colorKey = colorKey) }
    }

    fun toggleDraftPinned() {
        _editorDraft.update { it?.copy(isPinned = !it.isPinned) }
    }

    fun addChecklistItem(text: String = "") {
        _editorDraft.update { draft ->
            draft ?: return@update null
            draft.copy(checklistItems = draft.checklistItems + ChecklistItem(text = text))
        }
    }

    fun updateChecklistItemText(itemId: String, text: String) {
        _editorDraft.update { draft ->
            draft ?: return@update null
            draft.copy(
                checklistItems = draft.checklistItems.map {
                    if (it.id == itemId) it.copy(text = text) else it
                }
            )
        }
    }

    fun toggleDraftChecklistItem(itemId: String) {
        _editorDraft.update { draft ->
            draft ?: return@update null
            draft.copy(
                checklistItems = draft.checklistItems.map {
                    if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it
                }
            )
        }
    }

    fun removeDraftChecklistItem(itemId: String) {
        _editorDraft.update { draft ->
            draft ?: return@update null
            draft.copy(checklistItems = draft.checklistItems.filterNot { it.id == itemId })
        }
    }

    fun setVoiceListening(listening: Boolean, partial: String = "") {
        _isListening.value = listening
        _partialVoiceText.value = partial
    }

    fun appendDictatedText(spoken: String) {
        val clean = spoken.trim()
        if (clean.isEmpty()) return
        _editorDraft.update { draft ->
            draft ?: return@update null
            if (draft.noteType == NoteType.CHECKLIST || draft.noteType == NoteType.SHOPPING) {
                draft.copy(checklistItems = draft.checklistItems + ChecklistItem(text = clean))
            } else {
                val sep = if (draft.content.isBlank()) "" else "\n"
                draft.copy(content = "${draft.content}$sep$clean")
            }
        }
    }

    fun saveDraft() {
        val draft = _editorDraft.value ?: return
        val cleanedItems = draft.checklistItems.filter { it.text.isNotBlank() }
        viewModelScope.launch {
            notesRepository.saveNote(
                id = draft.existingId,
                title = draft.title,
                content = draft.content,
                noteType = draft.noteType,
                checklistItems = cleanedItems,
                isPinned = draft.isPinned,
                colorKey = draft.colorKey,
                existingCreatedAt = draft.existingCreatedAt
            )
            toolUsageRepository.recordToolLaunch("notes")
            _editorDraft.value = null
            _isListening.value = false
            _partialVoiceText.value = ""
        }
    }

    fun togglePin(note: NoteModel) {
        viewModelScope.launch { notesRepository.togglePin(note) }
    }

    fun toggleItemChecked(note: NoteModel, itemId: String) {
        viewModelScope.launch { notesRepository.toggleChecklistItem(note, itemId) }
    }

    fun deleteNote(note: NoteModel) {
        viewModelScope.launch {
            notesRepository.softDelete(note.id)
            _events.emit(NotesEvent.ShowMessage("Note deleted", undoNote = note))
        }
    }

    fun restoreDeletedNote(note: NoteModel) {
        viewModelScope.launch {
            notesRepository.restoreNote(note)
        }
    }

    fun shareNoteText(note: NoteModel) {
        val body = buildString {
            appendLine(note.title)
            appendLine()
            if (note.content.isNotBlank()) {
                appendLine(note.content)
            }
            note.checklistItems.forEach { item ->
                appendLine("${if (item.isChecked) "[x]" else "[ ]"} ${item.text}")
            }
        }.trim()
        viewModelScope.launch {
            _events.emit(NotesEvent.ShareText(note.title, body))
        }
    }

    fun exportNoteToPdf(note: NoteModel) {
        viewModelScope.launch {
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val safeName = note.title.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Note" }
            val out = File(exportDir, "${safeName}_${System.currentTimeMillis()}.pdf")

            val paragraphs = buildList {
                add(DocxParagraphItem(text = note.title, isHeading = true, isBold = true))
                if (note.content.isNotBlank()) {
                    note.content.split('\n').forEach { line ->
                        add(DocxParagraphItem(text = line))
                    }
                }
                note.checklistItems.forEach { item ->
                    val prefix = if (item.isChecked) "[Done]" else "[ ]"
                    add(DocxParagraphItem(text = "$prefix ${item.text}"))
                }
            }
            val model = DocxDocumentModel(file = null, displayName = "${safeName}.docx", paragraphs = paragraphs)
            officeEngine.exportDocxToPdf(model, out).onSuccess { pdfFile ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                libraryRepository.importFromUri(uri)
                _events.emit(NotesEvent.SharePdf(uri, pdfFile.name))
            }.onFailure { err ->
                _events.emit(NotesEvent.ShowMessage(err.localizedMessage ?: "PDF export failed"))
            }
        }
    }
}
