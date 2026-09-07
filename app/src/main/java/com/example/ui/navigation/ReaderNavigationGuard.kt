package com.example.ui.navigation

import com.example.data.local.database.entity.DailyRetentionStateEntity

object ReaderNavigationGuard {
    /**
     * Checks whether the user is in Cognitive Debt / Stagnation and locked from entering the reader.
     * Lock applies only when streakStatus == "FROZEN_DEBT", quizzesCompleted is false,
     * AND both strict database conditions are satisfied: totalBooksCount > 0 AND pendingQuizzesCount > 0.
     */
    fun isReaderLocked(
        retentionState: DailyRetentionStateEntity?,
        totalBooksCount: Int = 1,
        pendingQuizzesCount: Int = 1
    ): Boolean {
        if (retentionState == null) return false
        if (totalBooksCount <= 0 || pendingQuizzesCount <= 0) return false
        return retentionState.streakStatus == "FROZEN_DEBT" && !retentionState.quizzesCompleted
    }
}
