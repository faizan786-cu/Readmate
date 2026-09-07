package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "word_quiz_attempts",
    indices = [
        Index(value = ["attemptDate"]),
        Index(value = ["createdAt"])
    ]
)
data class WordQuizAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val attemptDate: Long = System.currentTimeMillis(),
    val totalQuestions: Int,
    val correctAnswers: Int,
    val recoveredCount: Int = 0,
    val xpEarned: Int = 0,
    val scorePercentage: Float,
    val wrongAnswersJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
