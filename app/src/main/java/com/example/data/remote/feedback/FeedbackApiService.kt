package com.example.data.remote.feedback

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class FeedbackReport(
    val action: String = "SUBMIT_REPORT",
    val email: String,
    val reportType: String,
    val message: String,
    val appVersion: String,
    val deviceModel: String
)

class FeedbackApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "FeedbackApiService"
        private const val APPS_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbzNEcdc_T9tgAa5d0XnMNGmul26cEjs4kGB4ba2oUZ4kZ4d5tfmYXhDC_Svd1WXCrhQCg/exec"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun submitReport(
        email: String,
        reportType: String,
        message: String,
        appVersion: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val payload = JSONObject().apply {
                put("action", "SUBMIT_REPORT")
                put("email", email.ifBlank { "guest" })
                put("report_type", reportType)
                put("message", message)
                put("app_version", appVersion)
                put("device_model", deviceModel)
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
                Log.e(TAG, "Feedback submission HTTP error: ${response.code} $responseBody")
                return@withContext Result.failure(Exception("Server returned status ${response.code}"))
            }

            // Google Apps Script Web Apps usually return JSON: {"success": true, ...} or text
            var isSuccess = true
            try {
                if (responseBody.isNotBlank() && (responseBody.startsWith("{") || responseBody.startsWith("["))) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.has("success")) {
                        isSuccess = jsonResponse.optBoolean("success", true)
                    } else if (jsonResponse.has("status")) {
                        isSuccess = jsonResponse.optString("status").equals("success", ignoreCase = true)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to parse JSON response, but HTTP was ${response.code}: $responseBody")
            }

            if (isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Submission rejected by server: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during feedback dispatch", e)
            Result.failure(e)
        }
    }
}
