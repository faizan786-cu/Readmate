package com.example.data.remote.books

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenLibraryApiService {

    @GET("search.json")
    suspend fun searchByTitleAndAuthor(
        @Query("title") title: String,
        @Query("author") author: String? = null,
        @Query("limit") limit: Int = 1
    ): Response<OpenLibrarySearchResponse>

    @GET("search.json")
    suspend fun searchByQuery(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): Response<OpenLibrarySearchResponse>

    companion object {
        private const val BASE_URL = "https://openlibrary.org/"

        fun create(okHttpClient: OkHttpClient): OpenLibraryApiService {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(OpenLibraryApiService::class.java)
        }
    }
}
