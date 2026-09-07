package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "mcq_questions",
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
        Index(value = ["passageTurnId"]),
        Index(value = ["isFlaggedForSpacedReview"]),
        Index(value = ["createdAt"])
    ]
)
data class McqQuestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val chapterId: Long,
    val passageTurnId: Long,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String, // "A", "B", "C", or "D"
    val explanation: String,
    val mistakeCount: Int = 0,
    val isFlaggedForSpacedReview: Boolean = false,
    val lastFailedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
