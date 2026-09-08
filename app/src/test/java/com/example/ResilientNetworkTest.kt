package com.example

import com.example.data.remote.network.ResilientNetworkClient
import com.example.data.remote.network.ResilientRetryInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ResilientNetworkTest {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Test
    fun testDelayCalculation_followsExponentialSequence() {
        val interceptor = ResilientRetryInterceptor(maxRetries = 4)

        // Jitter = 0 for deterministic base check
        assertEquals(1000L, interceptor.calculateDelayMs(1, 0L))
        assertEquals(2000L, interceptor.calculateDelayMs(2, 0L))
        assertEquals(4000L, interceptor.calculateDelayMs(3, 0L))
        assertEquals(8000L, interceptor.calculateDelayMs(4, 0L))

        // Check jitter clamping (never below 100ms)
        val negativeJitterDelay = interceptor.calculateDelayMs(1, -950L)
        assertTrue(negativeJitterDelay >= 100L)
    }

    @Test
    fun testRetriesOnIOException_andSucceeds() {
        var attempts = 0
        val recordedDelays = mutableListOf<Long>()

        val interceptor = ResilientRetryInterceptor(
            maxRetries = 4,
            sleeper = { recordedDelays.add(it) },
            jitterGenerator = { 0L }
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(Interceptor { chain ->
                attempts++
                if (attempts < 3) {
                    throw IOException("Simulated network timeout on attempt $attempts")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"status\":\"success\"}".toResponseBody(jsonMediaType))
                    .build()
            })
            .build()

        val request = Request.Builder().url("https://example.com/api").build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(3, attempts)
        assertEquals(2, recordedDelays.size) // 2 retries
        assertEquals(1000L, recordedDelays[0])
        assertEquals(2000L, recordedDelays[1])
    }

    @Test
    fun testRetriesOnHttp503_andStopsAtMaxRetries() {
        var attempts = 0
        val recordedDelays = mutableListOf<Long>()

        val interceptor = ResilientRetryInterceptor(
            maxRetries = 4,
            sleeper = { recordedDelays.add(it) },
            jitterGenerator = { 0L }
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(Interceptor { chain ->
                attempts++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("{\"error\":\"Over capacity\"}".toResponseBody(jsonMediaType))
                    .build()
            })
            .build()

        val request = Request.Builder().url("https://example.com/api").build()
        val response = client.newCall(request).execute()

        assertEquals(503, response.code)
        // 1 initial attempt + 4 retries = 5 attempts total
        assertEquals(5, attempts)
        assertEquals(4, recordedDelays.size)
        assertEquals(1000L, recordedDelays[0])
        assertEquals(2000L, recordedDelays[1])
        assertEquals(4000L, recordedDelays[2])
        assertEquals(8000L, recordedDelays[3])
    }

    @Test
    fun testRetriesOnHttp200ServerBusy_andSucceedsWhenRecovered() {
        var attempts = 0
        val recordedDelays = mutableListOf<Long>()

        val interceptor = ResilientRetryInterceptor(
            maxRetries = 4,
            sleeper = { recordedDelays.add(it) },
            jitterGenerator = { 0L }
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(Interceptor { chain ->
                attempts++
                if (attempts == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"status\": \"error\", \"error_code\": \"SERVER_BUSY\"}".toResponseBody(jsonMediaType))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"status\": \"success\", \"data\": \"ok\"}".toResponseBody(jsonMediaType))
                        .build()
                }
            })
            .build()

        val request = Request.Builder().url("https://example.com/api").build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, attempts)
        assertEquals(1, recordedDelays.size)
        assertEquals(1000L, recordedDelays[0])
        assertTrue(response.body!!.string().contains("\"data\": \"ok\""))
    }

    @Test
    fun testNoRetryOnClean200OrClientError400() {
        var attempts = 0

        val interceptor = ResilientRetryInterceptor(maxRetries = 4)
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(Interceptor { chain ->
                attempts++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("{\"error\": \"invalid input\"}".toResponseBody(jsonMediaType))
                    .build()
            })
            .build()

        val request = Request.Builder().url("https://example.com/api").build()
        val response = client.newCall(request).execute()

        assertEquals(400, response.code)
        assertEquals(1, attempts) // No retries for 400 Bad Request
    }

    @Test
    fun testResilientNetworkClient_configuredTimeouts() {
        val client = ResilientNetworkClient.createClient(
            maxRetries = 4,
            connectTimeoutSec = 15,
            readTimeoutSec = 20,
            writeTimeoutSec = 15
        )

        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(20000, client.readTimeoutMillis)
        assertEquals(15000, client.writeTimeoutMillis)
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertTrue(client.interceptors.any { it is ResilientRetryInterceptor })
    }
}
