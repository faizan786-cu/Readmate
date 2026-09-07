package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_vault",
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
        Index(value = ["chapterId"]),
        Index(value = ["bookId"]),
        Index(value = ["messageId"]),
        Index(value = ["normalizedWord"]),
        Index(value = ["isFlaggedForSpacedReview"]),
        Index(value = ["createdAt"])
    ]
)
data class WordVaultEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val chapterId: Long,
    val messageId: Long? = null,
    val word: String,
    val normalizedWord: String = word.trim().lowercase(),
    val meaning: String,
    val contextMeaning: String = "",
    val originalSentence: String,
    val explanation: String,
    val phraseOrIdiomExplanation: String = "",
    val simpleExample: String = "",
    val exampleMeaning: String = "",
    val mistakeCount: Int = 0,
    val isFlaggedForSpacedReview: Boolean = false,
    val lastFailedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
