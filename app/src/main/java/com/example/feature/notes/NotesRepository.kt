package com.example.feature.notes

import com.example.core.database.dao.NoteDao
import com.example.core.database.entity.NoteEntity
import com.example.core.database.entity.NoteType
import com.example.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class NoteModel(
    val id: String,
    val title: String,
    val content: String,
    val noteType: NoteType,
    val checklistItems: List<ChecklistItem>,
    val isPinned: Boolean,
    val colorKey: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Singleton
class NotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val preferencesRepository: PreferencesRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeNotes(): Flow<List<NoteModel>> = noteDao.observeNotes().map { list ->
        list.map { entity -> entity.toModel() }
    }

    suspend fun saveNote(
        id: String? = null,
        title: String,
        content: String,
        noteType: NoteType,
        checklistItems: List<ChecklistItem>,
        isPinned: Boolean,
        colorKey: String = "default",
        existingCreatedAt: Long? = null
    ): NoteModel {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val noteId = id ?: UUID.randomUUID().toString()
        val encodedChecklist = json.encodeToString(checklistItems)
        val entity = NoteEntity(
            id = noteId,
            title = title.trim().ifBlank { defaultTitleFor(noteType) },
            content = content,
            noteType = noteType,
            checklistJson = encodedChecklist,
            isPinned = isPinned,
            colorKey = colorKey,
            createdAt = existingCreatedAt ?: now,
            updatedAt = now,
            deviceId = deviceId,
            isDeleted = false
        )
        noteDao.upsertNote(entity)
        return entity.toModel()
    }

    suspend fun togglePin(note: NoteModel) {
        saveNote(
            id = note.id,
            title = note.title,
            content = note.content,
            noteType = note.noteType,
            checklistItems = note.checklistItems,
            isPinned = !note.isPinned,
            colorKey = note.colorKey,
            existingCreatedAt = note.createdAt
        )
    }

    suspend fun toggleChecklistItem(note: NoteModel, itemId: String) {
        val updated = note.checklistItems.map { item ->
            if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
        }
        saveNote(
            id = note.id,
            title = note.title,
            content = note.content,
            noteType = note.noteType,
            checklistItems = updated,
            isPinned = note.isPinned,
            colorKey = note.colorKey,
            existingCreatedAt = note.createdAt
        )
    }

    suspend fun softDelete(noteId: String) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        noteDao.softDeleteNote(noteId, System.currentTimeMillis(), deviceId)
    }

    suspend fun restoreNote(note: NoteModel) {
        saveNote(
            id = note.id,
            title = note.title,
            content = note.content,
            noteType = note.noteType,
            checklistItems = note.checklistItems,
            isPinned = note.isPinned,
            colorKey = note.colorKey,
            existingCreatedAt = note.createdAt
        )
    }

    private fun NoteEntity.toModel(): NoteModel {
        val items = runCatching {
            json.decodeFromString<List<ChecklistItem>>(checklistJson)
        }.getOrDefault(emptyList())
        return NoteModel(
            id = id,
            title = title,
            content = content,
            noteType = noteType,
            checklistItems = items,
            isPinned = isPinned,
            colorKey = colorKey,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun defaultTitleFor(type: NoteType): String = when (type) {
        NoteType.TEXT -> "Untitled Note"
        NoteType.CHECKLIST -> "To-Do List"
        NoteType.SHOPPING -> "Shopping List"
        NoteType.VOICE -> "Voice Memo"
    }
}
