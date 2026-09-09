package com.example.data.repository

import com.example.data.local.database.dao.BookDao
import com.example.data.local.database.dao.QuizDao
import com.example.data.local.database.entity.Book
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.remote.books.BookCoverFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class BookRepository(
    private val bookDao: BookDao,
    private val coverFetcher: BookCoverFetcher = BookCoverFetcher(),
    private val quizDao: QuizDao? = null
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val booksWithChapterCount: Flow<List<BookWithChapterCount>> =
        bookDao.getAllBooksWithChapterCount()

    val allBooks: Flow<List<Book>> =
        bookDao.getAllBooks()

    fun observeBook(id: Long): Flow<Book?> = bookDao.observeBookById(id)

    suspend fun getBook(id: Long): Book? = bookDao.getBookById(id)

    suspend fun getBookByCleanTitle(title: String): Book? = bookDao.getBookByCleanTitle(title.trim().lowercase())

    suspend fun createBook(
        title: String,
        author: String? = null,
        description: String? = null,
        coverImageUrl: String? = null,
        pdfFilePath: String? = null,
        pdfFileName: String? = null,
        pdfTotalPages: Int = 0,
        pdfLastReadPage: Int = 0
    ): Long {
        val now = System.currentTimeMillis()
        val book = Book(
            title = title.trim(),
            author = author?.trim()?.ifBlank { null },
            description = description?.trim()?.ifBlank { null },
            coverImageUrl = coverImageUrl?.trim()?.ifBlank { null },
            pdfFilePath = pdfFilePath,
            pdfFileName = pdfFileName,
            pdfTotalPages = pdfTotalPages,
            pdfLastReadPage = pdfLastReadPage,
            createdAt = now,
            updatedAt = now
        )
        val newBookId = bookDao.insertBook(book)
        if (newBookId > 0 && book.coverImageUrl.isNullOrBlank()) {
            triggerSilentCoverFetch(newBookId, book.title, book.author)
        }
        return newBookId
    }

    suspend fun updateBook(
        id: Long,
        title: String,
        author: String?,
        description: String?,
        coverImageUrl: String? = null,
        pdfFilePath: String? = null,
        pdfFileName: String? = null,
        pdfTotalPages: Int = 0,
        pdfLastReadPage: Int = 0,
        createdAt: Long
    ) {
        val existingBook = bookDao.getBookById(id)
        val finalCoverUrl = coverImageUrl ?: existingBook?.coverImageUrl
        val updatedBook = Book(
            id = id,
            title = title.trim(),
            author = author?.trim()?.ifBlank { null },
            description = description?.trim()?.ifBlank { null },
            coverImageUrl = finalCoverUrl,
            pdfFilePath = pdfFilePath,
            pdfFileName = pdfFileName,
            pdfTotalPages = pdfTotalPages,
            pdfLastReadPage = pdfLastReadPage,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        bookDao.updateBook(updatedBook)

        val titleChanged = existingBook?.title != updatedBook.title
        val authorChanged = existingBook?.author != updatedBook.author
        if (finalCoverUrl.isNullOrBlank() || titleChanged || authorChanged) {
            triggerSilentCoverFetch(id, updatedBook.title, updatedBook.author)
        }
    }

    fun triggerSilentCoverFetch(bookId: Long, title: String, author: String?) {
        repositoryScope.launch {
            try {
                val coverUrl = coverFetcher.fetchCoverUrl(title, author)
                if (!coverUrl.isNullOrBlank()) {
                    bookDao.updateCoverImageUrl(bookId, coverUrl)
                }
            } catch (_: Exception) {
                // Silently fail without interrupting user experience
            }
        }
    }

    suspend fun updateCoverImageUrl(bookId: Long, coverImageUrl: String?) {
        bookDao.updateCoverImageUrl(bookId, coverImageUrl)
    }

    suspend fun updatePdfInfo(bookId: Long, filePath: String?, fileName: String?, totalPages: Int, lastReadPage: Int) {
        bookDao.updatePdfInfo(bookId, filePath, fileName, totalPages, lastReadPage)
    }

    suspend fun updateLastReadPage(bookId: Long, lastReadPage: Int) {
        bookDao.updateLastReadPage(bookId, lastReadPage)
    }

    suspend fun updateHighestReadPageAnchor(bookId: Long, highestPage: Int) {
        bookDao.updateHighestReadPageAnchor(bookId, highestPage)
    }

    suspend fun deleteBookById(id: Long) {
        bookDao.deleteBookById(id)
        try {
            quizDao?.deleteQuizzesForBook(id)
        } catch (_: Exception) {}
    }

    suspend fun deleteBook(book: Book) {
        bookDao.deleteBook(book)
        try {
            quizDao?.deleteQuizzesForBook(book.id)
        } catch (_: Exception) {}
    }
}
