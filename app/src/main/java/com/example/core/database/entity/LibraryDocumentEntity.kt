package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "library_documents",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["last_opened_at"]),
        Index(value = ["is_deleted"])
    ]
)
data class LibraryDocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "source_uri")
    val sourceUri: String? = null,
    @ColumnInfo(name = "local_cache_path")
    val localCachePath: String,
    @ColumnInfo(name = "page_count")
    val pageCount: Int = 1,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",
    @ColumnInfo(name = "ocr_text")
    val ocrText: String = "",
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
