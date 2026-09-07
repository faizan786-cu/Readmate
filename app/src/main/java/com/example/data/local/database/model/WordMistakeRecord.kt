package com.example.data.local.database.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordMistakeRecord(
    val wordId: Long,
    val word: String,
    val originalSentence: String = "",
    val userSelectedOptionText: String,
    val correctOptionText: String,
    val romanUrduExplanation: String = "",
    val resolvedInRetry: Boolean = false
)
