package com.example.feature.documents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.PdfToolkitDatabase
import com.example.core.database.entity.NoteType
import com.example.core.datastore.PreferencesRepository
import com.example.feature.documents.engine.DocxParagraphItem
import com.example.feature.documents.engine.OfficeDocumentEngine
import com.example.feature.notes.ChecklistItem
import com.example.feature.notes.NotesRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentsAndNotesTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var officeEngine: OfficeDocumentEngine
    private lateinit var db: PdfToolkitDatabase
    private lateinit var notesRepository: NotesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        tempDir = File(context.cacheDir, "docs_notes_test_${System.nanoTime()}").apply { mkdirs() }
        officeEngine = OfficeDocumentEngine(context, Dispatchers.IO)
        db = Room.inMemoryDatabaseBuilder(context, PdfToolkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notesRepository = NotesRepository(db.noteDao(), PreferencesRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `docx create edit reload and export to pdf`() = runTest {
        val initial = officeEngine.createNewDocxModel().copy(
            paragraphs = listOf(
                DocxParagraphItem(text = "Project Plan", isHeading = true, isBold = true),
                DocxParagraphItem(text = "All processing happens on device.")
            )
        )
        val docxFile = File(tempDir, "plan.docx")
        officeEngine.saveDocx(initial, docxFile).getOrThrow()
        assertTrue(docxFile.exists())

        val reloaded = officeEngine.readDocx(docxFile).getOrThrow()
        assertEquals(2, reloaded.paragraphs.size)
        assertEquals("Project Plan", reloaded.paragraphs[0].text)
        assertEquals("All processing happens on device.", reloaded.paragraphs[1].text)

        val pdfFile = File(tempDir, "plan.pdf")
        officeEngine.exportDocxToPdf(reloaded, pdfFile).getOrThrow()
        PDDocument.load(pdfFile).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Project Plan"))
            assertTrue(text.contains("All processing happens on device"))
        }
    }

    @Test
    fun `xlsx create edit formula reload and export to pdf`() = runTest {
        val model = officeEngine.createNewXlsxModel()
        val sheet = model.sheets.first()
        val updatedRows = sheet.rows.mapIndexed { rIdx, row ->
            if (rIdx == 2) listOf("Total", "2", "=B2+1") else row
        }
        val edited = model.copy(sheets = listOf(sheet.copy(rows = updatedRows)))

        val xlsxFile = File(tempDir, "budget.xlsx")
        officeEngine.saveXlsx(edited, xlsxFile).getOrThrow()
        assertTrue(xlsxFile.exists())

        val reloaded = officeEngine.readXlsx(xlsxFile).getOrThrow()
        assertEquals("Item", reloaded.sheets[0].rows[0][0])
        assertEquals("2", reloaded.sheets[0].rows[2][2]) // Formula =B2+1 evaluated!

        val pdfFile = File(tempDir, "budget.pdf")
        officeEngine.exportXlsxToPdf(reloaded, pdfFile).getOrThrow()
        PDDocument.load(pdfFile).use { pdf ->
            assertTrue(pdf.numberOfPages >= 1)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Sample Item"))
        }
    }

    @Test
    fun `pptx read render bitmap and export to pdf`() = runTest {
        val pptxFile = File(tempDir, "deck.pptx")
        XMLSlideShow().use { ppt ->
            val slide1 = ppt.createSlide()
            slide1.createTextBox().apply { text = "Quarterly Offline Review" }
            val notes = ppt.getNotesSlide(slide1)
            val bodyPlaceholder = notes.placeholders.firstOrNull {
                it.textType == org.apache.poi.sl.usermodel.Placeholder.BODY
            }
            if (bodyPlaceholder != null) {
                bodyPlaceholder.text = "Mention zero cloud calls"
            } else {
                notes.createTextBox().apply { text = "Mention zero cloud calls" }
            }
            FileOutputStream(pptxFile).use { fos -> ppt.write(fos) }
        }

        val presentation = officeEngine.readPptx(pptxFile).getOrThrow()
        assertEquals(1, presentation.slides.size)
        assertTrue(presentation.slides[0].bodyLines.first().contains("Quarterly Offline Review"))
        assertTrue(presentation.slides[0].notes.contains("zero cloud calls"))

        val bmp = officeEngine.renderSlidePreviewBitmap(
            presentation.slides[0],
            presentation.slideWidthPt,
            presentation.slideHeightPt
        )
        assertNotNull(bmp)
        assertTrue(bmp.width > 100 && bmp.height > 100)
        bmp.recycle()

        val pdfFile = File(tempDir, "deck.pdf")
        officeEngine.exportPptxToPdf(presentation, includeSpeakerNotes = true, pdfFile).getOrThrow()
        PDDocument.load(pdfFile).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("Quarterly Offline Review"))
            assertTrue(text.contains("Mention zero cloud calls"))
        }
    }

    @Test
    fun `notes repository supports checklists pinning soft delete and restore`() = runTest {
        val grocery = notesRepository.saveNote(
            title = "Weekend Groceries",
            content = "",
            noteType = NoteType.SHOPPING,
            checklistItems = listOf(
                ChecklistItem(id = "i1", text = "Oat milk", isChecked = false),
                ChecklistItem(id = "i2", text = "Coffee beans", isChecked = true)
            ),
            isPinned = false
        )
        val memo = notesRepository.saveNote(
            title = "Pinned Meeting Note",
            content = "Voice summary from standup",
            noteType = NoteType.VOICE,
            checklistItems = emptyList(),
            isPinned = true
        )

        val initialList = notesRepository.observeNotes().first()
        assertEquals(2, initialList.size)
        // Pinned note sorts first
        assertEquals(memo.id, initialList.first().id)

        notesRepository.toggleChecklistItem(grocery, "i1")
        val afterToggle = notesRepository.observeNotes().first().first { it.id == grocery.id }
        assertTrue(afterToggle.checklistItems.first { it.id == "i1" }.isChecked)

        notesRepository.softDelete(grocery.id)
        val afterDelete = notesRepository.observeNotes().first()
        assertEquals(1, afterDelete.size)
        assertFalse(afterDelete.any { it.id == grocery.id })

        notesRepository.restoreNote(afterToggle)
        val afterRestore = notesRepository.observeNotes().first()
        assertEquals(2, afterRestore.size)
    }
}
