package com.example.data.repository

import com.example.data.local.database.dao.QuizDao
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.QuizStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class QuizRepository(
    private val quizDao: QuizDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getTodayDateString(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    // --- Observables ---

    fun observeAllQuizzes(): Flow<List<QuizEntity>> =
        quizDao.observeAllQuizzes()

    fun observeUnservedPendingBeforeDate(todayDate: String = getTodayDateString()): Flow<List<QuizEntity>> =
        quizDao.observeUnservedPendingBeforeDate(todayDate)

    fun observeUnservedPendingBeforeDateCount(todayDate: String = getTodayDateString()): Flow<Int> =
        quizDao.observeUnservedPendingBeforeDateCount(todayDate)

    fun observeMistakeBankQuizzes(): Flow<List<QuizEntity>> =
        quizDao.observeMistakeBankQuizzes()

    fun observeMistakeBankCount(): Flow<Int> =
        quizDao.observeMistakeBankCount()

    fun observePendingRetentionQuizzesCount(): Flow<Int> =
        quizDao.observePendingRetentionQuizzesCount()

    suspend fun deleteOrphanedQuizzes() = withContext(ioDispatcher) {
        quizDao.deleteOrphanedQuizzes()
    }

    // --- Operations ---

    suspend fun getAllQuizzes(): List<QuizEntity> = withContext(ioDispatcher) {
        quizDao.getAllQuizzes()
    }

    suspend fun getQuizById(id: Long): QuizEntity? = withContext(ioDispatcher) {
        quizDao.getQuizById(id)
    }

    suspend fun getQuizzesForChapter(chapterId: Long): List<QuizEntity> = withContext(ioDispatcher) {
        quizDao.getQuizzesForChapter(chapterId)
    }

    suspend fun saveQuiz(quiz: QuizEntity): Long = withContext(ioDispatcher) {
        quizDao.insertQuiz(quiz)
    }

    suspend fun saveQuizzes(quizzes: List<QuizEntity>): List<Long> = withContext(ioDispatcher) {
        if (quizzes.isEmpty()) emptyList() else quizDao.insertQuizzes(quizzes)
    }

    suspend fun updateQuiz(quiz: QuizEntity) = withContext(ioDispatcher) {
        quizDao.updateQuiz(quiz)
    }

    suspend fun updateQuizzes(quizzes: List<QuizEntity>) = withContext(ioDispatcher) {
        quizDao.updateQuizzes(quizzes)
    }

    suspend fun getQuizzesServedOnDate(date: String = getTodayDateString()): List<QuizEntity> = withContext(ioDispatcher) {
        quizDao.getQuizzesServedOnDate(date)
    }

    suspend fun getMistakeBankQuizzes(limit: Int = 5): List<QuizEntity> = withContext(ioDispatcher) {
        quizDao.getMistakeBankQuizzes(limit)
    }

    suspend fun markQuizzesAsMistake(ids: List<Long>) = withContext(ioDispatcher) {
        if (ids.isNotEmpty()) {
            quizDao.markQuizzesAsMistake(ids)
        }
    }

    suspend fun markQuizzesMastered(ids: List<Long>) = withContext(ioDispatcher) {
        if (ids.isNotEmpty()) {
            quizDao.markQuizzesMastered(ids)
        }
    }

    suspend fun markQuizStatus(id: Long, status: String, lastServedDate: String? = null) = withContext(ioDispatcher) {
        quizDao.markQuizStatus(id, status, lastServedDate)
    }

    /**
     * Day 1+ Daily Test Assembly (Quota Selection Algorithm):
     * 1. Fetches unserved pool: Items where status == "PENDING" and createdAtDate < Today.
     * 2. If unserved count <= 10: Serve all available unserved items.
     * 3. If unserved count > 10: Pick 10 random items from the unserved pool.
     * 4. If includeMistakes > 0: Injects up to `includeMistakes` random questions from Mistake Bank.
     * 5. Marks selected pending items as lastServedDate = Today, timesServed += 1, status = "SERVED".
     */
    suspend fun assembleDailyTest(
        todayDate: String = getTodayDateString(),
        maxNewQuotas: Int = 10,
        maxMistakesToInject: Int = 0
    ): List<QuizEntity> = withContext(ioDispatcher) {
        // Check if quizzes were already served today
        val alreadyServedToday = quizDao.getQuizzesServedOnDate(todayDate)
        if (alreadyServedToday.isNotEmpty()) {
            return@withContext alreadyServedToday
        }

        // Fetch unserved pool created strictly before today (Zero Same-Day Testing rule)
        val unservedPool = quizDao.getUnservedPendingBeforeDate(todayDate)
        val selectedNew = if (unservedPool.size <= maxNewQuotas) {
            unservedPool
        } else {
            unservedPool.shuffled().take(maxNewQuotas)
        }

        val mistakeItems = if (maxMistakesToInject > 0) {
            quizDao.getMistakeBankQuizzes(maxMistakesToInject)
        } else {
            emptyList()
        }

        val allSelected = (selectedNew + mistakeItems).distinctBy { it.id }

        if (allSelected.isNotEmpty()) {
            val selectedIds = allSelected.map { it.id }
            quizDao.markQuizzesServed(selectedIds, todayDate)
        }

        allSelected
    }

    suspend fun deleteQuizzesForChapter(chapterId: Long) = withContext(ioDispatcher) {
        quizDao.deleteQuizzesForChapter(chapterId)
    }

    suspend fun deleteQuizzesForBook(bookId: Long) = withContext(ioDispatcher) {
        quizDao.deleteQuizzesForBook(bookId)
    }

    suspend fun deleteAllQuizzes() = withContext(ioDispatcher) {
        quizDao.deleteAllQuizzes()
    }
}
