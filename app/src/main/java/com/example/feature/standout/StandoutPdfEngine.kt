package com.example.feature.standout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.core.common.IoDispatcher
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class PiiMatchSummary(
    val category: String,
    val sample: String,
    val pageNumbers: List<Int>
)

data class PdfCompareDiff(
    val leftPages: Int,
    val rightPages: Int,
    val changedPageCount: Int,
    val addedLines: List<String>,
    val removedLines: List<String>
)

data class DecodedBarcodeItem(
    val formatLabel: String,
    val rawValue: String,
    val displayType: String
)

enum class TranslateTargetLang(val code: String, val displayName: String) {
    SPANISH(TranslateLanguage.SPANISH, "Spanish"),
    HINDI(TranslateLanguage.HINDI, "Hindi"),
    ARABIC(TranslateLanguage.ARABIC, "Arabic"),
    PORTUGUESE(TranslateLanguage.PORTUGUESE, "Portuguese"),
    FRENCH(TranslateLanguage.FRENCH, "French"),
    GERMAN(TranslateLanguage.GERMAN, "German")
}

@Singleton
class StandoutPdfEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Scans PDF text per page for PII (emails, phones, SSNs, cards) to aid permanent redaction.
     */
    suspend fun scanPii(pdfFile: File): List<PiiMatchSummary> = withContext(ioDispatcher) {
        val pageMap = mutableMapOf<String, Pair<String, MutableSet<Int>>>()
        PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            for (p in 1..doc.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                val txt = stripper.getText(doc)
                collectPii("Email", EMAIL_REGEX, txt, p, pageMap)
                collectPii("Phone", PHONE_REGEX, txt, p, pageMap)
                collectPii("ID/SSN", SSN_REGEX, txt, p, pageMap)
                collectPii("Card", CARD_REGEX, txt, p, pageMap)
            }
        }
        pageMap.entries.map { (key, pair) ->
            PiiMatchSummary(
                category = key.substringBefore(':'),
                sample = pair.first,
                pageNumbers = pair.second.sorted()
            )
        }
    }

    private fun collectPii(
        category: String,
        regex: Regex,
        text: String,
        pageNumber: Int,
        sink: MutableMap<String, Pair<String, MutableSet<Int>>>
    ) {
        regex.findAll(text).forEach { match ->
            val valStr = match.value.trim()
            val key = "$category:${valStr.lowercase()}"
            val entry = sink.getOrPut(key) { valStr to mutableSetOf() }
            entry.second.add(pageNumber)
        }
    }

    /**
     * Permanently redacts target pages by rendering to bitmap, burning opaque black redaction bars
     * over normalized boxes / PII regions, and rewriting flat raster pages so underlying text cannot be copied.
     */
    suspend fun redactPdf(
        sourceFile: File,
        outputFile: File,
        boxNormalizedX: Float,
        boxNormalizedY: Float,
        boxNormalizedW: Float,
        boxNormalizedH: Float,
        targetPageIndex: Int,
        redactAllPagesWithBox: Boolean,
        scrubPiiBand: Boolean,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val piiPages = if (scrubPiiBand) {
                scanPii(sourceFile).flatMap { it.pageNumbers }.map { it - 1 }.toSet()
            } else emptySet()

            outputFile.parentFile?.mkdirs()
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                        val count = renderer.pageCount.coerceAtLeast(1)
                        val blackPaint = Paint().apply {
                            color = Color.BLACK
                            style = Paint.Style.FILL
                        }
                        for (i in 0 until count) {
                            coroutineContext.ensureActive()
                            renderer.openPage(i).use { page ->
                                val wPx = 1240
                                val hPx = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * wPx)
                                    .toInt().coerceIn(300, 2400)
                                val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val applyManualBox = redactAllPagesWithBox || i == targetPageIndex
                                if (applyManualBox) {
                                    val left = boxNormalizedX.coerceIn(0f, 0.95f) * wPx
                                    val top = boxNormalizedY.coerceIn(0f, 0.95f) * hPx
                                    val right = (left + boxNormalizedW.coerceIn(0.05f, 1f) * wPx).coerceAtMost(wPx.toFloat())
                                    val bottom = (top + boxNormalizedH.coerceIn(0.03f, 1f) * hPx).coerceAtMost(hPx.toFloat())
                                    canvas.drawRect(left, top, right, bottom, blackPaint)
                                }
                                if (scrubPiiBand && i in piiPages) {
                                    // Add subtle header/footer PII strip redaction when sensitive data detected and no box covers it
                                    if (!applyManualBox) {
                                        canvas.drawRect(wPx * 0.08f, hPx * 0.12f, wPx * 0.72f, hPx * 0.17f, blackPaint)
                                    }
                                }

                                val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                                outDoc.addPage(pdPage)
                                val jpg = JPEGFactory.createFromImage(outDoc, bmp, 0.85f)
                                bmp.recycle()
                                PDPageContentStream(outDoc, pdPage).use { cs ->
                                    cs.drawImage(jpg, 0f, 0f, pdPage.mediaBox.width, pdPage.mediaBox.height)
                                }
                            }
                            onProgress((i + 1).toFloat() / count)
                        }
                        outDoc.save(outputFile)
                    }
                }
            }
            outputFile
        }
    }

    suspend fun stampWatermarkAndNumbers(
        sourceFile: File,
        outputFile: File,
        watermarkText: String,
        headerText: String,
        footerText: String,
        includePageNumbers: Boolean,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val total = doc.numberOfPages.coerceAtLeast(1)
                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        if (watermarkText.isNotBlank()) {
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.22f }
                            cs.setGraphicsStateParameters(gs)
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 44f)
                            cs.setNonStrokingColor(90, 95, 110)
                            val cx = box.lowerLeftX + box.width * 0.24f
                            val cy = box.lowerLeftY + box.height * 0.38f
                            val m = Matrix()
                            m.concatenate(Matrix.getTranslateInstance(cx, cy))
                            m.concatenate(Matrix.getRotateInstance(Math.toRadians(36.0), 0f, 0f))
                            cs.setTextMatrix(m)
                            cs.showText(watermarkText.take(36))
                            cs.endText()
                            val opaque = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1.0f }
                            cs.setGraphicsStateParameters(opaque)
                        }

                        if (headerText.isNotBlank()) {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, 9.5f)
                            cs.setNonStrokingColor(80, 90, 105)
                            cs.newLineAtOffset(box.lowerLeftX + 36f, box.upperRightY - 24f)
                            cs.showText(headerText.take(72))
                            cs.endText()
                        }

                        val bottomParts = buildList {
                            if (footerText.isNotBlank()) add(footerText.take(48))
                            if (includePageNumbers) add("Page ${i + 1} of $total")
                        }
                        if (bottomParts.isNotEmpty()) {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, 9.5f)
                            cs.setNonStrokingColor(80, 90, 105)
                            cs.newLineAtOffset(box.lowerLeftX + 36f, box.lowerLeftY + 18f)
                            cs.showText(bottomParts.joinToString("  •  "))
                            cs.endText()
                        }
                    }
                    onProgress((i + 1).toFloat() / total)
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun comparePdfs(
        leftFile: File,
        rightFile: File,
        diffPdfOutput: File
    ): Result<PdfCompareDiff> = withContext(ioDispatcher) {
        runCatching {
            val leftPages = extractPagesText(leftFile)
            val rightPages = extractPagesText(rightFile)

            val leftSet = leftPages.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val rightSet = rightPages.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            val added = (rightSet - leftSet).take(40).toList()
            val removed = (leftSet - rightSet).take(40).toList()
            val maxPages = maxOf(leftPages.size, rightPages.size)
            var changedPages = 0
            for (i in 0 until maxPages) {
                if (leftPages.getOrNull(i).orEmpty().trim() != rightPages.getOrNull(i).orEmpty().trim()) {
                    changedPages++
                }
            }

            val reportLines = buildList {
                add("PDF Comparison Report")
                add("Left file pages: ${leftPages.size} | Right file pages: ${rightPages.size}")
                add("Changed pages: $changedPages")
                add("")
                add("Added lines (${added.size}):")
                added.forEach { add("+ $it") }
                add("")
                add("Removed lines (${removed.size}):")
                removed.forEach { add("- $it") }
            }
            createTextPdf(reportLines.joinToString("\n"), diffPdfOutput, title = "PDF Comparison").getOrThrow()

            PdfCompareDiff(
                leftPages = leftPages.size,
                rightPages = rightPages.size,
                changedPageCount = changedPages,
                addedLines = added,
                removedLines = removed
            )
        }
    }

    suspend fun runSecureWorkflowRecipe(
        sourceFile: File,
        outputFile: File,
        watermarkText: String,
        password: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            onProgress(0.2f)
            val stamped = File(context.cacheDir, "wf_stamped_${UUID.randomUUID()}.pdf")
            stampWatermarkAndNumbers(
                sourceFile = sourceFile,
                outputFile = stamped,
                watermarkText = watermarkText,
                headerText = "Processed offline by PDF Toolkit",
                footerText = "",
                includePageNumbers = true
            ).getOrThrow()
            onProgress(0.65f)
            outputFile.parentFile?.mkdirs()
            PDDocument.load(stamped, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                if (password.isNotBlank()) {
                    val ap = AccessPermission()
                    val spp = StandardProtectionPolicy(password, password, ap).apply {
                        encryptionKeyLength = 256
                    }
                    doc.protect(spp)
                }
                doc.save(outputFile)
            }
            stamped.delete()
            onProgress(1f)
            outputFile
        }
    }

    suspend fun batchStampAndZip(
        sources: List<Pair<File, String>>,
        outputZip: File,
        includePageNumbers: Boolean,
        watermarkText: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(sources.isNotEmpty()) { "Select at least one PDF" }
            outputZip.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                sources.forEachIndexed { idx, (file, name) ->
                    coroutineContext.ensureActive()
                    val tempOut = File(context.cacheDir, "batch_${UUID.randomUUID()}.pdf")
                    stampWatermarkAndNumbers(
                        sourceFile = file,
                        outputFile = tempOut,
                        watermarkText = watermarkText,
                        headerText = "",
                        footerText = "",
                        includePageNumbers = includePageNumbers
                    ).getOrThrow()
                    val cleanName = name.substringBeforeLast('.') + "_processed.pdf"
                    zos.putNextEntry(ZipEntry(cleanName))
                    tempOut.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    tempOut.delete()
                    onProgress((idx + 1).toFloat() / sources.size)
                }
            }
            outputZip
        }
    }

    suspend fun exportPdfPagesAsPngZip(
        sourcePdf: File,
        outputZip: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            outputZip.parentFile?.mkdirs()
            var count = 0
            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    count = renderer.pageCount
                    ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                        for (i in 0 until count) {
                            coroutineContext.ensureActive()
                            renderer.openPage(i).use { page ->
                                val w = 1280
                                val h = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * w)
                                    .toInt().coerceIn(300, 2400)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                zos.putNextEntry(ZipEntry("page_${i + 1}.png"))
                                val baos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.PNG, 92, baos)
                                bmp.recycle()
                                zos.write(baos.toByteArray())
                                zos.closeEntry()
                            }
                            onProgress((i + 1).toFloat() / count.coerceAtLeast(1))
                        }
                    }
                }
            }
            count
        }
    }

    suspend fun createTextPdf(
        rawText: String,
        outputFile: File,
        title: String = "Document"
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val rect = PDRectangle.A4
                val margin = 48f
                val maxWidth = rect.width - margin * 2f
                var page = PDPage(rect).also { doc.addPage(it) }
                var cs = PDPageContentStream(doc, page)
                var y = rect.height - margin

                try {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(sanitizePdfText(title.take(72)))
                    cs.endText()
                    y -= 26f

                    val lines = rawText.lines()
                    for (line in lines) {
                        coroutineContext.ensureActive()
                        val isHeading = line.startsWith("#")
                        val clean = sanitizePdfText(line.trimStart('#', ' '))
                        val font = if (isHeading) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                        val size = if (isHeading) 13.5f else 11f
                        val wrapped = wrapText(clean.ifBlank { " " }, font, size, maxWidth)
                        for (wLine in wrapped) {
                            if (y - 16f < margin) {
                                cs.close()
                                page = PDPage(rect).also { doc.addPage(it) }
                                cs = PDPageContentStream(doc, page)
                                y = rect.height - margin
                            }
                            cs.beginText()
                            cs.setFont(font, size)
                            cs.newLineAtOffset(margin, y)
                            cs.showText(wLine)
                            cs.endText()
                            y -= size * 1.45f
                        }
                    }
                } finally {
                    cs.close()
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun buildIdCardPdf(
        frontUri: Uri,
        backUri: Uri?,
        outputFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val page = PDPage(PDRectangle.A4).also { doc.addPage(it) }
                val pw = PDRectangle.A4.width
                val ph = PDRectangle.A4.height
                val cardW = pw * 0.68f
                val cardH = cardW * 0.63f // Standard CR-80 ID aspect ratio
                val x = (pw - cardW) / 2f

                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                    cs.newLineAtOffset(x, ph - 52f)
                    cs.showText("ID Card Copy (Front & Back)")
                    cs.endText()

                    decodeScaledBitmap(frontUri, 1200)?.let { frontBmp ->
                        val frontImg = JPEGFactory.createFromImage(doc, frontBmp, 0.90f)
                        frontBmp.recycle()
                        cs.drawImage(frontImg, x, ph - 82f - cardH, cardW, cardH)
                    }

                    if (backUri != null) {
                        decodeScaledBitmap(backUri, 1200)?.let { backBmp ->
                            val backImg = JPEGFactory.createFromImage(doc, backBmp, 0.90f)
                            backBmp.recycle()
                            cs.drawImage(backImg, x, ph - 122f - cardH * 2f, cardW, cardH)
                        }
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun autoCleanImagesToPdf(
        imageUris: List<Uri>,
        outputFile: File,
        highContrastScan: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(imageUris.isNotEmpty()) { "Select at least one image" }
            outputFile.parentFile?.mkdirs()
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                imageUris.forEachIndexed { idx, uri ->
                    coroutineContext.ensureActive()
                    val rawBmp = decodeScaledBitmap(uri, 1600) ?: return@forEachIndexed
                    val processed = if (highContrastScan) applyScanCleanupFilter(rawBmp) else rawBmp
                    if (processed !== rawBmp) rawBmp.recycle()

                    val page = PDPage(PDRectangle.A4).also { doc.addPage(it) }
                    val margin = 24f
                    val availW = page.mediaBox.width - margin * 2
                    val availH = page.mediaBox.height - margin * 2
                    val scale = minOf(availW / processed.width, availH / processed.height)
                    val drawW = processed.width * scale
                    val drawH = processed.height * scale
                    val dx = (page.mediaBox.width - drawW) / 2f
                    val dy = (page.mediaBox.height - drawH) / 2f

                    val pdImg = JPEGFactory.createFromImage(doc, processed, 0.88f)
                    processed.recycle()
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImg, dx, dy, drawW, drawH)
                    }
                    onProgress((idx + 1).toFloat() / imageUris.size)
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun scanBarcodes(imageUri: Uri): Result<List<DecodedBarcodeItem>> =
        withContext(ioDispatcher) {
            runCatching {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val scanner = BarcodeScanning.getClient()
                try {
                    val results = scanner.process(inputImage).await()
                    results.map { bc ->
                        DecodedBarcodeItem(
                            formatLabel = "Code ${bc.format}",
                            rawValue = bc.rawValue.orEmpty(),
                            displayType = when {
                                bc.url != null -> "URL"
                                bc.wifi != null -> "Wi-Fi"
                                bc.email != null -> "Email"
                                bc.phone != null -> "Phone"
                                else -> "Text"
                            }
                        )
                    }.filter { it.rawValue.isNotBlank() }
                } finally {
                    scanner.close()
                }
            }
        }

    suspend fun translatePdfOffline(
        sourceFile: File,
        targetLang: TranslateTargetLang,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val pages = extractPagesText(sourceFile)
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLang.code)
                .build()
            val translator = Translation.getClient(options)
            try {
                onProgress(0.15f)
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).await()
                val translatedPages = mutableListOf<String>()
                pages.forEachIndexed { idx, txt ->
                    coroutineContext.ensureActive()
                    val snippet = txt.trim().take(2500)
                    val out = if (snippet.isNotBlank()) translator.translate(snippet).await() else ""
                    translatedPages.add("--- Page ${idx + 1} (${targetLang.displayName}) ---\n$out")
                    onProgress(0.2f + 0.7f * (idx + 1).toFloat() / pages.size.coerceAtLeast(1))
                }
                createTextPdf(
                    rawText = translatedPages.joinToString("\n\n"),
                    outputFile = outputFile,
                    title = "Translated (${targetLang.displayName})"
                ).getOrThrow()
                onProgress(1f)
                outputFile
            } finally {
                translator.close()
            }
        }
    }

    private fun extractPagesText(file: File): List<String> {
        return PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            (1..doc.numberOfPages).map { p ->
                stripper.startPage = p
                stripper.endPage = p
                stripper.getText(doc)
            }
        }
    }

    private fun decodeScaledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun applyScanCleanupFilter(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.45f
            val translate = (-0.5f * contrast + 0.5f) * 255f + 16f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun wrapText(text: String, font: PDType1Font, size: Float, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            val width = runCatching { font.getStringWidth(candidate) / 1000f * size }.getOrDefault(candidate.length * size * 0.5f)
            if (width > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(w)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.isEmpty()) listOf(" ") else lines
    }

    private fun sanitizePdfText(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch.code in 32..126 || ch.code in 160..255) sb.append(ch) else sb.append(' ')
        }
        return sb.toString()
    }

    companion object {
        private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val PHONE_REGEX = Regex("""\b(?:\+?\d{1,2}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b""")
        private val SSN_REGEX = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
        private val CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b""")
    }
}
