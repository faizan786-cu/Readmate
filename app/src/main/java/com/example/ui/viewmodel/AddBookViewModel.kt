package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ReadMateApplication
import com.example.data.local.database.entity.Chapter
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.model.ExtractedBookPayload
import com.example.data.model.ExtractedChapterSection
import com.example.data.pdf.PdfStorageManager
import com.example.data.pdf.PdfStructureExtractor
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.worker.CoverExtractionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for zero-friction 1-tap PDF book ingestion with auto-detected metadata and chapter extraction.
 */
class AddBookViewModel(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    application: Application
) : AndroidViewModel(application) {

    var selectedFilePath by mutableStateOf<String?>(null)
        private set

    var selectedFileName by mutableStateOf<String?>(null)
        private set

    var selectedFileSizeMb by mutableStateOf<Double?>(null)
        private set

    var selectedTotalPages by mutableIntStateOf(0)
        private set

    var isImportingPdf by mutableStateOf(false)
        private set

    var isCreatingBook by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onPdfSelected(context: Context, uri: Uri) {
        isImportingPdf = true
        errorMessage = null

        viewModelScope.launch {
            val tempId = System.currentTimeMillis()
            val importResult = withContext(Dispatchers.IO) {
                PdfStorageManager.importPdfForBook(context, uri, tempId)
            }

            isImportingPdf = false
            importResult.fold(
                onSuccess = { result ->
                    selectedFilePath = result.filePath
                    selectedFileName = result.fileName
                    selectedTotalPages = result.totalPages
                    val file = File(result.filePath)
                    selectedFileSizeMb = if (file.exists()) {
                        file.length().toDouble() / (1024.0 * 1024.0)
                    } else {
                        null
                    }
                    errorMessage = null
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Failed to import selected PDF document."
                }
            )
        }
    }

    fun removePdf() {
        val pathToDelete = selectedFilePath
        if (!pathToDelete.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                PdfStorageManager.deletePdfFile(pathToDelete)
            }
        }
        selectedFilePath = null
        selectedFileName = null
        selectedFileSizeMb = null
        selectedTotalPages = 0
        errorMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun createBook(onSuccess: (Long) -> Unit) {
        val filePath = selectedFilePath
        if (filePath.isNullOrBlank()) {
            errorMessage = "Please attach a PDF document first."
            return
        }

        if (isCreatingBook) return
        isCreatingBook = true
        errorMessage = null

        val fileName = selectedFileName ?: "document.pdf"
        val totalPages = selectedTotalPages.coerceAtLeast(1)
        val fallbackTitle = fileName.removeSuffix(".pdf")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Untitled Book" }

        viewModelScope.launch {
            try {
                // 0. Pre-Parse Guard: Check if fallback title matches an existing book in Room
                val preExisting = withContext(Dispatchers.IO) {
                    bookRepository.getBookByCleanTitle(fallbackTitle)
                }
                if (preExisting != null) {
                    errorMessage = "This book already exists in your library."
                    isCreatingBook = false
                    return@launch
                }

                val file = File(filePath)
                val rotationManager = GeminiKeyRotationManager(secureApiKeyStorage)
                val extractor = PdfStructureExtractor(rotationManager)

                // 1 & 2. Flash-Lite AI extraction with silent 3.1 -> 3.5 failover and multi-key rotation
                val extractionResult = withContext(Dispatchers.IO) {
                    extractor.extractBookAndStructure(file, totalPages, fallbackTitle)
                }

                val payload: ExtractedBookPayload = extractionResult.getOrNull() ?: ExtractedBookPayload(
                    bookTitle = fallbackTitle,
                    author = null,
                    sections = extractor.createFallbackSections(totalPages, fallbackTitle)
                )

                // 3. Fallback to clean file name if title is null or blank
                val finalTitle = payload.bookTitle?.trim()?.ifBlank { fallbackTitle } ?: fallbackTitle
                val finalAuthor = payload.author?.trim()?.ifBlank { null }

                // Post-Parse Guard: If AI identified an alternate title, verify it isn't also a duplicate
                val existingAlternate = withContext(Dispatchers.IO) {
                    bookRepository.getBookByCleanTitle(finalTitle)
                }
                if (existingAlternate != null) {
                    errorMessage = "This book already exists in your library."
                    isCreatingBook = false
                    return@launch
                }

                val rawSections = payload.sections.ifEmpty {
                    extractor.createFallbackSections(totalPages, finalTitle)
                }

                // 4. Save the book entry into Room books table
                val newBookId = withContext(Dispatchers.IO) {
                    bookRepository.createBook(
                        title = finalTitle,
                        author = finalAuthor,
                        description = null,
                        pdfFilePath = filePath,
                        pdfFileName = fileName,
                        pdfTotalPages = totalPages,
                        pdfLastReadPage = 0
                    )
                }

                // Trigger intelligent background Gemini Vision cover extraction
                CoverExtractionWorker.enqueue(
                    context = getApplication(),
                    bookId = newBookId,
                    filePath = filePath,
                    bookTitle = finalTitle
                )

                // 5. Batch insert payload.sections into Room chapters table mapped to the new bookId
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    var coreCounter = 1
                    val chapterEntities = rawSections.mapIndexed { index, sec ->
                        val cleanSecTitle = sec.title.trim().ifEmpty { "Chapter ${index + 1}" }
                        val chapPages = (sec.endPage - sec.startPage + 1).coerceAtLeast(1)
                        val chapNum = if (sec.sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) {
                            sec.chapterNumber ?: coreCounter++
                        } else {
                            null
                        }
                        Chapter(
                            bookId = newBookId,
                            chapterNumber = chapNum,
                            sectionType = sec.sectionType,
                            startPage = sec.startPage,
                            endPage = sec.endPage,
                            title = cleanSecTitle,
                            pdfFilePath = filePath,
                            pdfFileName = fileName,
                            pdfTotalPages = chapPages,
                            pdfLastReadPage = 0,
                            createdAt = now,
                            updatedAt = now
                        )
                    }

                    if (chapterEntities.isNotEmpty()) {
                        chapterRepository.insertChapters(chapterEntities)
                    }

                    // Purge any preexisting junk chapters
                    chapterRepository.purgeJunkChapters()
                }

                isCreatingBook = false

                // Trigger background community catalog synchronization without blocking user
                try {
                    com.example.data.worker.CatalogContributionSyncWorker.enqueue(
                        context = getApplication(),
                        bookId = newBookId,
                        title = finalTitle,
                        author = finalAuthor,
                        pageCount = totalPages,
                        filePath = filePath
                    )
                } catch (_: Exception) {}

                // 6. Dismiss and trigger immediate navigation to the chapter list screen
                onSuccess(newBookId)
            } catch (e: Exception) {
                Log.e("AddBookViewModel", "Failed to create book: ${e.message}", e)
                errorMessage = e.message ?: "Failed to process PDF."
                isCreatingBook = false
            }
        }
    }
}
