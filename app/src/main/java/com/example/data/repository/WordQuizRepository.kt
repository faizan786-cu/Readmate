package com.example.data.repository

import com.example.data.local.database.dao.WordQuizAttemptDao
import com.example.data.local.database.entity.WordQuizAttempt
import kotlinx.coroutines.flow.Flow

class WordQuizRepository(
    private val wordQuizAttemptDao: WordQuizAttemptDao
) {
    val allAttempts: Flow<List<WordQuizAttempt>> = wordQuizAttemptDao.observeAllAttempts()
    val totalAttemptsCount: Flow<Int> = wordQuizAttemptDao.observeTotalAttemptsCount()
    val averageScore: Flow<Float?> = wordQuizAttemptDao.observeAverageScore()
    val totalCorrectAnswers: Flow<Int?> = wordQuizAttemptDao.observeTotalCorrectAnswers()
    val totalQuestionsAttempted: Flow<Int?> = wordQuizAttemptDao.observeTotalQuestionsAttempted()
    val totalXpEarned: Flow<Int?> = wordQuizAttemptDao.observeTotalXpEarned()

    suspend fun recordAttempt(attempt: WordQuizAttempt): Long {
        return wordQuizAttemptDao.insertAttempt(attempt)
    }

    suspend fun getAllAttempts(): List<WordQuizAttempt> {
        return wordQuizAttemptDao.getAllAttempts()
    }
}
