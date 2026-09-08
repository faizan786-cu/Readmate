package com.example.data.remote.auth

import com.example.data.remote.network.ResilientNetworkClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API interface for ReadMate Authentication Engine.
 * Supports automated redirect handling required for Google Apps Script Web App endpoints.
 */
interface AuthApiService {

    @POST("exec")
    suspend fun executeAuthAction(@Body payload: AuthRequestPayload): AuthResponsePayload

    companion object {
        const val BASE_URL = "https://script.google.com/macros/s/AKfycbzNEcdc_T9tgAa5d0XnMNGmul26cEjs4kGB4ba2oUZ4kZ4d5tfmYXhDC_Svd1WXCrhQCg/"

        fun createOkHttpClient(): OkHttpClient {
            return ResilientNetworkClient.createClient()
        }

        fun create(
            baseUrl: String = BASE_URL,
            okHttpClient: OkHttpClient = createOkHttpClient()
        ): AuthApiService {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(AuthApiService::class.java)
        }
    }
}
