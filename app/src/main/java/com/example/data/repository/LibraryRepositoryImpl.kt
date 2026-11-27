package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.R
import com.example.core.common.IoDispatcher
import com.example.core.database.dao.LibraryDao
import com.example.core.database.dao.ReaderDao
import com.example.core.database.entity.BookmarkEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CollectionEntity
import com.example.core.database.entity.DocumentCollectionCrossRef
import com.example.core.database.entity.HighlightEntity
import com.example.core.database.entity.LibraryDocumentEntity
import com.example.core.database.entity.ReadingProgressEntity
import com.example.core.database.entity.ReadingStatsEntity
import com.example.core.datastore.PreferencesRepository
import com.example.core.security.VaultCryptoManager
import com.example.data.pdf.PdfTextExtractor
import com.example.domain.model.Bookmark
import com.example.domain.model.DocumentCategory
import com.example.domain.model.DocumentCollection
import com.example.domain.model.Highlight
import com.example.domain.model.LibraryDocument
import com.example.domain.model.LibrarySortOrder
import com.example.domain.model.ReadingProgress
import com.example.feature.reader.EpubParser
import com.example.system.PdfToolkitGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val readerDao: ReaderDao,
    private val preferencesRepository: PreferencesRepository,
    private val pdfTextExtractor: PdfTextExtractor,
    private val epubParser: EpubParser,
    private val vaultCryptoManager: VaultCryptoManager,
    private val encryptedBackupManager: EncryptedBackupManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LibraryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeCategories(): Flow<List<DocumentCategory>> =
        libraryDao.observeCategories().map { list -> list.map { it.toDomain() } }

    override fun observeCollections(): Flow<List<DocumentCollection>> =
        libraryDao.observeCollections().map { list -> list.map { it.toDomain() } }

    override fun observeDocumentIdsInCollection(collectionId: String): Flow<List<String>> =
        libraryDao.observeDocumentIdsInCollection(collectionId)

    override fun observeAllReadingProgress(): Flow<List<ReadingProgress>> =
        readerDao.observeAllProgress().map { list -> list.map { it.toDomain() } }

    override fun observeDocuments(
        query: String,
        categoryId: String?,
        sortOrder: LibrarySortOrder,
        includeLocked: Boolean
    ): Flow<List<LibraryDocument>> {
        val trimmedQuery = query.trim()
        val sourceFlow = if (trimmedQuery.isNotEmpty()) {
            val sanitized = sanitizeFtsQuery(trimmedQuery)
            if (sanitized.isEmpty()) {
                libraryDao.observeDocuments(includeLocked)
            } else {
                libraryDao.searchDocumentsFts(sanitized, includeLocked)
            }
        } else if (categoryId != null) {
            libraryDao.observeDocumentsByCategory(categoryId, includeLocked)
        } else {
            libraryDao.observeDocuments(includeLocked)
        }

        return sourceFlow.map { entities ->
            val filtered = if (trimmedQuery.isNotEmpty() && categoryId != null) {
                entities.filter { it.categoryId == categoryId }
            } else {
                entities
            }
            val mapped = filtered.map { it.toDomain() }
            when (sortOrder) {
                LibrarySortOrder.RECENT -> mapped.sortedByDescending { it.lastOpenedAt }
                LibrarySortOrder.TITLE_AZ -> mapped.sortedBy { it.title.lowercase() }
                LibrarySortOrder.SIZE_DESC -> mapped.sortedByDescending { it.fileSizeBytes }
            }
        }
    }

    override fun observeRecentDocuments(limit: Int): Flow<List<LibraryDocument>> =
        libraryDao.observeRecentDocuments(limit).map { list -> list.map { it.toDomain() } }

    override fun observeLockedDocuments(): Flow<List<LibraryDocument>> =
        libraryDao.observeLockedDocuments().map { list -> list.map { it.toDomain() } }

    override fun observeDocumentById(documentId: String): Flow<LibraryDocument?> =
        libraryDao.observeDocumentById(documentId).map { it?.toDomain() }

    override fun observeReadingProgress(documentId: String): Flow<ReadingProgress?> =
        readerDao.observeProgress(documentId).map { it?.toDomain() }

    override fun observeBookmarks(documentId: String): Flow<List<Bookmark>> =
        readerDao.observeBookmarks(documentId).map { list -> list.map { it.toDomain() } }

    override fun observeHighlights(documentId: String): Flow<List<Highlight>> =
        readerDao.observeHighlights(documentId).map { list -> list.map { it.toDomain() } }

    override fun observeTotalTtsMinutes(): Flow<Int> =
        readerDao.observeTotalTtsMinutes()

    override fun observeTotalMinutesRead(): Flow<Int> =
        readerDao.observeTotalMinutesRead()

    override suspend fun getDocumentById(documentId: String): LibraryDocument? =
        withContext(ioDispatcher) { libraryDao.getDocumentById(documentId)?.toDomain() }

    override suspend fun createCategory(
        name: String,
        iconName: String,
        colorHex: Long,
        isLocked: Boolean
    ): DocumentCategory = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val existingCount = libraryDao.getAllCategoriesSync().size
        val entity = CategoryEntity(
            id = "cat_${UUID.randomUUID()}",
            name = name.trim(),
            iconName = iconName.ifBlank { if (isLocked) "lock" else "folder" },
            colorHex = colorHex,
            isLocked = isLocked,
            isSystemDefault = false,
            sortOrder = existingCount + 1,
            updatedAt = now,
            deviceId = deviceId,
            isDeleted = false
        )
        libraryDao.upsertCategory(entity)
        entity.toDomain()
    }

    override suspend fun softDeleteCategory(categoryId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        libraryDao.softDeleteCategory(categoryId, System.currentTimeMillis(), deviceId)
    }

    override suspend fun updateDocumentTags(documentId: String, tags: List<String>) =
        withContext(ioDispatcher) {
            val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            libraryDao.updateDocumentTags(
                documentId = documentId,
                tagsJson = json.encodeToString(cleaned),
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId
            )
        }

    override suspend fun createCollection(name: String, description: String): DocumentCollection =
        withContext(ioDispatcher) {
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            val now = System.currentTimeMillis()
            val entity = CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                updatedAt = now,
                deviceId = deviceId,
                isDeleted = false
            )
            libraryDao.upsertCollection(entity)
            entity.toDomain()
        }

    override suspend fun addDocumentToCollection(documentId: String, collectionId: String) =
        withContext(ioDispatcher) {
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            libraryDao.addDocumentToCollection(
                DocumentCollectionCrossRef(
                    documentId = documentId,
                    collectionId = collectionId,
                    updatedAt = System.currentTimeMillis(),
                    deviceId = deviceId,
                    isDeleted = false
                )
            )
        }

    override suspend fun importFromUri(
        uri: Uri,
        categoryId: String?
    ): Result<LibraryDocument> = importFromUriWithMode(
        uri = uri,
        categoryId = categoryId,
        deleteOriginal = false,
        lockInVault = false
    ).map { it.document }

    override suspend fun importFromUriWithMode(
        uri: Uri,
        categoryId: String?,
        deleteOriginal: Boolean,
        lockInVault: Boolean
    ): Result<ImportDocumentResult> = withContext(ioDispatcher) {
        runCatching {
            val resolver = context.contentResolver
            var displayName = "Document_${System.currentTimeMillis()}"
            var reportedSize = 0L

            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val mimeType = resolver.getType(uri)?.takeIf { it != "application/octet-stream" }
                ?: inferMimeType(displayName)
            val ext = inferExtension(displayName, mimeType)
            val docId = UUID.randomUUID().toString()
            val libraryDir = File(context.filesDir, "library").apply {
                mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }
            val destFile = File(libraryDir, "$docId.$ext")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Unable to open document stream from Storage Access Framework")

            val actualSize = destFile.length().takeIf { it > 0 } ?: reportedSize
            val doc = persistLocalFile(
                docId = docId,
                destFile = destFile,
                fileName = displayName,
                mimeType = mimeType,
                sourceUri = uri.toString(),
                fileSizeBytes = actualSize,
                categoryId = categoryId,
                forceLockInVault = lockInVault
            )

            val deletedOriginal = if (deleteOriginal) {
                runCatching {
                    DocumentsContract.deleteDocument(resolver, uri)
                }.getOrDefault(false)
            } else {
                false
            }
            ImportDocumentResult(document = doc, originalDeleted = deletedOriginal)
        }
    }

    override suspend fun importLocalFile(
        file: File,
        displayName: String,
        mimeType: String,
        categoryId: String?
    ): Result<LibraryDocument> = withContext(ioDispatcher) {
        runCatching {
            val docId = UUID.randomUUID().toString()
            val ext = inferExtension(displayName, mimeType)
            val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
            val destFile = File(libraryDir, "$docId.$ext")
            file.copyTo(destFile, overwrite = true)
            persistLocalFile(
                docId = docId,
                destFile = destFile,
                fileName = displayName,
                mimeType = mimeType,
                sourceUri = null,
                fileSizeBytes = destFile.length(),
                categoryId = categoryId
            )
        }
    }

    override suspend fun importBundledSamplePdf(): Result<LibraryDocument> = withContext(ioDispatcher) {
        runCatching {
            val existing = libraryDao.getDocumentById(SAMPLE_DOC_ID)
            if (existing != null && File(existing.localCachePath).exists()) {
                return@runCatching existing.toDomain()
            }
            val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
            val destFile = File(libraryDir, "$SAMPLE_DOC_ID.pdf")
            context.resources.openRawResource(R.raw.sample_guide).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            persistLocalFile(
                docId = SAMPLE_DOC_ID,
                destFile = destFile,
                fileName = "PDF_Toolkit_Offline_Guide.pdf",
                mimeType = "application/pdf",
                sourceUri = null,
                fileSizeBytes = destFile.length(),
                categoryId = "cat_work"
            )
        }
    }

    private suspend fun persistLocalFile(
        docId: String,
        destFile: File,
        fileName: String,
        mimeType: String,
        sourceUri: String?,
        fileSizeBytes: Long,
        categoryId: String?,
        forceLockInVault: Boolean = false
    ): LibraryDocument {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val isPdf = mimeType == "application/pdf" || destFile.extension.equals("pdf", ignoreCase = true)
        val isEpub = mimeType == "application/epub+zip" || destFile.extension.equals("epub", ignoreCase = true)
        val isDocx = mimeType == DOCX_MIME || destFile.extension.equals("docx", ignoreCase = true)

        val (extractedTitle, pageCount, ocrText, thumbnailPath) = when {
            isPdf -> {
                val meta = pdfTextExtractor.inspectAndExtract(destFile, docId)
                Quad(meta.title, meta.pageCount, meta.extractedText, meta.thumbnailPath)
            }
            isEpub -> {
                val book = epubParser.parse(destFile).getOrNull()
                val text = book?.chapters?.joinToString("\n\n") { "${it.title}\n${it.text}" }.orEmpty()
                Quad(
                    book?.title?.takeIf { it.isNotBlank() },
                    (book?.chapters?.size ?: 1).coerceAtLeast(1),
                    text.take(40_000),
                    null
                )
            }
            isDocx -> {
                val text = extractDocxPlainText(destFile)
                Quad(null, 1, text.take(40_000), null)
            }
            else -> {
                val text = runCatching { destFile.readText().take(25_000) }.getOrDefault("")
                Quad(null, 1, text, null)
            }
        }

        val cleanTitle = extractedTitle?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.').replace('_', ' ')
        val isCategoryLocked = categoryId?.let { libraryDao.getCategoryById(it)?.isLocked } ?: false
        val shouldLock = forceLockInVault || isCategoryLocked

        val finalPath: String
        val finalThumbnailPath: String?
        if (shouldLock) {
            val encryptedFile = vaultCryptoManager.encryptFileToVault(destFile, docId).getOrThrow()
            destFile.delete()
            thumbnailPath?.let { File(it).delete() }
            finalPath = encryptedFile.absolutePath
            finalThumbnailPath = null
        } else {
            finalPath = destFile.absolutePath
            finalThumbnailPath = thumbnailPath
        }

        val entity = LibraryDocumentEntity(
            id = docId,
            title = cleanTitle,
            fileName = fileName,
            mimeType = mimeType,
            sourceUri = sourceUri,
            localCachePath = finalPath,
            pageCount = pageCount,
            fileSizeBytes = fileSizeBytes,
            categoryId = categoryId,
            tagsJson = json.encodeToString(emptyList<String>()),
            ocrText = ocrText,
            thumbnailPath = finalThumbnailPath,
            isLocked = shouldLock,
            isFavorite = false,
            lastOpenedAt = now,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
            isDeleted = false
        )
        libraryDao.upsertDocument(entity)
        PdfToolkitGlanceWidget.requestUpdate(context)
        return entity.toDomain()
    }

    override suspend fun extractSpeakableText(documentId: String): String = withContext(ioDispatcher) {
        val doc = libraryDao.getDocumentById(documentId) ?: return@withContext ""
        val file = if (doc.isLocked && doc.localCachePath.endsWith(".enc")) {
            val temp = File(context.cacheDir, "tts_vault_${doc.id}.tmp")
            vaultCryptoManager.decryptVaultFileToTemp(File(doc.localCachePath), temp).getOrNull() ?: return@withContext doc.ocrText
        } else {
            File(doc.localCachePath)
        }
        val extracted = when {
            doc.mimeType == "application/epub+zip" || doc.fileName.endsWith(".epub", ignoreCase = true) -> {
                epubParser.parse(file).getOrNull()?.chapters?.joinToString("\n\n") { "${it.title}. ${it.text}" }.orEmpty()
            }
            doc.mimeType == DOCX_MIME || doc.fileName.endsWith(".docx", ignoreCase = true) -> {
                extractDocxPlainText(file)
            }
            else -> pdfTextExtractor.extractSpeakableText(file, doc.mimeType)
        }
        if (doc.isLocked && doc.localCachePath.endsWith(".enc")) {
            file.delete()
        }
        if (extracted.isNotBlank()) extracted else doc.ocrText
    }

    override suspend fun getReadingProgress(documentId: String): ReadingProgress? =
        readerDao.getProgress(documentId)?.toDomain()

    override suspend fun updateTtsProgress(
        documentId: String,
        ttsSentenceIndex: Int,
        pageNumber: Int?
    ) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val existing = readerDao.getProgress(documentId)
        val doc = libraryDao.getDocumentById(documentId)
        val now = System.currentTimeMillis()
        val updated = ReadingProgressEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            documentId = documentId,
            currentPage = pageNumber?.let { (it - 1).coerceAtLeast(0) } ?: existing?.currentPage ?: 0,
            totalPages = doc?.pageCount ?: existing?.totalPages ?: 1,
            scrollOffsetFraction = existing?.scrollOffsetFraction ?: 0f,
            ttsSentenceIndex = ttsSentenceIndex.coerceAtLeast(0),
            lastReadDurationSec = existing?.lastReadDurationSec ?: 0L,
            updatedAt = now,
            deviceId = deviceId,
            isDeleted = false
        )
        readerDao.upsertProgress(updated)
    }

    override suspend fun saveReadingProgress(
        documentId: String,
        currentPage: Int,
        totalPages: Int,
        scrollOffsetFraction: Float
    ) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val existing = readerDao.getProgress(documentId)
        val now = System.currentTimeMillis()
        readerDao.upsertProgress(
            ReadingProgressEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                documentId = documentId,
                currentPage = currentPage.coerceAtLeast(0),
                totalPages = totalPages.coerceAtLeast(1),
                scrollOffsetFraction = scrollOffsetFraction.coerceIn(0f, 1f),
                ttsSentenceIndex = existing?.ttsSentenceIndex ?: 0,
                lastReadDurationSec = existing?.lastReadDurationSec ?: 0L,
                updatedAt = now,
                deviceId = deviceId,
                isDeleted = false
            )
        )
        libraryDao.markDocumentOpened(documentId, now, deviceId)
    }

    override suspend fun recordTtsListeningMinute(documentId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val today = LocalDate.now().toString()
        val existing = readerDao.getDailyStats(documentId, today)
        val now = System.currentTimeMillis()
        readerDao.upsertReadingStats(
            ReadingStatsEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                dateIso = today,
                documentId = documentId,
                minutesRead = existing?.minutesRead ?: 0,
                pagesRead = existing?.pagesRead ?: 0,
                ttsMinutesListened = (existing?.ttsMinutesListened ?: 0) + 1,
                updatedAt = now,
                deviceId = deviceId,
                isDeleted = false
            )
        )
    }

    override suspend fun recordReadingMinute(documentId: String, pagesReadDelta: Int) =
        withContext(ioDispatcher) {
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            val today = LocalDate.now().toString()
            val existing = readerDao.getDailyStats(documentId, today)
            val now = System.currentTimeMillis()
            readerDao.upsertReadingStats(
                ReadingStatsEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    dateIso = today,
                    documentId = documentId,
                    minutesRead = (existing?.minutesRead ?: 0) + 1,
                    pagesRead = (existing?.pagesRead ?: 0) + pagesReadDelta.coerceAtLeast(0),
                    ttsMinutesListened = existing?.ttsMinutesListened ?: 0,
                    updatedAt = now,
                    deviceId = deviceId,
                    isDeleted = false
                )
            )
        }

    override suspend fun addBookmark(
        documentId: String,
        pageNumber: Int,
        label: String,
        excerpt: String
    ) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        readerDao.upsertBookmark(
            BookmarkEntity(
                documentId = documentId,
                pageNumber = pageNumber,
                label = label,
                excerpt = excerpt,
                createdAt = now,
                updatedAt = now,
                deviceId = deviceId
            )
        )
    }

    override suspend fun softDeleteBookmark(bookmarkId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        readerDao.softDeleteBookmark(bookmarkId, System.currentTimeMillis(), deviceId)
    }

    override suspend fun addHighlight(
        documentId: String,
        pageNumber: Int,
        colorKey: String,
        quotedText: String,
        noteText: String
    ) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        readerDao.upsertHighlight(
            HighlightEntity(
                documentId = documentId,
                pageNumber = pageNumber,
                colorKey = colorKey,
                quotedText = quotedText,
                noteText = noteText,
                createdAt = now,
                updatedAt = now,
                deviceId = deviceId
            )
        )
    }

    override suspend fun softDeleteHighlight(highlightId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        readerDao.softDeleteHighlight(highlightId, System.currentTimeMillis(), deviceId)
    }

    override suspend fun updateDocumentTitle(documentId: String, newTitle: String) =
        withContext(ioDispatcher) {
            val clean = newTitle.trim()
            if (clean.isEmpty()) return@withContext
            val doc = libraryDao.getDocumentById(documentId) ?: return@withContext
            val ext = doc.fileName.substringAfterLast('.', "pdf")
            val sanitizedName = clean.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".$ext"
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            libraryDao.updateDocumentTitle(
                documentId = documentId,
                title = clean,
                fileName = sanitizedName,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId
            )
        }

    override suspend fun moveToCategory(documentId: String, categoryId: String?) = withContext(ioDispatcher) {
        val doc = libraryDao.getDocumentById(documentId) ?: return@withContext
        val targetLocked = categoryId?.let { libraryDao.getCategoryById(it)?.isLocked } ?: false
        when {
            targetLocked && !doc.isLocked -> {
                moveDocumentToVault(documentId, targetCategoryId = categoryId)
            }
            !targetLocked && doc.isLocked -> {
                unlockDocumentFromVault(documentId, targetCategoryId = categoryId)
            }
            else -> {
                val deviceId = preferencesRepository.getOrCreateDeviceId()
                libraryDao.moveDocumentToCategory(
                    documentId = documentId,
                    categoryId = categoryId,
                    isLocked = doc.isLocked,
                    updatedAt = System.currentTimeMillis(),
                    deviceId = deviceId
                )
            }
        }
    }

    override suspend fun moveDocumentToVault(
        documentId: String,
        targetCategoryId: String?
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val doc = libraryDao.getDocumentById(documentId) ?: error("Document not found")
                val source = File(doc.localCachePath)
                require(source.exists()) { "Source file missing on disk" }
                val encryptedFile = if (source.name.endsWith(".enc")) {
                    source
                } else {
                    val enc = vaultCryptoManager.encryptFileToVault(source, doc.id).getOrThrow()
                    source.delete()
                    enc
                }
                doc.thumbnailPath?.let { File(it).delete() }
                val deviceId = preferencesRepository.getOrCreateDeviceId()
                libraryDao.upsertDocument(
                    doc.copy(
                        localCachePath = encryptedFile.absolutePath,
                        categoryId = targetCategoryId ?: doc.categoryId,
                        thumbnailPath = null,
                        isLocked = true,
                        updatedAt = System.currentTimeMillis(),
                        deviceId = deviceId
                    )
                )
            }
        }

    override suspend fun unlockDocumentFromVault(
        documentId: String,
        targetCategoryId: String?
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val doc = libraryDao.getDocumentById(documentId) ?: error("Document not found")
            val currentFile = File(doc.localCachePath)
            val ext = inferExtension(doc.fileName, doc.mimeType)
            val libraryDir = File(context.filesDir, "library").apply {
                mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }
            val restoredFile = File(libraryDir, "${doc.id}.$ext")
            if (currentFile.exists() && currentFile.name.endsWith(".enc")) {
                vaultCryptoManager.decryptVaultFileToTemp(currentFile, restoredFile).getOrThrow()
                currentFile.delete()
            } else if (currentFile.exists()) {
                currentFile.copyTo(restoredFile, overwrite = true)
            }
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            libraryDao.upsertDocument(
                doc.copy(
                    localCachePath = restoredFile.absolutePath,
                    isLocked = false,
                    categoryId = targetCategoryId ?: doc.categoryId,
                    updatedAt = System.currentTimeMillis(),
                    deviceId = deviceId
                )
            )
        }
    }

    override suspend fun openVaultDecryptedCopy(document: LibraryDocument): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                val src = File(document.localCachePath)
                require(src.exists()) { "Vault file missing" }
                if (!document.isLocked || !src.name.endsWith(".enc")) {
                    return@runCatching src
                }
                val ext = inferExtension(document.fileName, document.mimeType)
                val tempOut = File(context.cacheDir, "vault_preview/${document.id}.$ext")
                vaultCryptoManager.decryptVaultFileToTemp(src, tempOut).getOrThrow()
            }
        }

    override suspend fun exportEncryptedBackup(
        passphrase: String,
        includeVaultFiles: Boolean
    ): Result<ByteArray> = encryptedBackupManager.exportEncryptedBackup(passphrase, includeVaultFiles)

    override suspend fun restoreEncryptedBackup(
        encryptedBytes: ByteArray,
        passphrase: String
    ): Result<Int> = encryptedBackupManager.restoreEncryptedBackup(encryptedBytes, passphrase)

    override suspend fun softDeleteDocument(documentId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        libraryDao.softDeleteDocument(documentId, System.currentTimeMillis(), deviceId)
    }

    override suspend fun restoreDocument(documentId: String) = withContext(ioDispatcher) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        libraryDao.restoreDocument(documentId, System.currentTimeMillis(), deviceId)
    }

    override suspend fun getShareUri(document: LibraryDocument): Uri? = withContext(ioDispatcher) {
        if (document.isLocked) return@withContext null
        val file = File(document.localCachePath)
        if (!file.exists()) return@withContext null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun extractDocxPlainText(file: File): String {
        return runCatching {
            FileInputStream(file).use { fis ->
                XWPFDocument(fis).use { doc ->
                    doc.paragraphs.joinToString("\n\n") { it.text.orEmpty() }.trim()
                }
            }
        }.getOrDefault("")
    }

    private fun sanitizeFtsQuery(raw: String): String {
        val tokens = raw
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^A-Za-z0-9_]"), "") }
            .filter { it.isNotEmpty() }
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun inferMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "epub" -> "application/epub+zip"
        "docx" -> DOCX_MIME
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }

    private fun inferExtension(name: String, mimeType: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.isNotEmpty() && fromName.length <= 5) return fromName
        return when (mimeType) {
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/epub+zip" -> "epub"
            DOCX_MIME -> "docx"
            else -> "bin"
        }
    }

    private fun CategoryEntity.toDomain() = DocumentCategory(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isLocked = isLocked,
        isSystemDefault = isSystemDefault,
        sortOrder = sortOrder
    )

    private fun CollectionEntity.toDomain() = DocumentCollection(
        id = id,
        name = name,
        description = description,
        updatedAt = updatedAt
    )

    private fun LibraryDocumentEntity.toDomain(): LibraryDocument {
        val parsedTags = runCatching { json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList())
        return LibraryDocument(
            id = id,
            title = title,
            fileName = fileName,
            mimeType = mimeType,
            sourceUri = sourceUri,
            localCachePath = localCachePath,
            pageCount = pageCount,
            fileSizeBytes = fileSizeBytes,
            categoryId = categoryId,
            tags = parsedTags,
            ocrText = ocrText,
            thumbnailPath = thumbnailPath,
            isLocked = isLocked,
            isFavorite = isFavorite,
            lastOpenedAt = lastOpenedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ReadingProgressEntity.toDomain() = ReadingProgress(
        documentId = documentId,
        currentPage = currentPage,
        totalPages = totalPages,
        scrollOffsetFraction = scrollOffsetFraction,
        ttsSentenceIndex = ttsSentenceIndex,
        updatedAt = updatedAt
    )

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id,
        documentId = documentId,
        pageNumber = pageNumber,
        label = label,
        excerpt = excerpt,
        createdAt = createdAt
    )

    private fun HighlightEntity.toDomain() = Highlight(
        id = id,
        documentId = documentId,
        pageNumber = pageNumber,
        colorKey = colorKey,
        quotedText = quotedText,
        noteText = noteText,
        createdAt = createdAt
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        const val SAMPLE_DOC_ID = "00000000-0000-4000-8000-000000000001"
        private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
