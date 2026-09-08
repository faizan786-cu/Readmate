package com.example.data.remote.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Factory for resilient OkHttpClient instances pre-configured with fault-tolerant
 * exponential backoff, jitter, redirect handling, and standardized timeouts.
 */
object ResilientNetworkClient {

    /**
     * Standard timeouts:
     * - Connect: 15s
     * - Read: 20s
     * - Write: 15s
     */
    fun createClient(
        maxRetries: Int = 4,
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 20,
        writeTimeoutSec: Long = 15,
        interceptor: ResilientRetryInterceptor = ResilientRetryInterceptor(maxRetries = maxRetries)
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(interceptor)
            .build()
    }
}
