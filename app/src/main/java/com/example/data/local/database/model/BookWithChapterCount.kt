package com.example.data.local.database.model

import androidx.room.Embedded
import com.example.data.local.database.entity.Book

data class BookWithChapterCount(
    @Embedded
    val book: Book,
    val chapterCount: Int
)
