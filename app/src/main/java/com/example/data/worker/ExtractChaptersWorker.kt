package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import com.example.data.local.database.entity.Chapter
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.manager.PdfExtractionStateManager
import com.example.data.model.ExtractedChapterSection
import com.example.data.pdf.PdfStructureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for full PDF auto-chapter extraction and zero-friction Room database sync.
 */
class ExtractChaptersWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ExtractChaptersWorker"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_BOOK_TITLE = "book_title"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TOTAL_PAGES = "total_pages"

        fun enqueue(
            context: Context,
            bookId: Long,
            bookTitle: String,
            filePath: String?,
            fileName: String?,
            totalPages: Int
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_BOOK_ID, bookId)
                .putString(KEY_BOOK_TITLE, bookTitle)
                .putString(KEY_FILE_PATH, filePath)
                .putString(KEY_FILE_NAME, fileName)
                .putInt(KEY_TOTAL_PAGES, totalPages)
                .build()

            val request = OneTimeWorkRequestBuilder<ExtractChaptersWorker>()
                .setInputData(inputData)
                .addTag("extract_chapters_$bookId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Direct coroutine executor for immediate reactive execution.
         */
        suspend fun executeDirectly(
            app: ReadMateApplication,
            bookId: Long,
            bookTitle: String,
            filePath: String?,
            fileName: String?,
            totalPages: Int
        ): Int = withContext(Dispatchers.IO) {
            try {
                PdfExtractionStateManager.setProcessing(bookId, bookTitle)

                val rotationManager = GeminiKeyRotationManager(app.secureApiKeyStorage)
                val extractor = PdfStructureExtractor(rotationManager)

                val file = filePath?.let { File(it) }
                val extractionResult: kotlin.Result<List<ExtractedChapterSection>> = if (file != null && file.exists()) {
                    extractor.extractStructure(file, totalPages, bookTitle)
                } else {
                    kotlin.Result.success(extractor.createFallbackSections(totalPages, bookTitle))
                }

                val sections: List<ExtractedChapterSection> = extractionResult.getOrNull()?.ifEmpty {
                    extractor.createFallbackSections(totalPages, bookTitle)
                } ?: extractor.createFallbackSections(totalPages, bookTitle)

                // Check existing chapters to avoid duplicate inserts
                val existingChapters = app.chapterRepository.getChaptersForBookSync(bookId)
                val existingTitles = existingChapters.map { it.title.trim().lowercase() }.toSet()

                val now = System.currentTimeMillis()
                val newChapters = mutableListOf<Chapter>()

                var coreCounter = 1
                sections.forEachIndexed { index, sec ->
                    val cleanTitle = sec.title.trim()
                    if (cleanTitle.isNotEmpty() && 
                        !ExtractedChapterSection.isBlacklisted(cleanTitle) &&
                        !existingTitles.contains(cleanTitle.lowercase())
                    ) {
                        val chapterPages = (sec.endPage - sec.startPage + 1).coerceAtLeast(1)
                        val chapNum = if (sec.sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) {
                            sec.chapterNumber ?: coreCounter++
                        } else {
                            null
                        }
                        newChapters.add(
                            Chapter(
                                bookId = bookId,
                                chapterNumber = chapNum,
                                sectionType = sec.sectionType,
                                startPage = sec.startPage,
                                endPage = sec.endPage,
                                title = cleanTitle,
                                pdfFilePath = filePath,
                                pdfFileName = fileName,
                                pdfTotalPages = chapterPages,
                                pdfLastReadPage = 0,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }

                if (newChapters.isNotEmpty()) {
                    app.chapterRepository.insertChapters(newChapters)
                }

                // Purge any preexisting junk chapters
                app.chapterRepository.purgeJunkChapters()

                val totalCount = existingChapters.size + newChapters.size
                PdfExtractionStateManager.setSuccess(bookId, bookTitle, totalCount.coerceAtLeast(newChapters.size))
                newChapters.size
            } catch (e: Exception) {
                Log.e(TAG, "Extraction error: ${e.message}", e)
                PdfExtractionStateManager.setError(bookId, bookTitle, e.message ?: "Extraction failed")
                0
            }
        }
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        if (bookId <= 0L) return androidx.work.ListenableWorker.Result.success()

        val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Book"
        val filePath = inputData.getString(KEY_FILE_PATH)
        val fileName = inputData.getString(KEY_FILE_NAME)
        val totalPages = inputData.getInt(KEY_TOTAL_PAGES, 1)

        val app = applicationContext as? ReadMateApplication ?: return androidx.work.ListenableWorker.Result.success()

        executeDirectly(
            app = app,
            bookId = bookId,
            bookTitle = bookTitle,
            filePath = filePath,
            fileName = fileName,
            totalPages = totalPages
        )

        return androidx.work.ListenableWorker.Result.success()
    }
}
