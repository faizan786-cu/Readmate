package com.example.data.util

import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.entity.WordQuizAttempt
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RetentionRateResult(
    val ratePercentage: Int?,
    val displayString: String,
    val dailyDrillAccuracy: Float?,
    val chapterMasteryAccuracy: Float?,
    val totalAttemptedQuestions: Int,
    val totalCorrectAnswers: Int,
    val decayPenaltyFactor: Float = 0f,
    val isOverdue: Boolean = false
)

object EbbinghausRetentionCalculator {

    // Multi-tier weights: Daily retention testing immediate recall = 60%, Chapter mastery = 40%
    private const val DAILY_RETENTION_WEIGHT = 0.60f
    private const val CHAPTER_MASTERY_WEIGHT = 0.40f

    // Chapter mastery challenges typically have >= 20 questions (standard 50 MCQs)
    private const val MASTERY_QUESTION_THRESHOLD = 20

    // Maximum decay penalty capped at 35%
    private const val MAX_DECAY_PENALTY = 0.35f
    private const val MS_PER_DAY = 86_400_000L

    /**
     * Calculates the dynamic Ebbinghaus Retention Rate strictly evaluated on user-attempted questions.
     *
     * @param mcqAttempts All historical UserMcqAttempt records from the database
     * @param wordAttempts All historical WordQuizAttempt records (if any)
     * @param pendingQuizzesCount Count of currently pending / overdue retention quizzes
     * @param isCognitiveDebtActive Whether the user is in frozen debt state
     * @param currentTimeMs Current timestamp in epoch milliseconds
     */
    fun calculateRetentionRate(
        mcqAttempts: List<UserMcqAttempt>,
        wordAttempts: List<WordQuizAttempt> = emptyList(),
        pendingQuizzesCount: Int = 0,
        isCognitiveDebtActive: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RetentionRateResult {
        // 1. Strict Attempted-Only Scope: Filter strictly where attemptDate > 0 and totalQuestions > 0
        val validMcqAttempts = mcqAttempts.filter { it.attemptDate > 0 && it.totalQuestions > 0 }
        val validWordAttempts = wordAttempts.filter { it.attemptDate > 0 && it.totalQuestions > 0 }

        val totalAttempted = validMcqAttempts.sumOf { it.totalQuestions } + validWordAttempts.sumOf { it.totalQuestions }
        val totalCorrect = validMcqAttempts.sumOf { it.correctAnswers } + validWordAttempts.sumOf { it.correctAnswers }

        // Null / Zero-State Graceful Handling:
        // If total lifetime attempts == 0, display "--%" in a neutral state, without throwing divide-by-zero exceptions
        if (totalAttempted == 0) {
            return RetentionRateResult(
                ratePercentage = null,
                displayString = "--%",
                dailyDrillAccuracy = null,
                chapterMasteryAccuracy = null,
                totalAttemptedQuestions = 0,
                totalCorrectAnswers = 0,
                decayPenaltyFactor = 0f,
                isOverdue = false
            )
        }

        // 2. Multi-Tier Weighting (Mastery Exams + Daily Drills):
        // Partition MCQ attempts into Chapter Mastery Challenges (>= 20 questions) vs Daily Retention Drills (< 20 questions)
        val masteryAttempts = validMcqAttempts.filter { it.totalQuestions >= MASTERY_QUESTION_THRESHOLD }
        val dailyMcqAttempts = validMcqAttempts.filter { it.totalQuestions < MASTERY_QUESTION_THRESHOLD }

        val dailyAttemptedQuestions = dailyMcqAttempts.sumOf { it.totalQuestions } + validWordAttempts.sumOf { it.totalQuestions }
        val dailyCorrectAnswers = dailyMcqAttempts.sumOf { it.correctAnswers } + validWordAttempts.sumOf { it.correctAnswers }

        val masteryAttemptedQuestions = masteryAttempts.sumOf { it.totalQuestions }
        val masteryCorrectAnswers = masteryAttempts.sumOf { it.correctAnswers }

        val dailyAccuracy: Float? = if (dailyAttemptedQuestions > 0) {
            (dailyCorrectAnswers.toFloat() / dailyAttemptedQuestions.toFloat()).coerceIn(0f, 1f)
        } else null

        val masteryAccuracy: Float? = if (masteryAttemptedQuestions > 0) {
            (masteryCorrectAnswers.toFloat() / masteryAttemptedQuestions.toFloat()).coerceIn(0f, 1f)
        } else null

        // Derive baseline accuracy based on available tiers
        val baseAccuracy: Float = when {
            dailyAccuracy != null && masteryAccuracy != null -> {
                (dailyAccuracy * DAILY_RETENTION_WEIGHT) + (masteryAccuracy * CHAPTER_MASTERY_WEIGHT)
            }
            dailyAccuracy != null -> dailyAccuracy
            masteryAccuracy != null -> masteryAccuracy
            else -> (totalCorrect.toFloat() / totalAttempted.toFloat()).coerceIn(0f, 1f)
        }

        // 3. Spaced Repetition Decay Penalty (Forgetting Curve Integration):
        // Find recency of latest quiz attempt across all attempts
        val latestAttemptTimestamp = max(
            validMcqAttempts.maxOfOrNull { it.attemptDate } ?: 0L,
            validWordAttempts.maxOfOrNull { it.attemptDate } ?: 0L
        )

        val daysSinceLastAttempt = if (latestAttemptTimestamp > 0L) {
            ((currentTimeMs - latestAttemptTimestamp) / MS_PER_DAY).toInt().coerceAtLeast(0)
        } else 0

        val isNeglectedByTime = daysSinceLastAttempt > 1
        val isNeglectedByPending = pendingQuizzesCount > 0 || isCognitiveDebtActive
        val isOverdue = isNeglectedByTime || isNeglectedByPending

        val decayPenaltyFactor = if (isOverdue) {
            val overdueDays = if (daysSinceLastAttempt > 1) (daysSinceLastAttempt - 1) else 0
            // Gentle logarithmic decay: simulates exponential forgetting curve over time
            val timeDecay = 0.05f * ln(1.0 + overdueDays.toDouble()).toFloat()
            val debtDecay = min(0.15f, pendingQuizzesCount * 0.02f) + (if (isCognitiveDebtActive) 0.05f else 0f)
            min(MAX_DECAY_PENALTY, timeDecay + debtDecay)
        } else {
            0f
        }

        // Passing pending drills immediately restores decay penalty to 0
        val effectiveRetention = (baseAccuracy * (1.0f - decayPenaltyFactor)).coerceIn(0f, 1f)
        val percentage = (effectiveRetention * 100f).roundToInt().coerceIn(0, 100)

        return RetentionRateResult(
            ratePercentage = percentage,
            displayString = "$percentage%",
            dailyDrillAccuracy = dailyAccuracy,
            chapterMasteryAccuracy = masteryAccuracy,
            totalAttemptedQuestions = totalAttempted,
            totalCorrectAnswers = totalCorrect,
            decayPenaltyFactor = decayPenaltyFactor,
            isOverdue = isOverdue
        )
    }
}
