package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.UserMcqAttempt
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMcqAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: UserMcqAttempt): Long

    @Query("SELECT * FROM user_mcq_attempts ORDER BY attemptDate DESC")
    fun observeAllAttempts(): Flow<List<UserMcqAttempt>>

    @Query("SELECT * FROM user_mcq_attempts WHERE bookId = :bookId ORDER BY attemptDate DESC")
    fun observeAttemptsForBook(bookId: Long): Flow<List<UserMcqAttempt>>

    @Query("SELECT * FROM user_mcq_attempts WHERE chapterId = :chapterId ORDER BY attemptDate DESC")
    fun observeAttemptsForChapter(chapterId: Long): Flow<List<UserMcqAttempt>>

    @Query("SELECT * FROM user_mcq_attempts ORDER BY attemptDate DESC")
    suspend fun getAllAttempts(): List<UserMcqAttempt>

    @Query("SELECT COUNT(*) FROM user_mcq_attempts")
    fun observeTotalAttemptsCount(): Flow<Int>

    @Query("SELECT AVG(scorePercentage) FROM user_mcq_attempts")
    fun observeAverageScore(): Flow<Float?>

    @Query("SELECT SUM(correctAnswers) FROM user_mcq_attempts")
    fun observeTotalCorrectAnswers(): Flow<Int?>

    @Query("SELECT SUM(totalQuestions) FROM user_mcq_attempts")
    fun observeTotalQuestionsAttempted(): Flow<Int?>

    @Delete
    suspend fun deleteAttempt(attempt: UserMcqAttempt)
}
