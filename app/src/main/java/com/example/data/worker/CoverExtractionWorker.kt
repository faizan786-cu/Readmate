package com.example.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerationConfig
import com.example.data.remote.gemini.GeminiInlineData
import com.example.data.remote.gemini.GeminiPart
import com.example.ui.components.pdf.PdfPageRendererHelper
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@JsonClass(generateAdapter = true)
data class CoverSelectionResponse(
    @Json(name = "coverPageIndex") val coverPageIndex: Int? = null,
    @Json(name = "confidence") val confidence: Double? = null,
    @Json(name = "reasoning") val reasoning: String? = null
)

/**
 * Intelligent Gemini Vision-Powered Background Cover Extraction Worker.
 *
 * 1. Checks BookEntity.coverImageUrl; if already present and valid, exits silently.
 * 2. Renders low-res thumbnail previews of the first 5 pages using PdfPageRendererHelper.
 * 3. Dispatches a multimodal Vision request to Gemini (with Flash-Lite multi-tier fallback & key rotation)
 *    to analyze which page is the authentic aesthetic book front cover.
 * 4. Renders the identified cover page at high resolution (800px width), saves it to internal storage:
 *    context.filesDir/covers/cover_${bookId}.png
 * 5. Updates BookEntity.coverImageUrl in Room database so all UI components immediately display the true cover.
 */
class CoverExtractionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "CoverExtractionWorker"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_BOOK_TITLE = "book_title"

        fun enqueue(
            context: Context,
            bookId: Long,
            filePath: String,
            bookTitle: String
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_BOOK_ID, bookId)
                .putString(KEY_FILE_PATH, filePath)
                .putString(KEY_BOOK_TITLE, bookTitle)
                .build()

            val request = OneTimeWorkRequestBuilder<CoverExtractionWorker>()
                .setInputData(inputData)
                .addTag("cover_extraction_$bookId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        suspend fun executeDirectly(
            app: ReadMateApplication,
            bookId: Long,
            filePath: String,
            bookTitle: String
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val book = app.bookRepository.getBook(bookId) ?: return@withContext false

                // If cover is already present and points to a valid file/url, skip
                if (!book.coverImageUrl.isNullOrBlank()) {
                    val existingFile = File(book.coverImageUrl)
                    if (existingFile.exists() && existingFile.length() > 0) {
                        Log.d(TAG, "Book $bookId already has a valid cover image: ${book.coverImageUrl}. Skipping extraction.")
                        return@withContext true
                    }
                }

                val pdfFile = File(filePath)
                if (!pdfFile.exists() || pdfFile.length() == 0L) {
                    Log.w(TAG, "PDF file does not exist or is empty: $filePath")
                    return@withContext false
                }

                val pagesToScan = 5
                val thumbnailParts = mutableListOf<GeminiPart>()
                val validPageIndices = mutableListOf<Int>()

                for (pageIdx in 0 until pagesToScan) {
                    val bitmap = PdfPageRendererHelper.renderPageBitmap(
                        filePath = filePath,
                        pageIndex = pageIdx,
                        targetWidthPx = 300,
                        targetHeightPx = 420
                    ) ?: continue

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                    val base64Thumbnail = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    validPageIndices.add(pageIdx)

                    thumbnailParts.add(
                        GeminiPart(
                            text = "Page $pageIdx (Physical PDF page index $pageIdx):"
                        )
                    )
                    thumbnailParts.add(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = base64Thumbnail
                            )
                        )
                    )
                }

                if (thumbnailParts.isEmpty()) {
                    Log.w(TAG, "No pages could be rendered for book $bookId")
                    return@withContext false
                }

                val userPromptText = """
                    You are an expert digital librarian and graphic design specialist.
                    The user imported a book titled "$bookTitle".
                    Attached are the rendered thumbnails of the first pages (0-indexed) of this PDF.
                    
                    Identify which physical page (0 to ${validPageIndices.last()}) represents the authentic aesthetic front book cover (showing the main cover art, prominent title typography, author name, and cover layout).
                    Note:
                    - Page 0 is often the true cover, but sometimes page 0 is a blank page, publisher promo, or scan artifact.
                    - If page 0 is the authentic cover, pick 0.
                    - If page 0 is blank/junk/scan artifact and page 1 or 2 is the actual artistic cover, pick that index.
                    - If none look like a traditional graphical cover, pick the page that best serves as the title/cover presentation (usually page 0 or 1).
                    
                    Return ONLY a valid JSON object matching this schema:
                    {
                      "coverPageIndex": 0,
                      "confidence": 0.95,
                      "reasoning": "Page 0 has the full illustrated cover artwork and prominent title."
                    }
                """.trimIndent()

                thumbnailParts.add(GeminiPart(text = userPromptText))

                val request = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = thumbnailParts
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        temperature = 0.1f,
                        responseMimeType = "application/json"
                    )
                )

                val rotationManager = GeminiKeyRotationManager(app.secureApiKeyStorage)
                val responseResult = rotationManager.executeFlashLiteRequest(
                    request = request,
                    operationName = "Gemini Vision Cover Extraction for $bookTitle"
                )

                var chosenIndex = validPageIndices.first() // default fallback to page 0

                val response = responseResult.getOrNull()
                val candidateText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull { !it.text.isNullOrBlank() }?.text

                if (!candidateText.isNullOrBlank()) {
                    try {
                        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                        val adapter = moshi.adapter(CoverSelectionResponse::class.java)
                        val cleanedJson = candidateText.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val parsed = adapter.fromJson(cleanedJson)
                        if (parsed?.coverPageIndex != null && validPageIndices.contains(parsed.coverPageIndex)) {
                            chosenIndex = parsed.coverPageIndex
                            Log.d(TAG, "Gemini selected cover page index $chosenIndex with confidence ${parsed.confidence}: ${parsed.reasoning}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse Gemini cover selection JSON: ${e.message}. Using fallback index $chosenIndex")
                    }
                } else {
                    Log.w(TAG, "Gemini response empty or failed. Defaulting to first available page $chosenIndex")
                }

                // Render high-resolution (800px width) cover of the selected page
                val highResBitmap = PdfPageRendererHelper.renderPageBitmap(
                    filePath = filePath,
                    pageIndex = chosenIndex,
                    targetWidthPx = 800,
                    targetHeightPx = 1120
                ) ?: return@withContext false

                val coversDir = File(app.filesDir, "covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }

                val coverFile = File(coversDir, "cover_${bookId}.png")
                FileOutputStream(coverFile).use { outStream ->
                    highResBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    outStream.flush()
                }

                val coverPath = coverFile.absolutePath
                Log.d(TAG, "Successfully extracted and saved high-res cover to $coverPath")

                // Update Room database
                app.bookRepository.updateCoverImageUrl(bookId, coverPath)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error in executeDirectly for book $bookId: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        val filePath = inputData.getString(KEY_FILE_PATH)
        val bookTitle = inputData.getString(KEY_BOOK_TITLE) ?: "Book"

        if (bookId <= 0L || filePath.isNullOrBlank()) {
            return androidx.work.ListenableWorker.Result.success()
        }

        val app = applicationContext as? ReadMateApplication ?: return androidx.work.ListenableWorker.Result.success()

        executeDirectly(
            app = app,
            bookId = bookId,
            filePath = filePath,
            bookTitle = bookTitle
        )

        return androidx.work.ListenableWorker.Result.success()
    }
}
