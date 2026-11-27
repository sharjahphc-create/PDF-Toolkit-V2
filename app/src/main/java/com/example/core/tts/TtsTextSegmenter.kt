package com.example.core.tts

import java.text.BreakIterator
import java.util.Locale

object TtsTextSegmenter {

    private const val MAX_UTTERANCE_CHARS = 3500

    /**
     * Splits raw document text into clean speakable sentences with character offsets.
     * Supports optional page markers ("\u000C" form feed or explicit page blocks).
     */
    fun segment(
        rawText: String,
        locale: Locale = Locale.getDefault()
    ): List<TtsSentence> {
        if (rawText.isBlank()) return emptyList()

        val pages = rawText.split('\u000C')
        val result = mutableListOf<TtsSentence>()
        var globalOffset = 0
        var sentenceIndex = 0

        pages.forEachIndexed { pageIdx, pageText ->
            val pageNumber = pageIdx + 1
            val normalizedPage = pageText
                .replace(Regex("[ \\t\\r]+"), " ")
                .replace(Regex("\\n{2,}"), "\n")
                .trim()

            if (normalizedPage.isNotEmpty()) {
                val iterator = BreakIterator.getSentenceInstance(locale)
                iterator.setText(normalizedPage)
                var start = iterator.first()
                var end = iterator.next()
                while (end != BreakIterator.DONE) {
                    val slice = normalizedPage.substring(start, end)
                        .replace('\n', ' ')
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    if (slice.any { it.isLetterOrDigit() }) {
                        if (slice.length <= MAX_UTTERANCE_CHARS) {
                            result.add(
                                TtsSentence(
                                    index = sentenceIndex++,
                                    text = slice,
                                    startOffset = globalOffset + start,
                                    endOffset = globalOffset + end,
                                    pageNumber = pageNumber
                                )
                            )
                        } else {
                            // Split oversized paragraphs safely under TextToSpeech.getMaxSpeechInputLength()
                            var chunkStart = 0
                            while (chunkStart < slice.length) {
                                val hardEnd = (chunkStart + MAX_UTTERANCE_CHARS).coerceAtMost(slice.length)
                                val splitAt = if (hardEnd < slice.length) {
                                    slice.lastIndexOf(' ', hardEnd).takeIf { it > chunkStart } ?: hardEnd
                                } else {
                                    hardEnd
                                }
                                val chunk = slice.substring(chunkStart, splitAt).trim()
                                if (chunk.isNotEmpty()) {
                                    result.add(
                                        TtsSentence(
                                            index = sentenceIndex++,
                                            text = chunk,
                                            startOffset = globalOffset + start + chunkStart,
                                            endOffset = globalOffset + start + splitAt,
                                            pageNumber = pageNumber
                                        )
                                    )
                                }
                                chunkStart = if (splitAt <= chunkStart) hardEnd else splitAt + 1
                            }
                        }
                    }
                    start = end
                    end = iterator.next()
                }
            }
            globalOffset += pageText.length + 1
        }
        return result
    }
}
