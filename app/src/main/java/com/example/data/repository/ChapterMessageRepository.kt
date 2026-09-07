package com.example.data.repository

import com.example.data.local.database.dao.ChapterMessageDao
import com.example.data.local.database.entity.ChapterMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChapterMessageRepository(
    private val chapterMessageDao: ChapterMessageDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val allMessageTimestamps: Flow<List<Long>> = chapterMessageDao.observeAllMessageTimestamps()
    val totalMessageCount: Flow<Int> = chapterMessageDao.observeTotalMessageCount()
    val latestMessage: Flow<ChapterMessage?> = chapterMessageDao.observeLatestMessage()

    fun observeMessagesForChapter(chapterId: Long): Flow<List<ChapterMessage>> {
        return chapterMessageDao.observeMessagesForChapter(chapterId)
    }

    suspend fun getLatestMessage(): ChapterMessage? = withContext(ioDispatcher) {
        chapterMessageDao.getLatestMessage()
    }

    suspend fun getMessagesForChapter(chapterId: Long): List<ChapterMessage> = withContext(ioDispatcher) {
        chapterMessageDao.getMessagesForChapter(chapterId)
    }

    suspend fun saveMessage(
        chapterId: Long,
        originalText: String,
        aiResponse: String
    ): Long = withContext(ioDispatcher) {
        val message = ChapterMessage(
            chapterId = chapterId,
            originalText = originalText.trim(),
            aiResponse = aiResponse.trim(),
            createdAt = System.currentTimeMillis()
        )
        chapterMessageDao.insertMessage(message)
    }

    suspend fun getMessageById(messageId: Long): ChapterMessage? = withContext(ioDispatcher) {
        chapterMessageDao.getMessageById(messageId)
    }

    suspend fun updateAiResponse(messageId: Long, aiResponse: String) = withContext(ioDispatcher) {
        chapterMessageDao.updateAiResponse(messageId, aiResponse.trim())
    }

    suspend fun deleteMessageById(messageId: Long) = withContext(ioDispatcher) {
        chapterMessageDao.deleteMessageById(messageId)
    }

    suspend fun deleteMessage(message: ChapterMessage) = withContext(ioDispatcher) {
        chapterMessageDao.deleteMessage(message)
    }

    suspend fun deleteMessagesForChapter(chapterId: Long) = withContext(ioDispatcher) {
        chapterMessageDao.deleteMessagesForChapter(chapterId)
    }
}
