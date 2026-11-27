package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = LibraryDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"])
    ]
)
data class HighlightEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "color_key")
    val colorKey: String = "yellow",
    @ColumnInfo(name = "quoted_text")
    val quotedText: String,
    @ColumnInfo(name = "note_text")
    val noteText: String = "",
    @ColumnInfo(name = "rects_json")
    val rectsJson: String = "[]",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
