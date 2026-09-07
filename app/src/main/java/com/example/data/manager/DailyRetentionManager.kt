package com.example.data.manager

import android.util.Log
import com.example.data.local.database.dao.DailyRetentionStateDao
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.QuizStatus
import com.example.data.local.database.entity.RetentionStreakStatus
import com.example.data.repository.QuizRepository
import com.example.ui.util.StreakEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Core Retention Engine & Midnight Lifecycle Manager.
 * Governs:
 * 1. Day 0 Grace Period (Zero Same-Day Testing on install/snip day).
 * 2. Day 1+ Daily Test Assembly & Quota Selection.
 * 3. Midnight Rollover, Skip Penalty (-6.0f retention drop), FROZEN_DEBT status lock.
 * 4. Rank Downgrade to "Cognitive Stagnation" on debt.
 * 5. Compounding Mistake Bank injection (up to 5 mistake questions injected upon missed days).
 */
class DailyRetentionManager(
    private val dailyRetentionStateDao: DailyRetentionStateDao,
    private val quizRepository: QuizRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "DailyRetentionManager"
        const val SKIP_PENALTY_RETENTION_DROP = 6.0f
        const val SUCCESS_RETENTION_BOOST = 2.0f
        const val DAILY_QUOTA_MAX_NEW = 10
        const val COMPOUNDING_MISTAKE_INJECTION_COUNT = 5
        const val RANK_COGNITIVE_STAGNATION = "Cognitive Stagnation"
    }

    fun getTodayDateString(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun observeTodayRetentionState(todayDate: String = getTodayDateString()): Flow<DailyRetentionStateEntity?> {
        return dailyRetentionStateDao.observeStateForDate(todayDate).distinctUntilChanged()
    }

    fun observeLatestRetentionState(): Flow<DailyRetentionStateEntity?> {
        return dailyRetentionStateDao.observeLatestState().distinctUntilChanged()
    }

    fun observeAllRetentionStates(): Flow<List<DailyRetentionStateEntity>> {
        return dailyRetentionStateDao.observeAllStates().distinctUntilChanged()
    }

    /**
     * Resolves the effective reader rank:
     * Overridden to "Cognitive Stagnation" only if the user has a "FROZEN_DEBT" status lock
     * AND both conditions hold: hasActiveBooks > 0 and hasPendingQuizzes > 0.
     * When no debt exists, returns "Ready to Read" (for 0 XP / 0 books) or the calculated XP rank.
     */
    fun getEffectiveRank(
        totalXp: Int,
        streakStatus: String?,
        hasActiveBooks: Boolean = true,
        hasPendingQuizzes: Boolean = true
    ): String {
        return if (streakStatus == RetentionStreakStatus.FROZEN_DEBT && hasActiveBooks && hasPendingQuizzes) {
            RANK_COGNITIVE_STAGNATION
        } else {
            if (totalXp == 0 && !hasActiveBooks) {
                "Ready to Read"
            } else {
                StreakEngine.calculateReaderRank(totalXp)
            }
        }
    }

    /**
     * Proactively dissolves ghost / orphaned FROZEN_DEBT status when books or pending quizzes reach zero.
     */
    suspend fun clearOrphanedDebtIfNoBooksOrQuizzes(
        totalBooks: Int,
        pendingQuizzes: Int
    ) = withContext(ioDispatcher) {
        if (totalBooks <= 0 || pendingQuizzes <= 0) {
            val todayState = dailyRetentionStateDao.getStateForDate(getTodayDateString())
                ?: dailyRetentionStateDao.getLatestState()
            if (todayState != null && todayState.streakStatus == RetentionStreakStatus.FROZEN_DEBT) {
                dailyRetentionStateDao.insertOrReplace(todayState.copy(streakStatus = RetentionStreakStatus.ACTIVE))
                Log.d(TAG, "Proactively resolved ghost debt: totalBooks=$totalBooks, pendingQuizzes=$pendingQuizzes")
            }
        }
    }

    /**
     * Checks daily status and executes rollover math if a new calendar day has started.
     */
    suspend fun checkAndRollOverDailyStatus(
        todayDate: String = getTodayDateString()
    ): DailyRetentionStateEntity = withContext(ioDispatcher) {
        val existingToday = dailyRetentionStateDao.getStateForDate(todayDate)
        if (existingToday != null) {
            return@withContext existingToday
        }

        val latestPrevious = dailyRetentionStateDao.getLatestStateBeforeDate(todayDate)

        if (latestPrevious == null) {
            // First time running app: Day 0 Grace Period
            val day0State = DailyRetentionStateEntity(
                date = todayDate,
                quizzesCompleted = false,
                totalQuizzesServed = 0,
                correctCount = 0,
                retentionIndex = 100.0f,
                streakStatus = RetentionStreakStatus.ACTIVE,
                streakDays = 0,
                isFirstDayGracePeriod = true
            )
            dailyRetentionStateDao.insertOrReplace(day0State)
            Log.d(TAG, "Initialized Day 0 Grace Period for $todayDate")
            return@withContext day0State
        }

        // Calculate days elapsed between last recorded date and today
        val prevDateParsed = try {
            LocalDate.parse(latestPrevious.date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            LocalDate.now().minusDays(1)
        }
        val todayParsed = try {
            LocalDate.parse(todayDate, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val daysElapsed = ChronoUnit.DAYS.between(prevDateParsed, todayParsed).toInt().coerceAtLeast(1)

        var newRetention = latestPrevious.retentionIndex
        var newStreakStatus = latestPrevious.streakStatus
        var newStreakDays = latestPrevious.streakDays

        if (latestPrevious.isFirstDayGracePeriod) {
            // Transitioning from Day 0 install day to Day 1
            newRetention = 100.0f
            newStreakStatus = RetentionStreakStatus.ACTIVE
            newStreakDays = 0
        } else if (!latestPrevious.quizzesCompleted && latestPrevious.totalQuizzesServed > 0) {
            // Case B: User Skipped / Missed Yesterday's Test!
            // 1. Retention Drop (-6.0f)
            newRetention = max(0f, newRetention - SKIP_PENALTY_RETENTION_DROP)
            // 2. Status Lock
            newStreakStatus = RetentionStreakStatus.FROZEN_DEBT
            newStreakDays = 0

            // 3. Mistake Compounding: Move unattempted questions from yesterday into MISTAKE bank
            val yesterdayQuizzes = quizRepository.getQuizzesServedOnDate(latestPrevious.date)
            val unattemptedIds = yesterdayQuizzes.filter { it.status == QuizStatus.SERVED }.map { it.id }
            if (unattemptedIds.isNotEmpty()) {
                quizRepository.markQuizzesAsMistake(unattemptedIds)
                Log.d(TAG, "Compounded ${unattemptedIds.size} unattempted questions into Mistake Bank.")
            }
        } else if (latestPrevious.quizzesCompleted) {
            // Yesterday was completed
            if (daysElapsed > 1) {
                // Gap of skipped days (> 1 day missed)
                val skippedDays = (daysElapsed - 1).toFloat()
                newRetention = max(0f, newRetention - (SKIP_PENALTY_RETENTION_DROP * skippedDays))
                newStreakStatus = RetentionStreakStatus.FROZEN_DEBT
                newStreakDays = 0
            } else {
                // Exactly 1 day passed and yesterday was completed
                newStreakStatus = RetentionStreakStatus.ACTIVE
            }
        }

        val todayState = DailyRetentionStateEntity(
            date = todayDate,
            quizzesCompleted = false,
            totalQuizzesServed = 0,
            correctCount = 0,
            retentionIndex = newRetention,
            streakStatus = newStreakStatus,
            streakDays = newStreakDays,
            isFirstDayGracePeriod = false
        )
        dailyRetentionStateDao.insertOrReplace(todayState)
        Log.d(TAG, "Rolled over daily state to $todayDate. Retention=$newRetention, Status=$newStreakStatus, Streak=$newStreakDays")
        todayState
    }

    /**
     * Assembles the daily test according to Day 0 rules, quota algorithm, and compounding mistake bank.
     */
    suspend fun assembleDailyQuizSession(
        todayDate: String = getTodayDateString()
    ): List<QuizEntity> = withContext(ioDispatcher) {
        val todayState = checkAndRollOverDailyStatus(todayDate)

        // Day 0 Grace Period: Zero same-day testing
        if (todayState.isFirstDayGracePeriod) {
            Log.d(TAG, "Day 0 Grace Period active. 0 tests served today.")
            return@withContext emptyList()
        }

        // If today's status has FROZEN_DEBT or mistake bank questions exist, inject up to 5 mistake questions
        val mistakeCountToInject = if (todayState.streakStatus == RetentionStreakStatus.FROZEN_DEBT) {
            COMPOUNDING_MISTAKE_INJECTION_COUNT
        } else {
            0
        }

        val assembledQuizzes = quizRepository.assembleDailyTest(
            todayDate = todayDate,
            maxNewQuotas = DAILY_QUOTA_MAX_NEW,
            maxMistakesToInject = mistakeCountToInject
        )

        // Update served count on today's state
        if (assembledQuizzes.size != todayState.totalQuizzesServed) {
            val updated = todayState.copy(totalQuizzesServed = assembledQuizzes.size)
            dailyRetentionStateDao.updateState(updated)
        }

        assembledQuizzes
    }

    /**
     * Records the completion of today's quiz session:
     * - Boosts retention index on good accuracy
     * - Clears FROZEN_DEBT back to ACTIVE
     * - Increments streakDays
     * - Updates quiz statuses to MASTERED or MISTAKE
     */
    suspend fun recordDailyQuizCompleted(
        todayDate: String = getTodayDateString(),
        correctCount: Int,
        totalCount: Int,
        answeredCorrectMap: Map<Long, Boolean> // quizId -> isCorrect
    ): DailyRetentionStateEntity = withContext(ioDispatcher) {
        val currentState = dailyRetentionStateDao.getStateForDate(todayDate) ?: checkAndRollOverDailyStatus(todayDate)

        val total = totalCount.coerceAtLeast(1)
        val accuracy = (correctCount.toFloat() / total.toFloat()) * 100f

        // Recalculate retention positively
        val retentionBoost = if (accuracy >= 80f) SUCCESS_RETENTION_BOOST else (accuracy / 100f) * SUCCESS_RETENTION_BOOST
        val newRetention = min(100.0f, currentState.retentionIndex + retentionBoost)

        // Clear debt and increment streak
        val newStreakStatus = RetentionStreakStatus.ACTIVE
        val newStreakDays = currentState.streakDays + 1

        val updatedState = currentState.copy(
            quizzesCompleted = true,
            totalQuizzesServed = totalCount,
            correctCount = correctCount,
            retentionIndex = newRetention,
            streakStatus = newStreakStatus,
            streakDays = newStreakDays
        )
        dailyRetentionStateDao.insertOrReplace(updatedState)

        // Update mastered/mistake statuses for answered quizzes
        val correctIds = answeredCorrectMap.filter { it.value }.keys.toList()
        val incorrectIds = answeredCorrectMap.filter { !it.value }.keys.toList()

        if (correctIds.isNotEmpty()) {
            quizRepository.markQuizzesMastered(correctIds)
        }
        if (incorrectIds.isNotEmpty()) {
            quizRepository.markQuizzesAsMistake(incorrectIds)
        }

        Log.d(TAG, "Recorded daily quiz completion for $todayDate: $correctCount/$totalCount ($accuracy%). New retention: $newRetention%, Streak: $newStreakDays")
        updatedState
    }

    /**
     * Explicit penalize missed day function called on midnight rollover or upon missing a day cycle.
     */
    suspend fun penalizeMissedDay(
        todayDate: String = getTodayDateString()
    ): DailyRetentionStateEntity = withContext(ioDispatcher) {
        checkAndRollOverDailyStatus(todayDate)
    }
}
