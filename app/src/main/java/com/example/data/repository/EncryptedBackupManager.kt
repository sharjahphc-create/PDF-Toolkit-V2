package com.example.data.repository

import android.content.Context
import com.example.core.common.IoDispatcher
import com.example.core.database.dao.LibraryDao
import com.example.core.database.dao.NoteDao
import com.example.core.database.dao.ReaderDao
import com.example.core.database.entity.BookmarkEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.HighlightEntity
import com.example.core.database.entity.LibraryDocumentEntity
import com.example.core.database.entity.NoteEntity
import com.example.core.database.entity.NoteType
import com.example.core.datastore.LibraryViewMode
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderThemeMode
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.ToolsSortMode
import com.example.core.datastore.ToolsViewMode
import com.example.core.security.BiometricSignatureVault
import com.example.core.security.VaultCryptoManager
import com.example.core.security.VaultSignatureMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val readerDao: ReaderDao,
    private val noteDao: NoteDao,
    private val preferencesRepository: PreferencesRepository,
    private val vaultCryptoManager: VaultCryptoManager,
    private val signatureVault: BiometricSignatureVault,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportEncryptedBackup(
        passphrase: String,
        includeVaultFiles: Boolean = true
    ): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            require(passphrase.length >= 4) { "Passphrase must be at least 4 characters" }

            val categories = libraryDao.getAllCategoriesSync()
            val allDocuments = libraryDao.getAllDocumentsSync()
            val documents = if (includeVaultFiles) {
                allDocuments
            } else {
                allDocuments.filterNot { it.isLocked }
            }
            val includedDocIds = documents.map { it.id }.toSet()
            val bookmarks = readerDao.getAllBookmarksSync().filter { it.documentId in includedDocIds }
            val highlights = readerDao.getAllHighlightsSync().filter { it.documentId in includedDocIds }
            val notes = noteDao.getAllNotesSync()
            val signatures = signatureVault.loadAllEntries()
            val prefs = preferencesRepository.preferences.first()

            val docRecords = mutableListOf<BackupDocumentRecord>()
            val sigRecords = mutableListOf<BackupSignatureRecord>()

            val zipBytes = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zos ->
                    for (doc in documents) {
                        val file = File(doc.localCachePath)
                        val ext = inferExtension(doc.fileName, doc.mimeType)
                        // Portable v2 archive always stores decrypted plaintext inside the
                        // passphrase-encrypted ZIP so restoring on a new device/reinstall works.
                        val entryName = "files/${doc.id}.$ext"
                        if (file.exists()) {
                            if (doc.isLocked && file.name.endsWith(".enc")) {
                                val tempPlain = File(context.cacheDir, "backup_tmp_${doc.id}.$ext")
                                try {
                                    vaultCryptoManager.decryptVaultFileToTemp(file, tempPlain).getOrThrow()
                                    zos.putNextEntry(ZipEntry(entryName))
                                    tempPlain.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                } finally {
                                    tempPlain.delete()
                                }
                            } else {
                                zos.putNextEntry(ZipEntry(entryName))
                                file.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                        docRecords.add(
                            BackupDocumentRecord(
                                id = doc.id,
                                title = doc.title,
                                fileName = doc.fileName,
                                mimeType = doc.mimeType,
                                sourceUri = doc.sourceUri,
                                pageCount = doc.pageCount,
                                fileSizeBytes = doc.fileSizeBytes,
                                categoryId = doc.categoryId,
                                tagsJson = doc.tagsJson,
                                ocrText = doc.ocrText,
                                isLocked = doc.isLocked,
                                isFavorite = doc.isFavorite,
                                lastOpenedAt = doc.lastOpenedAt,
                                createdAt = doc.createdAt,
                                updatedAt = doc.updatedAt,
                                deviceId = doc.deviceId,
                                archiveEntryName = entryName
                            )
                        )
                    }

                    for (sig in signatures) {
                        val safeId = sig.metadata.id.replace(Regex("[^A-Za-z0-9_-]"), "")
                        if (safeId.isBlank()) continue
                        val entryName = "signatures/$safeId.png"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.write(sig.pngBytes)
                        zos.closeEntry()
                        sigRecords.add(
                            BackupSignatureRecord(
                                id = safeId,
                                label = sig.metadata.label,
                                kind = sig.metadata.kind,
                                createdAt = sig.metadata.createdAt,
                                archiveEntryName = entryName
                            )
                        )
                    }

                    val settingsSnapshot = BackupSettingsRecord(
                        themeMode = prefs.themeMode.name,
                        dynamicColor = prefs.dynamicColor,
                        highContrast = prefs.highContrast,
                        reduceMotion = prefs.reduceMotion,
                        toolsViewMode = prefs.toolsViewMode.name,
                        toolsSortMode = prefs.toolsSortMode.name,
                        libraryViewMode = prefs.libraryViewMode.name,
                        ttsSpeechRate = prefs.ttsSpeechRate,
                        ttsPitch = prefs.ttsPitch,
                        ttsDefaultSleepMinutes = prefs.ttsDefaultSleepMinutes,
                        hideLockedThumbnails = prefs.hideLockedThumbnails,
                        vaultAutoLockMinutes = prefs.vaultAutoLockMinutes,
                        readerTheme = prefs.readerTheme.name,
                        readerFontChoice = prefs.readerFontChoice.name,
                        readerFontScale = prefs.readerFontScale,
                        appLocaleTag = prefs.appLocaleTag
                    )

                    val manifest = BackupManifest(
                        version = 2,
                        exportedAt = System.currentTimeMillis(),
                        includesVaultFiles = includeVaultFiles,
                        categories = categories.map {
                            BackupCategoryRecord(
                                id = it.id,
                                name = it.name,
                                iconName = it.iconName,
                                colorHex = it.colorHex,
                                isLocked = it.isLocked,
                                isSystemDefault = it.isSystemDefault,
                                sortOrder = it.sortOrder,
                                updatedAt = it.updatedAt,
                                deviceId = it.deviceId
                            )
                        },
                        documents = docRecords,
                        bookmarks = bookmarks.map {
                            BackupBookmarkRecord(
                                id = it.id,
                                documentId = it.documentId,
                                pageNumber = it.pageNumber,
                                label = it.label,
                                excerpt = it.excerpt,
                                createdAt = it.createdAt,
                                updatedAt = it.updatedAt,
                                deviceId = it.deviceId
                            )
                        },
                        highlights = highlights.map {
                            BackupHighlightRecord(
                                id = it.id,
                                documentId = it.documentId,
                                pageNumber = it.pageNumber,
                                colorKey = it.colorKey,
                                quotedText = it.quotedText,
                                noteText = it.noteText,
                                rectsJson = it.rectsJson,
                                createdAt = it.createdAt,
                                updatedAt = it.updatedAt,
                                deviceId = it.deviceId
                            )
                        },
                        notes = notes.map {
                            BackupNoteRecord(
                                id = it.id,
                                title = it.title,
                                content = it.content,
                                noteType = it.noteType.name,
                                checklistJson = it.checklistJson,
                                isPinned = it.isPinned,
                                colorKey = it.colorKey,
                                createdAt = it.createdAt,
                                updatedAt = it.updatedAt,
                                deviceId = it.deviceId
                            )
                        },
                        signatures = sigRecords,
                        settings = settingsSnapshot
                    )
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                baos.toByteArray()
            }
            vaultCryptoManager.encryptBackupBytes(zipBytes, passphrase)
        }
    }

    suspend fun restoreEncryptedBackup(
        encryptedBytes: ByteArray,
        passphrase: String
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val zipBytes = vaultCryptoManager.decryptBackupBytes(encryptedBytes, passphrase).getOrThrow()
            var manifest: BackupManifest? = null
            val restoredFiles = mutableMapOf<String, ByteArray>()
            val restoredSignatures = mutableMapOf<String, ByteArray>()

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // Zip Slip protection: reject any traversal or absolute path
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val data = zis.readBytes()
                    when {
                        name == "manifest.json" -> {
                            manifest = json.decodeFromString<BackupManifest>(data.toString(Charsets.UTF_8))
                        }
                        name.startsWith("files/") -> {
                            restoredFiles[name] = data
                        }
                        name.startsWith("signatures/") -> {
                            restoredSignatures[name] = data
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val parsed = manifest ?: error("Missing manifest.json in backup")
            parsed.categories.forEach { cat ->
                libraryDao.upsertCategory(
                    CategoryEntity(
                        id = cat.id,
                        name = cat.name,
                        iconName = cat.iconName,
                        colorHex = cat.colorHex,
                        isLocked = cat.isLocked,
                        isSystemDefault = cat.isSystemDefault,
                        sortOrder = cat.sortOrder,
                        updatedAt = cat.updatedAt,
                        deviceId = cat.deviceId,
                        isDeleted = false
                    )
                )
            }

            val libraryDir = File(context.filesDir, "library").apply {
                mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }
            val vaultDir = File(context.filesDir, "vault").apply {
                mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }

            var restoredCount = 0

            parsed.documents.forEach { doc ->
                val payload = restoredFiles[doc.archiveEntryName]
                val safeDocId = doc.id.replace(Regex("[^A-Za-z0-9_-]"), "")
                if (safeDocId.isBlank()) return@forEach
                val ext = inferExtension(doc.fileName, doc.mimeType)

                val finalFile: File = if (doc.isLocked) {
                    if (payload != null && !doc.archiveEntryName.endsWith(".enc")) {
                        val tempPlain = File(context.cacheDir, "restore_tmp_$safeDocId.$ext")
                        try {
                            tempPlain.writeBytes(payload)
                            vaultCryptoManager.encryptFileToVault(tempPlain, safeDocId).getOrThrow()
                        } finally {
                            tempPlain.delete()
                        }
                    } else {
                        File(vaultDir, "$safeDocId.enc").also { file ->
                            if (payload != null) file.writeBytes(payload)
                        }
                    }
                } else {
                    File(libraryDir, "$safeDocId.$ext").also { file ->
                        if (payload != null) file.writeBytes(payload)
                    }
                }

                libraryDao.upsertDocument(
                    LibraryDocumentEntity(
                        id = doc.id,
                        title = doc.title,
                        fileName = doc.fileName,
                        mimeType = doc.mimeType,
                        sourceUri = doc.sourceUri,
                        localCachePath = finalFile.absolutePath,
                        pageCount = doc.pageCount,
                        fileSizeBytes = doc.fileSizeBytes,
                        categoryId = doc.categoryId,
                        tagsJson = doc.tagsJson,
                        ocrText = doc.ocrText,
                        thumbnailPath = null,
                        isLocked = doc.isLocked,
                        isFavorite = doc.isFavorite,
                        lastOpenedAt = doc.lastOpenedAt,
                        createdAt = doc.createdAt,
                        updatedAt = doc.updatedAt,
                        deviceId = doc.deviceId,
                        isDeleted = false
                    )
                )
                restoredCount++
            }

            parsed.bookmarks.forEach { bm ->
                readerDao.upsertBookmark(
                    BookmarkEntity(
                        id = bm.id,
                        documentId = bm.documentId,
                        pageNumber = bm.pageNumber,
                        label = bm.label,
                        excerpt = bm.excerpt,
                        createdAt = bm.createdAt,
                        updatedAt = bm.updatedAt,
                        deviceId = bm.deviceId,
                        isDeleted = false
                    )
                )
            }

            parsed.highlights.forEach { hl ->
                readerDao.upsertHighlight(
                    HighlightEntity(
                        id = hl.id,
                        documentId = hl.documentId,
                        pageNumber = hl.pageNumber,
                        colorKey = hl.colorKey,
                        quotedText = hl.quotedText,
                        noteText = hl.noteText,
                        rectsJson = hl.rectsJson,
                        createdAt = hl.createdAt,
                        updatedAt = hl.updatedAt,
                        deviceId = hl.deviceId,
                        isDeleted = false
                    )
                )
            }

            parsed.notes.forEach { note ->
                noteDao.upsertNote(
                    NoteEntity(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        noteType = runCatching { NoteType.valueOf(note.noteType) }.getOrDefault(NoteType.TEXT),
                        checklistJson = note.checklistJson,
                        isPinned = note.isPinned,
                        colorKey = note.colorKey,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt,
                        deviceId = note.deviceId,
                        isDeleted = false
                    )
                )
                restoredCount++
            }

            parsed.signatures.forEach { sig ->
                val pngBytes = restoredSignatures[sig.archiveEntryName] ?: return@forEach
                signatureVault.restoreSignature(
                    metadata = VaultSignatureMetadata(
                        id = sig.id,
                        label = sig.label,
                        kind = sig.kind,
                        createdAt = sig.createdAt
                    ),
                    pngBytes = pngBytes
                )
                restoredCount++
            }

            parsed.settings?.let { s ->
                runCatching { ThemeMode.valueOf(s.themeMode) }.getOrNull()?.let {
                    preferencesRepository.setThemeMode(it)
                }
                preferencesRepository.setDynamicColor(s.dynamicColor)
                preferencesRepository.setHighContrast(s.highContrast)
                preferencesRepository.setReduceMotion(s.reduceMotion)
                runCatching { ToolsViewMode.valueOf(s.toolsViewMode) }.getOrNull()?.let {
                    preferencesRepository.setToolsViewMode(it)
                }
                runCatching { ToolsSortMode.valueOf(s.toolsSortMode) }.getOrNull()?.let {
                    preferencesRepository.setToolsSortMode(it)
                }
                runCatching { LibraryViewMode.valueOf(s.libraryViewMode) }.getOrNull()?.let {
                    preferencesRepository.setLibraryViewMode(it)
                }
                preferencesRepository.setTtsSpeechRate(s.ttsSpeechRate)
                preferencesRepository.setTtsPitch(s.ttsPitch)
                preferencesRepository.setTtsSleepMinutes(s.ttsDefaultSleepMinutes)
                preferencesRepository.setHideLockedThumbnails(s.hideLockedThumbnails)
                preferencesRepository.setVaultAutoLockMinutes(s.vaultAutoLockMinutes)
                runCatching { ReaderThemeMode.valueOf(s.readerTheme) }.getOrNull()?.let {
                    preferencesRepository.setReaderTheme(it)
                }
                runCatching { ReaderFontChoice.valueOf(s.readerFontChoice) }.getOrNull()?.let {
                    preferencesRepository.setReaderFont(it)
                }
                preferencesRepository.setReaderFontScale(s.readerFontScale)
                preferencesRepository.setAppLocaleTag(s.appLocaleTag)
            }

            restoredCount
        }
    }

    private fun inferExtension(name: String, mimeType: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.isNotEmpty() && fromName.length <= 5 && fromName.all { it.isLetterOrDigit() }) {
            return fromName
        }
        return when (mimeType) {
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/epub+zip" -> "epub"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> "bin"
        }
    }
}
