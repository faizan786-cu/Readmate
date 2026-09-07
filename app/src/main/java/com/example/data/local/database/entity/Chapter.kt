package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"])
    ]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val chapterNumber: Int? = null,
    val sectionType: String = "CORE_CHAPTER",
    val startPage: Int = 1,
    val endPage: Int = 1,
    val title: String,
    val pdfFilePath: String? = null,
    val pdfFileName: String? = null,
    val pdfTotalPages: Int = 0,
    val pdfLastReadPage: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: Long = 0L,
    val masteryScore: Int? = null,
    val isMastered: Boolean = false,
    val masteredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

