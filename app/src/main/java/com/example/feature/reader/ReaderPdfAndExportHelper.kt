package com.example.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.core.common.IoDispatcher
import com.example.domain.model.Highlight
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Singleton
class ReaderPdfAndExportHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedCustomDictionary: Map<String, String>? = null

    suspend fun extractPdfToc(
        pdfFile: File,
        password: String?,
        fallbackSections: List<ReaderPageText>
    ): List<ReaderTocEntry> = withContext(ioDispatcher) {
        val outlineEntries = runCatching {
            val doc = if (password != null) {
                PDDocument.load(pdfFile, password, MemoryUsageSetting.setupTempFileOnly())
            } else {
                PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly())
            }
            doc.use { pdDoc ->
                val result = mutableListOf<ReaderTocEntry>()
                val root = pdDoc.documentCatalog?.documentOutline
                if (root != null) {
                    collectOutlineItems(pdDoc, root.firstChild, level = 0, out = result)
                }
                result
            }
        }.getOrDefault(emptyList())

        if (outlineEntries.isNotEmpty()) {
            return@withContext outlineEntries
        }

        fallbackSections.mapNotNull { sec ->
            val firstHeading = sec.body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.length in 3..72 }
            val label = firstHeading ?: sec.title
            ReaderTocEntry(title = label, pageIndex = sec.pageIndex, level = 0)
        }
    }

    private fun collectOutlineItems(
        doc: PDDocument,
        start: PDOutlineItem?,
        level: Int,
        out: MutableList<ReaderTocEntry>
    ) {
        if (level > 3 || out.size >= 200) return
        var cursor = start
        while (cursor != null && out.size < 200) {
            val title = cursor.title?.trim().orEmpty()
            val pageIdx = runCatching {
                val destPage = cursor.findDestinationPage(doc)
                if (destPage != null) doc.pages.indexOf(destPage).coerceAtLeast(0) else 0
            }.getOrDefault(0)
            if (title.isNotEmpty()) {
                out.add(ReaderTocEntry(title = title, pageIndex = pageIdx, level = level))
            }
            cursor.firstChild?.let { child ->
                collectOutlineItems(doc, child, level + 1, out)
            }
            cursor = cursor.nextSibling
        }
    }

    suspend fun autoCropWhiteMargins(source: Bitmap): Bitmap = withContext(ioDispatcher) {
        if (source.isRecycled || source.width < 64 || source.height < 64) return@withContext source
        val w = source.width
        val h = source.height
        val step = max(2, min(w, h) / 220)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var foundInk = false

        var y = 0
        while (y < h) {
            val rowOffset = y * w
            var x = 0
            while (x < w) {
                val px = pixels[rowOffset + x]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                if (r < 242 || g < 242 || b < 242) {
                    foundInk = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }

        if (!foundInk || maxX <= minX || maxY <= minY) return@withContext source

        val padX = (w * 0.025f).toInt()
        val padY = (h * 0.025f).toInt()
        val left = (minX - padX).coerceAtLeast(0)
        val top = (minY - padY).coerceAtLeast(0)
        val right = (maxX + padX).coerceAtMost(w - 1)
        val bottom = (maxY + padY).coerceAtMost(h - 1)
        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)

        // Only crop if margins are meaningfully large (> 8% savings)
        if (cropW > (w * 0.92f).toInt() && cropH > (h * 0.92f).toInt()) {
            return@withContext source
        }
        runCatching { Bitmap.createBitmap(source, left, top, cropW, cropH) }.getOrDefault(source)
    }

    suspend fun exportHighlightsToMarkdown(
        bookTitle: String,
        highlights: List<Highlight>
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val safeName = bookTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").take(36).ifBlank { "Document" }
            val outFile = File(exportsDir, "${safeName}_highlights_${System.currentTimeMillis()}.md")
            outFile.writeText(buildHighlightsMarkdownContent(bookTitle, highlights), Charsets.UTF_8)
            outFile
        }
    }

    suspend fun exportHighlightsToPdf(
        bookTitle: String,
        highlights: List<Highlight>
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val safeName = bookTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").take(36).ifBlank { "Document" }
            val outFile = File(exportsDir, "${safeName}_highlights_${System.currentTimeMillis()}.pdf")

            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val rect = PDRectangle.A4
                val margin = 48f
                val maxCharsPerLine = 82
                var page = PDPage(rect).also { doc.addPage(it) }
                var cs = PDPageContentStream(doc, page)
                var y = rect.height - margin

                fun ensureSpace(needed: Float) {
                    if (y - needed < margin) {
                        cs.close()
                        page = PDPage(rect).also { doc.addPage(it) }
                        cs = PDPageContentStream(doc, page)
                        y = rect.height - margin
                    }
                }

                fun drawLine(text: String, bold: Boolean = false, size: Float = 11f) {
                    ensureSpace(size * 1.6f)
                    cs.beginText()
                    cs.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, size)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(sanitizePdfAscii(text))
                    cs.endText()
                    y -= size * 1.48f
                }

                try {
                    drawLine("Highlights & Notes: $bookTitle", bold = true, size = 15f)
                    drawLine("Exported on ${LocalDate.now()} • ${highlights.size} highlight(s)", size = 10f)
                    y -= 10f

                    highlights.forEachIndexed { idx, hl ->
                        drawLine(
                            "${idx + 1}. Page ${hl.pageNumber} [${hl.colorKey.uppercase(Locale.US)}]",
                            bold = true,
                            size = 11.5f
                        )
                        wrapLines("\"${hl.quotedText}\"", maxCharsPerLine).forEach { line ->
                            drawLine(line, bold = false, size = 10.5f)
                        }
                        if (hl.noteText.isNotBlank()) {
                            wrapLines("Note: ${hl.noteText}", maxCharsPerLine).forEach { line ->
                                drawLine(line, bold = false, size = 10f)
                            }
                        }
                        y -= 8f
                    }
                } finally {
                    cs.close()
                }
                doc.save(outFile)
            }
            outFile
        }
    }

    fun getShareUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    suspend fun importOfflineDictionaryPack(uri: Uri): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Unable to read dictionary file")
            val parsed = parseDictionaryText(raw)
            require(parsed.isNotEmpty()) { "No valid word definitions found (expected word: definition or JSON map)" }
            val dictDir = File(context.filesDir, "dictionary").apply { mkdirs() }
            val outFile = File(dictDir, "custom_pack.txt")
            val serialized = parsed.entries.joinToString("\n") { (k, v) -> "$k\t$v" }
            outFile.writeText(serialized, Charsets.UTF_8)
            cachedCustomDictionary = parsed
            parsed.size
        }
    }

    suspend fun lookupOfflineWord(rawInput: String): Triple<String, String?, Pair<String, Int>> =
        withContext(ioDispatcher) {
            val customDict = loadCustomDictionary()
            val cleaned = rawInput.trim()
                .replace(Regex("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$"), "")
                .lowercase(Locale.US)
            if (cleaned.isEmpty()) {
                return@withContext Triple("", null, "Built-in offline glossary" to customDict.size)
            }
            val candidates = candidateStems(cleaned)
            for (cand in candidates) {
                customDict[cand]?.let { def ->
                    return@withContext Triple(cand, def, "Imported offline dictionary pack" to customDict.size)
                }
            }
            for (cand in candidates) {
                BUILT_IN_GLOSSARY[cand]?.let { def ->
                    return@withContext Triple(cand, def, "Built-in offline glossary" to customDict.size)
                }
            }
            Triple(cleaned, null, "Built-in offline glossary" to customDict.size)
        }

    suspend fun isTranslationPackDownloaded(target: ReaderTranslateLanguage): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val model = TranslateRemoteModel.Builder(target.mlKitCode).build()
                RemoteModelManager.getInstance().isModelDownloaded(model).await()
            }.getOrDefault(false)
        }

    suspend fun downloadTranslationPack(target: ReaderTranslateLanguage): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(target.mlKitCode)
                    .build()
                val translator = Translation.getClient(options)
                try {
                    val conditions = DownloadConditions.Builder().build()
                    translator.downloadModelIfNeeded(conditions).await()
                    Unit
                } finally {
                    translator.close()
                }
            }
        }

    suspend fun translateOffline(
        text: String,
        target: ReaderTranslateLanguage
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            require(text.isNotBlank()) { "Enter or select text to translate" }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target.mlKitCode)
                .build()
            val translator = Translation.getClient(options)
            try {
                val conditions = DownloadConditions.Builder().build()
                translator.downloadModelIfNeeded(conditions).await()
                translator.translate(text.trim().take(1200)).await()
            } finally {
                translator.close()
            }
        }
    }

    private fun loadCustomDictionary(): Map<String, String> {
        cachedCustomDictionary?.let { return it }
        val file = File(File(context.filesDir, "dictionary"), "custom_pack.txt")
        if (!file.exists()) {
            cachedCustomDictionary = emptyMap()
            return emptyMap()
        }
        val map = runCatching {
            file.readLines(Charsets.UTF_8).mapNotNull { line ->
                val idx = line.indexOf('\t')
                if (idx > 0) line.substring(0, idx).lowercase(Locale.US) to line.substring(idx + 1)
                else null
            }.toMap()
        }.getOrDefault(emptyMap())
        cachedCustomDictionary = map
        return map
    }

    private fun parseDictionaryText(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            val fromJson = runCatching { json.decodeFromString<Map<String, String>>(trimmed) }.getOrNull()
            if (fromJson != null) {
                return fromJson.entries.associate { (k, v) -> k.trim().lowercase(Locale.US) to v.trim() }
                    .filterKeys { it.isNotEmpty() }
            }
        }
        val out = linkedMapOf<String, String>()
        trimmed.lineSequence().forEach { line ->
            val sep = when {
                line.contains('\t') -> '\t'
                line.contains(" : ") -> ':'
                line.contains(':') -> ':'
                line.contains('|') -> '|'
                else -> null
            }
            if (sep != null) {
                val idx = line.indexOf(sep)
                if (idx > 0 && idx < line.length - 1) {
                    val key = line.substring(0, idx).trim().lowercase(Locale.US)
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
                }
            }
        }
        return out
    }

    private fun sanitizePdfAscii(text: String): String =
        text.map { ch -> if (ch.code in 32..126) ch else ' ' }.joinToString("")

    private fun wrapLines(text: String, maxChars: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty() && sb.length + 1 + word.length > maxChars) {
                lines.add(sb.toString())
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())
        return lines
    }

    companion object {
        fun buildHighlightsMarkdownContent(
            bookTitle: String,
            highlights: List<Highlight>
        ): String = buildString {
            appendLine("# Highlights & Notes — $bookTitle")
            appendLine()
            appendLine("_Exported offline on ${LocalDate.now()} • ${highlights.size} highlight(s)_")
            appendLine()
            highlights.forEach { hl ->
                appendLine("## Page ${hl.pageNumber} • `${hl.colorKey.uppercase(Locale.US)}`")
                appendLine()
                appendLine("> ${hl.quotedText.replace("\n", "\n> ")}")
                if (hl.noteText.isNotBlank()) {
                    appendLine()
                    appendLine("**Note:** ${hl.noteText}")
                }
                appendLine()
            }
        }

        fun isWarmLightScheduledAtHour(currentHour24: Int, startHour: Int, endHour: Int): Boolean {
            val h = currentHour24.coerceIn(0, 23)
            val s = startHour.coerceIn(0, 23)
            val e = endHour.coerceIn(0, 23)
            return if (s == e) {
                true
            } else if (s < e) {
                h in s until e
            } else {
                h >= s || h < e
            }
        }

        fun estimateRemainingReadingMinutes(
            sections: List<ReaderPageText>,
            currentPage: Int,
            totalPages: Int,
            wordsPerMinute: Int = 220
        ): Pair<Int, Int> {
            val totalWords = sections.sumOf { countWords(it.body) }
            val remainingWords = sections.drop(currentPage.coerceAtLeast(0)).sumOf { countWords(it.body) }
            val mins = if (remainingWords >= 40) {
                ceil(remainingWords.toDouble() / wordsPerMinute.coerceAtLeast(100)).toInt().coerceAtLeast(1)
            } else {
                val pagesLeft = (totalPages - currentPage).coerceAtLeast(1)
                ceil(pagesLeft * 1.2).toInt().coerceAtLeast(1)
            }
            return mins to totalWords
        }

        fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

        fun candidateStems(word: String): List<String> {
            val list = mutableListOf(word)
            if (word.endsWith("ies") && word.length > 4) list.add(word.dropLast(3) + "y")
            if (word.endsWith("es") && word.length > 3) list.add(word.dropLast(2))
            if (word.endsWith("s") && word.length > 3) list.add(word.dropLast(1))
            if (word.endsWith("ing") && word.length > 5) {
                list.add(word.dropLast(3))
                list.add(word.dropLast(3) + "e")
            }
            if (word.endsWith("ed") && word.length > 4) {
                list.add(word.dropLast(2))
                list.add(word.dropLast(1))
            }
            if (word.endsWith("ly") && word.length > 4) list.add(word.dropLast(2))
            return list.distinct()
        }

        private val BUILT_IN_GLOSSARY: Map<String, String> = mapOf(
            "pdf" to "(noun) Portable Document Format — a fixed-layout document standard preserving fonts, graphics, and page geometry.",
            "offline" to "(adj.) Operating independently on your local device without an internet connection or cloud transmission.",
            "encrypt" to "(verb) To transform readable information into ciphertext using a cryptographic algorithm such as AES-256.",
            "encryption" to "(noun) The process of securing data so only authorized parties with the proper key or password can read it.",
            "redact" to "(verb) To permanently remove sensitive text or imagery from a document prior to sharing or publication.",
            "signature" to "(noun) A handwritten mark or cryptographic certificate confirming authorship, approval, or document integrity.",
            "certificate" to "(noun) A digital credential (such as PKCS#12) binding an identity to a cryptographic key pair.",
            "biometric" to "(adj.) Relating to physical verification traits such as fingerprint or facial recognition.",
            "vault" to "(noun) An encrypted, access-controlled storage area designed to protect confidential documents.",
            "ocr" to "(noun) Optical Character Recognition — converting scanned images of text into machine-readable characters.",
            "epub" to "(noun) Electronic Publication — a reflowable open e-book format built on XHTML and ZIP packaging.",
            "reflow" to "(verb) To dynamically re-wrap text across lines and pages to fit the reader's screen size and typography.",
            "bookmark" to "(noun) A saved marker referencing a specific page or section for instant return.",
            "highlight" to "(verb) To emphasize a passage of text with color and optional margin commentary.",
            "annotation" to "(noun) A note, highlight, or mark added to a document as commentary or explanation.",
            "compress" to "(verb) To reduce file size by optimizing embedded images, streams, and redundant metadata.",
            "merge" to "(verb) To combine two or more separate documents sequentially into a single file.",
            "split" to "(verb) To divide a multi-page document into smaller files or selected page ranges.",
            "watermark" to "(noun) A faint translucent stamp placed across document pages to denote status or ownership.",
            "metadata" to "(noun) Descriptive attributes embedded in a file, such as author, title, creation date, and keywords.",
            "privacy" to "(noun) The state of keeping personal records and files free from unauthorized observation or collection.",
            "clause" to "(noun) A distinct provision or article within a legal contract, treaty, or formal agreement.",
            "contract" to "(noun) A legally binding written or spoken agreement between two or more parties.",
            "invoice" to "(noun) An itemized commercial bill requesting payment for delivered goods or services.",
            "receipt" to "(noun) Written acknowledgment that a specified sum of money or item has been received.",
            "appendix" to "(noun) Supplementary material attached at the end of a book, report, or formal document.",
            "glossary" to "(noun) An alphabetical list of specialized terms and their definitions.",
            "summary" to "(noun) A concise overview capturing the main points of a longer text.",
            "excerpt" to "(noun) A short passage or quotation selected from a book, article, or document.",
            "typography" to "(noun) The art and technique of arranging type, font families, spacing, and alignment for readability.",
            "dyslexia" to "(noun) A learning difference affecting reading fluency; high-distinction letterforms improve legibility.",
            "immersive" to "(adj.) Full-screen presentation that hides system chrome to minimize distraction while reading.",
            "algorithm" to "(noun) A step-by-step computational procedure for solving a problem or processing data.",
            "protocol" to "(noun) A formal set of rules governing communication, security, or operational procedures.",
            "compliance" to "(noun) Adherence to applicable laws, technical standards, or organizational policies.",
            "liability" to "(noun) Legal or financial responsibility for obligations, damages, or commitments.",
            "indemnify" to "(verb) To compensate or protect a party against financial loss or legal claims.",
            "jurisdiction" to "(noun) The official authority of a court or governing body over a defined territory or subject.",
            "archive" to "(noun) A long-term collection of preserved documents and records."
        )
    }
}
