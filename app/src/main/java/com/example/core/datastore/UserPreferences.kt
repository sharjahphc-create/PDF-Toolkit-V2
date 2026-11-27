package com.example.core.datastore

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

enum class ReaderThemeMode {
    PAPER,
    SEPIA,
    SLATE_DARK,
    OLED_BLACK
}

enum class ReaderFontChoice {
    SANS,
    SERIF,
    MONO
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val toolsViewMode: ToolsViewMode = ToolsViewMode.GRID_2,
    val toolsSortMode: ToolsSortMode = ToolsSortMode.DEFAULT,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceName: String? = null,
    val ttsDefaultSleepMinutes: Int = 0,
    val hideLockedThumbnails: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val defaultPdfPromptCount: Int = 0,
    val dontAskDefaultPdf: Boolean = false,
    // Reader settings (Section 7)
    val readerTheme: ReaderThemeMode = ReaderThemeMode.PAPER,
    val readerFontChoice: ReaderFontChoice = ReaderFontChoice.SERIF,
    val readerFontScale: Float = 1.0f,
    val readerBrightness: Float = -1f, // -1f = system default
    val readerWarmLight: Float = 0.0f,
    // Vault & App Lock (Section 8)
    val appLockEnabled: Boolean = false,
    val vaultPinHash: String? = null,
    val vaultAutoLockMinutes: Int = 1,
    // Localization (Section 14)
    val appLocaleTag: String = ""
)
