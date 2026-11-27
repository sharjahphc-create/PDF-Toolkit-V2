package com.example.feature.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignToolTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun `coordinate mapper handles zero and non-zero cropbox offsets across all rotations`() {
        val box = PdfPageBox(
            lowerLeftX = 36f,
            lowerLeftY = 48f,
            width = 500f,
            height = 700f,
            rotationDegrees = 0
        )

        // Centered 20% x 10% box on 0-deg page
        val r0 = PdfCoordinateMapper.toPdfUserSpace(0.4f, 0.45f, 0.2f, 0.1f, 0f, box)
        assertEquals(36f + 0.4f * 500f, r0.bottomLeftX, 0.5f)
        assertEquals(48f + 0.45f * 700f, r0.bottomLeftY, 0.5f)
        assertEquals(100f, r0.width, 0.5f)
        assertEquals(70f, r0.height, 0.5f)
        assertEquals(0f, r0.totalRotationDegrees, 0.01f)

        // 90-deg CW page rotation
        val r90 = PdfCoordinateMapper.toPdfUserSpace(
            0.1f, 0.2f, 0.2f, 0.1f, 0f, box.copy(rotationDegrees = 90)
        )
        assertEquals(90f, r90.totalRotationDegrees, 0.01f)
        assertEquals(0.2f * 700f, r90.width, 0.5f)
        assertEquals(0.1f * 500f, r90.height, 0.5f)

        // 180-deg page rotation
        val r180 = PdfCoordinateMapper.toPdfUserSpace(
            0.1f, 0.1f, 0.2f, 0.1f, 0f, box.copy(rotationDegrees = 180)
        )
        assertEquals(180f, r180.totalRotationDegrees, 0.01f)

        // 270-deg page rotation with overlay rotation
        val r270 = PdfCoordinateMapper.toPdfUserSpace(
            0.1f, 0.1f, 0.2f, 0.1f, 15f, box.copy(rotationDegrees = 270)
        )
        assertEquals(255f, r270.totalRotationDegrees, 0.01f)
    }

    @Test
    fun `coordinate mapper clamps out-of-bounds placement inside page`() {
        val clamped = PdfCoordinateMapper.clampNormalized(0.9f, -0.2f, 0.3f, 0.2f)
        assertEquals(0.7f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun `flatten writes new signed pdf without modifying original and ignores unconfirmed cancel path`() = runTest {
        val tempDir = File(context.cacheDir, "sign_test_${System.nanoTime()}").apply { mkdirs() }
        val sourcePdf = File(tempDir, "contract.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.LETTER))
            doc.addPage(PDPage(PDRectangle.LETTER))
            doc.save(sourcePdf)
        }
        val originalBytes = sourcePdf.readBytes()

        // Create tiny PNG signature bitmap
        val sigBmp = Bitmap.createBitmap(120, 48, Bitmap.Config.ARGB_8888)
        Canvas(sigBmp).drawLine(10f, 30f, 110f, 15f, Paint().apply { color = Color.BLACK; strokeWidth = 4f })
        val pngBytes = ByteArrayOutputStream().also { sigBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        sigBmp.recycle()

        val flattener = PdfSignatureFlattener(context, Dispatchers.IO)

        // Cancel / unconfirmed path writes nothing to PDF
        val cancelledOut = File(tempDir, "cancelled.pdf")
        val unconfirmed = SignaturePlacement(
            pageIndex = 0,
            assetId = "a1",
            pngBytes = pngBytes,
            normalizedX = 0.2f,
            normalizedY = 0.7f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.1f,
            isConfirmed = false
        )
        flattener.flattenToNewPdf(sourcePdf, cancelledOut, listOf(unconfirmed)).getOrThrow()
        assertEquals(originalBytes.size, sourcePdf.readBytes().size)
        assertTrue(sourcePdf.readBytes().contentEquals(originalBytes))

        // Confirmed placement flattens into new PDF cleanly
        val signedOut = File(tempDir, "contract_signed.pdf")
        val confirmed = unconfirmed.copy(isConfirmed = true, includeDateStamp = true, dateStampText = "2026-11-27", signedByText = "Alex")
        val res = flattener.flattenToNewPdf(sourcePdf, signedOut, listOf(confirmed))
        assertTrue(res.isSuccess)
        assertTrue(signedOut.exists())
        assertTrue(signedOut.length() > sourcePdf.length())
        assertTrue(sourcePdf.readBytes().contentEquals(originalBytes))

        PDDocument.load(signedOut).use { check ->
            assertEquals(2, check.numberOfPages)
        }
        assertFalse(signedOut.absolutePath == sourcePdf.absolutePath)
        tempDir.deleteRecursively()
    }
}
