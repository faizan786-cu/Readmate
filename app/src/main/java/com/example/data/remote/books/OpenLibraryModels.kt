package com.example.data.remote.books

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenLibrarySearchResponse(
    @Json(name = "numFound")
    val numFound: Int = 0,
    @Json(name = "docs")
    val docs: List<OpenLibraryDoc>? = null
)

@JsonClass(generateAdapter = true)
data class OpenLibraryDoc(
    @Json(name = "key")
    val key: String? = null,
    @Json(name = "title")
    val title: String? = null,
    @Json(name = "author_name")
    val authorName: List<String>? = null,
    @Json(name = "cover_i")
    val coverI: Long? = null,
    @Json(name = "cover_edition_key")
    val coverEditionKey: String? = null
)
