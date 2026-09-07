package com.example

import com.example.data.util.ProgressCalculator
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying ProgressCalculator title cleaning and core behaviors.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCleanChapterTitle_stripsRedundantPrefixes() {
        assertEquals(
            "The Self-Image: Your Key to Living Without Limits",
            ProgressCalculator.cleanChapterTitle("Chapter One: The Self-Image: Your Key to Living Without Limits")
        )
        assertEquals(
            "How to Awaken the Automatic Success Mechanism",
            ProgressCalculator.cleanChapterTitle("Chapter Two: How to Awaken the Automatic Success Mechanism")
        )
        assertEquals(
            "De-Hypnotize Yourself From False Beliefs",
            ProgressCalculator.cleanChapterTitle("Chapter 3: De-Hypnotize Yourself From False Beliefs")
        )
        assertEquals(
            "Utilizing the Power of Mental Imagery",
            ProgressCalculator.cleanChapterTitle("Ch. 4 - Utilizing the Power of Mental Imagery")
        )
        assertEquals(
            "The Master Key",
            ProgressCalculator.cleanChapterTitle("Chapter IV: The Master Key")
        )
        assertEquals(
            "The Seductive Character",
            ProgressCalculator.cleanChapterTitle("Part One: The Seductive Character")
        )
    }

    @Test
    fun testCleanChapterTitle_preservesNonRedundantTitles() {
        assertEquals(
            "Introduction",
            ProgressCalculator.cleanChapterTitle("Introduction")
        )
        assertEquals(
            "Preface: A Personal Note",
            ProgressCalculator.cleanChapterTitle("Preface: A Personal Note")
        )
        assertEquals(
            "Chapter 1",
            ProgressCalculator.cleanChapterTitle("Chapter 1")
        )
    }
}
