package com.example.data.remote.books

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleBooksResponse(
    @Json(name = "items")
    val items: List<GoogleBookItem>? = null,
    @Json(name = "totalItems")
    val totalItems: Int = 0
)

@JsonClass(generateAdapter = true)
data class GoogleBookItem(
    @Json(name = "id")
    val id: String? = null,
    @Json(name = "volumeInfo")
    val volumeInfo: GoogleBookVolumeInfo? = null
)

@JsonClass(generateAdapter = true)
data class GoogleBookVolumeInfo(
    @Json(name = "title")
    val title: String? = null,
    @Json(name = "authors")
    val authors: List<String>? = null,
    @Json(name = "imageLinks")
    val imageLinks: GoogleBookImageLinks? = null
)

@JsonClass(generateAdapter = true)
data class GoogleBookImageLinks(
    @Json(name = "smallThumbnail")
    val smallThumbnail: String? = null,
    @Json(name = "thumbnail")
    val thumbnail: String? = null,
    @Json(name = "small")
    val small: String? = null,
    @Json(name = "medium")
    val medium: String? = null,
    @Json(name = "large")
    val large: String? = null,
    @Json(name = "extraLarge")
    val extraLarge: String? = null
)
