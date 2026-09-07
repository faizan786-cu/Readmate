package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startPage ASC, chapterNumber ASC, id ASC")
    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startPage ASC, chapterNumber ASC, id ASC")
    suspend fun getChaptersForBookSync(bookId: Long): List<Chapter>

    @Query("SELECT * FROM chapters ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestUpdatedChapter(): Chapter?

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): Chapter?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun observeChapterById(id: Long): Flow<Chapter?>

    @Query("SELECT MAX(chapterNumber) FROM chapters WHERE bookId = :bookId")
    suspend fun getMaxChapterNumber(bookId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapter(chapter: Chapter): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>): List<Long>

    @Update
    suspend fun updateChapter(chapter: Chapter)

    @Delete
    suspend fun deleteChapter(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterById(id: Long)

    @Query("UPDATE chapters SET pdfFilePath = :filePath, pdfFileName = :fileName, pdfTotalPages = :totalPages, pdfLastReadPage = :lastReadPage, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updatePdfInfo(chapterId: Long, filePath: String?, fileName: String?, totalPages: Int, lastReadPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET pdfLastReadPage = :lastReadPage, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateLastReadPage(chapterId: Long, lastReadPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET pdfLastReadPage = CASE WHEN :highestPage > pdfLastReadPage THEN :highestPage ELSE pdfLastReadPage END, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateHighestReadPageAnchor(chapterId: Long, highestPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET isCompleted = :isCompleted, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateCompletionStatus(chapterId: Long, isCompleted: Boolean, completedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET masteryScore = :score, isMastered = :isMastered, masteredAt = :masteredAt, isCompleted = 1, completedAt = CASE WHEN completedAt > 0 THEN completedAt ELSE :masteredAt END, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateMasteryScore(chapterId: Long, score: Int, isMastered: Boolean, masteredAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chapters WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun observeCompletedChapters(): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE isCompleted = 1 ORDER BY completedAt DESC")
    suspend fun getCompletedChaptersSync(): List<Chapter>

    @Query("SELECT * FROM chapters ORDER BY bookId ASC, chapterNumber ASC")
    fun observeAllChapters(): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters ORDER BY bookId ASC, chapterNumber ASC")
    suspend fun getAllChaptersSync(): List<Chapter>

    @Query("DELETE FROM chapters WHERE LOWER(title) IN ('title page', 'title', 'cover', 'front cover', 'back cover', 'half title', 'half-title', 'copyright', 'copyright page', 'publisher info', 'publisher', 'table of contents', 'contents', 'toc', 'index', 'blank page', 'blank pages', 'blank') OR LOWER(title) LIKE 'table of contents%' OR LOWER(title) LIKE 'copyright%'")
    suspend fun purgeJunkChapters(): Int
}
