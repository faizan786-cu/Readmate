package com.example.data.worker

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Background worker that queues sanitized metadata for community catalog contribution
 * when a user imports a new local PDF, without blocking or slowing down the local reading experience.
 */
class CatalogContributionSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "CatalogContributionSync"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_TITLE = "title"
        const val KEY_AUTHOR = "author"
        const val KEY_PAGE_COUNT = "page_count"
        const val KEY_FILE_PATH = "file_path"

        fun enqueue(
            context: Context,
            bookId: Long,
            title: String,
            author: String?,
            pageCount: Int,
            filePath: String?
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_BOOK_ID, bookId)
                .putString(KEY_TITLE, title)
                .putString(KEY_AUTHOR, author)
                .putInt(KEY_PAGE_COUNT, pageCount)
                .putString(KEY_FILE_PATH, filePath)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CatalogContributionSyncWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("catalog_contrib_$bookId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: return@withContext Result.success()
        val author = inputData.getString(KEY_AUTHOR)
        val pageCount = inputData.getInt(KEY_PAGE_COUNT, 0)
        val filePath = inputData.getString(KEY_FILE_PATH)

        val app = appContext.applicationContext as ReadMateApplication

        try {
            Log.d(TAG, "Checking central curated catalog index for: '$title'")
            // 1. Check if this book title already exists in the central curated index
            val searchResult = app.driveExplorerRepository.searchExploreBooks(title)
            val matches = searchResult.getOrNull() ?: emptyList()
            val cleanTitle = title.trim().lowercase()

            val alreadyExists = matches.any { it.title.trim().lowercase() == cleanTitle }
            if (alreadyExists) {
                Log.d(TAG, "'$title' already exists in the central index. No contribution needed.")
                return@withContext Result.success()
            }

            // 2. Fetch chapter markers from Room database
            val chapters = app.chapterRepository.getChaptersForBookSync(bookId)
            val chapterArray = JSONArray()
            for (ch in chapters) {
                val chObj = JSONObject().apply {
                    put("chapterNumber", ch.chapterNumber ?: 0)
                    put("title", ch.title)
                    put("startPage", ch.startPage)
                    put("endPage", ch.endPage)
                    put("sectionType", ch.sectionType)
                }
                chapterArray.put(chObj)
            }

            // 3. Extract cover thumbnail base64 if available
            val coverFile = File(appContext.filesDir, "covers/book_${bookId}_cover.png")
            val base64Thumbnail = if (coverFile.exists() && coverFile.length() > 0) {
                try {
                    val bytes = coverFile.readBytes()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } catch (_: Exception) {
                    null
                }
            } else null

            // 4. Queue sanitized book metadata into local catalog contribution staging storage
            val payload = JSONObject().apply {
                put("bookId", bookId)
                put("title", title)
                put("author", author ?: "")
                put("pageCount", pageCount)
                put("chapterCount", chapters.size)
                put("chapters", chapterArray)
                put("coverThumbnailBase64", base64Thumbnail ?: "")
                put("timestamp", System.currentTimeMillis())
            }

            val queueDir = File(appContext.filesDir, "catalog_contributions")
            if (!queueDir.exists()) {
                queueDir.mkdirs()
            }
            val stagingFile = File(queueDir, "contrib_${bookId}_${System.currentTimeMillis()}.json")
            stagingFile.writeText(payload.toString())

            Log.d(TAG, "Sanitized book contribution record staged for '$title' at ${stagingFile.name}")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Failed catalog contribution sync for '$title': ${e.message}")
            Result.success()
        }
    }
}
