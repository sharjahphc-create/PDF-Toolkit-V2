package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = LibraryDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"], unique = true)
    ]
)
data class ReadingProgressEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "current_page")
    val currentPage: Int = 0,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 1,
    @ColumnInfo(name = "scroll_offset_fraction")
    val scrollOffsetFraction: Float = 0f,
    @ColumnInfo(name = "tts_sentence_index")
    val ttsSentenceIndex: Int = 0,
    @ColumnInfo(name = "last_read_duration_sec")
    val lastReadDurationSec: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
