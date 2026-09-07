package com.example.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StreakInfo(
    val activeStreakCount: Int,
    val todaySnipsCount: Int,
    val isTodayValid: Boolean,
    val requiredSnipsPerDay: Int = 3
)

object StreakEngine {

    const val REQUIRED_SNIPS_PER_DAY = 3

    /**
     * Calculates the active unbroken reading streak according to the strict 3-snip rule:
     * - A "Valid Reading Day" requires at least 3 distinct explanation/snip events.
     * - If today has >= 3 snips, today extends the streak backwards across all consecutive valid days.
     * - If today has < 3 snips:
     *     - If yesterday was valid (>= 3 snips), the streak holds from yesterday backwards.
     *     - If yesterday was skipped (< 3 snips), the active streak resets to 0.
     * - Opening the app without active reading/snipping never increments or preserves the streak.
     */
    fun calculateStreak(
        timestamps: List<Long>,
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StreakInfo {
        if (timestamps.isEmpty()) {
            return StreakInfo(
                activeStreakCount = 0,
                todaySnipsCount = 0,
                isTodayValid = false,
                requiredSnipsPerDay = REQUIRED_SNIPS_PER_DAY
            )
        }

        // Group timestamps into local calendar days and count snips per day
        val daySnipCounts = mutableMapOf<LocalDate, Int>()
        for (timestamp in timestamps) {
            val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
            daySnipCounts[date] = (daySnipCounts[date] ?: 0) + 1
        }

        val todaySnips = daySnipCounts[referenceDate] ?: 0
        val isTodayValid = todaySnips >= REQUIRED_SNIPS_PER_DAY

        val yesterday = referenceDate.minusDays(1)
        val yesterdaySnips = daySnipCounts[yesterday] ?: 0
        val isYesterdayValid = yesterdaySnips >= REQUIRED_SNIPS_PER_DAY

        var streak = 0

        if (isTodayValid) {
            // Streak starts with today and counts all preceding consecutive valid days
            streak = 1
            var checkDate = yesterday
            while ((daySnipCounts[checkDate] ?: 0) >= REQUIRED_SNIPS_PER_DAY) {
                streak++
                checkDate = checkDate.minusDays(1)
            }
        } else if (isYesterdayValid) {
            // Yesterday was valid, so yesterday's streak is currently maintained pending today's snips
            streak = 1
            var checkDate = yesterday.minusDays(1)
            while ((daySnipCounts[checkDate] ?: 0) >= REQUIRED_SNIPS_PER_DAY) {
                streak++
                checkDate = checkDate.minusDays(1)
            }
        } else {
            // Yesterday was skipped and today is not yet valid -> streak is 0
            streak = 0
        }

        return StreakInfo(
            activeStreakCount = streak,
            todaySnipsCount = todaySnips,
            isTodayValid = isTodayValid,
            requiredSnipsPerDay = REQUIRED_SNIPS_PER_DAY
        )
    }

    /**
     * Reader Rank progression calculated dynamically from total lifetime XP points:
     * - Novice Reader: 0 – 250 XP
     * - Consistent Scholar: 251 – 700 XP
     * - Analytical Thinker: 701 – 1,500 XP
     * - Strategic Mind: 1,501 – 3,000 XP
     * - Master Strategist: 3,000+ XP
     */
    fun calculateReaderRank(totalXp: Int): String {
        return when {
            totalXp >= 3001 || totalXp >= 3000 -> "Master Strategist"
            totalXp >= 1501 -> "Strategic Mind"
            totalXp >= 701 -> "Analytical Thinker"
            totalXp >= 251 -> "Consistent Scholar"
            else -> "Novice Reader"
        }
    }

    fun calculateReaderRankByXp(totalXp: Int): String = calculateReaderRank(totalXp)

    /**
     * Legacy snip/streak rank progression kept for backward-compatibility.
     */
    fun calculateReaderRank(totalSnips: Int, streakDays: Int): String {
        return when {
            totalSnips >= 100 || streakDays >= 30 -> "Literary Grandmaster"
            totalSnips >= 50 || streakDays >= 14 -> "Avid Scholar"
            totalSnips >= 25 || streakDays >= 7 -> "Master Reader"
            totalSnips >= 10 || streakDays >= 3 -> "Dedicated Reader"
            totalSnips >= 3 || streakDays >= 1 -> "Curious Explorer"
            else -> "Novice Apprentice"
        }
    }
}
