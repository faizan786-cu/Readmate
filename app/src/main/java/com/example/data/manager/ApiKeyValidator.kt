package com.example.data.manager

import com.example.data.model.GeminiModelRegistry
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerationConfig
import com.example.data.remote.gemini.GeminiPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ApiKeyValidationResult {
    object Idle : ApiKeyValidationResult
    object Testing : ApiKeyValidationResult
    data class Validated(
        val maskedKey: String,
        val message: String = "Connection Established • Ready to Deploy"
    ) : ApiKeyValidationResult
    data class Error(
        val message: String,
        val statusCode: Int? = null
    ) : ApiKeyValidationResult
}

class ApiKeyValidator(
    private val apiService: GeminiApiService = GeminiApiService.create()
) {
    suspend fun validateKey(
        rawKey: String,
        timeoutMs: Long = 4000L
    ): ApiKeyValidationResult = withContext(Dispatchers.IO) {
        val trimmedKey = rawKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ApiKeyValidationResult.Error("API key cannot be empty.")
        }
        if (trimmedKey.length < 20) {
            return@withContext ApiKeyValidationResult.Error("Invalid key format. Gemini API keys start with AIzaSy...")
        }

        val maskedKey = if (trimmedKey.length > 8) {
            "${trimmedKey.take(4)}••••••••${trimmedKey.takeLast(4)}"
        } else {
            "••••••••"
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                val pingRequest = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = "ping"))
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = 1,
                        temperature = 0.0f
                    )
                )

                // Ping the fast Flash-Lite model
                val model = GeminiModelRegistry.DEFAULT_TRANSLATION_MODEL
                val response = apiService.generateContent(
                    model = model,
                    apiKey = trimmedKey,
                    request = pingRequest
                )

                if (response.isSuccessful) {
                    ApiKeyValidationResult.Validated(maskedKey = maskedKey)
                } else {
                    val code = response.code()
                    val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
                    val message = when (code) {
                        400, 401 -> "Invalid Key or Quota Exceeded. Verify key."
                        403 -> "Permission Denied (HTTP 403). Check Google AI Studio enablement."
                        429 -> "Quota Exceeded or Rate Limited (HTTP 429). Verify key tier."
                        500, 503 -> "Gemini endpoint overloaded (HTTP $code). Try again."
                        else -> "Validation failed (HTTP $code). Verify key."
                    }
                    ApiKeyValidationResult.Error(message, statusCode = code)
                }
            }

            result ?: ApiKeyValidationResult.Error("Connection timed out (4s). Check your internet connection.", statusCode = 408)
        } catch (e: UnknownHostException) {
            ApiKeyValidationResult.Error("Could not reach Gemini endpoint. Check internet connection.")
        } catch (e: SocketTimeoutException) {
            ApiKeyValidationResult.Error("Connection timed out. Verify your internet connection.", statusCode = 408)
        } catch (e: IOException) {
            ApiKeyValidationResult.Error("Network error contacting Gemini. Check internet connection.")
        } catch (e: Exception) {
            ApiKeyValidationResult.Error("Validation error: ${e.message ?: "Unknown error"}")
        }
    }
}
