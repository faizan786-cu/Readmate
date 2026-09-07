package com.example

import com.example.data.remote.books.BookCoverFetcher
import com.example.data.remote.books.GoogleBooksApiService
import com.example.data.remote.books.GoogleBooksResponse
import com.example.data.remote.books.OpenLibraryApiService
import com.example.data.remote.books.OpenLibraryDoc
import com.example.data.remote.books.OpenLibrarySearchResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class BookCoverFetcherTest {

    @Test
    fun fetchCoverUrl_primaryOpenLibrary_returnsDirectJpgUrlWhenVerified() = runBlocking {
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                OkHttpResponse.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "image/jpeg")
                    .header("Content-Length", "15420")
                    .body("fake-image-bytes".toByteArray().toResponseBody("image/jpeg".toMediaType()))
                    .build()
            }
            .build()

        val mockOpenLibrary = object : OpenLibraryApiService {
            override suspend fun searchByTitleAndAuthor(
                title: String,
                author: String?,
                limit: Int
            ): Response<OpenLibrarySearchResponse> {
                return Response.success(
                    OpenLibrarySearchResponse(
                        numFound = 1,
                        docs = listOf(
                            OpenLibraryDoc(
                                title = "Atomic Habits",
                                coverI = 10524424L
                            )
                        )
                    )
                )
            }

            override suspend fun searchByQuery(query: String, limit: Int): Response<OpenLibrarySearchResponse> {
                return Response.success(OpenLibrarySearchResponse(numFound = 0, docs = emptyList()))
            }
        }

        val mockGoogleBooks = object : GoogleBooksApiService {
            override suspend fun searchVolumes(
                query: String,
                maxResults: Int,
                printType: String
            ): Response<GoogleBooksResponse> {
                return Response.success(GoogleBooksResponse(items = emptyList(), totalItems = 0))
            }
        }

        val fetcher = BookCoverFetcher(
            okHttpClient = testClient,
            openLibraryService = mockOpenLibrary,
            googleBooksService = mockGoogleBooks
        )

        val result = fetcher.fetchCoverUrl("Atomic Habits", "James Clear")
        assertEquals("https://covers.openlibrary.org/b/id/10524424-M.jpg", result)
    }

    @Test
    fun fetchCoverUrl_handlesEmptyOrBlankInputsSilently() = runBlocking {
        val fetcher = BookCoverFetcher()
        assertNull(fetcher.fetchCoverUrl("", ""))
        assertNull(fetcher.fetchCoverUrl("   ", null))
    }

    @Test
    fun fetchCoverUrl_handlesNetworkExceptionsSilently() = runBlocking {
        val mockOpenLibrary = object : OpenLibraryApiService {
            override suspend fun searchByTitleAndAuthor(
                title: String,
                author: String?,
                limit: Int
            ): Response<OpenLibrarySearchResponse> {
                throw RuntimeException("Simulated offline error")
            }

            override suspend fun searchByQuery(query: String, limit: Int): Response<OpenLibrarySearchResponse> {
                throw RuntimeException("Simulated offline error")
            }
        }

        val mockGoogleBooks = object : GoogleBooksApiService {
            override suspend fun searchVolumes(
                query: String,
                maxResults: Int,
                printType: String
            ): Response<GoogleBooksResponse> {
                throw RuntimeException("Simulated offline error")
            }
        }

        val fetcher = BookCoverFetcher(
            okHttpClient = OkHttpClient.Builder().build(),
            openLibraryService = mockOpenLibrary,
            googleBooksService = mockGoogleBooks
        )

        val result = fetcher.fetchCoverUrl("Any Title", "Any Author")
        assertNull(result)
    }
}
