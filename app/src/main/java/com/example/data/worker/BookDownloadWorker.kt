package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import com.example.data.local.database.entity.Chapter
import com.example.data.manager.BookDownloadManager
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.model.ExtractedBookPayload
import com.example.data.model.ExtractedChapterSection
import com.example.data.pdf.PdfStorageManager
import com.example.data.pdf.PdfStructureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resilient WorkManager background worker for fail-safe Google Drive PDF downloads,
 * low-disk-space validation, network-loss exponential retry, and auto-parsing handoff.
 */
class BookDownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "BookDownloadWorker"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_DRIVE_FILE_ID = "drive_file_id"
        const val KEY_BOOK_TITLE = "book_title"
        const val KEY_AUTHOR = "author"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_EXPECTED_SIZE = "expected_size"

        fun enqueue(
            context: Context,
            bookId: Long,
            driveFileId: String,
            bookTitle: String,
            author: String?,
            coverUrl: String?,
            expectedSizeBytes: Long?
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_BOOK_ID, bookId)
                .putString(KEY_DRIVE_FILE_ID, driveFileId)
                .putString(KEY_BOOK_TITLE, bookTitle)
                .putString(KEY_AUTHOR, author)
                .putString(KEY_COVER_URL, coverUrl)
                .putLong(KEY_EXPECTED_SIZE, expectedSizeBytes ?: -1L)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BookDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .addTag("download_book_$bookId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        val driveFileId = inputData.getString(KEY_DRIVE_FILE_ID) ?: return@withContext Result.failure()
        val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Untitled Book"
        val author = inputData.getString(KEY_AUTHOR)
        val coverUrl = inputData.getString(KEY_COVER_URL)
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, -1L).takeIf { it > 0 }

        val app = appContext.applicationContext as ReadMateApplication
        val driveRepo = app.driveExplorerRepository

        Log.d(TAG, "Starting resilient background download for bookId=$bookId, driveId=$driveFileId ('$bookTitle')")

        // 1. Edge Case Handling: Low Disk Space check
        val requiredBuffer = (expectedSize ?: (20L * 1024 * 1024)) + (15L * 1024 * 1024)
        val usableSpace = appContext.filesDir.usableSpace
        if (usableSpace < requiredBuffer) {
            Log.e(TAG, "Insufficient disk space: usable=$usableSpace bytes, required=$requiredBuffer bytes")
            if (bookId > 0) {
                app.bookRepository.deleteBookById(bookId)
            }
            BookDownloadManager.removeDownload(bookId, driveFileId)
            BookDownloadManager.postEvent("Insufficient storage space on device to download \"$bookTitle\".")
            return@withContext Result.failure()
        }

        // 2. Prepare target file destination
        val booksDir = File(appContext.filesDir, "books")
        if (!booksDir.exists()) {
            booksDir.mkdirs()
        }
        val targetFile = File(booksDir, "${driveFileId}.pdf")

        // 3. Resilient Streaming with up to 3 retries and exponential backoff
        var attempts = 0
        var downloadSuccess = false
        var lastError: Throwable? = null

        BookDownloadManager.updateProgress(bookId, driveFileId, 0.05f, "Connecting...")

        while (attempts < 3 && !downloadSuccess) {
            attempts++
            try {
                val streamResult = driveRepo.downloadBookFile(
                    fileId = driveFileId,
                    targetFile = targetFile,
                    expectedSizeBytes = expectedSize
                ) { progress ->
                    BookDownloadManager.updateProgress(
                        bookId = bookId,
                        driveFileId = driveFileId,
                        progress = progress.coerceIn(0.05f, 0.92f),
                        statusText = "Downloading ${(progress * 100).toInt()}%"
                    )
                }

                if (streamResult.isSuccess && targetFile.exists() && targetFile.length() > 0) {
                    downloadSuccess = true
                } else {
                    lastError = streamResult.exceptionOrNull() ?: IOException("Empty stream received")
                    Log.w(TAG, "Download attempt $attempts failed: ${lastError.message}")
                    if (attempts < 3) {
                        BookDownloadManager.updateProgress(
                            bookId = bookId,
                            driveFileId = driveFileId,
                            progress = 0.05f,
                            statusText = "Retrying connection (attempt $attempts)..."
                        )
                        delay(1000L * (1 shl (attempts - 1))) // 1s, 2s
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Exception during download attempt $attempts: ${e.message}")
                if (attempts < 3) {
                    BookDownloadManager.updateProgress(
                        bookId = bookId,
                        driveFileId = driveFileId,
                        progress = 0.05f,
                        statusText = "Retrying connection..."
                    )
                    delay(1000L * (1 shl (attempts - 1)))
                }
            }
        }

        // If network remained disconnected or all retries failed
        if (!downloadSuccess || !targetFile.exists()) {
            Log.e(TAG, "All download attempts failed for bookId=$bookId: ${lastError?.message}")
            if (bookId > 0) {
                app.bookRepository.deleteBookById(bookId)
            }
            BookDownloadManager.removeDownload(bookId, driveFileId)
            BookDownloadManager.postEvent("Network connection lost. Download of \"$bookTitle\" aborted.")
            return@withContext Result.failure()
        }

        // 4. Task 5: Auto-Parser Trigger
        BookDownloadManager.updateProgress(bookId, driveFileId, 0.95f, "Parsing document...")

        try {
            val totalPages = PdfStorageManager.getPdfPageCount(targetFile).coerceAtLeast(1)
            val rotationManager = GeminiKeyRotationManager(app.secureApiKeyStorage)
            val extractor = PdfStructureExtractor(rotationManager)
            val fallbackSections = extractor.createFallbackSections(totalPages, bookTitle)

            val payload: ExtractedBookPayload = try {
                if (app.secureApiKeyStorage.hasApiKey()) {
                    extractor.extractBookAndStructure(targetFile, totalPages, bookTitle).getOrNull()
                        ?: ExtractedBookPayload(bookTitle = bookTitle, author = author, sections = fallbackSections)
                } else {
                    ExtractedBookPayload(bookTitle = bookTitle, author = author, sections = fallbackSections)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-parser fallback triggered: ${e.message}")
                ExtractedBookPayload(bookTitle = bookTitle, author = author, sections = fallbackSections)
            }

            val finalTitle = payload.bookTitle?.trim()?.ifBlank { bookTitle } ?: bookTitle
            val finalAuthor = payload.author?.trim()?.ifBlank { author } ?: author

            // Update Book entity in Room DB from placeholder to ready
            app.bookRepository.updateBook(
                id = bookId,
                title = finalTitle,
                author = finalAuthor,
                description = null,
                coverImageUrl = coverUrl,
                pdfFilePath = targetFile.absolutePath,
                pdfFileName = "${driveFileId}.pdf",
                pdfTotalPages = totalPages,
                pdfLastReadPage = 0,
                createdAt = System.currentTimeMillis()
            )

            // Spawn background Gemini Vision worker to scan the first 5 pages for true front-cover artwork
            try {
                CoverExtractionWorker.enqueue(
                    context = appContext,
                    bookId = bookId,
                    filePath = targetFile.absolutePath,
                    bookTitle = finalTitle
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not enqueue CoverExtractionWorker: ${e.message}")
            }

            // Extract chapters and persist into Room
            val rawSections = payload.sections.ifEmpty { fallbackSections }
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
                    bookId = bookId,
                    chapterNumber = chapNum,
                    sectionType = sec.sectionType,
                    startPage = sec.startPage,
                    endPage = sec.endPage,
                    title = cleanSecTitle,
                    pdfFilePath = targetFile.absolutePath,
                    pdfFileName = "${driveFileId}.pdf",
                    pdfTotalPages = chapPages,
                    pdfLastReadPage = 0,
                    createdAt = now,
                    updatedAt = now
                )
            }

            if (chapterEntities.isNotEmpty()) {
                app.chapterRepository.insertChapters(chapterEntities)
            }
            app.chapterRepository.purgeJunkChapters()

            BookDownloadManager.removeDownload(bookId, driveFileId)
            BookDownloadManager.postEvent("\"$finalTitle\" is ready to read in My Books")
            Log.d(TAG, "BookId=$bookId ingestion and parsing completed successfully!")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ingested book: ${e.message}", e)
            BookDownloadManager.removeDownload(bookId, driveFileId)
            BookDownloadManager.postEvent("Finished downloading \"$bookTitle\".")
            Result.success()
        }
    }
}
