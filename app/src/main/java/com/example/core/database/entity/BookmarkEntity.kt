package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bookmarks",
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
data class BookmarkEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    val label: String = "",
    val excerpt: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_id")
    val deviceId: String = "",
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
