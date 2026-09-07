package com.example.data.model

import com.example.data.local.database.entity.Chapter
import com.example.data.util.ProgressCalculator

/**
 * Represents the sequential unlock state of a chapter.
 */
data class ChapterUnlockStatus(
    val chapter: Chapter,
    val isLocked: Boolean,
    val previousChapterNumber: Int? = null
)

/**
 * Sequential Chapter Lockout Protocol: Gated Progression for Core Chapters Only.
 * Evaluates unlock status ensuring that:
 * 1. Front Matter and Back Matter sections are ALWAYS unlocked.
 * 2. Chapter 1 is ALWAYS unlocked.
 * 3. Chapter N (N > 1) is unlocked ONLY if Chapter N-1 is Mastered (50-MCQ quiz passed).
 */
object SequentialChapterLockManager {

    fun evaluateUnlockStatuses(chapters: List<Chapter>): Map<Long, ChapterUnlockStatus> {
        val result = mutableMapOf<Long, ChapterUnlockStatus>()

        // 1. Separate and sort core chapters sequentially
        val coreChapters = chapters.filter {
            ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) == ExtractedChapterSection.TYPE_CORE_CHAPTER
        }.sortedWith(
            compareBy<Chapter> { it.chapterNumber ?: Int.MAX_VALUE }
                .thenBy { it.startPage }
                .thenBy { it.id }
        )

        // 2. Front Matter & Back Matter sections are ALWAYS unlocked
        chapters.forEach { chapter ->
            val normType = ExtractedChapterSection.normalizeSectionType(chapter.sectionType, chapter.title)
            if (normType != ExtractedChapterSection.TYPE_CORE_CHAPTER) {
                result[chapter.id] = ChapterUnlockStatus(
                    chapter = chapter,
                    isLocked = false,
                    previousChapterNumber = null
                )
            }
        }

        // 3. Core Chapters sequential unlock evaluation
        for (i in coreChapters.indices) {
            val current = coreChapters[i]
            if (i == 0) {
                // Chapter 1 is always unlocked
                result[current.id] = ChapterUnlockStatus(
                    chapter = current,
                    isLocked = false,
                    previousChapterNumber = null
                )
            } else {
                val previous = coreChapters[i - 1]
                val prevProgress = ProgressCalculator.calculateChapterProgress(previous)
                val isPrevMastered = previous.isMastered || prevProgress.isMastered || (previous.masteryScore != null && previous.masteryScore >= 80)
                val isLocked = !isPrevMastered
                val prevChapterNum = previous.chapterNumber ?: i
                result[current.id] = ChapterUnlockStatus(
                    chapter = current,
                    isLocked = isLocked,
                    previousChapterNumber = prevChapterNum
                )
            }
        }

        return result
    }
}
