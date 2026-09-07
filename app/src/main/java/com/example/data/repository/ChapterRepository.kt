package com.example.data.repository

import com.example.data.local.database.dao.ChapterDao
import com.example.data.local.database.entity.Chapter
import kotlinx.coroutines.flow.Flow

class ChapterRepository(private val chapterDao: ChapterDao) {

    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>> =
        chapterDao.getChaptersForBook(bookId)

    suspend fun getChaptersForBookSync(bookId: Long): List<Chapter> =
        chapterDao.getChaptersForBookSync(bookId)

    suspend fun getLatestUpdatedChapter(): Chapter? =
        chapterDao.getLatestUpdatedChapter()

    fun observeChapter(id: Long): Flow<Chapter?> = chapterDao.observeChapterById(id)

    suspend fun getChapter(id: Long): Chapter? = chapterDao.getChapterById(id)

    suspend fun getNextChapterNumber(bookId: Long): Int {
        val maxNumber = chapterDao.getMaxChapterNumber(bookId) ?: 0
        return maxNumber + 1
    }

    suspend fun createChapter(bookId: Long, chapterNumber: Int, title: String): Long {
        val now = System.currentTimeMillis()
        val chapter = Chapter(
            bookId = bookId,
            chapterNumber = chapterNumber,
            title = title.trim(),
            createdAt = now,
            updatedAt = now
        )
        return chapterDao.insertChapter(chapter)
    }

    suspend fun insertChapters(chapters: List<Chapter>): List<Long> {
        return chapterDao.insertChapters(chapters)
    }

    suspend fun updateChapter(
        id: Long,
        bookId: Long,
        chapterNumber: Int,
        title: String,
        createdAt: Long
    ) {
        val chapter = Chapter(
            id = id,
            bookId = bookId,
            chapterNumber = chapterNumber,
            title = title.trim(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        chapterDao.updateChapter(chapter)
    }

    suspend fun updatePdfInfo(chapterId: Long, filePath: String?, fileName: String?, totalPages: Int, lastReadPage: Int) {
        chapterDao.updatePdfInfo(chapterId, filePath, fileName, totalPages, lastReadPage)
    }

    suspend fun updateLastReadPage(chapterId: Long, lastReadPage: Int) {
        chapterDao.updateLastReadPage(chapterId, lastReadPage)
    }

    suspend fun updateHighestReadPageAnchor(chapterId: Long, highestPage: Int) {
        chapterDao.updateHighestReadPageAnchor(chapterId, highestPage)
    }

    suspend fun updateCompletionStatus(chapterId: Long, isCompleted: Boolean) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else 0L
        chapterDao.updateCompletionStatus(
            chapterId = chapterId,
            isCompleted = isCompleted,
            completedAt = completedAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun observeCompletedChapters(): Flow<List<Chapter>> = chapterDao.observeCompletedChapters()

    suspend fun getCompletedChaptersSync(): List<Chapter> = chapterDao.getCompletedChaptersSync()

    suspend fun updateMasteryScore(chapterId: Long, score: Int, isMastered: Boolean, masteredAt: Long = System.currentTimeMillis()) {
        chapterDao.updateMasteryScore(
            chapterId = chapterId,
            score = score,
            isMastered = isMastered,
            masteredAt = masteredAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun observeAllChapters(): Flow<List<Chapter>> = chapterDao.observeAllChapters()

    suspend fun getAllChaptersSync(): List<Chapter> = chapterDao.getAllChaptersSync()

    suspend fun deleteChapterById(id: Long) {
        chapterDao.deleteChapterById(id)
    }

    suspend fun deleteChapter(chapter: Chapter) {
        chapterDao.deleteChapter(chapter)
    }

    suspend fun purgeJunkChapters(): Int {
        return chapterDao.purgeJunkChapters()
    }
}
