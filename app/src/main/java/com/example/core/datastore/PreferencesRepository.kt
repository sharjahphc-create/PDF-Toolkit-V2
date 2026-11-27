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
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val TOOLS_VIEW_MODE = stringPreferencesKey("tools_view_mode")
        val TOOLS_SORT_MODE = stringPreferencesKey("tools_sort_mode")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val TTS_SLEEP_MINUTES = intPreferencesKey("tts_sleep_minutes")
        val HIDE_LOCKED_THUMBNAILS = booleanPreferencesKey("hide_locked_thumbnails")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PDF_PROMPT_COUNT = intPreferencesKey("default_pdf_prompt_count")
        val DONT_ASK_DEFAULT_PDF = booleanPreferencesKey("dont_ask_default_pdf")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val READER_WARM_LIGHT = floatPreferencesKey("reader_warm_light")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_AUTO_LOCK_MIN = intPreferencesKey("vault_auto_lock_minutes")
        val APP_LOCALE_TAG = stringPreferencesKey("app_locale_tag")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { prefs ->
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
            readerFontChoice = prefs[Keys.READER_FONT]?.let {
                runCatching { ReaderFontChoice.valueOf(it) }.getOrNull()
            } ?: ReaderFontChoice.SERIF,
            readerFontScale = (prefs[Keys.READER_FONT_SCALE] ?: 1.0f).coerceIn(0.8f, 1.7f),
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            readerWarmLight = (prefs[Keys.READER_WARM_LIGHT] ?: 0f).coerceIn(0f, 0.45f),
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
        context.userPreferencesDataStore.edit { it[Keys.READER_FONT_SCALE] = scale.coerceIn(0.8f, 1.7f) }
    }

    suspend fun setReaderBrightness(brightness: Float) {
        context.userPreferencesDataStore.edit { it[Keys.READER_BRIGHTNESS] = brightness }
    }

    suspend fun setReaderWarmLight(warmLight: Float) {
        context.userPreferencesDataStore.edit { it[Keys.READER_WARM_LIGHT] = warmLight.coerceIn(0f, 0.45f) }
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
