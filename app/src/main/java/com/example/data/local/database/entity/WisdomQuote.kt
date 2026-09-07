package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "wisdom_quotes",
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
        Index(value = ["messageId"]),
        Index(value = ["isFavorite"]),
        Index(value = ["createdAt"])
    ]
)
data class WisdomQuote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val bookTitle: String,
    val author: String? = null,
    val chapterId: Long,
    val chapterNumber: Int = 1,
    val chapterTitle: String,
    val englishQuote: String,
    val romanUrduPunchline: String,
    val themeTag: String,
    val messageId: Long? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
