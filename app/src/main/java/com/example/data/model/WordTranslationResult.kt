package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordTranslationResult(
    @Json(name = "word") val word: String = "",
    @Json(name = "simpleMeaning") val simpleMeaning: String = "",
    @Json(name = "contextMeaning") val contextMeaning: String = "",
    @Json(name = "originalSentence") val originalSentence: String = "",
    @Json(name = "sentenceUrduExplanation") val sentenceUrduExplanation: String = "",
    @Json(name = "phraseOrIdiomExplanation") val phraseOrIdiomExplanation: String = "",
    @Json(name = "simpleExample") val simpleExample: String = "",
    @Json(name = "exampleUrduExplanation") val exampleUrduExplanation: String = ""
)
