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
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
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
                sample = maskPiiSample(pair.first),
                pageNumbers = pair.second.sorted()
            )
        }
    }

    private fun maskPiiSample(raw: String): String {
        if (raw.length <= 6) return "***"
        return raw.take(2) + "•••" + raw.takeLast(3)
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
     * over all user boxes + optional PII bands, stripping metadata, and rewriting flat raster pages
     * so underlying text cannot be selected or copied.
     */
    suspend fun redactPdf(
        sourceFile: File,
        outputFile: File,
        boxes: List<RedactionBox>,
        redactAllPagesWithBoxes: Boolean,
        scrubPiiBand: Boolean,
        stripMetadata: Boolean = true,
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
                        if (stripMetadata) {
                            outDoc.documentInformation = PDDocumentInformation()
                        }
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

                                val activeBoxes = if (redactAllPagesWithBoxes) {
                                    boxes
                                } else {
                                    boxes.filter { it.pageIndex == i }
                                }
                                activeBoxes.forEach { box ->
                                    val left = box.x.coerceIn(0f, 0.96f) * wPx
                                    val top = box.y.coerceIn(0f, 0.96f) * hPx
                                    val right = (left + box.w.coerceIn(0.04f, 1f) * wPx).coerceAtMost(wPx.toFloat())
                                    val bottom = (top + box.h.coerceIn(0.025f, 1f) * hPx).coerceAtMost(hPx.toFloat())
                                    canvas.drawRect(left, top, right, bottom, blackPaint)
                                }
                                if (scrubPiiBand && i in piiPages && activeBoxes.isEmpty()) {
                                    canvas.drawRect(wPx * 0.08f, hPx * 0.12f, wPx * 0.72f, hPx * 0.17f, blackPaint)
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
        watermarkOpacity: Float = 0.22f,
        watermarkStyle: WatermarkStyle = WatermarkStyle.DIAGONAL,
        pageNumberFormat: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
        stripMetadata: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                if (stripMetadata) {
                    doc.documentInformation = PDDocumentInformation()
                }
                val total = doc.numberOfPages.coerceAtLeast(1)
                val cleanWatermark = sanitizePdfText(watermarkText.take(36))
                val cleanHeader = sanitizePdfText(headerText.take(72))
                val cleanFooter = sanitizePdfText(footerText.take(48))
                val alpha = watermarkOpacity.coerceIn(0.08f, 0.7f)

                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        if (cleanWatermark.isNotBlank()) {
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(90, 95, 110)

                            when (watermarkStyle) {
                                WatermarkStyle.DIAGONAL -> {
                                    drawRotatedWatermark(
                                        cs = cs,
                                        text = cleanWatermark,
                                        fontSize = 44f,
                                        x = box.lowerLeftX + box.width * 0.22f,
                                        y = box.lowerLeftY + box.height * 0.38f,
                                        degrees = 36.0
                                    )
                                }
                                WatermarkStyle.CENTER -> {
                                    val approxW = PDType1Font.HELVETICA_BOLD.getStringWidth(cleanWatermark) / 1000f * 42f
                                    drawRotatedWatermark(
                                        cs = cs,
                                        text = cleanWatermark,
                                        fontSize = 42f,
                                        x = box.lowerLeftX + ((box.width - approxW) / 2f).coerceAtLeast(24f),
                                        y = box.lowerLeftY + box.height * 0.48f,
                                        degrees = 0.0
                                    )
                                }
                                WatermarkStyle.TILED -> {
                                    for (row in 0..2) {
                                        for (col in 0..1) {
                                            drawRotatedWatermark(
                                                cs = cs,
                                                text = cleanWatermark,
                                                fontSize = 24f,
                                                x = box.lowerLeftX + box.width * (0.12f + col * 0.45f),
                                                y = box.lowerLeftY + box.height * (0.18f + row * 0.30f),
                                                degrees = 30.0
                                            )
                                        }
                                    }
                                }
                            }

                            val opaque = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1.0f }
                            cs.setGraphicsStateParameters(opaque)
                        }

                        if (cleanHeader.isNotBlank()) {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, 9.5f)
                            cs.setNonStrokingColor(80, 90, 105)
                            cs.newLineAtOffset(box.lowerLeftX + 36f, box.upperRightY - 24f)
                            cs.showText(cleanHeader)
                            cs.endText()
                        }

                        val pageLabel = when (pageNumberFormat) {
                            PageNumberFormat.PAGE_X_OF_Y -> "Page ${i + 1} of $total"
                            PageNumberFormat.X_SLASH_Y -> "${i + 1} / $total"
                            PageNumberFormat.NUMBER_ONLY -> "${i + 1}"
                        }
                        val bottomParts = buildList {
                            if (cleanFooter.isNotBlank()) add(cleanFooter)
                            if (includePageNumbers) add(pageLabel)
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

    private fun drawRotatedWatermark(
        cs: PDPageContentStream,
        text: String,
        fontSize: Float,
        x: Float,
        y: Float,
        degrees: Double
    ) {
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
        val m = Matrix()
        m.concatenate(Matrix.getTranslateInstance(x, y))
        if (degrees != 0.0) {
            m.concatenate(Matrix.getRotateInstance(Math.toRadians(degrees), 0f, 0f))
        }
        cs.setTextMatrix(m)
        cs.showText(text)
        cs.endText()
    }

    suspend fun compressPdfForBatch(
        sourceFile: File,
        outputFile: File,
        preset: BatchCompressionPreset,
        stripMetadata: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                        if (stripMetadata) {
                            outDoc.documentInformation = PDDocumentInformation()
                        }
                        val count = renderer.pageCount.coerceAtLeast(1)
                        for (i in 0 until count) {
                            coroutineContext.ensureActive()
                            renderer.openPage(i).use { page ->
                                val wPx = preset.maxWidthPx
                                val hPx = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * wPx)
                                    .toInt().coerceIn(280, 2200)
                                val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                                outDoc.addPage(pdPage)
                                val jpg = JPEGFactory.createFromImage(outDoc, bmp, preset.jpegQuality)
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

            val added = (rightSet - leftSet).take(50).toList()
            val removed = (leftSet - rightSet).take(50).toList()
            val maxPages = maxOf(leftPages.size, rightPages.size)
            var changedPages = 0
            for (i in 0 until maxPages) {
                if (leftPages.getOrNull(i).orEmpty().trim() != rightPages.getOrNull(i).orEmpty().trim()) {
                    changedPages++
                }
            }

            val reportLines = buildList {
                add("PDF Comparison Report")
                add("Document A pages: ${leftPages.size} | Document B pages: ${rightPages.size}")
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

    suspend fun runCustomWorkflowRecipe(
        sourceFile: File,
        outputFile: File,
        config: WorkflowRecipeConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            var currentInput = sourceFile
            val tempFiles = mutableListOf<File>()
            try {
                if (config.compress) {
                    onProgress(0.18f, "Step 1: Compressing PDF…")
                    val compressed = File(context.cacheDir, "wf_comp_${UUID.randomUUID()}.pdf")
                    compressPdfForBatch(
                        sourceFile = currentInput,
                        outputFile = compressed,
                        preset = config.compressionPreset,
                        stripMetadata = config.stripMetadata
                    ).getOrThrow()
                    tempFiles.add(compressed)
                    currentInput = compressed
                }

                if (config.watermarkText.isNotBlank() || config.includePageNumbers || config.stripMetadata) {
                    onProgress(0.55f, "Applying stamps & metadata cleanup…")
                    val stamped = File(context.cacheDir, "wf_stamp_${UUID.randomUUID()}.pdf")
                    stampWatermarkAndNumbers(
                        sourceFile = currentInput,
                        outputFile = stamped,
                        watermarkText = config.watermarkText,
                        headerText = "",
                        footerText = "",
                        includePageNumbers = config.includePageNumbers,
                        stripMetadata = config.stripMetadata
                    ).getOrThrow()
                    tempFiles.add(stamped)
                    currentInput = stamped
                }

                onProgress(0.82f, if (config.password.isNotBlank()) "Encrypting with AES-256…" else "Finalizing PDF…")
                outputFile.parentFile?.mkdirs()
                PDDocument.load(currentInput, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    if (config.stripMetadata) {
                        doc.documentInformation = PDDocumentInformation()
                    }
                    if (config.password.isNotBlank()) {
                        val ap = AccessPermission()
                        val spp = StandardProtectionPolicy(config.password, config.password, ap).apply {
                            encryptionKeyLength = 256
                        }
                        doc.protect(spp)
                    }
                    doc.save(outputFile)
                }
                onProgress(1f, "Workflow complete")
                outputFile
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun runBatchOperation(
        sources: List<Pair<File, String>>,
        outputZip: File,
        mode: BatchOperationMode,
        compressionPreset: BatchCompressionPreset = BatchCompressionPreset.BALANCED,
        watermarkText: String = "",
        includePageNumbers: Boolean = true,
        password: String = "",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<BatchRunSummary> = withContext(ioDispatcher) {
        runCatching {
            require(sources.isNotEmpty()) { "Select at least one PDF" }
            if (mode == BatchOperationMode.LOCK_AES256) {
                require(password.length >= 4) { "Enter a password (min 4 characters) for Batch Lock" }
            }
            outputZip.parentFile?.mkdirs()

            var succeeded = 0
            val failed = mutableListOf<String>()
            var inputBytes = 0L
            val usedEntryNames = mutableSetOf<String>()

            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                sources.forEachIndexed { idx, (file, rawName) ->
                    coroutineContext.ensureActive()
                    val safeBase = rawName.substringBeforeLast('.')
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .ifBlank { "document_${idx + 1}" }
                    onProgress(
                        idx.toFloat() / sources.size,
                        "Processing ${idx + 1} of ${sources.size}: $rawName"
                    )
                    inputBytes += file.length()

                    val tempOut = File(context.cacheDir, "batch_${UUID.randomUUID()}.tmp")
                    val stepResult = runCatching {
                        when (mode) {
                            BatchOperationMode.COMPRESS -> {
                                compressPdfForBatch(file, tempOut, compressionPreset).getOrThrow()
                                uniqueZipName("${safeBase}_compressed.pdf", usedEntryNames)
                            }
                            BatchOperationMode.WATERMARK_AND_NUMBERS -> {
                                stampWatermarkAndNumbers(
                                    sourceFile = file,
                                    outputFile = tempOut,
                                    watermarkText = watermarkText,
                                    headerText = "",
                                    footerText = "",
                                    includePageNumbers = includePageNumbers
                                ).getOrThrow()
                                uniqueZipName("${safeBase}_stamped.pdf", usedEntryNames)
                            }
                            BatchOperationMode.LOCK_AES256 -> {
                                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                                    val ap = AccessPermission()
                                    val spp = StandardProtectionPolicy(password, password, ap).apply {
                                        encryptionKeyLength = 256
                                    }
                                    doc.protect(spp)
                                    doc.save(tempOut)
                                }
                                uniqueZipName("${safeBase}_locked.pdf", usedEntryNames)
                            }
                            BatchOperationMode.EXTRACT_TEXT -> {
                                val pages = extractPagesText(file)
                                val combined = pages.mapIndexed { pIdx, text ->
                                    "--- Page ${pIdx + 1} ---\n${text.trim()}"
                                }.joinToString("\n\n")
                                tempOut.writeText(combined, Charsets.UTF_8)
                                uniqueZipName("${safeBase}_text.txt", usedEntryNames)
                            }
                        }
                    }

                    stepResult.onSuccess { entryName ->
                        zos.putNextEntry(ZipEntry(entryName))
                        tempOut.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        succeeded++
                    }.onFailure {
                        failed.add(rawName)
                    }
                    tempOut.delete()
                    onProgress((idx + 1).toFloat() / sources.size, "Completed ${idx + 1} of ${sources.size}")
                }
            }

            require(succeeded > 0) { "All batch files failed to process" }
            BatchRunSummary(
                totalFiles = sources.size,
                succeededCount = succeeded,
                failedNames = failed,
                inputTotalBytes = inputBytes,
                outputTotalBytes = outputZip.length()
            )
        }
    }

    private fun uniqueZipName(candidate: String, used: MutableSet<String>): String {
        if (used.add(candidate)) return candidate
        val base = candidate.substringBeforeLast('.')
        val ext = candidate.substringAfterLast('.', "")
        var counter = 2
        while (true) {
            val next = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
            if (used.add(next)) return next
            counter++
        }
    }

    suspend fun extractPdfToTextFile(
        sourcePdf: File,
        outputTxt: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            outputTxt.parentFile?.mkdirs()
            val pages = extractPagesText(sourcePdf)
            val sb = StringBuilder()
            pages.forEachIndexed { idx, txt ->
                coroutineContext.ensureActive()
                sb.append("=== Page ").append(idx + 1).append(" ===\n")
                sb.append(txt.trim()).append("\n\n")
                onProgress((idx + 1).toFloat() / pages.size.coerceAtLeast(1))
            }
            outputTxt.writeText(sb.toString().trim(), Charsets.UTF_8)
            pages.size
        }
    }

    suspend fun exportPdfPagesAsPngZip(
        sourcePdf: File,
        outputZip: File,
        targetWidthPx: Int = 1280,
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
                                val w = targetWidthPx.coerceIn(720, 2200)
                                val h = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * w)
                                    .toInt().coerceIn(300, 3200)
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
        outputFile: File,
        grayscaleClean: Boolean = false,
        addVerificationWatermark: Boolean = false
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

                    decodeScaledBitmap(frontUri, 1200)?.let { raw ->
                        val finalBmp = if (grayscaleClean) applyScanCleanupFilter(raw, ScanCleanMode.BLACK_AND_WHITE) else raw
                        if (finalBmp !== raw) raw.recycle()
                        val frontImg = JPEGFactory.createFromImage(doc, finalBmp, 0.90f)
                        finalBmp.recycle()
                        cs.drawImage(frontImg, x, ph - 82f - cardH, cardW, cardH)
                    }

                    if (backUri != null) {
                        decodeScaledBitmap(backUri, 1200)?.let { raw ->
                            val finalBmp = if (grayscaleClean) applyScanCleanupFilter(raw, ScanCleanMode.BLACK_AND_WHITE) else raw
                            if (finalBmp !== raw) raw.recycle()
                            val backImg = JPEGFactory.createFromImage(doc, finalBmp, 0.90f)
                            finalBmp.recycle()
                            cs.drawImage(backImg, x, ph - 122f - cardH * 2f, cardW, cardH)
                        }
                    }

                    if (addVerificationWatermark) {
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.28f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(150, 35, 35)
                        drawRotatedWatermark(
                            cs = cs,
                            text = "COPY FOR VERIFICATION ONLY",
                            fontSize = 24f,
                            x = pw * 0.16f,
                            y = ph * 0.44f,
                            degrees = 32.0
                        )
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
        cleanMode: ScanCleanMode = ScanCleanMode.BLACK_AND_WHITE,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(imageUris.isNotEmpty()) { "Select at least one image" }
            outputFile.parentFile?.mkdirs()
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                imageUris.forEachIndexed { idx, uri ->
                    coroutineContext.ensureActive()
                    val rawBmp = decodeScaledBitmap(uri, 1600) ?: return@forEachIndexed
                    val processed = if (cleanMode == ScanCleanMode.ORIGINAL) {
                        rawBmp
                    } else {
                        applyScanCleanupFilter(rawBmp, cleanMode)
                    }
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
                            formatLabel = readableBarcodeFormat(bc.format),
                            rawValue = bc.rawValue.orEmpty(),
                            displayType = when {
                                bc.url != null -> "URL"
                                bc.wifi != null -> "Wi-Fi"
                                bc.email != null -> "Email"
                                bc.phone != null -> "Phone"
                                bc.contactInfo != null -> "Contact"
                                else -> "Text"
                            }
                        )
                    }.filter { it.rawValue.isNotBlank() }
                } finally {
                    scanner.close()
                }
            }
        }

    private fun readableBarcodeFormat(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "Barcode"
    }

    suspend fun translatePdfOffline(
        sourceFile: File,
        sourceLang: TranslateTargetLang,
        targetLang: TranslateTargetLang,
        bilingualMode: Boolean,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(sourceLang != targetLang) { "Choose a different source and target language" }
            val pages = extractPagesText(sourceFile)
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang.code)
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
                    val pageBlock = if (bilingualMode) {
                        "--- Page ${idx + 1} (${sourceLang.displayName} → ${targetLang.displayName}) ---\n" +
                            "[Original]\n$snippet\n\n[Translated]\n$out"
                    } else {
                        "--- Page ${idx + 1} (${targetLang.displayName}) ---\n$out"
                    }
                    translatedPages.add(pageBlock)
                    onProgress(0.2f + 0.7f * (idx + 1).toFloat() / pages.size.coerceAtLeast(1))
                }
                createTextPdf(
                    rawText = translatedPages.joinToString("\n\n"),
                    outputFile = outputFile,
                    title = "Translated (${sourceLang.displayName} to ${targetLang.displayName})"
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

    private fun applyScanCleanupFilter(src: Bitmap, mode: ScanCleanMode): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = when (mode) {
            ScanCleanMode.BLACK_AND_WHITE -> ColorMatrix().apply {
                setSaturation(0f)
                val contrast = 1.48f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 18f
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
            ScanCleanMode.COLOR_ENHANCED -> ColorMatrix().apply {
                setSaturation(1.22f)
                val contrast = 1.24f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 12f
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
            ScanCleanMode.ORIGINAL -> ColorMatrix()
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
