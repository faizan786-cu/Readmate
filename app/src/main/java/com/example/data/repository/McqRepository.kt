package com.example.data.repository

import com.example.data.local.database.dao.McqQuestionDao
import com.example.data.local.database.dao.UserMcqAttemptDao
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.UserMcqAttempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class McqRepository(
    private val mcqQuestionDao: McqQuestionDao,
    private val userMcqAttemptDao: UserMcqAttemptDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    // --- MCQ Questions ---

    fun observeAllQuestions(): Flow<List<McqQuestion>> =
        mcqQuestionDao.observeAllQuestions()

    fun observeQuestionsForBook(bookId: Long): Flow<List<McqQuestion>> =
        mcqQuestionDao.observeQuestionsForBook(bookId)

    fun observeQuestionsForChapter(chapterId: Long): Flow<List<McqQuestion>> =
        mcqQuestionDao.observeQuestionsForChapter(chapterId)

    fun observeQuestionsForTurn(turnId: Long): Flow<List<McqQuestion>> =
        mcqQuestionDao.observeQuestionsForTurn(turnId)

    fun observeTotalQuestionsCount(): Flow<Int> =
        mcqQuestionDao.observeTotalQuestionsCount()

    fun observeDailyQuestionsCount(startOfDayTimestamp: Long): Flow<Int> =
        mcqQuestionDao.observeDailyQuestionsCount(startOfDayTimestamp)

    suspend fun getQuestionsForChapter(chapterId: Long): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getQuestionsForChapter(chapterId)
    }

    suspend fun getQuestionsForBook(bookId: Long): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getQuestionsForBook(bookId)
    }

    suspend fun getQuestionsForTurn(turnId: Long): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getQuestionsForTurn(turnId)
    }

    suspend fun getAllQuestions(): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getAllQuestions()
    }

    suspend fun getUnattemptedTodayQuestions(startOfDay: Long, endOfDay: Long): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getUnattemptedTodayQuestions(startOfDay, endOfDay)
    }

    suspend fun getFlaggedMistakeQuestions(limit: Int): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getFlaggedMistakeQuestions(limit)
    }

    suspend fun getChapterQuestionBank(chapterId: Long, maxLimit: Int = 50): List<McqQuestion> = withContext(ioDispatcher) {
        mcqQuestionDao.getChapterQuestionBank(chapterId, maxLimit)
    }

    fun observeFlaggedMistakeQuestions(): Flow<List<McqQuestion>> =
        mcqQuestionDao.observeFlaggedMistakeQuestions()

    fun observeFlaggedMistakeQuestionsCount(): Flow<Int> =
        mcqQuestionDao.observeFlaggedMistakeQuestionsCount()

    suspend fun updateQuestion(question: McqQuestion) = withContext(ioDispatcher) {
        mcqQuestionDao.updateQuestion(question)
    }

    suspend fun updateQuestions(questions: List<McqQuestion>) = withContext(ioDispatcher) {
        mcqQuestionDao.updateQuestions(questions)
    }

    suspend fun updateMistakeStatus(questionId: Long, isFlagged: Boolean, incrementMistake: Int, timestamp: Long) = withContext(ioDispatcher) {
        mcqQuestionDao.updateMistakeStatus(questionId, isFlagged, incrementMistake, timestamp)
    }

    suspend fun resolveMistake(questionId: Long) = withContext(ioDispatcher) {
        mcqQuestionDao.resolveMistake(questionId)
    }

    suspend fun saveQuestion(question: McqQuestion): Long = withContext(ioDispatcher) {
        mcqQuestionDao.insertQuestion(question)
    }

    suspend fun saveQuestions(questions: List<McqQuestion>): List<Long> = withContext(ioDispatcher) {
        if (questions.isEmpty()) emptyList() else mcqQuestionDao.insertQuestions(questions)
    }

    suspend fun deleteQuestion(question: McqQuestion) = withContext(ioDispatcher) {
        mcqQuestionDao.deleteQuestion(question)
    }

    suspend fun deleteQuestionsForChapter(chapterId: Long) = withContext(ioDispatcher) {
        mcqQuestionDao.deleteQuestionsForChapter(chapterId)
    }

    suspend fun deleteQuestionsForTurn(turnId: Long) = withContext(ioDispatcher) {
        mcqQuestionDao.deleteQuestionsForTurn(turnId)
    }

    // --- User MCQ Quiz Attempts ---

    fun observeAllAttempts(): Flow<List<UserMcqAttempt>> =
        userMcqAttemptDao.observeAllAttempts()

    fun observeAttemptsForBook(bookId: Long): Flow<List<UserMcqAttempt>> =
        userMcqAttemptDao.observeAttemptsForBook(bookId)

    fun observeAttemptsForChapter(chapterId: Long): Flow<List<UserMcqAttempt>> =
        userMcqAttemptDao.observeAttemptsForChapter(chapterId)

    fun observeTotalAttemptsCount(): Flow<Int> =
        userMcqAttemptDao.observeTotalAttemptsCount()

    fun observeAverageScore(): Flow<Float?> =
        userMcqAttemptDao.observeAverageScore()

    fun observeTotalCorrectAnswers(): Flow<Int?> =
        userMcqAttemptDao.observeTotalCorrectAnswers()

    fun observeTotalQuestionsAttempted(): Flow<Int?> =
        userMcqAttemptDao.observeTotalQuestionsAttempted()

    suspend fun getAllAttempts(): List<UserMcqAttempt> = withContext(ioDispatcher) {
        userMcqAttemptDao.getAllAttempts()
    }

    suspend fun recordAttempt(attempt: UserMcqAttempt): Long = withContext(ioDispatcher) {
        userMcqAttemptDao.insertAttempt(attempt)
    }

    suspend fun deleteAttempt(attempt: UserMcqAttempt) = withContext(ioDispatcher) {
        userMcqAttemptDao.deleteAttempt(attempt)
    }
}
