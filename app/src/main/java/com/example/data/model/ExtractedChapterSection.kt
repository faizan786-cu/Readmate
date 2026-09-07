package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Unified payload for book identity and AI-extracted structural sections.
 */
@JsonClass(generateAdapter = true)
data class ExtractedBookPayload(
    @Json(name = "bookTitle") val bookTitle: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "sections") val sections: List<ExtractedChapterSection> = emptyList()
)

/**
 * Standardized schema for AI-extracted PDF chapter sections.
 * Supports Front Matter, Core Chapters, and Back Matter categorization.
 */
@JsonClass(generateAdapter = true)
data class ExtractedChapterSection(
    @Json(name = "title") val title: String,
    @Json(name = "sectionType") val sectionType: String = "CORE_CHAPTER",
    @Json(name = "chapterNumber") val chapterNumber: Int? = null,
    @Json(name = "startPage") val startPage: Int = 1,
    @Json(name = "endPage") val endPage: Int = 1,
    @Json(name = "orderIndex") val orderIndex: Int = 1
) {
    companion object {
        const val TYPE_FRONT_MATTER = "FRONT_MATTER"
        const val TYPE_CORE_CHAPTER = "CORE_CHAPTER"
        const val TYPE_BACK_MATTER = "BACK_MATTER"

        private val BLACKLISTED_TITLES = setOf(
            "title page", "title", "cover", "front cover", "back cover",
            "half title", "half-title", "copyright", "copyright page",
            "publisher info", "publisher's note", "publisher", "publication data",
            "table of contents", "contents", "toc", "table of content",
            "index", "subject index", "name index", "general index",
            "blank page", "blank pages", "blank"
        )

        fun isBlacklisted(title: String): Boolean {
            val lower = title.trim().lowercase()
            if (lower.isEmpty()) return true
            if (lower in BLACKLISTED_TITLES) return true
            return lower.startsWith("table of contents") ||
                    lower.startsWith("contents") ||
                    lower.startsWith("copyright") ||
                    lower.startsWith("title page") ||
                    lower.startsWith("half title") ||
                    lower.startsWith("cover") ||
                    lower.startsWith("blank page") ||
                    (lower.startsWith("index") && !lower.contains("chapter"))
        }

        fun normalizeSectionType(type: String?, title: String = ""): String {
            val upperType = type?.trim()?.uppercase() ?: ""
            if (upperType in listOf(TYPE_FRONT_MATTER, TYPE_CORE_CHAPTER, TYPE_BACK_MATTER)) {
                return upperType
            }
            val lowerTitle = title.lowercase()
            return when {
                lowerTitle.contains("preface") ||
                lowerTitle.contains("introduction") ||
                lowerTitle.contains("foreword") ||
                lowerTitle.contains("dedication") ||
                lowerTitle.contains("acknowledgment") ||
                lowerTitle.contains("prologue") ||
                lowerTitle.contains("author's note") ||
                lowerTitle.contains("authors note") ||
                lowerTitle.contains("note to the reader") -> TYPE_FRONT_MATTER

                lowerTitle.contains("epilogue") ||
                lowerTitle.contains("conclusion") ||
                lowerTitle.contains("afterword") ||
                lowerTitle.contains("about the author") ||
                lowerTitle.contains("notes") ||
                lowerTitle.contains("appendix") ||
                lowerTitle.contains("glossary") -> TYPE_BACK_MATTER

                else -> TYPE_CORE_CHAPTER
            }
        }
    }
}
