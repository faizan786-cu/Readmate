package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeneratedMcqItem(
    @Json(name = "questionText")
    val questionText: String = "",

    @Json(name = "optionA")
    val optionA: String = "",

    @Json(name = "optionB")
    val optionB: String = "",

    @Json(name = "optionC")
    val optionC: String = "",

    @Json(name = "optionD")
    val optionD: String = "",

    @Json(name = "correctOption")
    val correctOption: String = "A", // "A", "B", "C", or "D"

    @Json(name = "explanation")
    val explanation: String = ""
)
