package com.example.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class BackupCategoryRecord(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val isLocked: Boolean,
    val isSystemDefault: Boolean,
    val sortOrder: Int,
    val updatedAt: Long,
    val deviceId: String
)

@Serializable
data class BackupDocumentRecord(
    val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sourceUri: String? = null,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val categoryId: String? = null,
    val tagsJson: String = "[]",
    val ocrText: String = "",
    val isLocked: Boolean = false,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val archiveEntryName: String
)

@Serializable
data class BackupBookmarkRecord(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val label: String,
    val excerpt: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String
)

@Serializable
data class BackupHighlightRecord(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val colorKey: String,
    val quotedText: String,
    val noteText: String,
    val rectsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String
)

@Serializable
data class BackupNoteRecord(
    val id: String,
    val title: String,
    val content: String,
    val noteType: String,
    val checklistJson: String,
    val isPinned: Boolean,
    val colorKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String
)

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<BackupCategoryRecord>,
    val documents: List<BackupDocumentRecord>,
    val bookmarks: List<BackupBookmarkRecord>,
    val highlights: List<BackupHighlightRecord>,
    val notes: List<BackupNoteRecord>
)
