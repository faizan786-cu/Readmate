package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.Book
import com.example.data.local.database.model.BookWithChapterCount
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query(
        """
        SELECT books.*, COUNT(chapters.id) AS chapterCount 
        FROM books 
        LEFT JOIN chapters ON books.id = chapters.bookId 
        GROUP BY books.id 
        ORDER BY books.updatedAt DESC
        """
    )
    fun getAllBooksWithChapterCount(): Flow<List<BookWithChapterCount>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBookById(id: Long): Flow<Book?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE books SET pdfFilePath = :filePath, pdfFileName = :fileName, pdfTotalPages = :totalPages, pdfLastReadPage = :lastReadPage, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updatePdfInfo(bookId: Long, filePath: String?, fileName: String?, totalPages: Int, lastReadPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET pdfLastReadPage = :lastReadPage, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updateLastReadPage(bookId: Long, lastReadPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET pdfLastReadPage = CASE WHEN :highestPage > pdfLastReadPage THEN :highestPage ELSE pdfLastReadPage END, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updateHighestReadPageAnchor(bookId: Long, highestPage: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE books SET coverImageUrl = :coverImageUrl, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun updateCoverImageUrl(bookId: Long, coverImageUrl: String?, updatedAt: Long = System.currentTimeMillis())
}
