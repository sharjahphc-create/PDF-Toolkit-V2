package com.example.feature.standout

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.UUID

data class RedactionBox(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

data class PiiMatchSummary(
    val category: String,
    val sample: String,
    val pageNumbers: List<Int>
)

data class PdfCompareDiff(
    val leftPages: Int,
    val rightPages: Int,
    val changedPageCount: Int,
    val addedLines: List<String>,
    val removedLines: List<String>
)

data class DecodedBarcodeItem(
    val formatLabel: String,
    val rawValue: String,
    val displayType: String
)

enum class TranslateTargetLang(val code: String, val displayName: String) {
    ENGLISH(TranslateLanguage.ENGLISH, "English"),
    SPANISH(TranslateLanguage.SPANISH, "Spanish"),
    HINDI(TranslateLanguage.HINDI, "Hindi"),
    ARABIC(TranslateLanguage.ARABIC, "Arabic"),
    PORTUGUESE(TranslateLanguage.PORTUGUESE, "Portuguese"),
    FRENCH(TranslateLanguage.FRENCH, "French"),
    GERMAN(TranslateLanguage.GERMAN, "German")
}

enum class ConvertSubMode {
    PDF_TO_PNG_ZIP,
    PDF_TO_TEXT,
    TEXT_TO_PDF
}

enum class BatchOperationMode(val label: String, val subtitle: String) {
    COMPRESS("Compress", "Shrink PDFs for sharing"),
    WATERMARK_AND_NUMBERS("Stamp", "Watermark & page numbers"),
    LOCK_AES256("Lock AES-256", "Password-protect every PDF"),
    EXTRACT_TEXT("Extract Text", "Export text files in ZIP")
}

enum class BatchCompressionPreset(val label: String, val jpegQuality: Float, val maxWidthPx: Int) {
    LIGHT("Light (Sharp)", 0.86f, 1500),
    BALANCED("Balanced", 0.72f, 1200),
    MAX("Maximum Small", 0.52f, 920)
}

data class BatchRunSummary(
    val totalFiles: Int,
    val succeededCount: Int,
    val failedNames: List<String>,
    val inputTotalBytes: Long,
    val outputTotalBytes: Long
)

enum class WatermarkStyle(val label: String) {
    DIAGONAL("Diagonal"),
    CENTER("Centered"),
    TILED("Tiled Grid")
}

enum class PageNumberFormat(val label: String) {
    PAGE_X_OF_Y("Page 1 of N"),
    X_SLASH_Y("1 / N"),
    NUMBER_ONLY("1")
}

enum class ScanCleanMode(val label: String) {
    BLACK_AND_WHITE("B&W Document"),
    COLOR_ENHANCED("Color Boost"),
    ORIGINAL("Original")
}

data class WorkflowRecipeConfig(
    val compress: Boolean = false,
    val compressionPreset: BatchCompressionPreset = BatchCompressionPreset.BALANCED,
    val stripMetadata: Boolean = true,
    val watermarkText: String = "CONFIDENTIAL",
    val includePageNumbers: Boolean = true,
    val password: String = ""
)
