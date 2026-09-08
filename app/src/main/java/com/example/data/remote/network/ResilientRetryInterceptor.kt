package com.example.data.remote.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

/**
 * Fault-tolerant OkHttp Interceptor that automatically retries failed network requests
 * using exponential backoff with random jitter to absorb high-concurrency bursts and
 * transient server throttling (including Apps Script SERVER_BUSY responses).
 *
 * Sequence:
 * Attempt 1 retry: ~1.0s (1000ms ± 250ms)
 * Attempt 2 retry: ~2.0s (2000ms ± 250ms)
 * Attempt 3 retry: ~4.0s (4000ms ± 250ms)
 * Attempt 4 retry: ~8.0s (8000ms ± 250ms)
 */
class ResilientRetryInterceptor(
    val maxRetries: Int = 4,
    private val sleeper: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
    private val jitterGenerator: () -> Long = { Random.nextLong(-250L, 251L) }
) : Interceptor {

    companion object {
        private const val TAG = "ResilientRetry"
        private val RETRY_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var retryAttempt = 0

        while (true) {
            try {
                val response = chain.proceed(request)

                // 1. Check if status code is a retryable server error or rate-limit
                if (RETRY_STATUS_CODES.contains(response.code)) {
                    if (retryAttempt < maxRetries) {
                        retryAttempt++
                        val delayMs = calculateDelayMs(retryAttempt, jitterGenerator())
                        logWarning(
                            "HTTP ${response.code} from ${request.url}. Retrying (attempt $retryAttempt/$maxRetries) in ${delayMs}ms"
                        )
                        response.close()
                        sleepSilently(delayMs)
                        continue
                    }
                    return response
                }

                // 2. Check if HTTP 200 contains Apps Script "error_code": "SERVER_BUSY"
                if (response.code == 200 && isServerBusyResponse(response)) {
                    if (retryAttempt < maxRetries) {
                        retryAttempt++
                        val delayMs = calculateDelayMs(retryAttempt, jitterGenerator())
                        logWarning(
                            "Received SERVER_BUSY from ${request.url}. Retrying (attempt $retryAttempt/$maxRetries) in ${delayMs}ms"
                        )
                        response.close()
                        sleepSilently(delayMs)
                        continue
                    }
                    return response
                }

                // Successful or non-retryable response (e.g. 200 OK, 400 Bad Request, 401 Unauthorized, etc.)
                return response
            } catch (ioe: IOException) {
                if (retryAttempt < maxRetries) {
                    retryAttempt++
                    val delayMs = calculateDelayMs(retryAttempt, jitterGenerator())
                    logWarning(
                        "IOException (${ioe.javaClass.simpleName}: ${ioe.message}) connecting to ${request.url}. " +
                            "Retrying (attempt $retryAttempt/$maxRetries) in ${delayMs}ms"
                    )
                    sleepSilently(delayMs)
                    continue
                } else {
                    logError("Exhausted all $maxRetries retries for ${request.url}", ioe)
                    throw ioe
                }
            }
        }
    }

    private fun logWarning(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: Throwable) {}
    }

    private fun logError(msg: String, throwable: Throwable? = null) {
        try {
            Log.e(TAG, msg, throwable)
        } catch (_: Throwable) {}
    }

    private fun sleepSilently(delayMs: Long) {
        try {
            sleeper(delayMs)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted during exponential backoff retry delay", ie)
        }
    }

    private fun isServerBusyResponse(response: Response): Boolean {
        return try {
            val bodySnippet = response.peekBody(64 * 1024L).string()
            bodySnippet.contains("\"error_code\": \"SERVER_BUSY\"") ||
                bodySnippet.contains("\"error_code\":\"SERVER_BUSY\"") ||
                (bodySnippet.contains("SERVER_BUSY") && bodySnippet.contains("error_code"))
        } catch (_: Exception) {
            false
        }
    }

    fun calculateDelayMs(attempt: Int, jitterMs: Long = 0L): Long {
        val baseMs = when (attempt) {
            1 -> 1000L
            2 -> 2000L
            3 -> 4000L
            4 -> 8000L
            else -> 8000L * (1L shl (attempt - 4).coerceIn(0, 5))
        }
        return (baseMs + jitterMs).coerceAtLeast(100L)
    }
}
