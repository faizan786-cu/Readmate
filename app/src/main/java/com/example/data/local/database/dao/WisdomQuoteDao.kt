package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.WisdomQuote
import kotlinx.coroutines.flow.Flow

@Dao
interface WisdomQuoteDao {

    @Query("SELECT * FROM wisdom_quotes ORDER BY createdAt DESC, id DESC")
    fun observeAllQuotes(): Flow<List<WisdomQuote>>

    @Query("SELECT * FROM wisdom_quotes WHERE isFavorite = 1 ORDER BY createdAt DESC, id DESC")
    fun observeFavoriteQuotes(): Flow<List<WisdomQuote>>

    @Query("SELECT * FROM wisdom_quotes WHERE bookId = :bookId ORDER BY createdAt DESC, id DESC")
    fun observeQuotesForBook(bookId: Long): Flow<List<WisdomQuote>>

    @Query("SELECT * FROM wisdom_quotes WHERE chapterId = :chapterId ORDER BY createdAt DESC, id DESC")
    fun observeQuotesForChapter(chapterId: Long): Flow<List<WisdomQuote>>

    @Query("SELECT * FROM wisdom_quotes WHERE messageId = :messageId ORDER BY id ASC")
    fun observeQuotesForMessage(messageId: Long): Flow<List<WisdomQuote>>

    @Query("SELECT * FROM wisdom_quotes WHERE chapterId = :chapterId ORDER BY createdAt DESC, id DESC")
    suspend fun getQuotesForChapter(chapterId: Long): List<WisdomQuote>

    @Query("SELECT * FROM wisdom_quotes WHERE bookId = :bookId ORDER BY createdAt DESC, id DESC")
    suspend fun getQuotesForBook(bookId: Long): List<WisdomQuote>

    @Query("SELECT * FROM wisdom_quotes WHERE id = :id LIMIT 1")
    suspend fun getQuoteById(id: Long): WisdomQuote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: WisdomQuote): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<WisdomQuote>): List<Long>

    @Update
    suspend fun updateQuote(quote: WisdomQuote)

    @Query("UPDATE wisdom_quotes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteQuote(quote: WisdomQuote)

    @Query("DELETE FROM wisdom_quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: Long)

    @Query("DELETE FROM wisdom_quotes WHERE chapterId = :chapterId")
    suspend fun deleteQuotesForChapter(chapterId: Long)

    @Query("DELETE FROM wisdom_quotes WHERE bookId = :bookId")
    suspend fun deleteQuotesForBook(bookId: Long)

    @Query("SELECT COUNT(*) FROM wisdom_quotes")
    fun observeTotalQuoteCount(): Flow<Int>
}
