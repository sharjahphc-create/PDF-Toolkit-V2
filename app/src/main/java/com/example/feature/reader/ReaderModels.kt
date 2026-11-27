package com.example.feature.reader

import android.graphics.Bitmap
import android.net.Uri
import com.example.domain.model.Bookmark
import com.example.domain.model.Highlight
import com.example.domain.model.LibraryDocument
import java.io.File

enum class ReaderContentKind {
    PDF,
    EPUB,
    TEXT
}

data class ReaderPageText(
    val pageIndex: Int,
    val title: String,
    val body: String
)

data class ReaderTocEntry(
    val title: String,
    val pageIndex: Int,
    val level: Int = 0
)

data class ReaderSearchHit(
    val pageIndex: Int,
    val pageLabel: String,
    val snippet: String,
    val matchCountOnPage: Int = 1
)

enum class ReaderTranslateLanguage(val displayName: String, val mlKitCode: String) {
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    HINDI("Hindi", "hi"),
    ARABIC("Arabic", "ar"),
    PORTUGUESE("Portuguese", "pt"),
    ITALIAN("Italian", "it")
}

data class WordLookupState(
    val queryText: String = "",
    val dictionaryTerm: String = "",
    val dictionaryDefinition: String? = null,
    val dictionarySourceLabel: String = "Built-in offline glossary",
    val customPackWordCount: Int = 0,
    val targetLanguage: ReaderTranslateLanguage = ReaderTranslateLanguage.SPANISH,
    val isLanguagePackDownloaded: Boolean = false,
    val isCheckingPack: Boolean = false,
    val isDownloadingPack: Boolean = false,
    val isTranslating: Boolean = false,
    val translatedText: String? = null,
    val translationError: String? = null
)

data class ReaderStatsSummary(
    val percentComplete: Int = 0,
    val estimatedMinutesLeft: Int = 1,
    val totalMinutesRead: Int = 0,
    val totalTtsMinutes: Int = 0,
    val sessionMinutes: Int = 0,
    val totalWordCount: Int = 0
)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val document: LibraryDocument? = null,
    val contentKind: ReaderContentKind = ReaderContentKind.PDF,
    val pdfWorkingFile: File? = null,
    val pdfPassword: String? = null,
    val passwordRequiredFile: File? = null,
    val passwordError: String? = null,
    val totalPages: Int = 1,
    val currentPage: Int = 0,
    val initialScrollFraction: Float = 0f,
    val pageBitmaps: Map<Int, Bitmap> = emptyMap(),
    val croppedPageBitmaps: Map<Int, Bitmap> = emptyMap(),
    val textSections: List<ReaderPageText> = emptyList(),
    val tocEntries: List<ReaderTocEntry> = emptyList(),
    val reflowTextMode: Boolean = false,
    val layoutWarningDismissed: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<ReaderSearchHit> = emptyList(),
    val totalSearchMatches: Int = 0,
    val isControlsVisible: Boolean = true,
    val stats: ReaderStatsSummary = ReaderStatsSummary(),
    val wordLookup: WordLookupState? = null
)

sealed interface ReaderEvent {
    data class ShowMessage(val message: String) : ReaderEvent
    data class ShareExportedFile(val uri: Uri, val mimeType: String, val title: String) : ReaderEvent
}
