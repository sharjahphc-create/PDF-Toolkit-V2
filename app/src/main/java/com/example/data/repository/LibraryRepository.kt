package com.example.data.repository

import android.net.Uri
import com.example.domain.model.Bookmark
import com.example.domain.model.DocumentCategory
import com.example.domain.model.DocumentCollection
import com.example.domain.model.Highlight
import com.example.domain.model.LibraryDocument
import com.example.domain.model.LibrarySortOrder
import com.example.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

data class ImportDocumentResult(
    val document: LibraryDocument,
    val originalDeleted: Boolean
)

interface LibraryRepository {
    fun observeCategories(): Flow<List<DocumentCategory>>
    fun observeCollections(): Flow<List<DocumentCollection>>
    fun observeDocumentIdsInCollection(collectionId: String): Flow<List<String>>
    fun observeAllReadingProgress(): Flow<List<ReadingProgress>>
    fun observeDocuments(
        query: String = "",
        categoryId: String? = null,
        sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
        includeLocked: Boolean = false
    ): Flow<List<LibraryDocument>>

    fun observeRecentDocuments(limit: Int = 10): Flow<List<LibraryDocument>>
    fun observeLockedDocuments(): Flow<List<LibraryDocument>>
    fun observeDocumentById(documentId: String): Flow<LibraryDocument?>
    fun observeReadingProgress(documentId: String): Flow<ReadingProgress?>
    fun observeBookmarks(documentId: String): Flow<List<Bookmark>>
    fun observeHighlights(documentId: String): Flow<List<Highlight>>
    fun observeTotalTtsMinutes(): Flow<Int>
    fun observeTotalMinutesRead(): Flow<Int>

    suspend fun getDocumentById(documentId: String): LibraryDocument?
    suspend fun createCategory(
        name: String,
        iconName: String,
        colorHex: Long,
        isLocked: Boolean
    ): DocumentCategory
    suspend fun softDeleteCategory(categoryId: String)
    suspend fun updateDocumentTags(documentId: String, tags: List<String>)
    suspend fun createCollection(name: String, description: String = ""): DocumentCollection
    suspend fun addDocumentToCollection(documentId: String, collectionId: String)
    suspend fun importFromUri(uri: Uri, categoryId: String? = null): Result<LibraryDocument>
    suspend fun importFromUriWithMode(
        uri: Uri,
        categoryId: String? = null,
        deleteOriginal: Boolean = false,
        lockInVault: Boolean = false
    ): Result<ImportDocumentResult>
    suspend fun importLocalFile(
        file: File,
        displayName: String,
        mimeType: String,
        categoryId: String? = null
    ): Result<LibraryDocument>
    suspend fun importBundledSamplePdf(): Result<LibraryDocument>
    suspend fun extractSpeakableText(documentId: String): String
    suspend fun getReadingProgress(documentId: String): ReadingProgress?
    suspend fun updateTtsProgress(documentId: String, ttsSentenceIndex: Int, pageNumber: Int?)
    suspend fun saveReadingProgress(documentId: String, currentPage: Int, totalPages: Int, scrollOffsetFraction: Float)
    suspend fun recordTtsListeningMinute(documentId: String)
    suspend fun recordReadingMinute(documentId: String, pagesReadDelta: Int)
    suspend fun addBookmark(documentId: String, pageNumber: Int, label: String, excerpt: String = "")
    suspend fun softDeleteBookmark(bookmarkId: String)
    suspend fun addHighlight(documentId: String, pageNumber: Int, colorKey: String, quotedText: String, noteText: String = "")
    suspend fun softDeleteHighlight(highlightId: String)
    suspend fun updateDocumentTitle(documentId: String, newTitle: String)
    suspend fun moveToCategory(documentId: String, categoryId: String?)
    suspend fun moveDocumentToVault(documentId: String, targetCategoryId: String? = null): Result<Unit>
    suspend fun unlockDocumentFromVault(documentId: String, targetCategoryId: String? = null): Result<Unit>
    suspend fun openVaultDecryptedCopy(document: LibraryDocument): Result<File>
    suspend fun exportEncryptedBackup(
        passphrase: String,
        includeVaultFiles: Boolean = true
    ): Result<ByteArray>
    suspend fun restoreEncryptedBackup(encryptedBytes: ByteArray, passphrase: String): Result<Int>
    suspend fun softDeleteDocument(documentId: String)
    suspend fun restoreDocument(documentId: String)
    suspend fun getShareUri(document: LibraryDocument): Uri?
}
