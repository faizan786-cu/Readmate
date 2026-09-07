package com.example.ui.components.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageSanitizerTest {

    @Test
    fun testSanitizeSnippet_removesRawClutterAndHashtags() {
        val raw = "### ## 📖 Original English Passage:\n\n**This** is a sample | snippet ~ with stray tokens."
        val info = PassageSanitizer.sanitizeSnippet(raw)

        assertEquals("This is a sample snippet with stray tokens.", info.cleanText)
        assertEquals("ORIGINAL PASSAGE", info.pageLabel)
    }

    @Test
    fun testSanitizeSnippet_extractsPageNumber() {
        val raw = "[Page 42] Greed is a bottomless pit which exhausts the person in an endless effort to satisfy the need without ever reaching satisfaction."
        val info = PassageSanitizer.sanitizeSnippet(raw)

        assertEquals("Greed is a bottomless pit which exhausts the person in an endless effort to satisfy the need without ever reaching satisfaction.", info.cleanText)
        assertEquals("ORIGINAL PASSAGE • PAGE 42", info.pageLabel)
        assertEquals(42, info.pageNumber)
    }

    @Test
    fun testSanitizeSnippet_extractsDistinctHeading() {
        val raw = "**The Rake**\n\nHe had a reputation for seduction and dangerous charm."
        val info = PassageSanitizer.sanitizeSnippet(raw)

        assertEquals("The Rake", info.heading)
        assertEquals("He had a reputation for seduction and dangerous charm.", info.bodyText)
        assertEquals("The Rake\n\nHe had a reputation for seduction and dangerous charm.", info.cleanText)
    }

    @Test
    fun testSanitizeSnippet_rejoinsHyphenatedLineBreaks() {
        val raw = "The most funda-\nmental rule of power is to never outshine the mas-\nter."
        val info = PassageSanitizer.sanitizeSnippet(raw)

        assertEquals("The most fundamental rule of power is to never outshine the master.", info.cleanText)
    }

    @Test
    fun testSanitizeSnippet_mergesAwkwardSentenceWrapsWhilePreservingParagraphs() {
        val raw = """
            First sentence continues
            on the second line cleanly.

            Second paragraph starts here
            and continues.
        """.trimIndent()
        val info = PassageSanitizer.sanitizeSnippet(raw)

        val expected = "First sentence continues on the second line cleanly.\n\nSecond paragraph starts here and continues."
        assertEquals(expected, info.cleanText)
    }

    @Test
    fun testSanitizeMarkdown_stripsLegacyOriginalPassage() {
        val raw = """
            ## 🧠 Asaan Samjh
            Yeh concept bohot zaroori hai.

            ## 💡 Main Lesson
            Hamesha seekhte rahein.

            ---

            ## 📖 Original English Passage
            Always learn from masters.
        """.trimIndent()

        val sanitized = PassageSanitizer.sanitizeMarkdown(raw)
        assertFalse(sanitized.contains("Original English Passage"))
        assertFalse(sanitized.contains("Always learn from masters"))
        assertTrue(sanitized.contains("Asaan Samjh"))
        assertTrue(sanitized.contains("Main Lesson"))
    }

    @Test
    fun testSanitizeMarkdown_normalizesBullets() {
        val raw = """
            * First point
            - Second point
            + Third point
        """.trimIndent()

        val sanitized = PassageSanitizer.sanitizeMarkdown(raw)
        assertTrue(sanitized.contains("• First point"))
        assertTrue(sanitized.contains("• Second point"))
        assertTrue(sanitized.contains("• Third point"))
    }

    @Test
    fun testParseMarkdownToAnnotatedString_handlesBoldAndItalics() {
        val text = "This has **bold text** and *italic text* and `inline code`."
        val annotated = parseMarkdownToAnnotatedString(text)

        assertNotNull(annotated)
        assertEquals("This has bold text and italic text and inline code.", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
