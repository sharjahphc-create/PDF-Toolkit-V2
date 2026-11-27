package com.example.feature.standout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StandoutAndBatchTest {

    private lateinit var context: Context
    private lateinit var engine: StandoutPdfEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        engine = StandoutPdfEngine(context, Dispatchers.IO)
    }

    @Test
    fun `scanPii detects email phone ssn and card numbers with masked samples`() = runTest {
        val pdf = File(context.cacheDir, "pii_sample.pdf")
        try {
            val content = """
                Client Intake Form
                Contact email: alice.johnson@example.com
                Mobile phone: (415) 555-0199
                Tax ID: 123-45-6789
                Card on file: 4532 1488 0343 6467
            """.trimIndent()
            engine.createTextPdf(content, pdf, title = "Client Intake").getOrThrow()

            val matches = engine.scanPii(pdf)
            val categories = matches.map { it.category }.toSet()

            assertTrue("Expected Email match", "Email" in categories)
            assertTrue("Expected Phone match", "Phone" in categories)
            assertTrue("Expected ID/SSN match", "ID/SSN" in categories)
            assertTrue("Expected Card match", "Card" in categories)
            assertTrue("All samples must be masked", matches.all { "•••" in it.sample || it.sample == "***" })
            assertTrue("All matches should record page 1", matches.all { 1 in it.pageNumbers })
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun `comparePdfs detects added lines removed lines and generates diff report pdf`() = runTest {
        val docA = File(context.cacheDir, "compare_a.pdf")
        val docB = File(context.cacheDir, "compare_b.pdf")
        val diffOut = File(context.cacheDir, "compare_diff.pdf")
        try {
            engine.createTextPdf(
                "Agreement Clause One\nLegacy Payment Term Net 60\nStandard Confidentiality",
                docA,
                title = "Contract V1"
            ).getOrThrow()
            engine.createTextPdf(
                "Agreement Clause One\nUpdated Payment Term Net 30\nStandard Confidentiality\nNew Arbitration Clause",
                docB,
                title = "Contract V2"
            ).getOrThrow()

            val diff = engine.comparePdfs(docA, docB, diffOut).getOrThrow()

            assertEquals(1, diff.leftPages)
            assertEquals(1, diff.rightPages)
            assertEquals(1, diff.changedPageCount)
            assertTrue(diff.addedLines.any { it.contains("Updated Payment Term Net 30") })
            assertTrue(diff.addedLines.any { it.contains("New Arbitration Clause") })
            assertTrue(diff.removedLines.any { it.contains("Legacy Payment Term Net 60") })
            assertTrue("Diff PDF must be created", diffOut.exists() && diffOut.length() > 0)
        } finally {
            docA.delete()
            docB.delete()
            diffOut.delete()
        }
    }

    @Test
    fun `custom workflow recipe strips metadata stamps watermark and encrypts with AES-256`() = runTest {
        val src = File(context.cacheDir, "workflow_src.pdf")
        val out = File(context.cacheDir, "workflow_out.pdf")
        try {
            engine.createTextPdf("Quarterly board summary text.", src, title = "Board Report").getOrThrow()
            PDDocument.load(src).use { doc ->
                doc.documentInformation = PDDocumentInformation().apply {
                    author = "Secret Author"
                    title = "Internal Draft"
                }
                doc.save(src)
            }

            val config = WorkflowRecipeConfig(
                compress = false,
                stripMetadata = true,
                watermarkText = "CONFIDENTIAL",
                includePageNumbers = true,
                password = "secret123"
            )
            engine.runCustomWorkflowRecipe(src, out, config).getOrThrow()
            assertTrue(out.exists() && out.length() > 0)

            val openWithoutPassword = runCatching { PDDocument.load(out).use { it.numberOfPages } }
            assertTrue("Encrypted PDF must fail to open without password", openWithoutPassword.isFailure)

            PDDocument.load(out, "secret123").use { doc ->
                assertTrue("Output PDF must be encrypted", doc.isEncrypted)
                assertTrue("Author metadata should be stripped", doc.documentInformation.author.isNullOrBlank())
                val text = PDFTextStripper().getText(doc)
                assertTrue("Watermark text should appear", text.contains("CONFIDENTIAL"))
                assertTrue("Page number should appear", text.contains("Page 1 of 1"))
            }
        } finally {
            src.delete()
            out.delete()
        }
    }

    @Test
    fun `batch operations stamp lock and extract text into deduplicated zip archives`() = runTest {
        val file1 = File(context.cacheDir, "batch_doc_1.pdf")
        val file2 = File(context.cacheDir, "batch_doc_2.pdf")
        val stampZip = File(context.cacheDir, "batch_stamp.zip")
        val lockZip = File(context.cacheDir, "batch_lock.zip")
        val textZip = File(context.cacheDir, "batch_text.zip")
        try {
            engine.createTextPdf("First invoice body 1001", file1, title = "Invoice A").getOrThrow()
            engine.createTextPdf("Second invoice body 2002", file2, title = "Invoice B").getOrThrow()

            val duplicateSources = listOf(file1 to "Report.pdf", file2 to "Report.pdf")

            val stampSummary = engine.runBatchOperation(
                sources = duplicateSources,
                outputZip = stampZip,
                mode = BatchOperationMode.WATERMARK_AND_NUMBERS,
                watermarkText = "DRAFT",
                includePageNumbers = true
            ).getOrThrow()
            assertEquals(2, stampSummary.totalFiles)
            assertEquals(2, stampSummary.succeededCount)
            assertTrue(stampSummary.failedNames.isEmpty())
            ZipFile(stampZip).use { zf ->
                val names = zf.entries().toList().map { it.name }.toSet()
                assertEquals(setOf("Report_stamped.pdf", "Report_stamped_2.pdf"), names)
            }

            val lockSummary = engine.runBatchOperation(
                sources = listOf(file1 to "Alpha.pdf", file2 to "Beta.pdf"),
                outputZip = lockZip,
                mode = BatchOperationMode.LOCK_AES256,
                password = "pass9876"
            ).getOrThrow()
            assertEquals(2, lockSummary.succeededCount)
            ZipFile(lockZip).use { zf ->
                val entry = zf.getEntry("Alpha_locked.pdf")
                val extracted = File(context.cacheDir, "alpha_check.pdf")
                try {
                    zf.getInputStream(entry).use { input -> extracted.outputStream().use { input.copyTo(it) } }
                    assertTrue(runCatching { PDDocument.load(extracted).use { } }.isFailure)
                    PDDocument.load(extracted, "pass9876").use { assertTrue(it.isEncrypted) }
                } finally {
                    extracted.delete()
                }
            }

            val textSummary = engine.runBatchOperation(
                sources = listOf(file1 to "Alpha.pdf", file2 to "Beta.pdf"),
                outputZip = textZip,
                mode = BatchOperationMode.EXTRACT_TEXT
            ).getOrThrow()
            assertEquals(2, textSummary.succeededCount)
            ZipFile(textZip).use { zf ->
                val txt1 = zf.getInputStream(zf.getEntry("Alpha_text.txt")).bufferedReader().readText()
                val txt2 = zf.getInputStream(zf.getEntry("Beta_text.txt")).bufferedReader().readText()
                assertTrue(txt1.contains("First invoice body 1001"))
                assertTrue(txt2.contains("Second invoice body 2002"))
            }
        } finally {
            file1.delete()
            file2.delete()
            stampZip.delete()
            lockZip.delete()
            textZip.delete()
        }
    }

    @Test
    fun `extractPdfToTextFile writes structured page text`() = runTest {
        val pdf = File(context.cacheDir, "extract_src.pdf")
        val txt = File(context.cacheDir, "extract_out.txt")
        try {
            engine.createTextPdf("Offline text extraction verification line.", pdf, title = "Memo").getOrThrow()
            val pageCount = engine.extractPdfToTextFile(pdf, txt).getOrThrow()

            assertEquals(1, pageCount)
            val content = txt.readText(Charsets.UTF_8)
            assertFalse(content.isBlank())
            assertTrue(content.contains("=== Page 1 ==="))
            assertTrue(content.contains("Offline text extraction verification line."))
        } finally {
            pdf.delete()
            txt.delete()
        }
    }
}
