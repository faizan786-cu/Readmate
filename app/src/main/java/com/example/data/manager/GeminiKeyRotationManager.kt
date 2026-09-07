package com.example.data.manager

import android.util.Log
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.KeyStatus
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerateContentResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-Tier Model Fallback & Multi-Key Rotation Engine.
 *
 * Dedicated API wrapper enforcing Flash-Lite dual-tier fallback per key with multi-key failover:
 * - API Key Pool: Array of configured Google AI Studio API Keys [KEY_1, KEY_2, KEY_3, ...]
 * - Strictly Flash-Lite models only:
 *     Tier 1: Primary Flash-Lite ("gemini-3.1-flash-lite")
 *     Tier 2: Secondary Fallback Flash-Lite ("gemini-3.5-flash-lite")
 * - Silent Auto-Rotation Logic:
 *     1. Active key executes Tier 1 Flash-Lite (gemini-3.1-flash-lite).
 *     2. On HTTP 429, timeout, or 500/503/API error -> Silently fallback to Tier 2 (gemini-3.5-flash-lite) on same key.
 *     3. If Tier 2 also fails on that key -> Mark key exhausted, rotate to next key, reset to Tier 1 on the new key.
 *     4. Performs retries with exponential backoff silently in the background.
 */
class GeminiKeyRotationManager(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "GeminiKeyRotation"

        // Strictly Flash-Lite models only
        const val TIER_1_PRIMARY_MODEL = "gemini-3.1-flash-lite"
        const val TIER_2_FALLBACK_MODEL = "gemini-3.5-flash-lite"

        val TIER_1_FLASH_LITE_MODELS = listOf(TIER_1_PRIMARY_MODEL)
        val TIER_2_FLASH_LITE_MODELS = listOf(TIER_2_FALLBACK_MODEL)

        private const val MAX_RETRIES_PER_TIER = 2
        private const val INITIAL_BACKOFF_MS = 500L
        private const val COOLDOWN_DURATION_MS = 60_000L
    }

    // In-memory model cooldown tracking: "keyId_modelId" -> cooldownUntilTimestamp
    private val modelCooldowns = ConcurrentHashMap<String, Long>()

    /**
     * Executes a Flash-Lite API request with multi-tier model fallback and multi-key failover.
     */
    suspend fun executeFlashLiteRequest(
        request: GeminiGenerateContentRequest,
        operationName: String = "Flash-Lite document parsing"
    ): Result<GeminiGenerateContentResponse> = withContext(ioDispatcher) {
        val keys = secureStorage.getApiKeys()
        if (keys.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No Google AI Studio API key configured. Please add an API key in Settings.")
            )
        }

        val currentTime = System.currentTimeMillis()
        // Auto-resume keys whose cooldown expired
        val activeKeys = keys.map { item ->
            if (item.status == KeyStatus.COOLDOWN && currentTime >= item.cooldownUntilTimestamp) {
                item.copy(status = KeyStatus.ACTIVE, cooldownUntilTimestamp = 0L, errorMessage = null)
            } else {
                item
            }
        }

        // Clean up expired in-memory model cooldowns
        modelCooldowns.entries.removeIf { it.value <= currentTime }

        // Sort keys to pick active and least-recently-used first
        val candidateKeys = activeKeys
            .filter { it.isAvailable(currentTime) || it.status == KeyStatus.ACTIVE }
            .sortedBy { it.lastUsedTimestamp }
            .ifEmpty { activeKeys } // Fallback to all keys if all are in cooldown

        var lastException: Throwable? = null

        for ((keyIndex, keyItem) in candidateKeys.withIndex()) {
            val keyString = keyItem.key.trim()
            if (keyString.isEmpty()) continue

            var currentKey = keyItem

            // Attempt Tier 1 Flash-Lite models first
            val tier1Result = tryModelTier(
                models = TIER_1_FLASH_LITE_MODELS,
                keyItem = currentKey,
                request = request,
                tierName = "Tier 1 Primary Flash-Lite"
            )

            if (tier1Result.isSuccess) {
                recordKeySuccess(currentKey)
                return@withContext tier1Result
            }

            Log.w(TAG, "Key #${keyIndex + 1} Tier 1 failed (${tier1Result.exceptionOrNull()?.message}). Silently falling back to Tier 2 on same key...")

            // Tier 1 hit 429/500/503/timeout -> Silently fallback to Tier 2 Flash-Lite on the same key
            val tier2Result = tryModelTier(
                models = TIER_2_FLASH_LITE_MODELS,
                keyItem = currentKey,
                request = request,
                tierName = "Tier 2 Fallback Flash-Lite"
            )

            if (tier2Result.isSuccess) {
                recordKeySuccess(currentKey)
                return@withContext tier2Result
            }

            // Both Tier 1 and Tier 2 failed on this key -> Mark key temporarily exhausted and rotate to next key
            Log.w(TAG, "Key #${keyIndex + 1} Tier 2 also failed. Marking key exhausted and rotating to next key...")
            markKeyExhausted(currentKey, tier2Result.exceptionOrNull()?.message ?: "Flash-Lite tiers exhausted")
            lastException = tier2Result.exceptionOrNull()
        }

        Result.failure(
            lastException ?: Exception("All Flash-Lite models and API keys failed to parse document.")
        )
    }

    private suspend fun tryModelTier(
        models: List<String>,
        keyItem: GeminiApiKeyItem,
        request: GeminiGenerateContentRequest,
        tierName: String
    ): Result<GeminiGenerateContentResponse> {
        val currentTime = System.currentTimeMillis()

        for (modelId in models) {
            val cooldownKey = "${keyItem.id}_$modelId"
            val cooldownUntil = modelCooldowns[cooldownKey] ?: 0L
            if (currentTime < cooldownUntil) {
                continue // Skip this model if currently in cooldown
            }

            var backoff = INITIAL_BACKOFF_MS
            for (attempt in 1..MAX_RETRIES_PER_TIER) {
                try {
                    val response = apiService.generateContent(
                        model = modelId,
                        apiKey = keyItem.key.trim(),
                        request = request
                    )

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            modelCooldowns.remove(cooldownKey)
                            return Result.success(body)
                        }
                    } else {
                        val code = response.code()
                        val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                        if (code == 429 || errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                            // Rate limit on this specific model
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            return Result.failure(Exception("Quota limit on $modelId (HTTP 429)"))
                        }

                        if (code in listOf(500, 503, 504, 408)) {
                            // Server error: backoff and retry once or step down
                            if (attempt < MAX_RETRIES_PER_TIER) {
                                delay(backoff)
                                backoff *= 2
                                continue
                            } else {
                                modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                                return Result.failure(Exception("Server error on $modelId (HTTP $code)"))
                            }
                        }

                        if (code in listOf(401, 403)) {
                            return Result.failure(Exception("Authentication error on key (HTTP $code)"))
                        }

                        // 404 or other client error
                        modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                        return Result.failure(Exception("API error $code on $modelId"))
                    }
                } catch (e: UnknownHostException) {
                    return Result.failure(Exception("No internet connection to Gemini ($modelId)"))
                } catch (e: SocketTimeoutException) {
                    if (attempt < MAX_RETRIES_PER_TIER) {
                        delay(backoff)
                        backoff *= 2
                        continue
                    } else {
                        modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                        return Result.failure(Exception("Timeout connecting to Gemini ($modelId)"))
                    }
                } catch (e: IOException) {
                    if (attempt < MAX_RETRIES_PER_TIER) {
                        delay(backoff)
                        backoff *= 2
                        continue
                    } else {
                        modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                        return Result.failure(Exception("Network error on $modelId: ${e.message}"))
                    }
                } catch (e: Exception) {
                    return Result.failure(e)
                }
            }
        }

        return Result.failure(Exception("$tierName exhausted for this key."))
    }

    private suspend fun recordKeySuccess(keyItem: GeminiApiKeyItem) {
        try {
            val updated = keyItem.copy(
                status = KeyStatus.ACTIVE,
                lastUsedTimestamp = System.currentTimeMillis(),
                successCount = keyItem.successCount + 1,
                successfulRequests = keyItem.successfulRequests + 1,
                errorMessage = null,
                cooldownUntilTimestamp = 0L
            )
            secureStorage.updateApiKey(updated)
        } catch (_: Exception) {}
    }

    private suspend fun markKeyExhausted(keyItem: GeminiApiKeyItem, reason: String) {
        try {
            val updated = keyItem.copy(
                status = KeyStatus.COOLDOWN,
                cooldownUntilTimestamp = System.currentTimeMillis() + COOLDOWN_DURATION_MS,
                errorMessage = reason,
                failureCount = keyItem.failureCount + 1
            )
            secureStorage.updateApiKey(updated)
        } catch (_: Exception) {}
    }
}
