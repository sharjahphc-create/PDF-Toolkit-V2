package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class NoteType {
    TEXT,
    CHECKLIST,
    SHOPPING,
    VOICE
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    @ColumnInfo(name = "note_type")
    val noteType: NoteType = NoteType.TEXT,
    @ColumnInfo(name = "checklist_json")
    val checklistJson: String = "[]",
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "color_key")
    val colorKey: String = "default",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)

@Entity(tableName = "feedback_drafts")
data class FeedbackDraftEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "feedback_type")
    val feedbackType: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "include_diagnostics")
    val includeDiagnostics: Boolean = false,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
