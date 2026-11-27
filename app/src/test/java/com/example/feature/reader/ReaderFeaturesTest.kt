package com.example.feature.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.LibraryViewMode
import com.example.core.datastore.PdfColorFilterMode
import com.example.core.datastore.PdfFitMode
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ReaderBookPreset
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderOrientationLock
import com.example.core.datastore.ReaderPageTurnMode
import com.example.core.datastore.ReaderTextAlignment
import com.example.core.datastore.ReaderThemeMode
import com.example.domain.model.Highlight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderFeaturesTest {

    @Test
    fun `reader global and per-book presets persist cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        val globalPreset = ReaderBookPreset(
            theme = ReaderThemeMode.SEPIA,
            pdfColorFilter = PdfColorFilterMode.SEPIA,
            pdfFitMode = PdfFitMode.AUTO_CROP,
            autoCropMargins = true,
            fontChoice = ReaderFontChoice.DYSLEXIC,
            fontScale = 1.3f,
            lineSpacingMultiplier = 1.75f,
            horizontalMarginDp = 32,
            textAlignment = ReaderTextAlignment.JUSTIFY,
            boldText = true,
            brightness = 0.72f,
            warmLight = 0.25f,
            warmLightScheduleEnabled = true,
            warmLightStartHour = 21,
            warmLightEndHour = 6,
            pageTurnMode = ReaderPageTurnMode.SCROLL,
            tapZonesEnabled = true,
            volumeKeysTurnPage = true,
            keepScreenOn = true,
            orientationLock = ReaderOrientationLock.PORTRAIT,
            immersiveMode = true
        )
        repo.saveGlobalReaderPreset(globalPreset)
        repo.setLibraryViewMode(LibraryViewMode.LIST)

        val bookPreset = globalPreset.copy(
            theme = ReaderThemeMode.OLED_BLACK,
            fontChoice = ReaderFontChoice.SERIF
        )
        repo.savePerBookReaderPreset("doc-123", bookPreset)

        val loaded = repo.preferences.first()
        assertEquals(LibraryViewMode.LIST, loaded.libraryViewMode)
        assertEquals(ReaderThemeMode.SEPIA, loaded.effectiveReaderPreset("other-doc").theme)
        assertEquals(ReaderFontChoice.DYSLEXIC, loaded.effectiveReaderPreset("other-doc").fontChoice)
        assertEquals(ReaderThemeMode.OLED_BLACK, loaded.effectiveReaderPreset("doc-123").theme)
        assertTrue(loaded.hasPerBookPreset("doc-123"))

        repo.clearPerBookReaderPreset("doc-123")
        val afterClear = repo.preferences.first()
        assertFalse(afterClear.hasPerBookPreset("doc-123"))
        assertEquals(ReaderThemeMode.SEPIA, afterClear.effectiveReaderPreset("doc-123").theme)
    }

    @Test
    fun `warm light schedule handles overnight and daytime windows`() {
        assertTrue(ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(22, 20, 6))
        assertTrue(ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(3, 20, 6))
        assertFalse(ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(14, 20, 6))
        assertTrue(ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(10, 9, 17))
        assertFalse(ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(19, 9, 17))
    }

    @Test
    fun `reading time remaining and highlight markdown export work cleanly`() {
        val sections = listOf(
            ReaderPageText(0, "Chapter 1", List(220) { "word" }.joinToString(" ")),
            ReaderPageText(1, "Chapter 2", List(440) { "word" }.joinToString(" "))
        )
        val (minsFromStart, totalWords) = ReaderPdfAndExportHelper.estimateRemainingReadingMinutes(
            sections = sections,
            currentPage = 0,
            totalPages = 2
        )
        assertEquals(660, totalWords)
        assertEquals(3, minsFromStart)

        val md = ReaderPdfAndExportHelper.buildHighlightsMarkdownContent(
            bookTitle = "Offline Guide",
            highlights = listOf(
                Highlight(
                    id = "h1",
                    documentId = "d1",
                    pageNumber = 2,
                    colorKey = "teal",
                    quotedText = "Runs 100% on your device.",
                    noteText = "Zero cloud uploads",
                    createdAt = 1L
                )
            )
        )
        assertTrue(md.contains("# Highlights & Notes — Offline Guide"))
        assertTrue(md.contains("Page 2 • `TEAL`"))
        assertTrue(md.contains("> Runs 100% on your device."))
        assertTrue(md.contains("**Note:** Zero cloud uploads"))
    }

    @Test
    fun `reading view highlights active search query and tts sentence`() {
        val body = "Privacy matters offline. Every PDF stays local on your phone."
        val annotated = buildHighlightedReadingText(
            rawBody = body,
            ttsSentence = "Every PDF stays local on your phone.",
            searchQuery = "offline"
        )
        assertEquals(body, annotated.text)
        assertTrue(annotated.spanStyles.size >= 2)
    }

    @Test
    fun `offline dictionary candidate stems normalize inflections`() {
        assertTrue("encrypt" in ReaderPdfAndExportHelper.candidateStems("encrypted"))
        assertTrue("redact" in ReaderPdfAndExportHelper.candidateStems("redacting"))
        assertTrue("summary" in ReaderPdfAndExportHelper.candidateStems("summaries"))
    }
}
