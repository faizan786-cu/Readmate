package com.example.data.repository

import com.example.data.local.database.dao.UserGamificationDao
import com.example.data.local.database.entity.UserGamificationStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class GamificationXpResult(
    val baseXp: Int,
    val bonusXp: Int,
    val totalAwardedXp: Int,
    val isDailyDoubleTriggered: Boolean,
    val newTotalXp: Int
)

class UserGamificationRepository(
    private val userGamificationDao: UserGamificationDao
) {
    companion object {
        const val DAILY_DOUBLE_BONUS_XP = 50
    }

    val userStats: Flow<UserGamificationStats> = userGamificationDao.observeStats().map { stats ->
        val todayStr = LocalDate.now().toString()
        if (stats == null) {
            UserGamificationStats(id = 1L, totalXp = 0, lastActiveDate = todayStr)
        } else if (stats.lastActiveDate.isNotBlank() && stats.lastActiveDate != todayStr) {
            // Represent reset state for UI if on a new day
            stats.copy(
                todaySnippetQuizCompleted = false,
                todayWordQuizCompleted = false,
                lastActiveDate = todayStr
            )
        } else {
            stats
        }
    }

    suspend fun getStats(): UserGamificationStats {
        val todayStr = LocalDate.now().toString()
        val current = userGamificationDao.getStats()
        return if (current == null) {
            val initial = UserGamificationStats(id = 1L, totalXp = 0, lastActiveDate = todayStr)
            userGamificationDao.insertOrUpdate(initial)
            initial
        } else if (current.lastActiveDate.isNotBlank() && current.lastActiveDate != todayStr) {
            val reset = current.copy(
                todaySnippetQuizCompleted = false,
                todayWordQuizCompleted = false,
                lastActiveDate = todayStr,
                updatedAt = System.currentTimeMillis()
            )
            userGamificationDao.insertOrUpdate(reset)
            reset
        } else {
            current
        }
    }

    suspend fun addXp(points: Int) {
        if (points <= 0) return
        val stats = getStats()
        val updated = stats.copy(
            totalXp = stats.totalXp + points,
            updatedAt = System.currentTimeMillis()
        )
        userGamificationDao.insertOrUpdate(updated)
    }

    suspend fun recordSnippetQuizCompleted(earnedBaseXp: Int): GamificationXpResult {
        val todayStr = LocalDate.now().toString()
        val current = userGamificationDao.getStats() ?: UserGamificationStats(id = 1L, totalXp = 0, lastActiveDate = todayStr)

        val isNewDay = current.lastActiveDate != todayStr
        val wordCompleted = if (isNewDay) false else current.todayWordQuizCompleted
        val prevSnippetCompleted = if (isNewDay) false else current.todaySnippetQuizCompleted

        var bonusXp = 0
        var isDailyDoubleTriggered = false

        // Check if completing snippet challenge triggers the Daily Double (+50 Bonus XP)
        if (!prevSnippetCompleted && wordCompleted) {
            bonusXp = DAILY_DOUBLE_BONUS_XP
            isDailyDoubleTriggered = true
        }

        val totalAwarded = earnedBaseXp.coerceAtLeast(0) + bonusXp
        val newTotalXp = current.totalXp + totalAwarded

        val updated = current.copy(
            totalXp = newTotalXp,
            todaySnippetQuizCompleted = true,
            todayWordQuizCompleted = wordCompleted,
            lastActiveDate = todayStr,
            updatedAt = System.currentTimeMillis()
        )
        userGamificationDao.insertOrUpdate(updated)

        return GamificationXpResult(
            baseXp = earnedBaseXp,
            bonusXp = bonusXp,
            totalAwardedXp = totalAwarded,
            isDailyDoubleTriggered = isDailyDoubleTriggered,
            newTotalXp = newTotalXp
        )
    }

    suspend fun recordWordQuizCompleted(earnedBaseXp: Int): GamificationXpResult {
        val todayStr = LocalDate.now().toString()
        val current = userGamificationDao.getStats() ?: UserGamificationStats(id = 1L, totalXp = 0, lastActiveDate = todayStr)

        val isNewDay = current.lastActiveDate != todayStr
        val snippetCompleted = if (isNewDay) false else current.todaySnippetQuizCompleted
        val prevWordCompleted = if (isNewDay) false else current.todayWordQuizCompleted

        var bonusXp = 0
        var isDailyDoubleTriggered = false

        // Check if completing word vault challenge triggers the Daily Double (+50 Bonus XP)
        if (!prevWordCompleted && snippetCompleted) {
            bonusXp = DAILY_DOUBLE_BONUS_XP
            isDailyDoubleTriggered = true
        }

        val totalAwarded = earnedBaseXp.coerceAtLeast(0) + bonusXp
        val newTotalXp = current.totalXp + totalAwarded

        val updated = current.copy(
            totalXp = newTotalXp,
            todaySnippetQuizCompleted = snippetCompleted,
            todayWordQuizCompleted = true,
            lastActiveDate = todayStr,
            updatedAt = System.currentTimeMillis()
        )
        userGamificationDao.insertOrUpdate(updated)

        return GamificationXpResult(
            baseXp = earnedBaseXp,
            bonusXp = bonusXp,
            totalAwardedXp = totalAwarded,
            isDailyDoubleTriggered = isDailyDoubleTriggered,
            newTotalXp = newTotalXp
        )
    }
}
