package com.example.feature.reader

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class EpubChapter(
    val index: Int,
    val title: String,
    val text: String
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

@Singleton
class EpubParser @Inject constructor() {

    suspend fun parse(epubFile: File): Result<EpubBook> = withContext(Dispatchers.IO) {
        runCatching {
            require(epubFile.exists()) { "EPUB file not found" }
            ZipFile(epubFile).use { zip ->
                val opfPath = findOpfPath(zip)
                val opfEntry = opfPath?.let { zip.getEntry(it) }
                val baseDir = opfPath?.substringBeforeLast('/', "")?.let {
                    if (it.isEmpty()) "" else "$it/"
                } ?: ""

                var bookTitle = epubFile.nameWithoutExtension
                var bookAuthor = ""
                val manifest = mutableMapOf<String, String>()
                val spineIds = mutableListOf<String>()

                if (opfEntry != null) {
                    zip.getInputStream(opfEntry).use { stream ->
                        val parser = Xml.newPullParser().apply {
                            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                            setInput(stream, "UTF-8")
                        }
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (parser.name.lowercase()) {
                                    "title" -> {
                                        val txt = runCatching { parser.nextText() }.getOrDefault("").trim()
                                        if (txt.isNotEmpty()) bookTitle = txt
                                    }
                                    "creator" -> {
                                        val txt = runCatching { parser.nextText() }.getOrDefault("").trim()
                                        if (txt.isNotEmpty() && bookAuthor.isEmpty()) bookAuthor = txt
                                    }
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id").orEmpty()
                                        val href = parser.getAttributeValue(null, "href").orEmpty()
                                        if (id.isNotEmpty() && href.isNotEmpty()) {
                                            manifest[id] = resolveRelativePath(baseDir, href)
                                        }
                                    }
                                    "itemref" -> {
                                        val idref = parser.getAttributeValue(null, "idref").orEmpty()
                                        if (idref.isNotEmpty()) spineIds.add(idref)
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                }

                val chapterPaths = spineIds
                    .mapNotNull { manifest[it] }
                    .filter { path ->
                        val lower = path.lowercase()
                        lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                    }
                    .ifEmpty {
                        zip.entries().asSequence()
                            .map { it.name }
                            .filter { name ->
                                val lower = name.lowercase()
                                (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                                    !lower.contains("nav") && !lower.contains("toc")
                            }
                            .sorted()
                            .toList()
                    }

                val chapters = chapterPaths.mapIndexedNotNull { idx, entryPath ->
                    val entry = zip.getEntry(entryPath) ?: return@mapIndexedNotNull null
                    val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = extractReadableChapter(html, defaultTitle = "Chapter ${idx + 1}")
                    if (parsed.second.isBlank()) null else EpubChapter(
                        index = idx,
                        title = parsed.first,
                        text = parsed.second
                    )
                }.ifEmpty {
                    listOf(EpubChapter(index = 0, title = bookTitle, text = "No readable text found in this EPUB."))
                }

                EpubBook(
                    title = bookTitle,
                    author = bookAuthor,
                    chapters = chapters
                )
            }
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml")
        if (containerEntry != null) {
            zip.getInputStream(containerEntry).use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(stream, "UTF-8")
                }
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                        val fullPath = parser.getAttributeValue(null, "full-path")
                        if (!fullPath.isNullOrBlank()) return fullPath
                    }
                    event = parser.next()
                }
            }
        }
        return zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
    }

    private fun resolveRelativePath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').trimStart('/')
        if (baseDir.isEmpty()) return cleanHref
        val combined = "$baseDir$cleanHref"
        val parts = combined.split('/')
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun extractReadableChapter(html: String, defaultTitle: String): Pair<String, String> {
        val withoutScripts = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        val headingMatch = Regex("<(h1|h2|h3|title)[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(withoutScripts)
        val rawHeading = headingMatch?.groupValues?.getOrNull(2)
            ?.replace(Regex("<[^>]+>"), "")
            ?.let(::decodeHtmlEntities)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val withBreaks = withoutScripts
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h1|h2|h3|h4|h5|h6|li|tr|blockquote)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")

        val plain = decodeHtmlEntities(withBreaks.replace(Regex("<[^>]+>"), ""))
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val title = rawHeading ?: plain.lineSequence().firstOrNull { it.isNotBlank() }?.take(64) ?: defaultTitle
        return title to plain
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }
}
