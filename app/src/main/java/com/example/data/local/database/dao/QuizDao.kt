package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.QuizStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizzes(quizzes: List<QuizEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Update
    suspend fun updateQuizzes(quizzes: List<QuizEntity>)

    @Query("SELECT * FROM quiz_bank ORDER BY id DESC")
    fun observeAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quiz_bank ORDER BY id DESC")
    suspend fun getAllQuizzes(): List<QuizEntity>

    @Query("SELECT * FROM quiz_bank WHERE id = :id")
    suspend fun getQuizById(id: Long): QuizEntity?

    @Query("SELECT * FROM quiz_bank WHERE status = 'PENDING' AND createdAtDate < :todayDate ORDER BY createdAtDate ASC, id ASC")
    suspend fun getUnservedPendingBeforeDate(todayDate: String): List<QuizEntity>

    @Query("SELECT * FROM quiz_bank WHERE status = 'PENDING' AND createdAtDate < :todayDate ORDER BY createdAtDate ASC, id ASC")
    fun observeUnservedPendingBeforeDate(todayDate: String): Flow<List<QuizEntity>>

    @Query("SELECT COUNT(*) FROM quiz_bank WHERE status = 'PENDING' AND createdAtDate < :todayDate")
    fun observeUnservedPendingBeforeDateCount(todayDate: String): Flow<Int>

    @Query("SELECT * FROM quiz_bank WHERE status = 'MISTAKE' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getMistakeBankQuizzes(limit: Int = 5): List<QuizEntity>

    @Query("SELECT * FROM quiz_bank WHERE status = 'MISTAKE'")
    fun observeMistakeBankQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT COUNT(*) FROM quiz_bank WHERE status = 'MISTAKE'")
    fun observeMistakeBankCount(): Flow<Int>

    @Query("SELECT * FROM quiz_bank WHERE lastServedDate = :date")
    suspend fun getQuizzesServedOnDate(date: String): List<QuizEntity>

    @Query("SELECT * FROM quiz_bank WHERE chapterId = :chapterId")
    suspend fun getQuizzesForChapter(chapterId: Long): List<QuizEntity>

    @Query("UPDATE quiz_bank SET status = 'MISTAKE', isFromMistakeBank = 1 WHERE id IN (:ids)")
    suspend fun markQuizzesAsMistake(ids: List<Long>)

    @Query("UPDATE quiz_bank SET status = :status, lastServedDate = :lastServedDate WHERE id = :id")
    suspend fun markQuizStatus(id: Long, status: String, lastServedDate: String? = null)

    @Query("UPDATE quiz_bank SET status = 'SERVED', lastServedDate = :date, timesServed = timesServed + 1 WHERE id IN (:ids)")
    suspend fun markQuizzesServed(ids: List<Long>, date: String)

    @Query("UPDATE quiz_bank SET status = 'MASTERED' WHERE id IN (:ids)")
    suspend fun markQuizzesMastered(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM quiz_bank WHERE status != 'MASTERED'")
    fun observePendingRetentionQuizzesCount(): Flow<Int>

    @Query("DELETE FROM quiz_bank WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun deleteOrphanedQuizzes()

    @Query("DELETE FROM quiz_bank WHERE chapterId = :chapterId")
    suspend fun deleteQuizzesForChapter(chapterId: Long)

    @Query("DELETE FROM quiz_bank WHERE bookId = :bookId")
    suspend fun deleteQuizzesForBook(bookId: Long)

    @Query("DELETE FROM quiz_bank")
    suspend fun deleteAllQuizzes()

    @Delete
    suspend fun deleteQuiz(quiz: QuizEntity)
}
