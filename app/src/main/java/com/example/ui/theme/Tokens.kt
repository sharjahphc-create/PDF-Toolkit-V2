package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PdfToolkitSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val minTouchTarget: Dp = 48.dp,
    val cardCorner: Dp = 22.dp,
    val sheetCorner: Dp = 28.dp
)

enum class ToolAccentCategory {
    PDF_CORE,
    SIGN_SECURITY,
    SCAN_OCR,
    DOCUMENTS_NOTES,
    AI_DEVICE,
    UTILITIES
}

@Immutable
data class AccentColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

fun ToolAccentCategory.colors(darkTheme: Boolean): AccentColors = when (this) {
    ToolAccentCategory.PDF_CORE -> if (darkTheme) {
        AccentColors(Color(0xFFFF8A75), Color(0xFF5C1D13), Color(0xFFFFDAD3))
    } else {
        AccentColors(Color(0xFFD94E34), Color(0xFFFFEAE5), Color(0xFF6B1809))
    }
    ToolAccentCategory.SIGN_SECURITY -> if (darkTheme) {
        AccentColors(Color(0xFF8FBFFF), Color(0xFF103766), Color(0xFFD2E4FA))
    } else {
        AccentColors(Color(0xFF164A82), Color(0xFFE3EFFC), Color(0xFF0A2747))
    }
    ToolAccentCategory.SCAN_OCR -> if (darkTheme) {
        AccentColors(Color(0xFF7DE0CB), Color(0xFF0A4A3E), Color(0xFFADEFE0))
    } else {
        AccentColors(Color(0xFF1E6F5E), Color(0xFFDDF6F0), Color(0xFF08352C))
    }
    ToolAccentCategory.DOCUMENTS_NOTES -> if (darkTheme) {
        AccentColors(Color(0xFFFFC168), Color(0xFF543600), Color(0xFFFFE5B8))
    } else {
        AccentColors(Color(0xFFB4690E), Color(0xFFFFF1DC), Color(0xFF4D2900))
    }
    ToolAccentCategory.AI_DEVICE -> if (darkTheme) {
        AccentColors(Color(0xFFC4B5FD), Color(0xFF3A2475), Color(0xFFEDE9FE))
    } else {
        AccentColors(Color(0xFF6D28D9), Color(0xFFF1EBFE), Color(0xFF331173))
    }
    ToolAccentCategory.UTILITIES -> if (darkTheme) {
        AccentColors(Color(0xFFF497C3), Color(0xFF5C193E), Color(0xFFFCE7F3))
    } else {
        AccentColors(Color(0xFFBE185D), Color(0xFFFDEAF2), Color(0xFF570829))
    }
}

val LocalPdfToolkitSpacing = staticCompositionLocalOf { PdfToolkitSpacing() }
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalIsDarkTheme = staticCompositionLocalOf { false }
