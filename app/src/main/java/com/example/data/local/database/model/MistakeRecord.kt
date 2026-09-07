package com.example.data.local.database.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MistakeRecord(
    val questionId: Long,
    val questionText: String,
    val userSelectedOptionText: String,
    val correctOptionText: String,
    val explanation: String,
    val resolvedInRetry: Boolean = false
)
