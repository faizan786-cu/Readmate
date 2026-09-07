package com.example.data.repository

import com.example.data.local.database.dao.WisdomQuoteDao
import com.example.data.local.database.entity.WisdomQuote
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WisdomQuoteRepository(
    private val wisdomQuoteDao: WisdomQuoteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeAllQuotes(): Flow<List<WisdomQuote>> =
        wisdomQuoteDao.observeAllQuotes()

    fun observeFavoriteQuotes(): Flow<List<WisdomQuote>> =
        wisdomQuoteDao.observeFavoriteQuotes()

    fun observeQuotesForBook(bookId: Long): Flow<List<WisdomQuote>> =
        wisdomQuoteDao.observeQuotesForBook(bookId)

    fun observeQuotesForChapter(chapterId: Long): Flow<List<WisdomQuote>> =
        wisdomQuoteDao.observeQuotesForChapter(chapterId)

    fun observeQuotesForMessage(messageId: Long): Flow<List<WisdomQuote>> =
        wisdomQuoteDao.observeQuotesForMessage(messageId)

    fun observeTotalQuoteCount(): Flow<Int> =
        wisdomQuoteDao.observeTotalQuoteCount()

    suspend fun getQuotesForChapter(chapterId: Long): List<WisdomQuote> = withContext(ioDispatcher) {
        wisdomQuoteDao.getQuotesForChapter(chapterId)
    }

    suspend fun getQuotesForBook(bookId: Long): List<WisdomQuote> = withContext(ioDispatcher) {
        wisdomQuoteDao.getQuotesForBook(bookId)
    }

    suspend fun getQuoteById(id: Long): WisdomQuote? = withContext(ioDispatcher) {
        wisdomQuoteDao.getQuoteById(id)
    }

    suspend fun saveQuote(quote: WisdomQuote): Long = withContext(ioDispatcher) {
        wisdomQuoteDao.insertQuote(quote)
    }

    suspend fun saveQuotes(quotes: List<WisdomQuote>): List<Long> = withContext(ioDispatcher) {
        if (quotes.isEmpty()) emptyList() else wisdomQuoteDao.insertQuotes(quotes)
    }

    suspend fun updateQuote(quote: WisdomQuote) = withContext(ioDispatcher) {
        wisdomQuoteDao.updateQuote(quote)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(ioDispatcher) {
        wisdomQuoteDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun deleteQuote(quote: WisdomQuote) = withContext(ioDispatcher) {
        wisdomQuoteDao.deleteQuote(quote)
    }

    suspend fun deleteQuoteById(id: Long) = withContext(ioDispatcher) {
        wisdomQuoteDao.deleteQuoteById(id)
    }

    suspend fun deleteQuotesForChapter(chapterId: Long) = withContext(ioDispatcher) {
        wisdomQuoteDao.deleteQuotesForChapter(chapterId)
    }

    suspend fun deleteQuotesForBook(bookId: Long) = withContext(ioDispatcher) {
        wisdomQuoteDao.deleteQuotesForBook(bookId)
    }
}
