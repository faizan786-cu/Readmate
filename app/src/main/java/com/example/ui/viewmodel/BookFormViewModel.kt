package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.ReadMateApplication
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.pdf.PdfStorageManager
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.worker.ExtractChaptersWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository? = null,
    private val secureApiKeyStorage: SecureApiKeyStorage? = null,
    application: Application? = null
) : AndroidViewModel(application ?: (ReadMateApplication())) {

    val bookId: Long? = savedStateHandle.get<Long>("bookId")?.takeIf { it > 0 }

    var title by mutableStateOf("")
        private set
    var author by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set

    var pdfFilePath by mutableStateOf<String?>(null)
        private set
    var pdfFileName by mutableStateOf<String?>(null)
        private set
    var pdfTotalPages by mutableIntStateOf(0)
        private set
    var isImportingPdf by mutableStateOf(false)
        private set
    var pdfErrorMessage by mutableStateOf<String?>(null)
        private set

    var titleError by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(bookId != null)
        private set

    var coverImageUrl by mutableStateOf<String?>(null)
        private set
    private var pdfLastReadPage: Int = 0
    private var createdAt: Long = System.currentTimeMillis()

    init {
        bookId?.let { id ->
            viewModelScope.launch {
                val book = bookRepository.getBook(id)
                if (book != null) {
                    title = book.title
                    author = book.author ?: ""
                    description = book.description ?: ""
                    coverImageUrl = book.coverImageUrl
                    pdfFilePath = book.pdfFilePath
                    pdfFileName = book.pdfFileName
                    pdfTotalPages = book.pdfTotalPages
                    pdfLastReadPage = book.pdfLastReadPage
                    createdAt = book.createdAt
                }
                isLoading = false
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        title = newTitle
        if (titleError != null && newTitle.isNotBlank()) {
            titleError = null
        }
    }

    fun onAuthorChange(newAuthor: String) {
        author = newAuthor
    }

    fun onDescriptionChange(newDescription: String) {
        description = newDescription
    }

    fun onPdfSelected(context: Context, uri: Uri) {
        isImportingPdf = true
        pdfErrorMessage = null

        viewModelScope.launch {
            val targetId = bookId ?: System.currentTimeMillis()
            val importResult = withContext(Dispatchers.IO) {
                PdfStorageManager.importPdfForBook(context, uri, targetId)
            }

            isImportingPdf = false
            importResult.fold(
                onSuccess = { result ->
                    pdfFilePath = result.filePath
                    pdfFileName = result.fileName
                    pdfTotalPages = result.totalPages
                    pdfErrorMessage = null
                },
                onFailure = { error ->
                    pdfErrorMessage = error.message ?: "Failed to import selected PDF document."
                }
            )
        }
    }

    fun removePdf(context: Context? = null) {
        val pathToDelete = pdfFilePath
        if (!pathToDelete.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                PdfStorageManager.deletePdfFile(pathToDelete)
            }
        }
        pdfFilePath = null
        pdfFileName = null
        pdfTotalPages = 0
        pdfErrorMessage = null
    }

    fun clearPdfError() {
        pdfErrorMessage = null
    }

    fun saveBook(onSuccess: (Long) -> Unit) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            titleError = "Book title is required"
            return
        }

        if (isSubmitting) return
        isSubmitting = true

        val app = getApplication<Application>() as? ReadMateApplication

        viewModelScope.launch {
            try {
                if (bookId != null && bookId > 0) {
                    bookRepository.updateBook(
                        id = bookId,
                        title = trimmedTitle,
                        author = author.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        coverImageUrl = coverImageUrl,
                        pdfFilePath = pdfFilePath,
                        pdfFileName = pdfFileName,
                        pdfTotalPages = pdfTotalPages,
                        pdfLastReadPage = pdfLastReadPage,
                        createdAt = createdAt
                    )

                    // If PDF was attached or updated and has no chapters yet, trigger background extraction
                    if (!pdfFilePath.isNullOrBlank() && app != null) {
                        ExtractChaptersWorker.enqueue(
                            context = app,
                            bookId = bookId,
                            bookTitle = trimmedTitle,
                            filePath = pdfFilePath,
                            fileName = pdfFileName,
                            totalPages = pdfTotalPages
                        )
                    }

                    onSuccess(bookId)
                } else {
                    val newId = bookRepository.createBook(
                        title = trimmedTitle,
                        author = author.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        pdfFilePath = pdfFilePath,
                        pdfFileName = pdfFileName,
                        pdfTotalPages = pdfTotalPages,
                        pdfLastReadPage = 0
                    )

                    // Dispatch full PDF auto-chapter extraction immediately in background
                    if (!pdfFilePath.isNullOrBlank() && app != null) {
                        ExtractChaptersWorker.enqueue(
                            context = app,
                            bookId = newId,
                            bookTitle = trimmedTitle,
                            filePath = pdfFilePath,
                            fileName = pdfFileName,
                            totalPages = pdfTotalPages
                        )
                    }

                    onSuccess(newId)
                }
            } finally {
                isSubmitting = false
            }
        }
    }
}
