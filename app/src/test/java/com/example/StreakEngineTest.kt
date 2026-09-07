package com.example

import com.example.ui.util.StreakEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakEngineTest {

    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 30)

    private fun toMillis(date: LocalDate, hour: Int = 12, minute: Int = 0): Long {
        return date.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()
    }

    @Test
    fun `empty timestamps returns 0 streak`() {
        val result = StreakEngine.calculateStreak(
            timestamps = emptyList(),
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(0, result.activeStreakCount)
        assertEquals(0, result.todaySnipsCount)
        assertFalse(result.isTodayValid)
    }

    @Test
    fun `fewer than 3 snips today without yesterday snips returns 0 streak`() {
        val timestamps = listOf(
            toMillis(today, 10),
            toMillis(today, 11)
        )
        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(0, result.activeStreakCount)
        assertEquals(2, result.todaySnipsCount)
        assertFalse(result.isTodayValid)
    }

    @Test
    fun `3 snips today returns 1 day streak`() {
        val timestamps = listOf(
            toMillis(today, 9),
            toMillis(today, 12),
            toMillis(today, 15)
        )
        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(1, result.activeStreakCount)
        assertEquals(3, result.todaySnipsCount)
        assertTrue(result.isTodayValid)
    }

    @Test
    fun `yesterday had 3 snips and today has 1 snip preserves yesterday streak`() {
        val yesterday = today.minusDays(1)
        val timestamps = listOf(
            toMillis(yesterday, 9),
            toMillis(yesterday, 12),
            toMillis(yesterday, 15),
            toMillis(today, 10)
        )
        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(1, result.activeStreakCount)
        assertEquals(1, result.todaySnipsCount)
        assertFalse(result.isTodayValid)
    }

    @Test
    fun `yesterday had 3 snips and today achieves 3 snips extends streak to 2`() {
        val yesterday = today.minusDays(1)
        val timestamps = listOf(
            toMillis(yesterday, 9),
            toMillis(yesterday, 12),
            toMillis(yesterday, 15),
            toMillis(today, 10),
            toMillis(today, 14),
            toMillis(today, 18)
        )
        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(2, result.activeStreakCount)
        assertEquals(3, result.todaySnipsCount)
        assertTrue(result.isTodayValid)
    }

    @Test
    fun `missed yesterday resets streak to 0 if today has fewer than 3 snips`() {
        val twoDaysAgo = today.minusDays(2)
        val timestamps = listOf(
            toMillis(twoDaysAgo, 9),
            toMillis(twoDaysAgo, 12),
            toMillis(twoDaysAgo, 15),
            // Yesterday skipped!
            toMillis(today, 10) // Today only 1 snip
        )
        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(0, result.activeStreakCount)
    }

    @Test
    fun `multi-day streak calculates consecutive valid days accurately`() {
        val timestamps = mutableListOf<Long>()
        // 5 valid days: today - 4 to today
        for (i in 0..4) {
            val date = today.minusDays(i.toLong())
            timestamps.add(toMillis(date, 9))
            timestamps.add(toMillis(date, 13))
            timestamps.add(toMillis(date, 17))
        }

        val result = StreakEngine.calculateStreak(
            timestamps = timestamps,
            referenceDate = today,
            zoneId = zoneId
        )
        assertEquals(5, result.activeStreakCount)
        assertTrue(result.isTodayValid)
    }

    @Test
    fun `reader rank progressions match rank tier milestones`() {
        assertEquals("Novice Apprentice", StreakEngine.calculateReaderRank(0, 0))
        assertEquals("Novice Apprentice", StreakEngine.calculateReaderRank(2, 0))
        assertEquals("Curious Explorer", StreakEngine.calculateReaderRank(3, 0))
        assertEquals("Curious Explorer", StreakEngine.calculateReaderRank(1, 1))
        assertEquals("Dedicated Reader", StreakEngine.calculateReaderRank(10, 0))
        assertEquals("Dedicated Reader", StreakEngine.calculateReaderRank(2, 3))
        assertEquals("Master Reader", StreakEngine.calculateReaderRank(25, 0))
        assertEquals("Master Reader", StreakEngine.calculateReaderRank(5, 7))
        assertEquals("Avid Scholar", StreakEngine.calculateReaderRank(50, 0))
        assertEquals("Avid Scholar", StreakEngine.calculateReaderRank(5, 14))
        assertEquals("Literary Grandmaster", StreakEngine.calculateReaderRank(100, 0))
        assertEquals("Literary Grandmaster", StreakEngine.calculateReaderRank(10, 30))
    }
}
