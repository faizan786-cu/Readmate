package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val pdfFilePath: String? = null,
    val pdfFileName: String? = null,
    val pdfTotalPages: Int = 0,
    val pdfLastReadPage: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
