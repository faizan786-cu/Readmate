package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.WordQuizAttempt
import kotlinx.coroutines.flow.Flow

@Dao
interface WordQuizAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: WordQuizAttempt): Long

    @Query("SELECT * FROM word_quiz_attempts ORDER BY attemptDate DESC")
    fun observeAllAttempts(): Flow<List<WordQuizAttempt>>

    @Query("SELECT * FROM word_quiz_attempts ORDER BY attemptDate DESC")
    suspend fun getAllAttempts(): List<WordQuizAttempt>

    @Query("SELECT COUNT(*) FROM word_quiz_attempts")
    fun observeTotalAttemptsCount(): Flow<Int>

    @Query("SELECT AVG(scorePercentage) FROM word_quiz_attempts")
    fun observeAverageScore(): Flow<Float?>

    @Query("SELECT SUM(correctAnswers) FROM word_quiz_attempts")
    fun observeTotalCorrectAnswers(): Flow<Int?>

    @Query("SELECT SUM(totalQuestions) FROM word_quiz_attempts")
    fun observeTotalQuestionsAttempted(): Flow<Int?>

    @Query("SELECT SUM(xpEarned) FROM word_quiz_attempts")
    fun observeTotalXpEarned(): Flow<Int?>

    @Delete
    suspend fun deleteAttempt(attempt: WordQuizAttempt)
}
