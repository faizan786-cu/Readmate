package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExtractedVocabulary(
    @Json(name = "word") val word: String,
    @Json(name = "meaning") val meaning: String,
    @Json(name = "originalSentence") val originalSentence: String,
    @Json(name = "explanation") val explanation: String
)
