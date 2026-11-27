package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pdf_toolkit_preferences"
)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val TOOLS_VIEW_MODE = stringPreferencesKey("tools_view_mode")
        val TOOLS_SORT_MODE = stringPreferencesKey("tools_sort_mode")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val TTS_SLEEP_MINUTES = intPreferencesKey("tts_sleep_minutes")
        val HIDE_LOCKED_THUMBNAILS = booleanPreferencesKey("hide_locked_thumbnails")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PDF_PROMPT_COUNT = intPreferencesKey("default_pdf_prompt_count")
        val DONT_ASK_DEFAULT_PDF = booleanPreferencesKey("dont_ask_default_pdf")

        // Reader settings
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_PDF_COLOR_FILTER = stringPreferencesKey("reader_pdf_color_filter")
        val READER_PDF_FIT_MODE = stringPreferencesKey("reader_pdf_fit_mode")
        val READER_AUTO_CROP_MARGINS = booleanPreferencesKey("reader_auto_crop_margins")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val READER_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val READER_HORIZONTAL_MARGIN_DP = intPreferencesKey("reader_horizontal_margin_dp")
        val READER_TEXT_ALIGNMENT = stringPreferencesKey("reader_text_alignment")
        val READER_BOLD_TEXT = booleanPreferencesKey("reader_bold_text")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val READER_WARM_LIGHT = floatPreferencesKey("reader_warm_light")
        val READER_WARM_LIGHT_SCHEDULE = booleanPreferencesKey("reader_warm_light_schedule")
        val READER_WARM_LIGHT_START_HOUR = intPreferencesKey("reader_warm_light_start_hour")
        val READER_WARM_LIGHT_END_HOUR = intPreferencesKey("reader_warm_light_end_hour")
        val READER_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
        val READER_TAP_ZONES = booleanPreferencesKey("reader_tap_zones")
        val READER_VOLUME_KEYS = booleanPreferencesKey("reader_volume_keys")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_ORIENTATION_LOCK = stringPreferencesKey("reader_orientation_lock")
        val READER_IMMERSIVE_MODE = booleanPreferencesKey("reader_immersive_mode")
        val READER_PER_BOOK_PRESETS_JSON = stringPreferencesKey("reader_per_book_presets_json")

        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_AUTO_LOCK_MIN = intPreferencesKey("vault_auto_lock_minutes")
        val APP_LOCALE_TAG = stringPreferencesKey("app_locale_tag")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { prefs ->
        val perBookMap: Map<String, ReaderBookPreset> = prefs[Keys.READER_PER_BOOK_PRESETS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, ReaderBookPreset>>(raw) }.getOrNull()
        } ?: emptyMap()

        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            toolsViewMode = prefs[Keys.TOOLS_VIEW_MODE]?.let {
                runCatching { ToolsViewMode.valueOf(it) }.getOrNull()
            } ?: ToolsViewMode.GRID_2,
            toolsSortMode = prefs[Keys.TOOLS_SORT_MODE]?.let {
                runCatching { ToolsSortMode.valueOf(it) }.getOrNull()
            } ?: ToolsSortMode.DEFAULT,
            libraryViewMode = prefs[Keys.LIBRARY_VIEW_MODE]?.let {
                runCatching { LibraryViewMode.valueOf(it) }.getOrNull()
            } ?: LibraryViewMode.COVERS_GRID,
            ttsSpeechRate = (prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f).coerceIn(0.5f, 2.5f),
            ttsPitch = (prefs[Keys.TTS_PITCH] ?: 1.0f).coerceIn(0.6f, 1.5f),
            ttsVoiceName = prefs[Keys.TTS_VOICE_NAME],
            ttsDefaultSleepMinutes = prefs[Keys.TTS_SLEEP_MINUTES] ?: 0,
            hideLockedThumbnails = prefs[Keys.HIDE_LOCKED_THUMBNAILS] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            defaultPdfPromptCount = prefs[Keys.DEFAULT_PDF_PROMPT_COUNT] ?: 0,
            dontAskDefaultPdf = prefs[Keys.DONT_ASK_DEFAULT_PDF] ?: false,
            readerTheme = prefs[Keys.READER_THEME]?.let {
                runCatching { ReaderThemeMode.valueOf(it) }.getOrNull()
            } ?: ReaderThemeMode.PAPER,
            readerPdfColorFilter = prefs[Keys.READER_PDF_COLOR_FILTER]?.let {
                runCatching { PdfColorFilterMode.valueOf(it) }.getOrNull()
            } ?: PdfColorFilterMode.LIGHT,
            readerPdfFitMode = prefs[Keys.READER_PDF_FIT_MODE]?.let {
                runCatching { PdfFitMode.valueOf(it) }.getOrNull()
            } ?: PdfFitMode.FIT_PAGE,
            readerAutoCropMargins = prefs[Keys.READER_AUTO_CROP_MARGINS] ?: false,
            readerFontChoice = prefs[Keys.READER_FONT]?.let {
                runCatching { ReaderFontChoice.valueOf(it) }.getOrNull()
            } ?: ReaderFontChoice.SERIF,
            readerFontScale = (prefs[Keys.READER_FONT_SCALE] ?: 1.0f).coerceIn(0.8f, 1.8f),
            readerLineSpacing = (prefs[Keys.READER_LINE_SPACING] ?: 1.5f).coerceIn(1.15f, 2.2f),
            readerHorizontalMarginDp = (prefs[Keys.READER_HORIZONTAL_MARGIN_DP] ?: 24).coerceIn(12, 48),
            readerTextAlignment = prefs[Keys.READER_TEXT_ALIGNMENT]?.let {
                runCatching { ReaderTextAlignment.valueOf(it) }.getOrNull()
            } ?: ReaderTextAlignment.START,
            readerBoldText = prefs[Keys.READER_BOLD_TEXT] ?: false,
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            readerWarmLight = (prefs[Keys.READER_WARM_LIGHT] ?: 0f).coerceIn(0f, 0.45f),
            readerWarmLightScheduleEnabled = prefs[Keys.READER_WARM_LIGHT_SCHEDULE] ?: false,
            readerWarmLightStartHour = (prefs[Keys.READER_WARM_LIGHT_START_HOUR] ?: 20).coerceIn(0, 23),
            readerWarmLightEndHour = (prefs[Keys.READER_WARM_LIGHT_END_HOUR] ?: 6).coerceIn(0, 23),
            readerPageTurnMode = prefs[Keys.READER_PAGE_TURN_MODE]?.let {
                runCatching { ReaderPageTurnMode.valueOf(it) }.getOrNull()
            } ?: ReaderPageTurnMode.SLIDE,
            readerTapZonesEnabled = prefs[Keys.READER_TAP_ZONES] ?: true,
            readerVolumeKeysTurnPage = prefs[Keys.READER_VOLUME_KEYS] ?: false,
            readerKeepScreenOn = prefs[Keys.READER_KEEP_SCREEN_ON] ?: false,
            readerOrientationLock = prefs[Keys.READER_ORIENTATION_LOCK]?.let {
                runCatching { ReaderOrientationLock.valueOf(it) }.getOrNull()
            } ?: ReaderOrientationLock.AUTO,
            readerImmersiveMode = prefs[Keys.READER_IMMERSIVE_MODE] ?: true,
            readerPerBookPresets = perBookMap,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            vaultPinHash = prefs[Keys.VAULT_PIN_HASH],
            vaultAutoLockMinutes = prefs[Keys.VAULT_AUTO_LOCK_MIN] ?: 1,
            appLocaleTag = prefs[Keys.APP_LOCALE_TAG] ?: ""
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.userPreferencesDataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val generated = UUID.randomUUID().toString()
        context.userPreferencesDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = generated
            }
        }
        return context.userPreferencesDataStore.data.first()[Keys.DEVICE_ID] ?: generated
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setToolsViewMode(mode: ToolsViewMode) {
        context.userPreferencesDataStore.edit { it[Keys.TOOLS_VIEW_MODE] = mode.name }
    }

    suspend fun setToolsSortMode(mode: ToolsSortMode) {
        context.userPreferencesDataStore.edit { it[Keys.TOOLS_SORT_MODE] = mode.name }
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        context.userPreferencesDataStore.edit { it[Keys.LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.userPreferencesDataStore.edit { it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 2.5f) }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.userPreferencesDataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.6f, 1.5f) }
    }

    suspend fun setTtsVoiceName(voiceName: String?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (voiceName == null) prefs.remove(Keys.TTS_VOICE_NAME)
            else prefs[Keys.TTS_VOICE_NAME] = voiceName
        }
    }

    suspend fun setTtsSleepMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit { it[Keys.TTS_SLEEP_MINUTES] = minutes.coerceAtLeast(0) }
    }

    suspend fun setHideLockedThumbnails(hide: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.HIDE_LOCKED_THUMBNAILS] = hide }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun incrementDefaultPdfPromptCount() {
        context.userPreferencesDataStore.edit {
            it[Keys.DEFAULT_PDF_PROMPT_COUNT] = (it[Keys.DEFAULT_PDF_PROMPT_COUNT] ?: 0) + 1
        }
    }

    suspend fun setDontAskDefaultPdf(dontAsk: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.DONT_ASK_DEFAULT_PDF] = dontAsk }
    }

    suspend fun setReaderTheme(theme: ReaderThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.READER_THEME] = theme.name }
    }

    suspend fun setReaderFont(font: ReaderFontChoice) {
        context.userPreferencesDataStore.edit { it[Keys.READER_FONT] = font.name }
    }

    suspend fun setReaderFontScale(scale: Float) {
        context.userPreferencesDataStore.edit { it[Keys.READER_FONT_SCALE] = scale.coerceIn(0.8f, 1.8f) }
    }

    suspend fun setReaderBrightness(brightness: Float) {
        context.userPreferencesDataStore.edit { it[Keys.READER_BRIGHTNESS] = brightness }
    }

    suspend fun setReaderWarmLight(warmLight: Float) {
        context.userPreferencesDataStore.edit { it[Keys.READER_WARM_LIGHT] = warmLight.coerceIn(0f, 0.45f) }
    }

    suspend fun saveGlobalReaderPreset(preset: ReaderBookPreset) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.READER_THEME] = preset.theme.name
            prefs[Keys.READER_PDF_COLOR_FILTER] = preset.pdfColorFilter.name
            prefs[Keys.READER_PDF_FIT_MODE] = preset.pdfFitMode.name
            prefs[Keys.READER_AUTO_CROP_MARGINS] = preset.autoCropMargins
            prefs[Keys.READER_FONT] = preset.fontChoice.name
            prefs[Keys.READER_FONT_SCALE] = preset.fontScale.coerceIn(0.8f, 1.8f)
            prefs[Keys.READER_LINE_SPACING] = preset.lineSpacingMultiplier.coerceIn(1.15f, 2.2f)
            prefs[Keys.READER_HORIZONTAL_MARGIN_DP] = preset.horizontalMarginDp.coerceIn(12, 48)
            prefs[Keys.READER_TEXT_ALIGNMENT] = preset.textAlignment.name
            prefs[Keys.READER_BOLD_TEXT] = preset.boldText
            prefs[Keys.READER_BRIGHTNESS] = preset.brightness
            prefs[Keys.READER_WARM_LIGHT] = preset.warmLight.coerceIn(0f, 0.45f)
            prefs[Keys.READER_WARM_LIGHT_SCHEDULE] = preset.warmLightScheduleEnabled
            prefs[Keys.READER_WARM_LIGHT_START_HOUR] = preset.warmLightStartHour.coerceIn(0, 23)
            prefs[Keys.READER_WARM_LIGHT_END_HOUR] = preset.warmLightEndHour.coerceIn(0, 23)
            prefs[Keys.READER_PAGE_TURN_MODE] = preset.pageTurnMode.name
            prefs[Keys.READER_TAP_ZONES] = preset.tapZonesEnabled
            prefs[Keys.READER_VOLUME_KEYS] = preset.volumeKeysTurnPage
            prefs[Keys.READER_KEEP_SCREEN_ON] = preset.keepScreenOn
            prefs[Keys.READER_ORIENTATION_LOCK] = preset.orientationLock.name
            prefs[Keys.READER_IMMERSIVE_MODE] = preset.immersiveMode
        }
    }

    suspend fun savePerBookReaderPreset(documentId: String, preset: ReaderBookPreset) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[Keys.READER_PER_BOOK_PRESETS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ReaderBookPreset>>(raw) }.getOrNull()
            }.orEmpty().toMutableMap()
            current[documentId] = preset
            prefs[Keys.READER_PER_BOOK_PRESETS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun clearPerBookReaderPreset(documentId: String) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[Keys.READER_PER_BOOK_PRESETS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ReaderBookPreset>>(raw) }.getOrNull()
            }.orEmpty().toMutableMap()
            current.remove(documentId)
            prefs[Keys.READER_PER_BOOK_PRESETS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setVaultPinHash(hash: String?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (hash == null) prefs.remove(Keys.VAULT_PIN_HASH)
            else prefs[Keys.VAULT_PIN_HASH] = hash
        }
    }

    suspend fun setVaultAutoLockMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit { it[Keys.VAULT_AUTO_LOCK_MIN] = minutes.coerceAtLeast(0) }
    }

    suspend fun setAppLocaleTag(tag: String) {
        context.userPreferencesDataStore.edit { it[Keys.APP_LOCALE_TAG] = tag }
    }
}
