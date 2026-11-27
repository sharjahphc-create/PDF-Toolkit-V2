package com.example.feature.pdftools.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.core.common.IoDispatcher
import com.example.feature.sign.PdfCoordinateMapper
import com.example.feature.sign.PdfPageBox
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class CompressionPreset(
    val label: String,
    val description: String,
    val targetDpiWidth: Int,
    val jpegQuality: Float
) {
    LIGHT("Light", "Crisp quality • ~25% smaller", 1400, 0.85f),
    BALANCED("Balanced", "Recommended for email & upload", 1080, 0.70f),
    STRONG("Strong", "Smallest size • ~70% smaller", 800, 0.50f)
}

data class PageOrganizeSpec(
    val originalPageIndex: Int,
    val rotationDelta: Int = 0,
    val cropInsetPercent: Int = 0,
    val isDeleted: Boolean = false
)

data class AcroFormFieldItem(
    val fullyQualifiedName: String,
    val label: String,
    val value: String,
    val isCheckbox: Boolean,
    val isChecked: Boolean
)

data class FreeTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val text: String,
    val normalizedX: Float = 0.12f,
    val normalizedY: Float = 0.25f,
    val fontSizePt: Float = 13f
)

@Singleton
class PdfCoreEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun mergePdfs(
        inputs: List<File>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(inputs.size >= 2) { "Select at least 2 PDF files to merge" }
            outputFile.parentFile?.mkdirs()
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            inputs.forEachIndexed { idx, file ->
                coroutineContext.ensureActive()
                merger.addSource(file)
                onProgress(((idx + 1).toFloat() / (inputs.size + 1).toFloat()) * 0.6f)
            }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            onProgress(1f)
            outputFile
        }
    }

    suspend fun splitPdf(
        sourceFile: File,
        selectedPageIndicesZeroBased: List<Int>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(selectedPageIndicesZeroBased.isNotEmpty()) { "Select at least 1 page to extract" }
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    val valid = selectedPageIndicesZeroBased
                        .filter { it in 0 until srcDoc.numberOfPages }
                        .distinct()
                    require(valid.isNotEmpty()) { "No valid pages selected" }
                    valid.forEachIndexed { idx, pageIdx ->
                        coroutineContext.ensureActive()
                        outDoc.importPage(srcDoc.getPage(pageIdx))
                        onProgress((idx + 1).toFloat() / valid.size.toFloat())
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun compressPdf(
        sourceFile: File,
        preset: CompressionPreset,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount.coerceAtLeast(1)
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                        for (i in 0 until total) {
                            coroutineContext.ensureActive()
                            renderer.openPage(i).use { page ->
                                val widthPt = page.width.toFloat().coerceAtLeast(1f)
                                val heightPt = page.height.toFloat().coerceAtLeast(1f)
                                val ratio = heightPt / widthPt
                                val targetW = preset.targetDpiWidth
                                val targetH = (targetW * ratio).toInt().coerceIn(200, 3000)

                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                try {
                                    Canvas(bmp).drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val pdPage = PDPage(PDRectangle(widthPt, heightPt))
                                    outDoc.addPage(pdPage)
                                    val jpg = JPEGFactory.createFromImage(outDoc, bmp, preset.jpegQuality)
                                    PDPageContentStream(outDoc, pdPage).use { cs ->
                                        cs.drawImage(jpg, 0f, 0f, widthPt, heightPt)
                                    }
                                } finally {
                                    bmp.recycle()
                                }
                            }
                            onProgress((i + 1).toFloat() / total.toFloat())
                        }
                        outDoc.save(outputFile)
                    }
                }
            }
            outputFile
        }
    }

    suspend fun organizePages(
        sourceFile: File,
        specs: List<PageOrganizeSpec>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val activeSpecs = specs.filterNot { it.isDeleted }
            require(activeSpecs.isNotEmpty()) { "Cannot delete all pages from PDF" }
            outputFile.parentFile?.mkdirs()

            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                    activeSpecs.forEachIndexed { idx, spec ->
                        coroutineContext.ensureActive()
                        if (spec.originalPageIndex in 0 until srcDoc.numberOfPages) {
                            val imported = outDoc.importPage(srcDoc.getPage(spec.originalPageIndex))
                            imported.rotation = ((imported.rotation + spec.rotationDelta) % 360 + 360) % 360

                            if (spec.cropInsetPercent > 0) {
                                val box = imported.cropBox ?: imported.mediaBox
                                val insetFrac = (spec.cropInsetPercent.coerceIn(0, 35)) / 100f
                                val dx = box.width * insetFrac
                                val dy = box.height * insetFrac
                                imported.cropBox = PDRectangle(
                                    box.lowerLeftX + dx,
                                    box.lowerLeftY + dy,
                                    (box.width - 2f * dx).coerceAtLeast(36f),
                                    (box.height - 2f * dy).coerceAtLeast(36f)
                                )
                            }
                        }
                        onProgress((idx + 1).toFloat() / activeSpecs.size.toFloat())
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun readAcroFormFields(sourceFile: File): List<AcroFormFieldItem> = withContext(ioDispatcher) {
        runCatching {
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm ?: return@use emptyList()
                val items = mutableListOf<AcroFormFieldItem>()
                acroForm.fieldTree.forEach { field: PDField ->
                    if (field is PDCheckBox) {
                        items.add(
                            AcroFormFieldItem(
                                fullyQualifiedName = field.fullyQualifiedName,
                                label = field.partialName ?: field.fullyQualifiedName,
                                value = if (field.isChecked) "Yes" else "Off",
                                isCheckbox = true,
                                isChecked = field.isChecked
                            )
                        )
                    } else if (field.widgets.isNotEmpty()) {
                        items.add(
                            AcroFormFieldItem(
                                fullyQualifiedName = field.fullyQualifiedName,
                                label = field.partialName ?: field.fullyQualifiedName,
                                value = field.valueAsString.orEmpty(),
                                isCheckbox = false,
                                isChecked = false
                            )
                        )
                    }
                }
                items
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fillFormAndOverlays(
        sourceFile: File,
        fieldValues: Map<String, String>,
        checkboxValues: Map<String, Boolean>,
        freeTextOverlays: List<FreeTextOverlay>,
        flattenForm: Boolean,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm
                if (acroForm != null) {
                    acroForm.fieldTree.forEach { field ->
                        val fqName = field.fullyQualifiedName
                        if (field is PDCheckBox && checkboxValues.containsKey(fqName)) {
                            if (checkboxValues[fqName] == true) field.check() else field.unCheck()
                        } else if (fieldValues.containsKey(fqName)) {
                            runCatching { field.setValue(fieldValues[fqName].orEmpty()) }
                        }
                    }
                    if (flattenForm) {
                        runCatching { acroForm.flatten() }
                    }
                }
                onProgress(0.5f)

                freeTextOverlays.forEachIndexed { idx, overlay ->
                    coroutineContext.ensureActive()
                    if (overlay.pageIndex in 0 until doc.numberOfPages && overlay.text.isNotBlank()) {
                        val page = doc.getPage(overlay.pageIndex)
                        val box = page.cropBox ?: page.mediaBox
                        val mapped = PdfCoordinateMapper.toPdfUserSpace(
                            normalizedX = overlay.normalizedX,
                            normalizedY = overlay.normalizedY,
                            normalizedWidth = 0.5f,
                            normalizedHeight = 0.05f,
                            overlayRotationDegrees = 0f,
                            pageBox = PdfPageBox(
                                lowerLeftX = box.lowerLeftX,
                                lowerLeftY = box.lowerLeftY,
                                width = box.width,
                                height = box.height,
                                rotationDegrees = page.rotation
                            )
                        )
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, overlay.fontSizePt.coerceIn(8f, 36f))
                            cs.setNonStrokingColor(17, 24, 39)
                            cs.newLineAtOffset(mapped.bottomLeftX, mapped.bottomLeftY)
                            cs.showText(overlay.text.replace('\n', ' ').take(120))
                            cs.endText()
                        }
                    }
                    onProgress(0.5f + 0.45f * ((idx + 1).toFloat() / freeTextOverlays.size.coerceAtLeast(1).toFloat()))
                }

                doc.save(outputFile)
                onProgress(1f)
                outputFile
            }
        }
    }

    suspend fun lockPdfAes256(
        sourceFile: File,
        userPassword: String,
        ownerPassword: String,
        allowPrinting: Boolean,
        allowCopy: Boolean,
        outputFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(userPassword.isNotEmpty() || ownerPassword.isNotEmpty()) {
                "Enter at least a user or owner password"
            }
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val ap = AccessPermission().apply {
                    setCanPrint(allowPrinting)
                    setCanExtractContent(allowCopy)
                    setCanExtractForAccessibility(true)
                }
                val policy = StandardProtectionPolicy(
                    ownerPassword.ifEmpty { userPassword },
                    userPassword,
                    ap
                ).apply {
                    encryptionKeyLength = 256
                    isPreferAES = true
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun unlockPdf(
        sourceFile: File,
        password: String,
        outputFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(sourceFile, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
            }
            outputFile
        }
    }

    companion object {
        /**
         * Parses 1-based user page range strings (e.g. "1-3, 5, 7-8") into 0-based sorted indices.
         */
        fun parsePageRanges(input: String, totalPages: Int): List<Int> {
            if (input.isBlank() || totalPages <= 0) return emptyList()
            val result = linkedSetOf<Int>()
            input.split(',').forEach { rawToken ->
                val token = rawToken.trim()
                if (token.isEmpty()) return@forEach
                if (token.contains('-')) {
                    val parts = token.split('-', limit = 2)
                    val start = parts[0].trim().toIntOrNull()
                    val end = parts[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        val low = minOf(start, end).coerceIn(1, totalPages)
                        val high = maxOf(start, end).coerceIn(1, totalPages)
                        for (p in low..high) result.add(p - 1)
                    }
                } else {
                    token.toIntOrNull()?.let { page ->
                        if (page in 1..totalPages) result.add(page - 1)
                    }
                }
            }
            return result.toList()
        }
    }
}
