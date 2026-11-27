package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.core.common.IoDispatcher
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedPdfMetadata(
    val title: String?,
    val pageCount: Int,
    val extractedText: String,
    val thumbnailPath: String?
)

@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Streams a PDF lazily using temporary disk backing so 200+ page PDFs do not OOM.
     * Extracts page count, metadata title, up to maxPagesForIndex text for FTS4 + TTS,
     * and renders a small first-page thumbnail.
     */
    suspend fun inspectAndExtract(
        pdfFile: File,
        documentId: String,
        maxPagesToExtract: Int = 50
    ): ExtractedPdfMetadata = withContext(ioDispatcher) {
        var title: String? = null
        var pageCount = 1
        val textBuilder = StringBuilder()

        runCatching {
            PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                pageCount = doc.numberOfPages.coerceAtLeast(1)
                title = doc.documentInformation?.title?.takeIf { it.isNotBlank() }

                val stripper = PDFTextStripper()
                val pagesToRead = pageCount.coerceAtMost(maxPagesToExtract)
                for (page in 1..pagesToRead) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(doc).trim()
                    if (pageText.isNotEmpty()) {
                        textBuilder.append(pageText)
                    }
                    if (page < pagesToRead) {
                        textBuilder.append('\u000C')
                    }
                }
            }
        }

        val thumbnailPath = renderThumbnail(pdfFile, documentId)
        ExtractedPdfMetadata(
            title = title,
            pageCount = pageCount,
            extractedText = textBuilder.toString(),
            thumbnailPath = thumbnailPath
        )
    }

    /**
     * Extracts full speakable text for any document (PDF via PdfBox-Android or plain text).
     */
    suspend fun extractSpeakableText(file: File, mimeType: String): String = withContext(ioDispatcher) {
        if (!file.exists()) return@withContext ""
        if (mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true)) {
            runCatching {
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val stripper = PDFTextStripper()
                    val builder = StringBuilder()
                    for (page in 1..doc.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val text = stripper.getText(doc).trim()
                        if (text.isNotEmpty()) {
                            builder.append(text)
                        }
                        if (page < doc.numberOfPages) {
                            builder.append('\u000C')
                        }
                    }
                    builder.toString()
                }
            }.getOrDefault("")
        } else {
            runCatching { file.readText() }.getOrDefault("")
        }
    }

    private fun renderThumbnail(pdfFile: File, documentId: String): String? {
        return runCatching {
            val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val outFile = File(thumbDir, "$documentId.png")
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val targetWidth = 320
                        val aspectRatio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val targetHeight = (targetWidth * aspectRatio).toInt().coerceIn(180, 520)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        FileOutputStream(outFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fos)
                        }
                        bitmap.recycle()
                    }
                }
            }
            outFile.absolutePath
        }.getOrNull()
    }
}
