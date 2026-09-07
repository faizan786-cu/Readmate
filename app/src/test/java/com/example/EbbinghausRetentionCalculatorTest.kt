package com.example

import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.entity.WordQuizAttempt
import com.example.data.util.EbbinghausRetentionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EbbinghausRetentionCalculatorTest {

    @Test
    fun testZeroLifetimeAttempts_returnsNeutralNullState() {
        val result = EbbinghausRetentionCalculator.calculateRetentionRate(
            mcqAttempts = emptyList(),
            wordAttempts = emptyList(),
            pendingQuizzesCount = 0,
            isCognitiveDebtActive = false
        )
        assertNull(result.ratePercentage)
        assertEquals("--%", result.displayString)
        assertEquals(0, result.totalAttemptedQuestions)
        assertEquals(0, result.totalCorrectAnswers)
    }

    @Test
    fun testStrictAttemptedOnlyScope_ignoresUnattemptedPool() {
        val now = System.currentTimeMillis()
        // User attempted 10 questions in a daily drill and got 9 right (90%)
        // The fact that the question bank might have 200 questions does not affect the calculation
        val attempts = listOf(
            UserMcqAttempt(
                id = 1L,
                attemptDate = now,
                totalQuestions = 10,
                correctAnswers = 9,
                scorePercentage = 90f
            )
        )
        val result = EbbinghausRetentionCalculator.calculateRetentionRate(
            mcqAttempts = attempts,
            currentTimeMs = now
        )
        assertEquals(90, result.ratePercentage)
        assertEquals("90%", result.displayString)
        assertEquals(10, result.totalAttemptedQuestions)
        assertEquals(9, result.totalCorrectAnswers)
    }

    @Test
    fun testMultiTierWeighting_dailySixty_masteryForty() {
        val now = System.currentTimeMillis()
        // Daily drill: 10 questions, 8 correct = 80%
        val dailyAttempt = UserMcqAttempt(
            id = 1L,
            attemptDate = now,
            totalQuestions = 10,
            correctAnswers = 8,
            scorePercentage = 80f
        )
        // Chapter mastery: 50 questions, 45 correct = 90%
        val masteryAttempt = UserMcqAttempt(
            id = 2L,
            attemptDate = now,
            totalQuestions = 50,
            correctAnswers = 45,
            scorePercentage = 90f
        )
        // Expected weighted: 80% * 0.60 + 90% * 0.40 = 48% + 36% = 84%
        val result = EbbinghausRetentionCalculator.calculateRetentionRate(
            mcqAttempts = listOf(dailyAttempt, masteryAttempt),
            currentTimeMs = now
        )
        assertEquals(84, result.ratePercentage)
        assertEquals("84%", result.displayString)
    }

    @Test
    fun testSpacedRepetitionDecayPenalty_appliesLogarithmicDecayWhenNeglected() {
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - (3 * 86_400_000L) // 3 days ago = 2 days overdue

        val attempt = UserMcqAttempt(
            id = 1L,
            attemptDate = threeDaysAgo,
            totalQuestions = 10,
            correctAnswers = 10,
            scorePercentage = 100f
        )

        // When neglected with pending quizzes
        val overdueResult = EbbinghausRetentionCalculator.calculateRetentionRate(
            mcqAttempts = listOf(attempt),
            pendingQuizzesCount = 3,
            currentTimeMs = now
        )
        assertTrue(overdueResult.isOverdue)
        assertTrue(overdueResult.decayPenaltyFactor > 0f)
        assertTrue(overdueResult.ratePercentage!! < 100)

        // When user completes a drill today (passing pending drills)
        val todayAttempt = UserMcqAttempt(
            id = 2L,
            attemptDate = now,
            totalQuestions = 10,
            correctAnswers = 10,
            scorePercentage = 100f
        )
        val restoredResult = EbbinghausRetentionCalculator.calculateRetentionRate(
            mcqAttempts = listOf(attempt, todayAttempt),
            pendingQuizzesCount = 0,
            currentTimeMs = now
        )
        assertEquals(0f, restoredResult.decayPenaltyFactor, 0.001f)
        assertEquals(100, restoredResult.ratePercentage)
        assertEquals("100%", restoredResult.displayString)
    }
}
