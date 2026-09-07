package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.McqQuestion
import kotlinx.coroutines.flow.Flow

@Dao
interface McqQuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<McqQuestion>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: McqQuestion): Long

    @Query("SELECT * FROM mcq_questions WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun observeQuestionsForChapter(chapterId: Long): Flow<List<McqQuestion>>

    @Query("SELECT * FROM mcq_questions WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeQuestionsForBook(bookId: Long): Flow<List<McqQuestion>>

    @Query("SELECT * FROM mcq_questions WHERE passageTurnId = :turnId ORDER BY id ASC")
    fun observeQuestionsForTurn(turnId: Long): Flow<List<McqQuestion>>

    @Query("SELECT * FROM mcq_questions ORDER BY createdAt DESC")
    fun observeAllQuestions(): Flow<List<McqQuestion>>

    @Query("SELECT * FROM mcq_questions WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    suspend fun getQuestionsForChapter(chapterId: Long): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getQuestionsForBook(bookId: Long): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE passageTurnId = :turnId ORDER BY id ASC")
    suspend fun getQuestionsForTurn(turnId: Long): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions ORDER BY createdAt DESC")
    suspend fun getAllQuestions(): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE createdAt >= :startOfDay AND createdAt <= :endOfDay ORDER BY createdAt DESC")
    suspend fun getUnattemptedTodayQuestions(startOfDay: Long, endOfDay: Long): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE isFlaggedForSpacedReview = 1 ORDER BY lastFailedTimestamp DESC, mistakeCount DESC LIMIT :limit")
    suspend fun getFlaggedMistakeQuestions(limit: Int): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE chapterId = :chapterId ORDER BY createdAt DESC LIMIT :maxLimit")
    suspend fun getChapterQuestionBank(chapterId: Long, maxLimit: Int = 50): List<McqQuestion>

    @Query("SELECT * FROM mcq_questions WHERE isFlaggedForSpacedReview = 1 ORDER BY lastFailedTimestamp DESC, mistakeCount DESC")
    fun observeFlaggedMistakeQuestions(): Flow<List<McqQuestion>>

    @Query("SELECT COUNT(*) FROM mcq_questions WHERE isFlaggedForSpacedReview = 1")
    fun observeFlaggedMistakeQuestionsCount(): Flow<Int>

    @androidx.room.Update
    suspend fun updateQuestion(question: McqQuestion)

    @androidx.room.Update
    suspend fun updateQuestions(questions: List<McqQuestion>)

    @Query("UPDATE mcq_questions SET isFlaggedForSpacedReview = :isFlagged, mistakeCount = mistakeCount + :incrementMistake, lastFailedTimestamp = :timestamp WHERE id = :questionId")
    suspend fun updateMistakeStatus(questionId: Long, isFlagged: Boolean, incrementMistake: Int, timestamp: Long)

    @Query("UPDATE mcq_questions SET isFlaggedForSpacedReview = 0 WHERE id = :questionId")
    suspend fun resolveMistake(questionId: Long)

    @Query("SELECT COUNT(*) FROM mcq_questions")
    fun observeTotalQuestionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mcq_questions WHERE createdAt >= :startOfDayTimestamp")
    fun observeDailyQuestionsCount(startOfDayTimestamp: Long): Flow<Int>

    @Delete
    suspend fun deleteQuestion(question: McqQuestion)

    @Query("DELETE FROM mcq_questions WHERE chapterId = :chapterId")
    suspend fun deleteQuestionsForChapter(chapterId: Long)

    @Query("DELETE FROM mcq_questions WHERE passageTurnId = :turnId")
    suspend fun deleteQuestionsForTurn(turnId: Long)
}
