package com.example.feature.ai

import com.example.core.common.IoDispatcher
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

data class ExtractedEntityBadge(
    val typeLabel: String,
    val value: String
)

data class DocumentAiInsights(
    val summary: String,
    val keyPoints: List<String>,
    val actionItems: List<String>,
    val entities: List<ExtractedEntityBadge>,
    val suggestedFilenames: List<String>,
    val chunkCount: Int,
    val usedMlKitEntityModel: Boolean
)

data class DocumentQaAnswer(
    val question: String,
    val answer: String,
    val citedPages: List<Int>
)

@Singleton
class OnDeviceAiEngine @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun analyzeDocument(
        rawText: String,
        fallbackTitle: String
    ): DocumentAiInsights = withContext(ioDispatcher) {
        val pages = rawText.split('\u000C').map { it.trim() }.filter { it.isNotEmpty() }
        val fullText = pages.joinToString("\n\n").ifBlank { rawText.trim() }

        val sentences = splitIntoSentences(fullText)
        val summary = buildExtractiveSummary(sentences, maxSentences = 4)
        val keyPoints = extractKeyPoints(sentences, maxPoints = 5)
        val actionItems = extractActionItems(sentences, maxItems = 5)
        val (entities, usedMlKit) = extractEntitiesOffline(fullText.take(12_000))
        val suggestedNames = suggestCleanFilenames(fullText, entities, fallbackTitle)
        val chunks = chunkPages(pages.ifEmpty { listOf(fullText) })

        DocumentAiInsights(
            summary = summary,
            keyPoints = keyPoints,
            actionItems = actionItems,
            entities = entities,
            suggestedFilenames = suggestedNames,
            chunkCount = chunks.size,
            usedMlKitEntityModel = usedMlKit
        )
    }

    suspend fun answerQuestion(
        rawText: String,
        question: String
    ): DocumentQaAnswer = withContext(ioDispatcher) {
        val cleanQ = question.trim()
        if (cleanQ.isBlank()) {
            return@withContext DocumentQaAnswer(
                question = cleanQ,
                answer = "Ask a question about dates, amounts, clauses, or topics in this document.",
                citedPages = emptyList()
            )
        }
        val pages = rawText.split('\u000C').ifEmpty { listOf(rawText) }
        val chunks = chunkPages(pages)
        val queryTokens = tokenize(cleanQ).filterNot { it in STOP_WORDS }.toSet()

        if (chunks.isEmpty() || queryTokens.isEmpty()) {
            return@withContext DocumentQaAnswer(
                question = cleanQ,
                answer = "Could not find specific matching content for \"$cleanQ\".",
                citedPages = emptyList()
            )
        }

        val scored = chunks.map { chunk ->
            val chunkLower = chunk.text.lowercase()
            val chunkTokens = tokenize(chunk.text)
            var score = 0.0
            for (qt in queryTokens) {
                val exactHits = chunkTokens.count { it == qt }
                val prefixHits = if (exactHits == 0) chunkTokens.count { it.startsWith(qt.take(4)) && qt.length >= 4 } else 0
                score += exactHits * 2.5 + prefixHits * 1.1
            }
            if (chunkLower.contains(cleanQ.lowercase())) {
                score += 6.0
            }
            chunk to score
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(3)

        if (scored.isEmpty()) {
            return@withContext DocumentQaAnswer(
                question = cleanQ,
                answer = "No matching passage was found inside the indexed pages for \"$cleanQ\".",
                citedPages = emptyList()
            )
        }

        val citedPages = scored.map { it.first.pageNumber }.distinct().sorted()
        val relevantSentences = scored
            .flatMap { (chunk, _) ->
                splitIntoSentences(chunk.text).filter { sent ->
                    val toks = tokenize(sent).toSet()
                    queryTokens.any { qt -> toks.any { it == qt || (qt.length >= 4 && it.startsWith(qt.take(4))) } }
                }
            }
            .distinct()
            .take(4)

        val answerBody = if (relevantSentences.isNotEmpty()) {
            relevantSentences.joinToString(" ")
        } else {
            scored.first().first.text.take(360)
        }

        DocumentQaAnswer(
            question = cleanQ,
            answer = answerBody,
            citedPages = citedPages
        )
    }

    private suspend fun extractEntitiesOffline(sample: String): Pair<List<ExtractedEntityBadge>, Boolean> {
        if (sample.isBlank()) return emptyList<ExtractedEntityBadge>() to false

        val mlKitRes = runCatching {
            val options = EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
            val extractor = EntityExtraction.getClient(options)
            try {
                val downloaded = extractor.isModelDownloaded.await()
                if (downloaded) {
                    val annotations = extractor.annotate(sample).await()
                    val badges = annotations.flatMap { ann ->
                        ann.entities.mapNotNull { entity ->
                            val label = when (entity.type) {
                                Entity.TYPE_DATE_TIME -> "Date"
                                Entity.TYPE_EMAIL -> "Email"
                                Entity.TYPE_PHONE -> "Phone"
                                Entity.TYPE_MONEY -> "Amount"
                                Entity.TYPE_URL -> "Link"
                                Entity.TYPE_ADDRESS -> "Address"
                                else -> null
                            }
                            label?.let { ExtractedEntityBadge(it, ann.annotatedText.trim()) }
                        }
                    }.distinctBy { "${it.typeLabel}:${it.value.lowercase()}" }.take(12)
                    badges to true
                } else {
                    emptyList<ExtractedEntityBadge>() to false
                }
            } finally {
                extractor.close()
            }
        }
        val mlBadges = mlKitRes.getOrNull()
        if (mlBadges != null && mlBadges.second && mlBadges.first.isNotEmpty()) {
            return mlBadges
        }

        // Fast deterministic on-device regex fallback when ML Kit Entity model isn't downloaded yet
        val fallback = mutableListOf<ExtractedEntityBadge>()
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""").findAll(sample).take(4).forEach {
            fallback.add(ExtractedEntityBadge("Email", it.value))
        }
        Regex("""\$\s?\d{1,3}(?:,\d{3})*(?:\.\d{2})?""").findAll(sample).take(4).forEach {
            fallback.add(ExtractedEntityBadge("Amount", it.value))
        }
        Regex("""\b\d{4}-\d{2}-\d{2}\b|\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2},?\s+\d{4}\b""", RegexOption.IGNORE_CASE)
            .findAll(sample).take(4).forEach {
                fallback.add(ExtractedEntityBadge("Date", it.value))
            }
        Regex("""\b(?:\+?\d{1,2}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b""").findAll(sample).take(3).forEach {
            fallback.add(ExtractedEntityBadge("Phone", it.value))
        }
        return fallback.distinctBy { "${it.typeLabel}:${it.value}" } to false
    }

    private fun buildExtractiveSummary(sentences: List<String>, maxSentences: Int): String {
        if (sentences.isEmpty()) return "No text available to summarize."
        if (sentences.size <= maxSentences) return sentences.joinToString(" ")

        val wordFreq = mutableMapOf<String, Int>()
        sentences.forEach { s ->
            tokenize(s).filterNot { it in STOP_WORDS }.forEach { tok ->
                wordFreq[tok] = (wordFreq[tok] ?: 0) + 1
            }
        }

        val ranked = sentences.mapIndexed { idx, sent ->
            val words = tokenize(sent).filterNot { it in STOP_WORDS }
            val tfScore = words.sumOf { wordFreq[it] ?: 0 }.toDouble() / (words.size + 2)
            val positionBoost = if (idx < 3) 1.35 else 1.0
            val lengthPenalty = if (sent.length < 25 || sent.length > 320) 0.65 else 1.0
            Triple(idx, sent, tfScore * positionBoost * lengthPenalty)
        }.sortedByDescending { it.third }
            .take(maxSentences)
            .sortedBy { it.first }

        return ranked.joinToString(" ") { it.second }
    }

    private fun extractKeyPoints(sentences: List<String>, maxPoints: Int): List<String> {
        if (sentences.isEmpty()) return emptyList()
        val bullets = sentences.filter {
            it.startsWith("•") || it.startsWith("-") || Regex("""^\d+[.)]\s""").containsMatchIn(it)
        }.map { it.trimStart('•', '-', ' ').trim() }
        if (bullets.size >= 2) return bullets.distinct().take(maxPoints)

        val wordFreq = mutableMapOf<String, Int>()
        sentences.forEach { s ->
            tokenize(s).filterNot { it in STOP_WORDS }.forEach { wordFreq[it] = (wordFreq[it] ?: 0) + 1 }
        }
        return sentences.map { s ->
            val toks = tokenize(s).filterNot { it in STOP_WORDS }
            val score = toks.sumOf { ln(1.0 + (wordFreq[it] ?: 0)) } / (toks.size + 3)
            s to score
        }.filter { it.first.length in 24..200 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(maxPoints)
    }

    private fun extractActionItems(sentences: List<String>, maxItems: Int): List<String> {
        val actionVerbs = setOf(
            "must", "required", "should", "need", "needs", "submit", "sign", "review",
            "complete", "deadline", "due", "verify", "send", "return", "pay", "approve", "update"
        )
        return sentences.filter { sent ->
            val toks = tokenize(sent)
            toks.any { it in actionVerbs }
        }.distinct().take(maxItems)
    }

    private fun suggestCleanFilenames(
        fullText: String,
        entities: List<ExtractedEntityBadge>,
        fallbackTitle: String
    ): List<String> {
        val firstLine = fullText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length in 5..60 && it.any(Char::isLetter) }
            ?.replace(Regex("[^A-Za-z0-9 ]"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), "_")

        val wordFreq = mutableMapOf<String, Int>()
        tokenize(fullText.take(4000))
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .forEach { wordFreq[it] = (wordFreq[it] ?: 0) + 1 }
        val topKeywords = wordFreq.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key.replaceFirstChar(Char::uppercaseChar) }

        val dateSuffix = entities.firstOrNull { it.typeLabel == "Date" }?.value
            ?.replace(Regex("[^A-Za-z0-9]"), "_")
            ?.trim('_')
            ?.take(12)

        val candidates = mutableListOf<String>()
        if (!firstLine.isNullOrBlank()) {
            candidates.add(if (dateSuffix != null) "${firstLine}_$dateSuffix" else firstLine)
        }
        if (topKeywords.isNotEmpty()) {
            candidates.add(topKeywords.joinToString("_") + (dateSuffix?.let { "_$it" } ?: ""))
        }
        val cleanedFallback = fallbackTitle.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "_")
        if (cleanedFallback.isNotBlank()) {
            candidates.add("${cleanedFallback}_Clean")
        }
        return candidates.map { it.take(48).trim('_') }.filter { it.isNotBlank() }.distinct().take(3)
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 16 }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }

    private data class PageChunk(val pageNumber: Int, val text: String)

    private fun chunkPages(pages: List<String>): List<PageChunk> {
        val chunks = mutableListOf<PageChunk>()
        pages.forEachIndexed { pageIdx, content ->
            val clean = content.trim()
            if (clean.isEmpty()) return@forEachIndexed
            var start = 0
            while (start < clean.length) {
                val end = (start + 480).coerceAtMost(clean.length)
                chunks.add(PageChunk(pageNumber = pageIdx + 1, text = clean.substring(start, end)))
                if (end == clean.length) break
                start += 360
            }
        }
        return chunks
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "from", "are", "was", "were",
            "have", "has", "had", "not", "but", "you", "your", "can", "will", "all",
            "into", "about", "than", "then", "also", "been", "they", "their", "them",
            "when", "where", "which", "who", "what", "how", "more", "some", "such"
        )
    }
}
