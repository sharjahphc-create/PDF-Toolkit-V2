package com.example.feature.pdftools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.feature.pdftools.engine.FreeTextOverlay
import com.example.feature.pdftools.engine.PageOrganizeSpec
import com.example.feature.pdftools.engine.PdfCoreEngine
import com.example.feature.pdftools.engine.PdfCryptoAndImageEngine
import com.example.feature.pdftools.engine.PdfPageSizeOption
import com.example.feature.pdftools.engine.PhotoPageConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CorePdfToolsTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var coreEngine: PdfCoreEngine
    private lateinit var cryptoImageEngine: PdfCryptoAndImageEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        tempDir = File(context.cacheDir, "core_pdf_tools_test_${System.nanoTime()}").apply { mkdirs() }
        coreEngine = PdfCoreEngine(context, Dispatchers.IO)
        cryptoImageEngine = PdfCryptoAndImageEngine(context, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createSamplePdf(name: String, pageCount: Int): File {
        val out = File(tempDir, name)
        PDDocument().use { doc ->
            for (i in 1..pageCount) {
                val page = PDPage(PDRectangle.LETTER)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Sample page $i")
                    cs.endText()
                }
            }
            doc.save(out)
        }
        return out
    }

    @Test
    fun `parsePageRanges handles ranges singletons and invalid bounds`() {
        assertEquals(listOf(0, 1, 2, 4), PdfCoreEngine.parsePageRanges("1-3, 5", 6))
        assertEquals(listOf(3, 2, 1), PdfCoreEngine.parsePageRanges("4, 3, 2", 5).let { listOf(3, 2, 1) }.let {
            // Note: parsePageRanges preserves token insertion order, reversed range 4-2 yields 2,3,4
            PdfCoreEngine.parsePageRanges("4, 3, 2", 5)
        })
        assertEquals(listOf(1, 2, 3), PdfCoreEngine.parsePageRanges("4-2", 5))
        assertEquals(emptyList<Int>(), PdfCoreEngine.parsePageRanges("99", 3))
    }

    @Test
    fun `merge and split preserve pages without mutating sources`() = runTest {
        val pdfA = createSamplePdf("a.pdf", 2)
        val pdfB = createSamplePdf("b.pdf", 3)
        val merged = File(tempDir, "merged.pdf")

        coreEngine.mergePdfs(listOf(pdfA, pdfB), merged).getOrThrow()
        PDDocument.load(merged).use { doc ->
            assertEquals(5, doc.numberOfPages)
        }

        val splitOut = File(tempDir, "split.pdf")
        coreEngine.splitPdf(merged, listOf(0, 2, 4), splitOut).getOrThrow()
        PDDocument.load(splitOut).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("Sample page 1"))
            assertTrue(text.contains("Sample page 3"))
        }
    }

    @Test
    fun `organizePages rotates deletes reorders and crops cleanly`() = runTest {
        val src = createSamplePdf("org_src.pdf", 3)
        val out = File(tempDir, "org_out.pdf")
        val specs = listOf(
            PageOrganizeSpec(originalPageIndex = 2, rotationDelta = 90, cropInsetPercent = 10, isDeleted = false),
            PageOrganizeSpec(originalPageIndex = 1, isDeleted = true),
            PageOrganizeSpec(originalPageIndex = 0, rotationDelta = 180, cropInsetPercent = 0, isDeleted = false)
        )

        coreEngine.organizePages(src, specs, out).getOrThrow()
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            assertTrue(doc.getPage(0).cropBox.width < PDRectangle.LETTER.width)
            assertEquals(180, doc.getPage(1).rotation)
        }
    }

    @Test
    fun `acroform read fill and freetext overlay flatten into output pdf`() = runTest {
        val formPdf = File(tempDir, "form.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm
            val textField = PDTextField(acroForm).apply {
                partialName = "applicant_name"
                val widget = PDAnnotationWidget().apply {
                    rectangle = PDRectangle(100f, 650f, 200f, 24f)
                    this.page = page
                }
                widgets = listOf(widget)
                page.annotations.add(widget)
            }
            acroForm.fields.add(textField)
            doc.save(formPdf)
        }

        val fields = coreEngine.readAcroFormFields(formPdf)
        assertEquals(1, fields.size)
        assertEquals("applicant_name", fields.first().fullyQualifiedName)

        val filledOut = File(tempDir, "form_filled.pdf")
        coreEngine.fillFormAndOverlays(
            sourceFile = formPdf,
            fieldValues = mapOf("applicant_name" to "Jordan Lee"),
            checkboxValues = emptyMap(),
            freeTextOverlays = listOf(FreeTextOverlay(pageIndex = 0, text = "CONFIDENTIAL NOTE")),
            flattenForm = true,
            outputFile = filledOut
        ).getOrThrow()

        PDDocument.load(filledOut).use { doc ->
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("CONFIDENTIAL NOTE"))
        }
    }

    @Test
    fun `lockPdfAes256 and unlockPdf enforce password and round trip cleanly`() = runTest {
        val src = createSamplePdf("secret.pdf", 1)
        val locked = File(tempDir, "secret_locked.pdf")
        coreEngine.lockPdfAes256(
            sourceFile = src,
            userPassword = "open123",
            ownerPassword = "owner456",
            allowPrinting = true,
            allowCopy = false,
            outputFile = locked
        ).getOrThrow()

        var threwInvalid = false
        try {
            PDDocument.load(locked, "wrong").close()
        } catch (_: InvalidPasswordException) {
            threwInvalid = true
        }
        assertTrue(threwInvalid)

        val unlocked = File(tempDir, "secret_unlocked.pdf")
        coreEngine.unlockPdf(locked, "open123", unlocked).getOrThrow()
        PDDocument.load(unlocked).use { doc ->
            assertFalse(doc.isEncrypted)
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test
    fun `pkcs12 digital signature signs and cryptographically verifies pdf`() = runTest {
        val src = createSamplePdf("agreement.pdf", 1)
        val p12 = File(tempDir, "test_id.p12")
        cryptoImageEngine.generateSelfSignedPkcs12("Casey Morgan", "pass1234", p12).getOrThrow()
        assertTrue(p12.exists())

        val signed = File(tempDir, "agreement_signed.pdf")
        cryptoImageEngine.signPdfPkcs12(
            sourceFile = src,
            pkcs12File = p12,
            pkcs12Password = "pass1234",
            reason = "Contract approval",
            location = "Offline",
            outputFile = signed
        ).getOrThrow()

        val verifications = cryptoImageEngine.verifyPdfSignatures(signed)
        assertEquals(1, verifications.size)
        val sig = verifications.first()
        assertTrue(sig.isCryptographicallyValid)
        assertTrue(sig.signerName.contains("Casey Morgan"))
        assertEquals("Contract approval", sig.reason)
    }

    @Test
    fun `imagesToPdf builds multi-page pdf with auto crop`() = runTest {
        val imgFiles = (1..2).map { idx ->
            val file = File(tempDir, "photo_$idx.jpg")
            val bmp = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.DKGRAY)
            canvas.drawRect(
                40f,
                50f,
                360f,
                510f,
                Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            )
            FileOutputStream(file).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos) }
            bmp.recycle()
            PhotoPageConfig(file = file, displayName = file.name, autoCropEdges = true)
        }

        val outPdf = File(tempDir, "photos.pdf")
        cryptoImageEngine.imagesToPdf(imgFiles, PdfPageSizeOption.A4, outPdf).getOrThrow()
        PDDocument.load(outPdf).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }
}
