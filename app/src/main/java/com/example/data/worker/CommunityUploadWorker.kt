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
import com.example.data.remote.drive.DriveExplorerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Autopilot background worker that transmits locally imported PDFs and metadata
 * to the Google Apps Script serverless relay without blocking local reading,
 * parsing, or Room database persistence.
 */
class CommunityUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "CommunityUploadWorker"
        const val RELAY_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbzx1gYv0W2Y7lFMY6m16g55jEYXGmNKoTMJI8CKQHROJmxGBxR4yxHsUrSJrZcpv-dcwA/exec"
        const val MAX_FILE_SIZE_BYTES = 35L * 1024 * 1024 // 35 MB

        const val KEY_BOOK_ID = "book_id"
        const val KEY_TITLE = "title"
        const val KEY_AUTHOR = "author"
        const val KEY_FILE_PATH = "file_path"

        fun enqueue(
            context: Context,
            bookId: Long,
            title: String,
            author: String?,
            filePath: String
        ) {
            val inputData = Data.Builder()
                .putLong(KEY_BOOK_ID, bookId)
                .putString(KEY_TITLE, title)
                .putString(KEY_AUTHOR, author)
                .putString(KEY_FILE_PATH, filePath)
                .build()

            // Allow WorkManager to retry once network connectivity is established
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CommunityUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("community_upload_$bookId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        val rawTitle = inputData.getString(KEY_TITLE)
        val rawAuthor = inputData.getString(KEY_AUTHOR)
        val filePath = inputData.getString(KEY_FILE_PATH)

        if (filePath.isNullOrBlank() || rawTitle.isNullOrBlank()) {
            return@withContext Result.success()
        }

        val pdfFile = File(filePath)
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            Log.w(TAG, "Local PDF file does not exist or cannot be read: $filePath")
            return@withContext Result.success()
        }

        // Guard against files exceeding 35MB to prevent Google Apps Script execution timeout
        if (pdfFile.length() > MAX_FILE_SIZE_BYTES) {
            Log.i(TAG, "File '${pdfFile.name}' (${pdfFile.length()} bytes) exceeds 35MB; skipping community cloud upload.")
            return@withContext Result.success()
        }

        if (pdfFile.length() == 0L) {
            return@withContext Result.success()
        }

        val cleanTitle = rawTitle.trim().ifBlank { "Untitled Document" }
        val cleanAuthor = rawAuthor?.trim()?.ifBlank { "Unknown" } ?: "Unknown"

        // Duplicate Cloud Pre-Check: verify if book title already exists in Drive repository
        try {
            val app = appContext.applicationContext as? ReadMateApplication
            val driveRepo = app?.driveExplorerRepository ?: DriveExplorerRepository()
            val searchResult = driveRepo.searchExploreBooks(cleanTitle)
            val matches = searchResult.getOrNull() ?: emptyList()
            val normTitle = normalizeForComparison(cleanTitle)
            val alreadyExists = matches.any { normalizeForComparison(it.title) == normTitle }
            if (alreadyExists) {
                Log.d(TAG, "Book '$cleanTitle' already exists in cloud repository; skipping duplicate upload.")
                return@withContext Result.success()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud duplicate pre-check encountered an error: ${e.message}. Proceeding with upload.")
        }

        // Construct JSON Payload
        val jsonPayload = try {
            val fileBytes = pdfFile.readBytes()
            val base64String = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
            JSONObject().apply {
                put("title", cleanTitle)
                put("author", cleanAuthor)
                put("base64", base64String)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode PDF to Base64 for '$cleanTitle': ${e.message}", e)
            return@withContext Result.success()
        }

        // Execute network POST via OkHttpClient with extended timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(RELAY_ENDPOINT)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string()
                Log.d(TAG, "Community upload succeeded for '$cleanTitle' by '$cleanAuthor': $responseBodyStr")
                Result.success()
            } else {
                Log.w(TAG, "Community upload HTTP failure for '$cleanTitle': ${response.code}")
                if (runAttemptCount < 1) Result.retry() else Result.success()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error uploading '$cleanTitle' to community relay: ${e.message}")
            if (runAttemptCount < 1) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error uploading '$cleanTitle': ${e.message}", e)
            Result.success()
        }
    }

    private fun normalizeForComparison(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "")
    }
}
