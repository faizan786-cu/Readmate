package com.example.data.remote.drive

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DriveExplorerRepository(
    private val okHttpClient: OkHttpClient = createDefaultHttpClient()
) {

    companion object {
        private const val TAG = "DriveExplorerRepo"
        const val API_KEY = "AIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kcVc"
        const val PARENT_FOLDER_ID = "1_EopB71PmM_Hc3HXkUeaeSIl5uoXFNtf"

        private const val BASE_FILES_URL = "https://www.googleapis.com/drive/v3/files"

        fun createDefaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "ReadMate-Android/1.0 (Mobile; Linux; Android)")
                        .build()
                    chain.proceed(req)
                }
                .build()
        }
    }

    /**
     * Fetches up to 100 curated PDF books directly from the public Google Drive folder.
     */
    suspend fun fetchExploreBooks(): Result<List<DriveBookItem>> = withContext(Dispatchers.IO) {
        try {
            val qParam = "'$PARENT_FOLDER_ID' in parents and mimeType='application/pdf' and trashed=false"
            val encodedQ = URLEncoder.encode(qParam, "UTF-8")
            val url = "$BASE_FILES_URL?q=$encodedQ&pageSize=100&fields=files(id,name,size,thumbnailLink,iconLink)&key=$API_KEY"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Failed to fetch files: HTTP ${response.code} - $errorBody")
                return@withContext Result.failure(IOException("Failed to load catalog: HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.success(emptyList())
            val books = parseFilesResponse(responseBody)
            Log.d(TAG, "Fetched ${books.size} explore books from Drive.")
            Result.success(books)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching explore books: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Performs a deep cloud search using Drive API with `name contains '${sanitizedQuery}'`.
     */
    suspend fun searchExploreBooks(query: String): Result<List<DriveBookItem>> = withContext(Dispatchers.IO) {
        try {
            val sanitized = query.replace("'", "\\'")
                .replace("\"", "")
                .trim()
            if (sanitized.isBlank()) return@withContext Result.success(emptyList())

            val qParam = "'$PARENT_FOLDER_ID' in parents and mimeType='application/pdf' and trashed=false and name contains '$sanitized'"
            val encodedQ = URLEncoder.encode(qParam, "UTF-8")
            val url = "$BASE_FILES_URL?q=$encodedQ&pageSize=100&fields=files(id,name,size,thumbnailLink,iconLink)&key=$API_KEY"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Search failed: HTTP ${response.code} - $errorBody")
                return@withContext Result.failure(IOException("Search failed: HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.success(emptyList())
            val books = parseFilesResponse(responseBody)
            Log.d(TAG, "Deep search for '$query' returned ${books.size} results.")
            Result.success(books)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching books on Drive: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Streams binary directly from Google Drive endpoint into target file with real-time progress callbacks.
     */
    suspend fun downloadBookFile(
        fileId: String,
        targetFile: File,
        expectedSizeBytes: Long?,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download_${System.currentTimeMillis()}.tmp")

            val downloadUrl = "$BASE_FILES_URL/$fileId?alt=media&key=$API_KEY"
            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Failed to download file: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(IOException("Download response body is empty"))
            val totalLength = if (body.contentLength() > 0) body.contentLength() else (expectedSizeBytes ?: -1L)

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportedProgress = -1f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val progress = (totalRead.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                            // Throttle progress updates slightly
                            if (progress - lastReportedProgress >= 0.02f || progress >= 1f) {
                                lastReportedProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IOException("Downloaded file is empty"))
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                // If rename failed across mounts, copy & delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1f)
            Log.d(TAG, "File $fileId downloaded successfully to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream download for $fileId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseFilesResponse(jsonStr: String): List<DriveBookItem> {
        val list = mutableListOf<DriveBookItem>()
        val root = JSONObject(jsonStr)
        val filesArray = root.optJSONArray("files") ?: return emptyList()

        for (i in 0 until filesArray.length()) {
            val obj = filesArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue

            val sizeStr = if (obj.has("size")) obj.optString("size") else null
            val thumb = if (obj.has("thumbnailLink")) obj.optString("thumbnailLink") else null
            val icon = if (obj.has("iconLink")) obj.optString("iconLink") else null

            list.add(
                DriveBookItem.fromDriveJson(
                    id = id,
                    name = name,
                    sizeStr = sizeStr,
                    thumbnailLink = thumb,
                    iconLink = icon
                )
            )
        }
        return list
    }
}
