package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.ChapterMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterMessageDao {

    @Query("SELECT * FROM chapter_messages WHERE chapterId = :chapterId ORDER BY createdAt ASC, id ASC")
    fun observeMessagesForChapter(chapterId: Long): Flow<List<ChapterMessage>>

    @Query("SELECT createdAt FROM chapter_messages ORDER BY createdAt DESC")
    fun observeAllMessageTimestamps(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM chapter_messages")
    fun observeTotalMessageCount(): Flow<Int>

    @Query("SELECT * FROM chapter_messages ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestMessage(): Flow<ChapterMessage?>

    @Query("SELECT * FROM chapter_messages ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMessage(): ChapterMessage?

    @Query("SELECT * FROM chapter_messages WHERE chapterId = :chapterId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesForChapter(chapterId: Long): List<ChapterMessage>

    @Query("SELECT * FROM chapter_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChapterMessage?

    @Query("UPDATE chapter_messages SET aiResponse = :aiResponse WHERE id = :messageId")
    suspend fun updateAiResponse(messageId: Long, aiResponse: String)

    @Query("DELETE FROM chapter_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChapterMessage): Long

    @Delete
    suspend fun deleteMessage(message: ChapterMessage)

    @Query("DELETE FROM chapter_messages WHERE chapterId = :chapterId")
    suspend fun deleteMessagesForChapter(chapterId: Long)
}
