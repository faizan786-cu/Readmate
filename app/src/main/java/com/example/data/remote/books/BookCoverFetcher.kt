package com.example.data.remote.books

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BookCoverFetcher(
    private val okHttpClient: OkHttpClient = createDefaultOkHttpClient(),
    private val openLibraryService: OpenLibraryApiService = OpenLibraryApiService.create(okHttpClient),
    private val googleBooksService: GoogleBooksApiService = GoogleBooksApiService.create()
) {

    companion object {
        private const val TAG = "BookCoverFetcher"

        fun createDefaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:109.0) Gecko/114.0 Firefox/114.0")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
    }

    /**
     * Silently fetches and verifies the official book cover image URL.
     * Uses Open Library direct cover static JPGs as primary source,
     * with staged fallback to Google Books volume search.
     * Verifies bitmap connectivity prior to returning URL.
     */
    suspend fun fetchCoverUrl(title: String, author: String? = null): String? = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return@withContext null

        Log.d(TAG, "Starting cover fetch for title='$trimmedTitle', author='${author ?: "N/A"}'")

        try {
            // Stage 1: Open Library Search with title and author
            val openLibraryUrl = tryFetchOpenLibraryCover(trimmedTitle, author?.trim())
            if (openLibraryUrl != null && verifyImageUrl(openLibraryUrl)) {
                Log.d(TAG, "Successfully resolved & verified Open Library cover: $openLibraryUrl")
                return@withContext openLibraryUrl
            }

            // Stage 2: Open Library Search with title query fallback
            val openLibraryTitleOnlyUrl = tryFetchOpenLibraryCover(trimmedTitle, null)
            if (openLibraryTitleOnlyUrl != null && verifyImageUrl(openLibraryTitleOnlyUrl)) {
                Log.d(TAG, "Successfully resolved & verified Open Library title-only cover: $openLibraryTitleOnlyUrl")
                return@withContext openLibraryTitleOnlyUrl
            }

            // Stage 3: Google Books fallback
            Log.d(TAG, "Open Library yielded no verified cover. Falling back to Google Books...")
            val googleBooksUrl = tryFetchGoogleBooksCover(trimmedTitle, author?.trim())
            if (googleBooksUrl != null && verifyImageUrl(googleBooksUrl)) {
                Log.d(TAG, "Successfully resolved & verified Google Books cover: $googleBooksUrl")
                return@withContext googleBooksUrl
            }

            Log.d(TAG, "No verified cover found across Open Library or Google Books for '$trimmedTitle'")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent cover fetch exception for '$trimmedTitle': ${e.message}")
            null
        }
    }

    private suspend fun tryFetchOpenLibraryCover(title: String, author: String?): String? {
        return try {
            val response = if (!author.isNullOrBlank()) {
                openLibraryService.searchByTitleAndAuthor(title = title, author = author, limit = 1)
            } else {
                openLibraryService.searchByQuery(query = title, limit = 1)
            }

            if (!response.isSuccessful) {
                Log.d(TAG, "Open Library search responded with HTTP ${response.code()}")
                return null
            }

            val docs = response.body()?.docs
            val doc = docs?.firstOrNull()
            val coverI = doc?.coverI

            if (coverI != null && coverI > 0) {
                "https://covers.openlibrary.org/b/id/$coverI-M.jpg"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Open Library lookup failed: ${e.message}")
            null
        }
    }

    private suspend fun tryFetchGoogleBooksCover(title: String, author: String?): String? {
        return try {
            val structuredQuery = buildGoogleBooksQuery(title, author)
            var response = googleBooksService.searchVolumes(query = structuredQuery, maxResults = 3)
            var coverUrl = extractGoogleBooksCoverUrl(response.body())

            if (coverUrl == null) {
                val cleanTitle = cleanQueryToken(title)
                response = googleBooksService.searchVolumes(query = "intitle:$cleanTitle", maxResults = 3)
                coverUrl = extractGoogleBooksCoverUrl(response.body())
            }

            if (coverUrl == null) {
                val keywordQuery = if (!author.isNullOrBlank()) {
                    "${cleanQueryToken(title)} ${cleanQueryToken(author)}"
                } else {
                    cleanQueryToken(title)
                }
                response = googleBooksService.searchVolumes(query = keywordQuery, maxResults = 3)
                coverUrl = extractGoogleBooksCoverUrl(response.body())
            }

            coverUrl
        } catch (e: Exception) {
            Log.d(TAG, "Google Books lookup failed: ${e.message}")
            null
        }
    }

    private fun cleanQueryToken(token: String): String {
        return token
            .replace(Regex("[\"'\\[\\]():]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildGoogleBooksQuery(title: String, author: String?): String {
        val cleanTitle = cleanQueryToken(title)
        return if (!author.isNullOrBlank()) {
            val cleanAuthor = cleanQueryToken(author)
            "intitle:$cleanTitle inauthor:$cleanAuthor"
        } else {
            "intitle:$cleanTitle"
        }
    }

    private fun extractGoogleBooksCoverUrl(response: GoogleBooksResponse?): String? {
        val items = response?.items ?: return null
        for (item in items) {
            val imageLinks = item.volumeInfo?.imageLinks ?: continue
            val rawUrl = imageLinks.thumbnail
                ?: imageLinks.smallThumbnail
                ?: imageLinks.medium
                ?: imageLinks.large
                ?: imageLinks.small
                ?: imageLinks.extraLarge

            if (!rawUrl.isNullOrBlank()) {
                var cleaned = rawUrl.trim()
                if (cleaned.startsWith("http://", ignoreCase = true)) {
                    cleaned = "https://" + cleaned.substring(7)
                }
                cleaned = cleaned
                    .replace("&edge=curl", "")
                    .replace("?edge=curl&", "?")
                    .replace("?edge=curl", "")
                    .replace("&amp;", "&")

                return cleaned
            }
        }
        return null
    }

    private fun verifyImageUrl(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:109.0) Gecko/114.0 Firefox/114.0")
                .head()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val contentType = response.header("Content-Type")
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            response.close()

            if (code in 200..299) {
                val isImage = contentType == null || contentType.startsWith("image/", ignoreCase = true)
                val isNotTrivialPlaceholder = contentLength < 0 || contentLength > 200
                if (isImage && isNotTrivialPlaceholder) {
                    return true
                }
            }

            // Fallback GET check in case HEAD was rejected or 405 Method Not Allowed
            val getRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:109.0) Gecko/114.0 Firefox/114.0")
                .header("Range", "bytes=0-1024")
                .get()
                .build()

            val getResponse = okHttpClient.newCall(getRequest).execute()
            val getCode = getResponse.code
            val getContentType = getResponse.header("Content-Type")
            getResponse.close()

            getCode in 200..299 && (getContentType == null || getContentType.startsWith("image/", ignoreCase = true))
        } catch (e: Exception) {
            Log.d(TAG, "Image verification check failed for $url: ${e.message}")
            false
        }
    }
}
