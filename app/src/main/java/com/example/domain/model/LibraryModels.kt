package com.example.domain.model

enum class LibrarySortOrder {
    RECENT,
    TITLE_AZ,
    SIZE_DESC
}

data class DocumentCategory(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val isLocked: Boolean,
    val isSystemDefault: Boolean,
    val sortOrder: Int
)

data class LibraryDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sourceUri: String?,
    val localCachePath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val categoryId: String?,
    val tags: List<String>,
    val ocrText: String,
    val thumbnailPath: String?,
    val isLocked: Boolean,
    val isFavorite: Boolean,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

data class ReadingProgress(
    val documentId: String,
    val currentPage: Int,
    val totalPages: Int,
    val scrollOffsetFraction: Float,
    val ttsSentenceIndex: Int,
    val updatedAt: Long
)

data class Bookmark(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val label: String,
    val excerpt: String,
    val createdAt: Long
)

data class Highlight(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val colorKey: String,
    val quotedText: String,
    val noteText: String,
    val createdAt: Long
)
