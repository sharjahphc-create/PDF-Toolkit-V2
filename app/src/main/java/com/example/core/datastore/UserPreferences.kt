package com.example.core.datastore

import kotlinx.serialization.Serializable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ToolsViewMode {
    GRID_2,
    LIST,
    COMPACT_3
}

enum class ToolsSortMode {
    DEFAULT,
    A_TO_Z,
    MOST_USED
}

enum class LibraryViewMode {
    LIST,
    COVERS_GRID
}

enum class ReaderThemeMode {
    PAPER,         // White
    SEPIA,         // Sepia
    GRAY,          // Soft Gray
    SLATE_DARK,    // Dark
    OLED_BLACK,    // True-black AMOLED
    FOLLOW_SYSTEM  // Follow system light/dark
}

enum class ReaderFontChoice {
    SERIF,
    SANS,
    DYSLEXIC,
    MONO
}

enum class PdfColorFilterMode {
    LIGHT,
    DARK,
    SEPIA,
    INVERT
}

enum class PdfFitMode {
    FIT_PAGE,
    FIT_WIDTH,
    AUTO_CROP
}

enum class ReaderTextAlignment {
    START,
    JUSTIFY,
    CENTER
}

enum class ReaderPageTurnMode {
    SLIDE,
    SCROLL,
    FADE,
    TAP_ONLY
}

enum class ReaderOrientationLock {
    AUTO,
    PORTRAIT,
    LANDSCAPE
}

@Serializable
data class ReaderBookPreset(
    val theme: ReaderThemeMode = ReaderThemeMode.PAPER,
    val pdfColorFilter: PdfColorFilterMode = PdfColorFilterMode.LIGHT,
    val pdfFitMode: PdfFitMode = PdfFitMode.FIT_PAGE,
    val autoCropMargins: Boolean = false,
    val fontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
    val fontScale: Float = 1.0f,
    val lineSpacingMultiplier: Float = 1.5f,
    val horizontalMarginDp: Int = 24,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val boldText: Boolean = false,
    val brightness: Float = -1f, // -1f = system/auto
    val warmLight: Float = 0.0f,
    val warmLightScheduleEnabled: Boolean = false,
    val warmLightStartHour: Int = 20,
    val warmLightEndHour: Int = 6,
    val pageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.SLIDE,
    val tapZonesEnabled: Boolean = true,
    val volumeKeysTurnPage: Boolean = false,
    val keepScreenOn: Boolean = false,
    val orientationLock: ReaderOrientationLock = ReaderOrientationLock.AUTO,
    val immersiveMode: Boolean = true
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val toolsViewMode: ToolsViewMode = ToolsViewMode.GRID_2,
    val toolsSortMode: ToolsSortMode = ToolsSortMode.DEFAULT,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.COVERS_GRID,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String? = null,
    val ttsDefaultSleepMinutes: Int = 0,
    val hideLockedThumbnails: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val defaultPdfPromptCount: Int = 0,
    val dontAskDefaultPdf: Boolean = false,
    // Reader global settings (Section 7)
    val readerTheme: ReaderThemeMode = ReaderThemeMode.PAPER,
    val readerPdfColorFilter: PdfColorFilterMode = PdfColorFilterMode.LIGHT,
    val readerPdfFitMode: PdfFitMode = PdfFitMode.FIT_PAGE,
    val readerAutoCropMargins: Boolean = false,
    val readerFontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
    val readerFontScale: Float = 1.0f,
    val readerLineSpacing: Float = 1.5f,
    val readerHorizontalMarginDp: Int = 24,
    val readerTextAlignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val readerBoldText: Boolean = false,
    val readerBrightness: Float = -1f, // -1f = system default
    val readerWarmLight: Float = 0.0f,
    val readerWarmLightScheduleEnabled: Boolean = false,
    val readerWarmLightStartHour: Int = 20,
    val readerWarmLightEndHour: Int = 6,
    val readerPageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.SLIDE,
    val readerTapZonesEnabled: Boolean = true,
    val readerVolumeKeysTurnPage: Boolean = false,
    val readerKeepScreenOn: Boolean = false,
    val readerOrientationLock: ReaderOrientationLock = ReaderOrientationLock.AUTO,
    val readerImmersiveMode: Boolean = true,
    val readerPerBookPresets: Map<String, ReaderBookPreset> = emptyMap(),
    // Vault & App Lock (Section 8)
    val appLockEnabled: Boolean = false,
    val vaultPinHash: String? = null,
    val vaultAutoLockMinutes: Int = 1,
    // Localization (Section 14)
    val appLocaleTag: String = ""
) {
    fun toGlobalReaderPreset(): ReaderBookPreset = ReaderBookPreset(
        theme = readerTheme,
        pdfColorFilter = readerPdfColorFilter,
        pdfFitMode = readerPdfFitMode,
        autoCropMargins = readerAutoCropMargins,
        fontChoice = readerFontChoice,
        fontScale = readerFontScale,
        lineSpacingMultiplier = readerLineSpacing,
        horizontalMarginDp = readerHorizontalMarginDp,
        textAlignment = readerTextAlignment,
        boldText = readerBoldText,
        brightness = readerBrightness,
        warmLight = readerWarmLight,
        warmLightScheduleEnabled = readerWarmLightScheduleEnabled,
        warmLightStartHour = readerWarmLightStartHour,
        warmLightEndHour = readerWarmLightEndHour,
        pageTurnMode = readerPageTurnMode,
        tapZonesEnabled = readerTapZonesEnabled,
        volumeKeysTurnPage = readerVolumeKeysTurnPage,
        keepScreenOn = readerKeepScreenOn,
        orientationLock = readerOrientationLock,
        immersiveMode = readerImmersiveMode
    )

    fun effectiveReaderPreset(documentId: String?): ReaderBookPreset =
        documentId?.let { readerPerBookPresets[it] } ?: toGlobalReaderPreset()

    fun hasPerBookPreset(documentId: String?): Boolean =
        documentId != null && readerPerBookPresets.containsKey(documentId)
}
