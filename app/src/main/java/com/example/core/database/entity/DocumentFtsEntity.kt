package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = LibraryDocumentEntity::class)
@Entity(tableName = "library_documents_fts")
data class DocumentFtsEntity(
    val title: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "ocr_text")
    val ocrText: String,
    @ColumnInfo(name = "tags_json")
    val tagsJson: String
)
