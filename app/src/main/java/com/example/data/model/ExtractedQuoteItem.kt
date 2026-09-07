package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExtractedQuoteItem(
    @Json(name = "englishQuote")
    val englishQuote: String = "",

    @Json(name = "romanUrduPunchline")
    val romanUrduPunchline: String = "",

    @Json(name = "themeTag")
    val themeTag: String = ""
)
