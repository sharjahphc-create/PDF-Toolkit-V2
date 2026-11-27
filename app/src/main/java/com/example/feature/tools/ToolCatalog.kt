package com.example.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.ToolAccentCategory

enum class ToolSection {
    PDF_TOOLS,
    DOCUMENTS_AND_NOTES
}

data class ToolDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentCategory: ToolAccentCategory,
    val section: ToolSection,
    val defaultOrder: Int,
    val defaultFavorite: Boolean = false
)

object ToolCatalog {
    val allTools: List<ToolDefinition> = listOf(
        // Core PDF tools (1-10)
        ToolDefinition(
            id = "sign",
            title = "Sign",
            subtitle = "Draw your signature, place it",
            icon = Icons.Default.Draw,
            accentCategory = ToolAccentCategory.SIGN_SECURITY,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 0,
            defaultFavorite = true
        ),
        ToolDefinition(
            id = "fill_form",
            title = "Fill a form",
            subtitle = "Type into fields or anywhere",
            icon = Icons.Default.EditNote,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 1
        ),
        ToolDefinition(
            id = "compress",
            title = "Compress",
            subtitle = "Small enough to upload",
            icon = Icons.Default.Compress,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 2,
            defaultFavorite = true
        ),
        ToolDefinition(
            id = "merge",
            title = "Merge",
            subtitle = "Many PDFs into one",
            icon = Icons.AutoMirrored.Filled.MergeType,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 3,
            defaultFavorite = true
        ),
        ToolDefinition(
            id = "split",
            title = "Split",
            subtitle = "Pull out the pages you need",
            icon = Icons.AutoMirrored.Filled.CallSplit,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 4
        ),
        ToolDefinition(
            id = "organize_pages",
            title = "Organize pages",
            subtitle = "Rotate, delete, reorder, crop",
            icon = Icons.Default.ViewAgenda,
            accentCategory = ToolAccentCategory.UTILITIES,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 5
        ),
        ToolDefinition(
            id = "digital_signature",
            title = "Digital signature",
            subtitle = "Digital ID and verify signatures",
            icon = Icons.Default.Verified,
            accentCategory = ToolAccentCategory.SIGN_SECURITY,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 6
        ),
        ToolDefinition(
            id = "password",
            title = "Password",
            subtitle = "Lock or unlock a PDF",
            icon = Icons.Default.Lock,
            accentCategory = ToolAccentCategory.SIGN_SECURITY,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 7
        ),
        ToolDefinition(
            id = "photos_to_pdf",
            title = "Photos → PDF",
            subtitle = "Photos of papers into a PDF",
            icon = Icons.Default.PhotoCamera,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 8,
            defaultFavorite = true
        ),
        ToolDefinition(
            id = "copy_text",
            title = "Copy text",
            subtitle = "From a photo or scan",
            icon = Icons.Default.TextFields,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 9
        ),
        // Standout PDF Tools (Section 9)
        ToolDefinition(
            id = "doc_scanner",
            title = "Scan Document",
            subtitle = "ML Kit document scanner & clean",
            icon = Icons.Default.DocumentScanner,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 10
        ),
        ToolDefinition(
            id = "redact",
            title = "Redact PDF",
            subtitle = "Scrub sensitive text & PII",
            icon = Icons.Default.HideSource,
            accentCategory = ToolAccentCategory.SIGN_SECURITY,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 11
        ),
        ToolDefinition(
            id = "watermark_pages",
            title = "Watermark & Pages",
            subtitle = "Stamp text, headers, page numbers",
            icon = Icons.Default.BrandingWatermark,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 12
        ),
        ToolDefinition(
            id = "compare_pdfs",
            title = "Compare PDFs",
            subtitle = "Spot text & page differences",
            icon = Icons.Default.Compare,
            accentCategory = ToolAccentCategory.UTILITIES,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 13
        ),
        ToolDefinition(
            id = "workflows",
            title = "Workflows",
            subtitle = "Multi-step offline PDF recipes",
            icon = Icons.Default.Route,
            accentCategory = ToolAccentCategory.UTILITIES,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 14
        ),
        ToolDefinition(
            id = "batch_tools",
            title = "Batch Process",
            subtitle = "Compress, stamp, or lock many",
            icon = Icons.Default.DynamicFeed,
            accentCategory = ToolAccentCategory.UTILITIES,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 15
        ),
        ToolDefinition(
            id = "convert_pdf",
            title = "Convert PDF",
            subtitle = "PDF → PNG or Text/MD → PDF",
            icon = Icons.Default.SwapHoriz,
            accentCategory = ToolAccentCategory.PDF_CORE,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 16
        ),
        ToolDefinition(
            id = "id_scanner",
            title = "ID Card → PDF",
            subtitle = "Front & back on one clean page",
            icon = Icons.Default.Badge,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 17
        ),
        ToolDefinition(
            id = "qr_barcode",
            title = "QR & Barcode",
            subtitle = "Scan codes offline or stamp",
            icon = Icons.Default.QrCodeScanner,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 18
        ),
        ToolDefinition(
            id = "autoclean_scan",
            title = "Auto-Clean Scan",
            subtitle = "Boost contrast & fix shadows",
            icon = Icons.Default.AutoFixHigh,
            accentCategory = ToolAccentCategory.SCAN_OCR,
            section = ToolSection.PDF_TOOLS,
            defaultOrder = 19
        ),
        // Documents, AI & Notes (11-16)
        ToolDefinition(
            id = "word",
            title = "Word",
            subtitle = "Open and edit .docx",
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 20
        ),
        ToolDefinition(
            id = "excel",
            title = "Excel",
            subtitle = "Open and edit .xlsx",
            icon = Icons.Default.GridOn,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 21
        ),
        ToolDefinition(
            id = "powerpoint",
            title = "PowerPoint",
            subtitle = "Open and turn into PDF",
            icon = Icons.Default.Slideshow,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 22
        ),
        ToolDefinition(
            id = "notes",
            title = "Notes",
            subtitle = "Notes, to-do, shopping, voice",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 23
        ),
        ToolDefinition(
            id = "translate_pdf",
            title = "Translate PDF",
            subtitle = "On-device ML Kit translation",
            icon = Icons.Default.Translate,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 24
        ),
        ToolDefinition(
            id = "ai_assistant",
            title = "Smart AI Reader",
            subtitle = "Summarize, key points & Q&A",
            icon = Icons.Default.AutoAwesome,
            accentCategory = ToolAccentCategory.DOCUMENTS_NOTES,
            section = ToolSection.DOCUMENTS_AND_NOTES,
            defaultOrder = 25
        )
    )

    fun findById(id: String): ToolDefinition? = allTools.firstOrNull { it.id == id }
}
