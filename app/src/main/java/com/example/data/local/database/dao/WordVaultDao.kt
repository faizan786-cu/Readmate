package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.WordVaultEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WordVaultDao {

    @Query("SELECT * FROM word_vault ORDER BY createdAt DESC, id DESC")
    fun observeAllWords(): Flow<List<WordVaultEntry>>

    @Query("SELECT * FROM word_vault WHERE bookId = :bookId ORDER BY createdAt DESC, id DESC")
    fun observeWordsForBook(bookId: Long): Flow<List<WordVaultEntry>>

    @Query("SELECT * FROM word_vault WHERE chapterId = :chapterId ORDER BY createdAt DESC, id DESC")
    fun observeWordsForChapter(chapterId: Long): Flow<List<WordVaultEntry>>

    @Query("SELECT * FROM word_vault WHERE messageId = :messageId ORDER BY id ASC")
    fun observeWordsForMessage(messageId: Long): Flow<List<WordVaultEntry>>

    @Query("SELECT * FROM word_vault WHERE messageId = :messageId AND normalizedWord = :normalizedWord LIMIT 1")
    suspend fun getEntryByWordInMessage(messageId: Long, normalizedWord: String): WordVaultEntry?

    @Query("SELECT * FROM word_vault WHERE chapterId = :chapterId AND normalizedWord = :normalizedWord LIMIT 1")
    suspend fun getEntryByWordInChapter(chapterId: Long, normalizedWord: String): WordVaultEntry?

    @Query("SELECT * FROM word_vault WHERE chapterId = :chapterId AND normalizedWord = :normalizedWord AND originalSentence = :originalSentence LIMIT 1")
    suspend fun getEntryByWordAndSentenceInChapter(chapterId: Long, normalizedWord: String, originalSentence: String): WordVaultEntry?

    @Query("SELECT * FROM word_vault WHERE bookId = :bookId AND normalizedWord = :normalizedWord LIMIT 1")
    suspend fun getEntryByWordInBook(bookId: Long, normalizedWord: String): WordVaultEntry?

    @Query("SELECT * FROM word_vault WHERE normalizedWord = :normalizedWord")
    suspend fun getEntriesByNormalizedWord(normalizedWord: String): List<WordVaultEntry>

    @Query("SELECT * FROM word_vault WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): WordVaultEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WordVaultEntry): Long

    @Update
    suspend fun updateEntry(entry: WordVaultEntry)

    @Delete
    suspend fun deleteEntry(entry: WordVaultEntry)

    @Query("DELETE FROM word_vault WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM word_vault WHERE chapterId = :chapterId")
    suspend fun deleteWordsForChapter(chapterId: Long)

    @Query("SELECT COUNT(*) FROM word_vault")
    fun observeTotalWordCount(): Flow<Int>

    @Query("SELECT * FROM word_vault WHERE isFlaggedForSpacedReview = 1 ORDER BY lastFailedTimestamp DESC, mistakeCount DESC LIMIT :limit")
    suspend fun getFlaggedMistakeWords(limit: Int): List<WordVaultEntry>

    @Query("SELECT * FROM word_vault WHERE isFlaggedForSpacedReview = 1 ORDER BY lastFailedTimestamp DESC, mistakeCount DESC")
    fun observeFlaggedMistakeWords(): Flow<List<WordVaultEntry>>

    @Query("SELECT COUNT(*) FROM word_vault WHERE isFlaggedForSpacedReview = 1")
    fun observeFlaggedMistakeWordsCount(): Flow<Int>

    @Query("UPDATE word_vault SET isFlaggedForSpacedReview = :isFlagged, mistakeCount = mistakeCount + :incrementMistake, lastFailedTimestamp = :timestamp WHERE id = :wordId")
    suspend fun updateWordMistakeStatus(wordId: Long, isFlagged: Boolean, incrementMistake: Int, timestamp: Long)

    @Query("UPDATE word_vault SET isFlaggedForSpacedReview = 0 WHERE id = :wordId")
    suspend fun resolveWordMistake(wordId: Long)
}
