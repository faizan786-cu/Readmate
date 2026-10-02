package com.example.data.manager

import android.util.Log
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.GeminiTaskType
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerateContentResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-Tier Model Fallback & Multi-Key Rotation Engine for Lightweight Utility Tasks.
 *
 * Enforces the Lightweight Utility Tier (Flash-Lite Pipeline) with Model-First Cross-Key Quota Cascade:
 * - Exact model ladder in priority order:
 *     1. gemini-3.5-flash-lite (Primary fast utility)
 *     2. gemini-3.1-flash-lite (Secondary fast utility, active until May 7, 2027)
 *     3. gemini-2.5-flash-lite (Emergency fast fallback, active until its announced sunset date)
 */
class GeminiKeyRotationManager(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiKeyManager: ApiKeyManager = GeminiApiKeyManager(secureStorage, apiService, ioDispatcher)
) {
    companion object {
        private const val TAG = "GeminiKeyRotation"

        // Lightweight Utility Tier (Flash-Lite Pipeline)
        const val TIER_1_PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val TIER_2_FALLBACK_MODEL = "gemini-3.1-flash-lite"
        const val TIER_3_FALLBACK_MODEL = "gemini-2.5-flash-lite"

        val FLASH_LITE_MODELS = listOf(
            TIER_1_PRIMARY_MODEL,
            TIER_2_FALLBACK_MODEL,
            TIER_3_FALLBACK_MODEL
        )
    }

    /**
     * Executes a Flash-Lite API request with Model-First Cross-Key Quota Cascade.
     */
    suspend fun executeFlashLiteRequest(
        request: GeminiGenerateContentRequest,
        operationName: String = "Flash-Lite document parsing"
    ): Result<GeminiGenerateContentResponse> = withContext(ioDispatcher) {
        apiKeyManager.executeWithAutoRotation(
            taskType = GeminiTaskType.PDF_PARSING,
            operationName = operationName
        ) { apiKey, model ->
            apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
        }
    }
}
