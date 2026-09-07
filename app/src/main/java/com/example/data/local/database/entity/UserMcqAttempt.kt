package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "user_mcq_attempts",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["chapterId"]),
        Index(value = ["attemptDate"]),
        Index(value = ["createdAt"])
    ]
)
data class UserMcqAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long? = null,
    val chapterId: Long? = null,
    val attemptDate: Long = System.currentTimeMillis(),
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Float,
    val wrongAnswersJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
