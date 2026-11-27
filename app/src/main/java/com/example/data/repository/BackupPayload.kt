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
data class BackupSignatureRecord(
    val id: String,
    val label: String,
    val kind: String,
    val createdAt: Long,
    val archiveEntryName: String
)

@Serializable
data class BackupSettingsRecord(
    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val toolsViewMode: String = "GRID_2",
    val toolsSortMode: String = "DEFAULT",
    val libraryViewMode: String = "COVERS_GRID",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsDefaultSleepMinutes: Int = 0,
    val hideLockedThumbnails: Boolean = true,
    val vaultAutoLockMinutes: Int = 1,
    val readerTheme: String = "PAPER",
    val readerFontChoice: String = "SERIF",
    val readerFontScale: Float = 1.0f,
    val appLocaleTag: String = ""
)

@Serializable
data class BackupManifest(
    val version: Int = 2,
    val exportedAt: Long,
    val includesVaultFiles: Boolean = true,
    val categories: List<BackupCategoryRecord>,
    val documents: List<BackupDocumentRecord>,
    val bookmarks: List<BackupBookmarkRecord>,
    val highlights: List<BackupHighlightRecord>,
    val notes: List<BackupNoteRecord>,
    val signatures: List<BackupSignatureRecord> = emptyList(),
    val settings: BackupSettingsRecord? = null
)
