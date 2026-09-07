package com.example

import com.example.data.ocr.TextMergeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TextMergeUtilsTest {

    @Test
    fun testSanitizeOcrText_singleLine() {
        val raw = "  The quick brown fox jumps over the lazy dog.  "
        val result = TextMergeUtils.sanitizeOcrText(raw)
        assertEquals("The quick brown fox jumps over the lazy dog.", result)
    }

    @Test
    fun testSanitizeOcrText_hyphenatedBreak() {
        val raw = """
            This is a fundamen-
            tal concept in physics.
        """.trimIndent()
        val result = TextMergeUtils.sanitizeOcrText(raw)
        assertEquals("This is a fundamental concept in physics.", result)
    }

    @Test
    fun testSanitizeOcrText_dashesAndWordCorruptions() {
        val raw = "beware offriends--they will betray you quickly and f0 hae no enmies is impossible."
        val result = TextMergeUtils.sanitizeOcrText(raw)
        assertEquals("beware of friends — they will betray you quickly and to have no enemies is impossible.", result)
    }

    @Test
    fun testSanitizeOcrText_structuralParagraphs() {
        val raw = """
            LAW 2

            NEVER PUT TOO MUCH TRUST IN FRIENDS
            LEARN HOW TO USE ENEMIES

            JUDGMENT
            Be wary offriends--they will quickly betray you.
        """.trimIndent()
        val result = TextMergeUtils.sanitizeOcrText(raw)
        val expected = "LAW 2\n\nNEVER PUT TOO MUCH TRUST IN FRIENDS LEARN HOW TO USE ENEMIES\n\nJUDGMENT\n\nBe wary of friends — they will quickly betray you."
        assertEquals(expected, result)
    }

    @Test
    fun testMergeMultiPagePassages_hyphenated() {
        val part1 = "It is important to understand the infor-"
        val part2 = "mation provided in this document."
        val merged = TextMergeUtils.mergeMultiPagePassages(part1, part2)
        assertEquals("It is important to understand the information provided in this document.", merged)
    }

    @Test
    fun testMergeMultiPagePassages_standard() {
        val part1 = "Chapter 1 begins with an overview of history."
        val part2 = "It discusses the primary revolutions."
        val merged = TextMergeUtils.mergeMultiPagePassages(part1, part2)
        assertEquals("Chapter 1 begins with an overview of history. It discusses the primary revolutions.", merged)
    }

    @Test
    fun testCountWords() {
        val text = "ReadMate empowers readers with on-device OCR and AI."
        val count = TextMergeUtils.countWords(text)
        assertEquals(8, count)
    }
}
