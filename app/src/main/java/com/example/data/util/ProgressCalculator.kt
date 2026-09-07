package com.example.data.util

import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.model.ExtractedChapterSection

data class ChapterProgressInfo(
    val chapterId: Long,
    val chapterNumber: Int,
    val totalPages: Int,
    val readPages: Int,
    val progressPercent: Int,
    val isMastered: Boolean,
    val pillLabel: String
)

data class BookProgressInfo(
    val bookId: Long,
    val totalCorePages: Int,
    val readCorePages: Int,
    val progressPercent: Int,
    val coreChaptersCount: Int,
    val isCompleted: Boolean,
    val statusLabel: String
)

/**
 * High-precision mathematical engine for calculating chapter and book progress.
 * Strictly calculates book progress across CORE_CHAPTER sections only, excluding FRONT_MATTER and BACK_MATTER.
 */
object ProgressCalculator {

    private val CHAPTER_PREFIX_REGEX = Regex(
        pattern = """^(?:chapter|part|section|ch\.?)\s+(?:\d+|[ivxlcdm]+|[a-z]+(?:[\s-][a-z]+)?)(?:\s*[:\-\—\–.]+\s*|\s+)""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Strips redundant chapter prefixes such as "Chapter One: ", "Chapter 1: ", "Chapter Two: ", etc.,
     * so only the direct chapter concept title renders cleanly beside the numeric badge.
     * E.g.: "Chapter One: The Self-Image: Your Key to Living Without Limits" -> "The Self-Image: Your Key to Living Without Limits"
     * E.g.: "Chapter Two: How to Awaken the Automatic Success Mechanism ..." -> "How to Awaken the Automatic Success Mechanism ..."
     */
    fun cleanChapterTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        val trimmed = rawTitle.trim()
        val cleaned = trimmed.replaceFirst(CHAPTER_PREFIX_REGEX, "").trim()
        return cleaned.ifBlank { trimmed }
    }

    /**
     * Calculates the accurate progress for an individual chapter.
     * Pages read are computed from pdfLastReadPage relative to startPage and clamped to chapter total pages.
     */
    fun calculateChapterProgress(chapter: Chapter): ChapterProgressInfo {
        val chapterTotalPages = when {
            chapter.startPage > 0 && chapter.endPage >= chapter.startPage -> {
                (chapter.endPage - chapter.startPage + 1).coerceAtLeast(1)
            }
            chapter.pdfTotalPages > 0 -> chapter.pdfTotalPages
            else -> 1
        }

        val isMastered = chapter.isMastered || (chapter.masteryScore != null && chapter.masteryScore >= 80)

        val rawReadPages = when {
            isMastered -> chapterTotalPages
            chapter.pdfLastReadPage > 0 -> {
                if (chapter.startPage > 0 && chapter.pdfLastReadPage >= chapter.startPage) {
                    (chapter.pdfLastReadPage - chapter.startPage + 1).coerceIn(0, chapterTotalPages)
                } else if (chapter.pdfLastReadPage <= chapterTotalPages) {
                    chapter.pdfLastReadPage.coerceIn(0, chapterTotalPages)
                } else {
                    0
                }
            }
            else -> 0
        }

        val clampedReadPages = rawReadPages.coerceIn(0, chapterTotalPages)

        val progressPercent = when {
            isMastered -> 100
            chapterTotalPages > 0 -> ((clampedReadPages.toFloat() / chapterTotalPages.toFloat()) * 100).toInt().coerceIn(0, 100)
            else -> 0
        }

        val chapNum = chapter.chapterNumber ?: 1
        val pillLabel = when {
            isMastered -> "Ch $chapNum • Mastered"
            progressPercent >= 100 -> "Ch $chapNum • 100% Explored"
            progressPercent > 0 -> "Ch $chapNum • $progressPercent% Explored"
            else -> "Ch $chapNum • Not Started"
        }

        return ChapterProgressInfo(
            chapterId = chapter.id,
            chapterNumber = chapNum,
            totalPages = chapterTotalPages,
            readPages = clampedReadPages,
            progressPercent = progressPercent,
            isMastered = isMastered,
            pillLabel = pillLabel
        )
    }

    /**
     * Calculates weighted core-chapter book progress.
     * STRICT SCOPE RULE: Exclude FRONT_MATTER and BACK_MATTER from both numerator and denominator.
     * Formula: Overall Book Progress % = (SUM(clampedReadPagesInCoreChapter) / SUM(coreChapterTotalPages)) * 100
     */
    fun calculateBookCoreProgress(chapters: List<Chapter>, book: Book? = null): BookProgressInfo {
        val coreChapters = chapters.filter {
            ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) == ExtractedChapterSection.TYPE_CORE_CHAPTER
        }

        val bookId = book?.id ?: chapters.firstOrNull()?.bookId ?: 0L

        if (coreChapters.isNotEmpty()) {
            var sumTotalPages = 0
            var sumReadPages = 0

            for (ch in coreChapters) {
                val chProgress = calculateChapterProgress(ch)
                sumTotalPages += chProgress.totalPages
                sumReadPages += chProgress.readPages
            }

            val progressPercent = if (sumTotalPages > 0) {
                ((sumReadPages.toFloat() / sumTotalPages.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }

            val isCompleted = progressPercent >= 100
            val statusLabel = when {
                isCompleted -> "Completed"
                progressPercent > 0 -> "In Progress"
                else -> "Not Started"
            }

            return BookProgressInfo(
                bookId = bookId,
                totalCorePages = sumTotalPages,
                readCorePages = sumReadPages,
                progressPercent = progressPercent,
                coreChaptersCount = coreChapters.size,
                isCompleted = isCompleted,
                statusLabel = statusLabel
            )
        }

        // Fallback if no sections are classified as core (e.g. single unparsed book / legacy)
        if (chapters.isNotEmpty()) {
            var sumTotalPages = 0
            var sumReadPages = 0

            for (ch in chapters) {
                val chProgress = calculateChapterProgress(ch)
                sumTotalPages += chProgress.totalPages
                sumReadPages += chProgress.readPages
            }

            val progressPercent = if (sumTotalPages > 0) {
                ((sumReadPages.toFloat() / sumTotalPages.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }

            val isCompleted = progressPercent >= 100
            val statusLabel = when {
                isCompleted -> "Completed"
                progressPercent > 0 -> "In Progress"
                else -> "Not Started"
            }

            return BookProgressInfo(
                bookId = bookId,
                totalCorePages = sumTotalPages,
                readCorePages = sumReadPages,
                progressPercent = progressPercent,
                coreChaptersCount = chapters.size,
                isCompleted = isCompleted,
                statusLabel = statusLabel
            )
        }

        // Fallback to book entity pages if available
        val bookTotal = book?.pdfTotalPages ?: 0
        val bookRead = (book?.pdfLastReadPage ?: 0).coerceIn(0, bookTotal)
        val progressPercent = if (bookTotal > 0) {
            ((bookRead.toFloat() / bookTotal.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return BookProgressInfo(
            bookId = bookId,
            totalCorePages = bookTotal,
            readCorePages = bookRead,
            progressPercent = progressPercent,
            coreChaptersCount = 0,
            isCompleted = progressPercent >= 100,
            statusLabel = when {
                progressPercent >= 100 -> "Completed"
                progressPercent > 0 -> "In Progress"
                else -> "Not Started"
            }
        )
    }
}
