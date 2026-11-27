package com.example.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsTextSegmenterTest {

    @Test
    fun `segment tracks page boundaries and clean sentences`() {
        val sample = "Welcome to PDF Toolkit. Every feature works offline.\u000CSecond page starts here! Does it track pages?"
        val sentences = TtsTextSegmenter.segment(sample, Locale.US)

        assertEquals(4, sentences.size)
        assertEquals(1, sentences[0].pageNumber)
        assertEquals(1, sentences[1].pageNumber)
        assertEquals(2, sentences[2].pageNumber)
        assertEquals(2, sentences[3].pageNumber)
        assertEquals("Welcome to PDF Toolkit.", sentences[0].text)
    }

    @Test
    fun `segment splits oversized paragraphs safely under TTS input limit`() {
        val longParagraph = buildString {
            repeat(500) {
                append("offline document processing ")
            }
        }
        val sentences = TtsTextSegmenter.segment(longParagraph, Locale.US)

        assertTrue(sentences.size >= 4)
        assertTrue(sentences.all { it.text.length <= 3500 })
    }
}
