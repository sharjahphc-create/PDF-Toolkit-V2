package com.example.feature.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.core.common.IoDispatcher
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI

sealed interface PdfOpenResult {
    data class Ready(
        val workingCopy: File,
        val displayName: String,
        val pageCount: Int,
        val password: String?
    ) : PdfOpenResult

    data class PasswordRequired(val tempLockedFile: File, val displayName: String) : PdfOpenResult
    data class Error(val message: String) : PdfOpenResult
}

@Singleton
class PdfSignatureFlattener @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // Bounded memory cache (~24 MB) for lazy PdfRenderer page bitmaps
    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun preparePdfFromUri(
        uri: Uri,
        password: String? = null
    ): PdfOpenResult = withContext(ioDispatcher) {
        val workDir = File(context.cacheDir, "sign_work").apply { mkdirs() }
        var displayName = "Document.pdf"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = c.getString(idx) ?: displayName
            }
        }
        val rawFile = File(workDir, "${UUID.randomUUID()}.pdf")
        val opened = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(rawFile).use { out -> input.copyTo(out) }
            } ?: error("Cannot read PDF stream")
        }
        if (opened.isFailure) {
            return@withContext PdfOpenResult.Error(opened.exceptionOrNull()?.localizedMessage ?: "Failed to read PDF")
        }
        preparePdfFromFile(rawFile, displayName, password)
    }

    suspend fun preparePdfFromFile(
        sourceFile: File,
        displayName: String = sourceFile.name,
        password: String? = null
    ): PdfOpenResult = withContext(ioDispatcher) {
        try {
            val doc = if (password != null) {
                PDDocument.load(sourceFile, password, MemoryUsageSetting.setupTempFileOnly())
            } else {
                PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly())
            }
            doc.use { pdDoc ->
                val pageCount = pdDoc.numberOfPages.coerceAtLeast(1)
                val previewFile = if (pdDoc.isEncrypted) {
                    val unlocked = File(
                        File(context.cacheDir, "sign_work").apply { mkdirs() },
                        "unlocked_${UUID.randomUUID()}.pdf"
                    )
                    pdDoc.isAllSecurityToBeRemoved = true
                    pdDoc.save(unlocked)
                    unlocked
                } else {
                    sourceFile
                }
                PdfOpenResult.Ready(
                    workingCopy = previewFile,
                    displayName = displayName,
                    pageCount = pageCount,
                    password = password
                )
            }
        } catch (_: InvalidPasswordException) {
            PdfOpenResult.PasswordRequired(sourceFile, displayName)
        } catch (t: Throwable) {
            PdfOpenResult.Error(t.localizedMessage ?: "Could not open PDF")
        }
    }

    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(ioDispatcher) {
        val cacheKey = "${pdfFile.absolutePath}_${pageIndex}_$targetWidthPx"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val targetHeight = (targetWidthPx * ratio).toInt().coerceIn(200, 2400)
                        val bmp = Bitmap.createBitmap(targetWidthPx, targetHeight, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapCache.put(cacheKey, bmp)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    /**
     * Flattens confirmed signature placements onto a new output PDF file without mutating sourceFile.
     */
    suspend fun flattenToNewPdf(
        sourceFile: File,
        outputFile: File,
        placements: List<SignaturePlacement>,
        password: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val doc = if (password != null) {
                PDDocument.load(sourceFile, password, MemoryUsageSetting.setupTempFileOnly())
            } else {
                PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly())
            }
            doc.use { pdDoc ->
                if (pdDoc.isEncrypted) {
                    pdDoc.isAllSecurityToBeRemoved = true
                }
                val confirmed = placements.filter { it.isConfirmed }
                val total = confirmed.size.coerceAtLeast(1)

                confirmed.forEachIndexed { idx, placement ->
                    if (placement.pageIndex in 0 until pdDoc.numberOfPages) {
                        val page = pdDoc.getPage(placement.pageIndex)
                        val box: PDRectangle = page.cropBox ?: page.mediaBox
                        val pageBox = PdfPageBox(
                            lowerLeftX = box.lowerLeftX,
                            lowerLeftY = box.lowerLeftY,
                            width = box.width,
                            height = box.height,
                            rotationDegrees = page.rotation
                        )
                        val mapped = PdfCoordinateMapper.toPdfUserSpace(
                            normalizedX = placement.normalizedX,
                            normalizedY = placement.normalizedY,
                            normalizedWidth = placement.normalizedWidth,
                            normalizedHeight = placement.normalizedHeight,
                            overlayRotationDegrees = placement.rotationDegrees,
                            pageBox = pageBox
                        )
                        val pdImage = PDImageXObject.createFromByteArray(pdDoc, placement.pngBytes, "sig_${placement.id}")
                        PDPageContentStream(
                            pdDoc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { cs ->
                            val thetaRad = (mapped.totalRotationDegrees * PI / 180.0).toFloat()
                            cs.saveGraphicsState()
                            // Translate to placement center, rotate, shift back by half width/height, then scale to image size
                            val toCenter = Matrix.getTranslateInstance(mapped.centerX, mapped.centerY)
                            val rotate = Matrix.getRotateInstance(thetaRad.toDouble(), 0f, 0f)
                            val offsetFromCenter = Matrix.getTranslateInstance(-mapped.width / 2f, -mapped.height / 2f)
                            val scale = Matrix.getScaleInstance(mapped.width, mapped.height)

                            val combined = Matrix()
                            combined.concatenate(toCenter)
                            combined.concatenate(rotate)
                            combined.concatenate(offsetFromCenter)
                            combined.concatenate(scale)
                            cs.drawImage(pdImage, combined)

                            // Optional "Signed by" / Date stamp caption cleanly anchored under the signature
                            val captionParts = buildList {
                                if (placement.signedByText.isNotBlank()) add("Signed by ${placement.signedByText.trim()}")
                                if (placement.includeDateStamp && placement.dateStampText.isNotBlank()) add(placement.dateStampText.trim())
                            }
                            if (captionParts.isNotEmpty()) {
                                val caption = captionParts.joinToString(" • ")
                                val textMatrix = Matrix()
                                textMatrix.concatenate(toCenter)
                                textMatrix.concatenate(rotate)
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 8.5f)
                                cs.setNonStrokingColor(55, 65, 81)
                                cs.setTextMatrix(textMatrix)
                                cs.newLineAtOffset(-mapped.width / 2f, -mapped.height / 2f - 10f)
                                cs.showText(caption.take(72))
                                cs.endText()
                            }
                            cs.restoreGraphicsState()
                        }
                    }
                    onProgress((idx + 1).toFloat() / total.toFloat())
                }

                outputFile.parentFile?.mkdirs()
                pdDoc.save(outputFile)
                outputFile
            }
        }
    }
}
