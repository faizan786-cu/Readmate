package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

object QuizStatus {
    const val PENDING = "PENDING"
    const val SERVED = "SERVED"
    const val MASTERED = "MASTERED"
    const val MISTAKE = "MISTAKE"
}

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "quiz_bank",
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
        Index(value = ["status"]),
        Index(value = ["createdAtDate"]),
        Index(value = ["lastServedDate"])
    ]
)
data class QuizEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val chapterId: Long,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val sourceSnippet: String,
    val createdAtDate: String, // Formatted YYYY-MM-DD
    val lastServedDate: String? = null,
    val timesServed: Int = 0,
    val isFromMistakeBank: Boolean = false,
    val status: String = QuizStatus.PENDING
)
