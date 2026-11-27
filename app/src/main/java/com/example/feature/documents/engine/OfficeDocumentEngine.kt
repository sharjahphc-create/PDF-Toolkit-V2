package com.example.feature.documents.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.common.IoDispatcher
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

data class DocxParagraphItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isHeading: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

data class DocxDocumentModel(
    val file: File?,
    val displayName: String,
    val paragraphs: List<DocxParagraphItem>,
    val tableCount: Int = 0
)

data class XlsxSheetModel(
    val name: String,
    val rows: List<List<String>>
)

data class XlsxWorkbookModel(
    val file: File?,
    val displayName: String,
    val sheets: List<XlsxSheetModel>
)

sealed interface PptxShapeElement {
    val leftPt: Float
    val topPt: Float
    val widthPt: Float
    val heightPt: Float

    data class TextBox(
        override val leftPt: Float,
        override val topPt: Float,
        override val widthPt: Float,
        override val heightPt: Float,
        val text: String,
        val fontSizePt: Float,
        val isBold: Boolean,
        val textColorRgb: Int
    ) : PptxShapeElement

    data class Picture(
        override val leftPt: Float,
        override val topPt: Float,
        override val widthPt: Float,
        override val heightPt: Float,
        val imageBytes: ByteArray
    ) : PptxShapeElement
}

data class PptxSlideModel(
    val index: Int,
    val title: String,
    val bodyLines: List<String>,
    val notes: String,
    val backgroundColorRgb: Int,
    val elements: List<PptxShapeElement>
)

data class PptxPresentationModel(
    val file: File,
    val displayName: String,
    val slideWidthPt: Float,
    val slideHeightPt: Float,
    val slides: List<PptxSlideModel>
)

@Singleton
class OfficeDocumentEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    init {
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }

    fun copyUriToWorkFile(uri: Uri, fallbackExt: String): Pair<File, String>? {
        val dir = File(context.cacheDir, "office_work").apply { mkdirs() }
        val displayName = queryDisplayName(uri) ?: "Document.$fallbackExt"
        val ext = displayName.substringAfterLast('.', fallbackExt).ifBlank { fallbackExt }
        val target = File(dir, "doc_${UUID.randomUUID()}.$ext")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return null
            target to displayName
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }

    // ==================== WORD (.docx) ====================

    suspend fun readDocx(file: File, displayName: String = file.name): Result<DocxDocumentModel> =
        withContext(ioDispatcher) {
            runCatching {
                FileInputStream(file).use { fis ->
                    XWPFDocument(fis).use { doc ->
                        val items = doc.paragraphs.map { p ->
                            val firstRun = p.runs.firstOrNull()
                            val style = p.style.orEmpty()
                            val isHeading = style.contains("Heading", ignoreCase = true) ||
                                style.contains("Title", ignoreCase = true)
                            DocxParagraphItem(
                                text = p.text.orEmpty(),
                                isHeading = isHeading,
                                isBold = firstRun?.isBold == true || isHeading,
                                isItalic = firstRun?.isItalic == true
                            )
                        }.ifEmpty {
                            listOf(DocxParagraphItem(text = "", isHeading = false))
                        }
                        DocxDocumentModel(
                            file = file,
                            displayName = displayName,
                            paragraphs = items,
                            tableCount = doc.tables.size
                        )
                    }
                }
            }
        }

    fun createNewDocxModel(): DocxDocumentModel = DocxDocumentModel(
        file = null,
        displayName = "Untitled.docx",
        paragraphs = listOf(
            DocxParagraphItem(text = "Document Title", isHeading = true, isBold = true),
            DocxParagraphItem(text = "Start typing your offline Word document here.")
        )
    )

    suspend fun saveDocx(model: DocxDocumentModel, outputFile: File): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                outputFile.parentFile?.mkdirs()
                val doc = if (model.file != null && model.file.exists()) {
                    FileInputStream(model.file).use { XWPFDocument(it) }
                } else {
                    XWPFDocument()
                }
                doc.use { xwpf ->
                    val existing = xwpf.paragraphs.toList()
                    model.paragraphs.forEachIndexed { idx, item ->
                        coroutineContext.ensureActive()
                        val para = if (idx < existing.size) {
                            val p = existing[idx]
                            for (rIdx in p.runs.size - 1 downTo 0) {
                                p.removeRun(rIdx)
                            }
                            p
                        } else {
                            xwpf.createParagraph()
                        }
                        if (item.isHeading && para.style.isNullOrBlank()) {
                            para.style = "Heading1"
                        }
                        val run = para.createRun()
                        run.setText(item.text)
                        run.isBold = item.isBold || item.isHeading
                        run.isItalic = item.isItalic
                        if (item.isHeading) {
                            run.fontSize = 16
                        }
                    }
                    // Clear excess trailing paragraphs if user deleted lines
                    if (existing.size > model.paragraphs.size) {
                        for (idx in existing.size - 1 downTo model.paragraphs.size) {
                            val pos = xwpf.getPosOfParagraph(existing[idx])
                            if (pos >= 0) xwpf.removeBodyElement(pos)
                        }
                    }
                    FileOutputStream(outputFile).use { fos -> xwpf.write(fos) }
                }
                outputFile
            }
        }

    suspend fun exportDocxToPdf(model: DocxDocumentModel, outputFile: File): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                outputFile.parentFile?.mkdirs()
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { pdf ->
                    val pageRect = PDRectangle.LETTER
                    val margin = 54f
                    val maxWidth = pageRect.width - 2f * margin
                    var page = PDPage(pageRect).also { pdf.addPage(it) }
                    var cs = PDPageContentStream(pdf, page)
                    var cursorY = pageRect.height - margin

                    try {
                        for (para in model.paragraphs) {
                            coroutineContext.ensureActive()
                            val font = when {
                                para.isHeading || para.isBold -> PDType1Font.HELVETICA_BOLD
                                para.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                                else -> PDType1Font.HELVETICA
                            }
                            val fontSize = if (para.isHeading) 16f else 11.5f
                            val lineHeight = fontSize * 1.45f
                            val lines = wrapPdfText(para.text.ifBlank { " " }, font, fontSize, maxWidth)

                            for (line in lines) {
                                if (cursorY - lineHeight < margin) {
                                    cs.close()
                                    page = PDPage(pageRect).also { pdf.addPage(it) }
                                    cs = PDPageContentStream(pdf, page)
                                    cursorY = pageRect.height - margin
                                }
                                cs.beginText()
                                cs.setFont(font, fontSize)
                                cs.setNonStrokingColor(24, 28, 36)
                                cs.newLineAtOffset(margin, cursorY)
                                runCatching { cs.showText(line) }
                                cs.endText()
                                cursorY -= lineHeight
                            }
                            cursorY -= if (para.isHeading) 8f else 5f
                        }
                    } finally {
                        cs.close()
                    }
                    pdf.save(outputFile)
                }
                outputFile
            }
        }

    // ==================== EXCEL (.xlsx) ====================

    suspend fun readXlsx(file: File, displayName: String = file.name): Result<XlsxWorkbookModel> =
        withContext(ioDispatcher) {
            runCatching {
                FileInputStream(file).use { fis ->
                    XSSFWorkbook(fis).use { wb ->
                        val formatter = DataFormatter(Locale.getDefault())
                        val evaluator = wb.creationHelper.createFormulaEvaluator()
                        val sheets = (0 until wb.numberOfSheets).map { sIdx ->
                            val sheet = wb.getSheetAt(sIdx)
                            val maxRow = sheet.lastRowNum.coerceAtLeast(0)
                            var maxCol = 1
                            for (r in 0..maxRow) {
                                val row = sheet.getRow(r) ?: continue
                                maxCol = max(maxCol, row.lastCellNum.toInt().coerceAtLeast(1))
                            }
                            val colCount = maxCol.coerceIn(1, 32)
                            val rows = (0..maxRow).map { r ->
                                val row = sheet.getRow(r)
                                (0 until colCount).map { c ->
                                    val cell = row?.getCell(c)
                                    if (cell == null) ""
                                    else runCatching { formatter.formatCellValue(cell, evaluator) }
                                        .getOrDefault(cell.toString())
                                }
                            }
                            XlsxSheetModel(name = sheet.sheetName ?: "Sheet ${sIdx + 1}", rows = rows)
                        }.ifEmpty {
                            listOf(XlsxSheetModel("Sheet 1", List(4) { List(3) { "" } }))
                        }
                        XlsxWorkbookModel(file = file, displayName = displayName, sheets = sheets)
                    }
                }
            }
        }

    fun createNewXlsxModel(): XlsxWorkbookModel = XlsxWorkbookModel(
        file = null,
        displayName = "Spreadsheet.xlsx",
        sheets = listOf(
            XlsxSheetModel(
                name = "Sheet 1",
                rows = listOf(
                    listOf("Item", "Quantity", "Amount"),
                    listOf("Sample Item", "1", "10.00"),
                    listOf("", "", ""),
                    listOf("", "", "")
                )
            )
        )
    )

    suspend fun saveXlsx(model: XlsxWorkbookModel, outputFile: File): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                outputFile.parentFile?.mkdirs()
                val wb = if (model.file != null && model.file.exists()) {
                    FileInputStream(model.file).use { XSSFWorkbook(it) }
                } else {
                    XSSFWorkbook()
                }
                wb.use { workbook ->
                    model.sheets.forEachIndexed { sIdx, sheetModel ->
                        coroutineContext.ensureActive()
                        val sheet = if (sIdx < workbook.numberOfSheets) {
                            workbook.getSheetAt(sIdx)
                        } else {
                            workbook.createSheet(sheetModel.name)
                        }
                        sheetModel.rows.forEachIndexed { rIdx, rowValues ->
                            val row = sheet.getRow(rIdx) ?: sheet.createRow(rIdx)
                            rowValues.forEachIndexed { cIdx, rawVal ->
                                val cell = row.getCell(cIdx) ?: row.createCell(cIdx)
                                val trimmed = rawVal.trim()
                                val num = trimmed.toDoubleOrNull()
                                when {
                                    trimmed.isEmpty() -> cell.setBlank()
                                    trimmed.startsWith("=") -> cell.cellFormula = trimmed.drop(1)
                                    num != null -> cell.setCellValue(num)
                                    else -> {
                                        cell.cellType
                                        cell.setCellValue(rawVal)
                                    }
                                }
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { fos -> workbook.write(fos) }
                }
                outputFile
            }
        }

    suspend fun exportXlsxToPdf(model: XlsxWorkbookModel, outputFile: File): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                outputFile.parentFile?.mkdirs()
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { pdf ->
                    val pageRect = PDRectangle(PDRectangle.LETTER.height, PDRectangle.LETTER.width) // Landscape
                    val margin = 36f
                    val tableWidth = pageRect.width - 2f * margin

                    model.sheets.forEach { sheet ->
                        coroutineContext.ensureActive()
                        val colCount = (sheet.rows.maxOfOrNull { it.size } ?: 1).coerceIn(1, 12)
                        val colWidth = tableWidth / colCount.toFloat()
                        val rowHeight = 20f

                        var page = PDPage(pageRect).also { pdf.addPage(it) }
                        var cs = PDPageContentStream(pdf, page)
                        var y = pageRect.height - margin

                        try {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                            cs.newLineAtOffset(margin, y)
                            runCatching { cs.showText(sanitizePdfAscii(sheet.name)) }
                            cs.endText()
                            y -= 24f

                            sheet.rows.forEachIndexed { rIdx, rowVals ->
                                if (y - rowHeight < margin) {
                                    cs.close()
                                    page = PDPage(pageRect).also { pdf.addPage(it) }
                                    cs = PDPageContentStream(pdf, page)
                                    y = pageRect.height - margin
                                }
                                if (rIdx == 0) {
                                    cs.setNonStrokingColor(232, 238, 246)
                                    cs.addRect(margin, y - rowHeight, tableWidth, rowHeight)
                                    cs.fill()
                                }
                                cs.setStrokingColor(200, 208, 218)
                                cs.addRect(margin, y - rowHeight, tableWidth, rowHeight)
                                cs.stroke()

                                for (cIdx in 0 until colCount) {
                                    val cellX = margin + cIdx * colWidth
                                    cs.moveTo(cellX, y)
                                    cs.lineTo(cellX, y - rowHeight)
                                    cs.stroke()

                                    val text = sanitizePdfAscii(rowVals.getOrNull(cIdx).orEmpty()).take(22)
                                    if (text.isNotEmpty()) {
                                        cs.beginText()
                                        cs.setFont(
                                            if (rIdx == 0) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
                                            9.5f
                                        )
                                        cs.setNonStrokingColor(20, 24, 30)
                                        cs.newLineAtOffset(cellX + 4f, y - 14f)
                                        runCatching { cs.showText(text) }
                                        cs.endText()
                                    }
                                }
                                y -= rowHeight
                            }
                        } finally {
                            cs.close()
                        }
                    }
                    pdf.save(outputFile)
                }
                outputFile
            }
        }

    // ==================== POWERPOINT (.pptx) ====================

    suspend fun readPptx(file: File, displayName: String = file.name): Result<PptxPresentationModel> =
        withContext(ioDispatcher) {
            runCatching {
                FileInputStream(file).use { fis ->
                    XMLSlideShow(fis).use { ppt ->
                        val sldSz = runCatching { ppt.ctPresentation?.sldSz }.getOrNull()
                        val wPt = ((sldSz?.cx ?: (960L * 12700L)).toFloat() / 12700f).coerceAtLeast(200f)
                        val hPt = ((sldSz?.cy ?: (540L * 12700L)).toFloat() / 12700f).coerceAtLeast(200f)

                        val slideModels = ppt.slides.mapIndexed { idx, slide ->
                            coroutineContext.ensureActive()
                            val bgColor = extractSlideBgRgb(slide)
                            val elements = mutableListOf<PptxShapeElement>()
                            val textLines = mutableListOf<String>()

                            slide.shapes.forEachIndexed { shapeIdx, shape ->
                                val fallbackTop = 44f + shapeIdx * 96f
                                val (left, top, width, height) = extractShapeBoundsPt(shape, wPt, fallbackTop)

                                when (shape) {
                                    is XSLFPictureShape -> {
                                        val bytes = runCatching { shape.pictureData?.data }.getOrNull()
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            elements.add(
                                                PptxShapeElement.Picture(left, top, width, height, bytes)
                                            )
                                        }
                                    }
                                    is XSLFTextShape -> {
                                        val rawText = shape.text.orEmpty().trim()
                                        if (rawText.isNotEmpty()) {
                                            textLines.add(rawText)
                                            val firstPara = shape.textParagraphs.firstOrNull()
                                            val firstRun = firstPara?.textRuns?.firstOrNull()
                                            val fontPt = (firstRun?.fontSize ?: 18.0).toFloat().coerceIn(8f, 54f)
                                            val isBold = firstRun?.isBold == true
                                            elements.add(
                                                PptxShapeElement.TextBox(
                                                    leftPt = left,
                                                    topPt = top,
                                                    widthPt = width,
                                                    heightPt = height,
                                                    text = rawText,
                                                    fontSizePt = fontPt,
                                                    isBold = isBold,
                                                    textColorRgb = Color.rgb(24, 28, 36)
                                                )
                                            )
                                        }
                                    }
                                    is XSLFTable -> {
                                        val tableSummary = shape.rows.joinToString("\n") { row ->
                                            row.cells.joinToString(" | ") { it.text.orEmpty().trim() }
                                        }.trim()
                                        if (tableSummary.isNotEmpty()) {
                                            textLines.add(tableSummary)
                                            elements.add(
                                                PptxShapeElement.TextBox(
                                                    leftPt = left,
                                                    topPt = top,
                                                    widthPt = width,
                                                    heightPt = height,
                                                    text = tableSummary,
                                                    fontSizePt = 11f,
                                                    isBold = false,
                                                    textColorRgb = Color.rgb(31, 41, 55)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            val notesText = runCatching {
                                slide.notes?.shapes
                                    ?.filterIsInstance<XSLFTextShape>()
                                    ?.filter { it.textType != org.apache.poi.sl.usermodel.Placeholder.SLIDE_NUMBER }
                                    ?.mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
                                    ?.joinToString("\n")
                                    .orEmpty()
                            }.getOrDefault("")

                            val title = textLines.firstOrNull()?.lineSequence()?.firstOrNull()?.take(80)
                                ?: "Slide ${idx + 1}"

                            PptxSlideModel(
                                index = idx,
                                title = title,
                                bodyLines = textLines,
                                notes = notesText,
                                backgroundColorRgb = bgColor,
                                elements = elements
                            )
                        }

                        PptxPresentationModel(
                            file = file,
                            displayName = displayName,
                            slideWidthPt = wPt,
                            slideHeightPt = hPt,
                            slides = slideModels
                        )
                    }
                }
            }
        }

    fun renderSlidePreviewBitmap(
        slide: PptxSlideModel,
        slideWidthPt: Float,
        slideHeightPt: Float,
        targetWidthPx: Int = 960
    ): Bitmap {
        val scale = targetWidthPx.toFloat() / slideWidthPt.coerceAtLeast(1f)
        val targetH = (slideHeightPt * scale).toInt().coerceIn(180, 1400)
        val bmp = Bitmap.createBitmap(targetWidthPx, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(slide.backgroundColorRgb)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        slide.elements.forEach { el ->
            when (el) {
                is PptxShapeElement.Picture -> {
                    val img = BitmapFactory.decodeByteArray(el.imageBytes, 0, el.imageBytes.size)
                    if (img != null) {
                        val dest = RectF(
                            el.leftPt * scale,
                            el.topPt * scale,
                            (el.leftPt + el.widthPt) * scale,
                            (el.topPt + el.heightPt) * scale
                        )
                        canvas.drawBitmap(img, null, dest, null)
                        img.recycle()
                    }
                }
                is PptxShapeElement.TextBox -> {
                    textPaint.color = el.textColorRgb
                    textPaint.textSize = (el.fontSizePt * scale).coerceIn(14f, 96f)
                    textPaint.isFakeBoldText = el.isBold
                    val x = el.leftPt * scale + 6f
                    var y = el.topPt * scale + textPaint.textSize
                    val maxW = (el.widthPt * scale - 12f).coerceAtLeast(40f)
                    val maxY = (el.topPt + el.heightPt) * scale
                    el.text.split('\n').forEach { rawLine ->
                        var remaining = rawLine
                        while (remaining.isNotEmpty() && y <= maxY) {
                            val count = textPaint.breakText(remaining, true, maxW, null).coerceAtLeast(1)
                            canvas.drawText(remaining.substring(0, count), x, y, textPaint)
                            remaining = remaining.substring(count).trimStart()
                            y += textPaint.textSize * 1.25f
                        }
                    }
                }
            }
        }
        return bmp
    }

    suspend fun exportPptxToPdf(
        model: PptxPresentationModel,
        includeSpeakerNotes: Boolean,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { pdf ->
                val total = model.slides.size.coerceAtLeast(1)
                val notesBand = if (includeSpeakerNotes) 76f else 0f
                val pageRect = PDRectangle(model.slideWidthPt, model.slideHeightPt + notesBand)

                model.slides.forEachIndexed { idx, slide ->
                    coroutineContext.ensureActive()
                    val page = PDPage(pageRect).also { pdf.addPage(it) }
                    PDPageContentStream(pdf, page).use { cs ->
                        val bg = slide.backgroundColorRgb
                        cs.setNonStrokingColor(Color.red(bg), Color.green(bg), Color.blue(bg))
                        cs.addRect(0f, notesBand, model.slideWidthPt, model.slideHeightPt)
                        cs.fill()

                        slide.elements.forEach { el ->
                            when (el) {
                                is PptxShapeElement.Picture -> {
                                    runCatching {
                                        val pdImg = PDImageXObject.createFromByteArray(
                                            pdf,
                                            el.imageBytes,
                                            "slide_${idx}_img"
                                        )
                                        val pdfY = pageRect.height - el.topPt - el.heightPt
                                        cs.drawImage(pdImg, el.leftPt, pdfY, el.widthPt, el.heightPt)
                                    }
                                }
                                is PptxShapeElement.TextBox -> {
                                    val font = if (el.isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                                    val lines = wrapPdfText(el.text, font, el.fontSizePt, el.widthPt)
                                    var textY = pageRect.height - el.topPt - el.fontSizePt
                                    val lineStep = el.fontSizePt * 1.28f
                                    lines.forEach { line ->
                                        if (textY >= notesBand + 8f) {
                                            cs.beginText()
                                            cs.setFont(font, el.fontSizePt)
                                            cs.setNonStrokingColor(
                                                Color.red(el.textColorRgb),
                                                Color.green(el.textColorRgb),
                                                Color.blue(el.textColorRgb)
                                            )
                                            cs.newLineAtOffset(el.leftPt, textY)
                                            runCatching { cs.showText(line) }
                                            cs.endText()
                                            textY -= lineStep
                                        }
                                    }
                                }
                            }
                        }

                        if (includeSpeakerNotes && slide.notes.isNotBlank()) {
                            cs.setNonStrokingColor(242, 244, 248)
                            cs.addRect(0f, 0f, model.slideWidthPt, notesBand)
                            cs.fill()
                            val noteLines = wrapPdfText("Notes: ${slide.notes}", PDType1Font.HELVETICA, 10f, model.slideWidthPt - 48f)
                            var ny = notesBand - 16f
                            noteLines.take(4).forEach { nl ->
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 10f)
                                cs.setNonStrokingColor(55, 65, 81)
                                cs.newLineAtOffset(24f, ny)
                                runCatching { cs.showText(nl) }
                                cs.endText()
                                ny -= 13f
                            }
                        }
                    }
                    onProgress((idx + 1).toFloat() / total.toFloat())
                }
                pdf.save(outputFile)
            }
            outputFile
        }
    }

    private data class ShapeRectPt(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    private fun extractShapeBoundsPt(
        shape: org.apache.poi.xslf.usermodel.XSLFShape,
        slideWidthPt: Float,
        fallbackTopPt: Float
    ): ShapeRectPt {
        val defaultRect = ShapeRectPt(36f, fallbackTopPt, (slideWidthPt - 72f).coerceAtLeast(100f), 84f)
        return runCatching {
            val xmlObj = shape.xmlObject
            val xfrm = when (xmlObj) {
                is org.openxmlformats.schemas.presentationml.x2006.main.CTShape -> xmlObj.spPr?.xfrm
                is org.openxmlformats.schemas.presentationml.x2006.main.CTPicture -> xmlObj.spPr?.xfrm
                is org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame -> xmlObj.xfrm
                else -> null
            } ?: return defaultRect

            val off = xfrm.off
            val ext = xfrm.ext
            val left = (off?.x?.toString()?.toLongOrNull()?.takeIf { it >= 0L }?.toFloat() ?: (36f * 12700f)) / 12700f
            val top = (off?.y?.toString()?.toLongOrNull()?.takeIf { it >= 0L }?.toFloat() ?: (fallbackTopPt * 12700f)) / 12700f
            val rawW = ext?.cx?.takeIf { it > 0L }?.toFloat()?.div(12700f) ?: (slideWidthPt - left - 36f)
            val rawH = ext?.cy?.takeIf { it > 0L }?.toFloat()?.div(12700f) ?: 140f
            val width = rawW.coerceAtLeast(120f)
            val height = rawH.coerceAtLeast(36f)
            ShapeRectPt(left, top, width, height)
        }.getOrDefault(defaultRect)
    }

    private fun extractSlideBgRgb(slide: org.apache.poi.xslf.usermodel.XSLFSlide): Int {
        val awtColor = runCatching { slide.background?.fillColor }.getOrNull() ?: return Color.WHITE
        val rgb = runCatching {
            val r = awtColor.javaClass.getMethod("getRed").invoke(awtColor) as Int
            val g = awtColor.javaClass.getMethod("getGreen").invoke(awtColor) as Int
            val b = awtColor.javaClass.getMethod("getBlue").invoke(awtColor) as Int
            Color.rgb(r, g, b)
        }.getOrDefault(Color.WHITE)
        return rgb
    }

    private fun sanitizePdfAscii(text: String): String =
        text.replace(Regex("[^\\x20-\\x7E\\n]"), " ")

    private fun wrapPdfText(
        rawText: String,
        font: PDType1Font,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val clean = sanitizePdfAscii(rawText)
        val out = mutableListOf<String>()
        clean.split('\n').forEach { paragraph ->
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                out.add("")
                return@forEach
            }
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                val w = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }.getOrDefault(candidate.length * fontSize * 0.55f)
                if (w <= maxWidth || line.isEmpty()) {
                    line = StringBuilder(candidate)
                } else {
                    out.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            if (line.isNotEmpty()) out.add(line.toString())
        }
        return out
    }
}
