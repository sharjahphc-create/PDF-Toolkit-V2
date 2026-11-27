package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reading_stats",
    foreignKeys = [
        ForeignKey(
            entity = LibraryDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id", "date_iso"], unique = true)
    ]
)
data class ReadingStatsEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "date_iso")
    val dateIso: String,
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "minutes_read")
    val minutesRead: Int = 0,
    @ColumnInfo(name = "pages_read")
    val pagesRead: Int = 0,
    @ColumnInfo(name = "tts_minutes_listened")
    val ttsMinutesListened: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
