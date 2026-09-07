package com.example.data.manager

import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.GeminiConnectionState
import com.example.data.model.GeminiModelInfo
import com.example.data.model.GeminiModelRegistry
import com.example.data.model.GeminiTaskType
import com.example.data.model.KeyStatus
import com.example.data.model.ModelQuotaState
import com.example.data.model.ModelTestResult
import com.example.data.model.TestConnectionResult
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiPart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

interface ApiKeyManager {
    val connectionState: StateFlow<GeminiConnectionState>

    fun getApiKeys(): List<GeminiApiKeyItem>
    fun hasApiKey(): Boolean
    fun getMaskedApiKey(): String?
    fun getApiKey(): String?

    suspend fun addApiKey(key: String, label: String = ""): GeminiApiKeyItem?
    suspend fun updateApiKey(item: GeminiApiKeyItem): Boolean
    suspend fun removeApiKey(id: String): Boolean
    suspend fun clearAllApiKeys(): Boolean
    suspend fun resetKeyStatus(id: String): Boolean
    suspend fun resetAllCooldowns(): Boolean
    suspend fun bulkImportKeys(rawInput: String): Pair<Int, Int>

    fun isModelInCooldown(keyId: String, modelId: String): Boolean
    fun getModelCooldownRemainingSeconds(keyId: String, modelId: String): Long

    suspend fun testConnection(apiKey: String, model: String = GeminiModelRegistry.DEFAULT_MODEL): TestConnectionResult
    suspend fun testSingleKey(keyId: String): TestConnectionResult
    suspend fun testAllModelsForApiKey(apiKey: String): List<ModelTestResult>
    suspend fun testAllModelsForSingleKey(keyId: String): List<ModelTestResult>

    suspend fun <T> executeWithAutoRotation(
        taskType: GeminiTaskType,
        operationName: String = "Gemini request",
        block: suspend (apiKey: String, model: String) -> Response<T>
    ): Result<T>

    suspend fun <T> executeWithAutoRotation(
        operationName: String = "Gemini request",
        block: suspend (apiKey: String, model: String) -> Response<T>
    ): Result<T> = executeWithAutoRotation(GeminiTaskType.PASSAGE_ANALYSIS, operationName, block)

    suspend fun <T> executeWithAutoRotation(
        operationName: String = "Gemini request",
        block: suspend (apiKey: String) -> Response<T>
    ): Result<T> = executeWithAutoRotation(GeminiTaskType.PASSAGE_ANALYSIS, operationName) { apiKey, _ -> block(apiKey) }

    fun refreshState()
}

class GeminiApiKeyManager(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApiKeyManager {

    // Thread-safe cache of exhausted model cooldowns: "keyId_modelId" -> cooldownUntilTimestamp
    private val modelCooldowns = ConcurrentHashMap<String, Long>()

    private val _connectionState = MutableStateFlow<GeminiConnectionState>(
        determineInitialState()
    )
    override val connectionState: StateFlow<GeminiConnectionState> = _connectionState.asStateFlow()

    private fun determineInitialState(): GeminiConnectionState {
        val keys = secureStorage.getApiKeys()
        return if (keys.isNotEmpty()) {
            val total = keys.size
            val active = keys.count { it.status == KeyStatus.ACTIVE || (it.status == KeyStatus.COOLDOWN && it.remainingCooldownSeconds() == 0L) }
            val cooldown = keys.count { it.status == KeyStatus.COOLDOWN && it.remainingCooldownSeconds() > 0L }
            val error = keys.count { it.status in listOf(KeyStatus.INVALID, KeyStatus.PERMISSION_ERROR, KeyStatus.ERROR, KeyStatus.TEST_FAILED) }
            val masked = secureStorage.getMaskedApiKey() ?: "••••••••"
            GeminiConnectionState.Connected(
                maskedKey = masked,
                totalKeyCount = total,
                activeKeyCount = active,
                cooldownKeyCount = cooldown,
                errorKeyCount = error,
                keys = keys
            )
        } else {
            GeminiConnectionState.NotConnected
        }
    }

    override fun hasApiKey(): Boolean = secureStorage.hasApiKey()

    override fun getMaskedApiKey(): String? = secureStorage.getMaskedApiKey()

    override fun getApiKey(): String? = secureStorage.getApiKey()

    override fun getApiKeys(): List<GeminiApiKeyItem> = secureStorage.getApiKeys()

    override fun isModelInCooldown(keyId: String, modelId: String): Boolean {
        val expiry = modelCooldowns["${keyId}_$modelId"] ?: return false
        return System.currentTimeMillis() < expiry
    }

    override fun getModelCooldownRemainingSeconds(keyId: String, modelId: String): Long {
        val expiry = modelCooldowns["${keyId}_$modelId"] ?: return 0L
        val now = System.currentTimeMillis()
        return if (expiry > now) ((expiry - now + 999) / 1000).coerceAtLeast(1L) else 0L
    }

    override suspend fun addApiKey(key: String, label: String): GeminiApiKeyItem? = withContext(ioDispatcher) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return@withContext null
        val item = secureStorage.addApiKey(trimmed, label)
        refreshState()
        item
    }

    override suspend fun updateApiKey(item: GeminiApiKeyItem): Boolean = withContext(ioDispatcher) {
        val updated = secureStorage.updateApiKey(item)
        if (updated) refreshState()
        updated
    }

    override suspend fun removeApiKey(id: String): Boolean = withContext(ioDispatcher) {
        // Clear in-memory model cooldowns for this key
        modelCooldowns.keys.filter { it.startsWith("${id}_") }.forEach { modelCooldowns.remove(it) }
        val removed = secureStorage.removeApiKey(id)
        if (removed) refreshState()
        removed
    }

    override suspend fun resetKeyStatus(id: String): Boolean = withContext(ioDispatcher) {
        // Clear in-memory model cooldowns for this key
        modelCooldowns.keys.filter { it.startsWith("${id}_") }.forEach { modelCooldowns.remove(it) }
        val keys = secureStorage.getApiKeys()
        val target = keys.firstOrNull { it.id == id } ?: return@withContext false
        val resetItem = target.copy(
            status = KeyStatus.ACTIVE,
            cooldownUntilTimestamp = 0L,
            errorMessage = null
        )
        val updated = secureStorage.updateApiKey(resetItem)
        if (updated) refreshState()
        updated
    }

    override suspend fun resetAllCooldowns(): Boolean = withContext(ioDispatcher) {
        modelCooldowns.clear()
        val keys = secureStorage.getApiKeys()
        val updatedList = keys.map { item ->
            if (item.status == KeyStatus.COOLDOWN || item.cooldownUntilTimestamp > 0L) {
                item.copy(status = KeyStatus.ACTIVE, cooldownUntilTimestamp = 0L, errorMessage = null)
            } else {
                item
            }
        }
        val saved = secureStorage.saveApiKeys(updatedList)
        if (saved) refreshState()
        saved
    }

    override suspend fun clearAllApiKeys(): Boolean = withContext(ioDispatcher) {
        modelCooldowns.clear()
        val deleted = secureStorage.clearAllApiKeys()
        if (deleted) {
            _connectionState.value = GeminiConnectionState.NotConnected
            true
        } else {
            false
        }
    }

    override suspend fun bulkImportKeys(rawInput: String): Pair<Int, Int> = withContext(ioDispatcher) {
        val tokens = rawInput.split('\n', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val existingKeys = secureStorage.getApiKeys().map { it.key.trim() }.toSet()
        var addedCount = 0
        var skippedCount = 0

        for (token in tokens) {
            if (GeminiApiKeyItem.isValidKeyFormat(token)) {
                if (token in existingKeys) {
                    skippedCount++
                } else {
                    val item = secureStorage.addApiKey(token)
                    if (item != null) addedCount++ else skippedCount++
                }
            } else {
                skippedCount++
            }
        }
        if (addedCount > 0) refreshState()
        Pair(addedCount, skippedCount)
    }

    override suspend fun testConnection(apiKey: String, model: String): TestConnectionResult = withContext(ioDispatcher) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext TestConnectionResult.Failure("Enter your Gemini API key first.")
        }
        if (!GeminiApiKeyItem.isValidKeyFormat(trimmedKey)) {
            return@withContext TestConnectionResult.Failure("Please enter a valid Gemini API key.")
        }

        try {
            val minimalRequest = GeminiGenerateContentRequest.forText("ping")

            val response = apiService.generateContent(
                model = model,
                apiKey = trimmedKey,
                request = minimalRequest
            )

            if (response.isSuccessful) {
                TestConnectionResult.Success
            } else {
                val code = response.code()
                val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
                val message = extractGeminiErrorMessage(code, errorBody)
                TestConnectionResult.Failure(message, statusCode = code)
            }
        } catch (e: UnknownHostException) {
            TestConnectionResult.Failure("Could not connect to Gemini. Check your internet connection and try again.")
        } catch (e: SocketTimeoutException) {
            TestConnectionResult.Failure("Connection timed out. Check your internet connection and try again.", statusCode = 408)
        } catch (e: IOException) {
            TestConnectionResult.Failure("Could not connect to Gemini. Check your internet connection and try again.")
        } catch (e: Exception) {
            TestConnectionResult.Failure("Gemini returned an error: ${e.message}")
        }
    }

    override suspend fun testSingleKey(keyId: String): TestConnectionResult = withContext(ioDispatcher) {
        val keys = secureStorage.getApiKeys()
        val target = keys.firstOrNull { it.id == keyId }
            ?: return@withContext TestConnectionResult.Failure("Key not found.")

        // Run priority model test (using default translation model for fast ping)
        val testResult = testConnection(target.key, GeminiModelRegistry.DEFAULT_TRANSLATION_MODEL)
        val updatedItem = when (testResult) {
            is TestConnectionResult.Success -> {
                // Clear any in-memory model cooldowns on this key
                modelCooldowns.keys.filter { it.startsWith("${target.id}_") }.forEach { modelCooldowns.remove(it) }
                target.copy(
                    status = KeyStatus.ACTIVE,
                    lastUsedTimestamp = System.currentTimeMillis(),
                    errorMessage = null,
                    cooldownUntilTimestamp = 0L,
                    successCount = target.successCount + 1,
                    successfulRequests = target.successfulRequests + 1
                )
            }
            is TestConnectionResult.Failure -> {
                val code = testResult.statusCode
                when (code) {
                    429 -> {
                        target.copy(
                            status = KeyStatus.COOLDOWN,
                            cooldownUntilTimestamp = System.currentTimeMillis() + 60_000L,
                            errorMessage = "Quota / Rate limit reached (HTTP 429)",
                            rateLimitErrors429 = target.rateLimitErrors429 + 1,
                            failureCount = target.failureCount + 1
                        )
                    }
                    401 -> {
                        target.copy(
                            status = KeyStatus.INVALID,
                            errorMessage = "Invalid or expired API key (HTTP 401)",
                            authenticationErrors401 = target.authenticationErrors401 + 1,
                            failureCount = target.failureCount + 1
                        )
                    }
                    403 -> {
                        target.copy(
                            status = KeyStatus.PERMISSION_ERROR,
                            errorMessage = testResult.message,
                            permissionErrors403 = target.permissionErrors403 + 1,
                            failureCount = target.failureCount + 1
                        )
                    }
                    503, 500, 504, 408 -> {
                        target.copy(
                            status = KeyStatus.ACTIVE,
                            errorMessage = testResult.message,
                            serviceUnavailableErrors503 = target.serviceUnavailableErrors503 + 1
                        )
                    }
                    404 -> {
                        target.copy(
                            status = KeyStatus.ACTIVE,
                            errorMessage = testResult.message,
                            configurationErrors404 = target.configurationErrors404 + 1
                        )
                    }
                    400 -> {
                        if (testResult.message.contains("invalid", ignoreCase = true) || testResult.message.contains("key", ignoreCase = true)) {
                            target.copy(
                                status = KeyStatus.INVALID,
                                errorMessage = testResult.message,
                                authenticationErrors401 = target.authenticationErrors401 + 1,
                                failureCount = target.failureCount + 1
                            )
                        } else {
                            target.copy(
                                status = KeyStatus.ACTIVE,
                                errorMessage = testResult.message,
                                badRequestErrors400 = target.badRequestErrors400 + 1
                            )
                        }
                    }
                    else -> {
                        target.copy(
                            errorMessage = testResult.message
                        )
                    }
                }
            }
        }
        secureStorage.updateApiKey(updatedItem)
        refreshState()
        testResult
    }

    override suspend fun testAllModelsForApiKey(apiKey: String): List<ModelTestResult> = withContext(ioDispatcher) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty() || !GeminiApiKeyItem.isValidKeyFormat(trimmedKey)) {
            return@withContext GeminiModelRegistry.ALL_MODELS.map { model ->
                ModelTestResult(
                    modelId = model.modelId,
                    displayName = model.displayName,
                    priority = model.priority,
                    rpd = model.rpd,
                    rpm = model.rpm,
                    taskType = model.taskType,
                    httpStatusCode = 400,
                    httpStatusText = "Invalid Key",
                    quotaState = ModelQuotaState.ERROR,
                    latencyMs = 0L,
                    errorMessage = "Invalid Gemini API key format."
                )
            }
        }

        val minimalRequest = GeminiGenerateContentRequest.forText("ping")

        val results = mutableListOf<ModelTestResult>()

        for (model in GeminiModelRegistry.ALL_MODELS) {
            val startTime = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(
                    model = model.modelId,
                    apiKey = trimmedKey,
                    request = minimalRequest
                )
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val code = response.code()
                if (response.isSuccessful) {
                    results.add(
                        ModelTestResult(
                            modelId = model.modelId,
                            displayName = model.displayName,
                            priority = model.priority,
                            rpd = model.rpd,
                            rpm = model.rpm,
                            taskType = model.taskType,
                            httpStatusCode = 200,
                            httpStatusText = "200 OK",
                            quotaState = ModelQuotaState.ACTIVE,
                            latencyMs = latency,
                            errorMessage = null
                        )
                    )
                } else {
                    val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
                    val message = extractGeminiErrorMessage(code, errorBody)

                    val quotaState = when {
                        code == 429 -> {
                            if (errorBody.contains("per day", ignoreCase = true) ||
                                errorBody.contains("Daily", ignoreCase = true) ||
                                errorBody.contains("RPD", ignoreCase = true) ||
                                errorBody.contains("Quota exceeded", ignoreCase = true)
                            ) {
                                ModelQuotaState.DAILY_LIMIT_EXHAUSTED
                            } else if (errorBody.contains("per minute", ignoreCase = true) ||
                                errorBody.contains("RPM", ignoreCase = true) ||
                                errorBody.contains("rate limit", ignoreCase = true)
                            ) {
                                ModelQuotaState.RATE_LIMITED
                            } else {
                                ModelQuotaState.DAILY_LIMIT_EXHAUSTED
                            }
                        }
                        code in listOf(401, 403) -> ModelQuotaState.ERROR
                        code == 404 -> ModelQuotaState.UNAVAILABLE
                        else -> ModelQuotaState.ERROR
                    }

                    val statusText = when (code) {
                        429 -> "429 Quota Exceeded"
                        401 -> "401 Unauthorized"
                        403 -> "403 Forbidden"
                        404 -> "404 Not Found"
                        500, 503 -> "503 Unavailable"
                        else -> "HTTP $code Error"
                    }

                    results.add(
                        ModelTestResult(
                            modelId = model.modelId,
                            displayName = model.displayName,
                            priority = model.priority,
                            rpd = model.rpd,
                            rpm = model.rpm,
                            taskType = model.taskType,
                            httpStatusCode = code,
                            httpStatusText = statusText,
                            quotaState = quotaState,
                            latencyMs = latency,
                            errorMessage = message
                        )
                    )
                }
            } catch (e: Exception) {
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                results.add(
                    ModelTestResult(
                        modelId = model.modelId,
                        displayName = model.displayName,
                        priority = model.priority,
                        rpd = model.rpd,
                        rpm = model.rpm,
                        taskType = model.taskType,
                        httpStatusCode = -1,
                        httpStatusText = "Network Error",
                        quotaState = ModelQuotaState.ERROR,
                        latencyMs = latency,
                        errorMessage = e.message ?: "Connection failed"
                    )
                )
            }
        }

        results
    }

    override suspend fun testAllModelsForSingleKey(keyId: String): List<ModelTestResult> = withContext(ioDispatcher) {
        val keys = secureStorage.getApiKeys()
        val target = keys.firstOrNull { it.id == keyId }
            ?: return@withContext emptyList()

        val results = testAllModelsForApiKey(target.key).map { res ->
            val isCooldown = isModelInCooldown(target.id, res.modelId)
            val remainingSec = getModelCooldownRemainingSeconds(target.id, res.modelId)
            res.copy(
                isCooldown = isCooldown,
                cooldownRemainingSec = remainingSec,
                quotaState = if (isCooldown && res.quotaState == ModelQuotaState.ACTIVE) ModelQuotaState.RATE_LIMITED else res.quotaState
            )
        }

        val anyActive = results.any { it.quotaState == ModelQuotaState.ACTIVE && !it.isCooldown }
        val all429 = results.all { it.httpStatusCode == 429 || it.isCooldown }
        val any401 = results.any { it.httpStatusCode == 401 }
        val any403 = results.any { it.httpStatusCode == 403 }

        val updatedItem = when {
            anyActive -> {
                target.copy(
                    status = KeyStatus.ACTIVE,
                    lastUsedTimestamp = System.currentTimeMillis(),
                    cooldownUntilTimestamp = 0L,
                    errorMessage = null,
                    successfulRequests = target.successfulRequests + 1
                )
            }
            all429 -> {
                target.copy(
                    status = KeyStatus.COOLDOWN,
                    cooldownUntilTimestamp = System.currentTimeMillis() + 60_000L,
                    errorMessage = "All models quota reached (HTTP 429)",
                    rateLimitErrors429 = target.rateLimitErrors429 + 1
                )
            }
            any401 -> {
                target.copy(
                    status = KeyStatus.INVALID,
                    errorMessage = "Invalid or expired API key (HTTP 401)",
                    authenticationErrors401 = target.authenticationErrors401 + 1
                )
            }
            any403 -> {
                target.copy(
                    status = KeyStatus.PERMISSION_ERROR,
                    errorMessage = "Permission issue (HTTP 403)",
                    permissionErrors403 = target.permissionErrors403 + 1
                )
            }
            else -> target
        }
        secureStorage.updateApiKey(updatedItem)
        refreshState()

        results
    }

    /**
     * Executes a Gemini API call using Task-Specific Model Selection & Hierarchical Two-Tier Fallback:
     *
     * 1. TASK-SPECIFIC MODEL POOLS:
     *    - WORD_TRANSLATION & VISION_EXTRACTION:
     *        Priority 1: gemini-3.1-flash-lite
     *        Priority 2: gemini-3.5-flash-lite
     *    - PASSAGE_ANALYSIS:
     *        Priority 1: gemini-3.6-flash
     *        Priority 2: gemini-3.5-flash
     *        Priority 3: gemini-3.7-flash
     *
     * 2. TIER 1 (Model-First on Active Key):
     *    - Iterates through the task's model priority pool.
     *    - If HTTP 429 occurs on a model, cooldown ONLY that specific (Key, Model) pair.
     *    - Steps down to the NEXT model in the task's pool on the SAME key without switching keys immediately.
     *
     * 3. TIER 2 (Key Rotation):
     *    - Only after ALL models in the task's pool are exhausted on the current key does it rotate
     *      to the next eligible API key and reset to Priority 1 of that task's pool.
     *
     * 4. STABILITY:
     *    - HTTP 503 / 500 / 504 / 408: Exponential backoff retries on same model & key without quota cooldown.
     *    - HTTP 401: Key auth problem -> Mark INVALID, rotate to next key.
     *    - HTTP 403: Permission issue -> Mark PERMISSION_ERROR, rotate to next key.
     *    - HTTP 404: Endpoint/model problem -> Skip model, step down without disabling key.
     */
    override suspend fun <T> executeWithAutoRotation(
        taskType: GeminiTaskType,
        operationName: String,
        block: suspend (apiKey: String, model: String) -> Response<T>
    ): Result<T> = withContext(ioDispatcher) {
        val currentTime = System.currentTimeMillis()
        val allKeys = secureStorage.getApiKeys()

        if (allKeys.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key connected. Please configure your API key in Settings.")
            )
        }

        // Auto-resume any keys whose cooldown expired
        var cooldownUpdated = false
        val refreshedKeys = allKeys.map { keyItem ->
            if (keyItem.status == KeyStatus.COOLDOWN && currentTime >= keyItem.cooldownUntilTimestamp) {
                cooldownUpdated = true
                keyItem.copy(status = KeyStatus.ACTIVE, errorMessage = null, cooldownUntilTimestamp = 0L)
            } else {
                keyItem
            }
        }
        if (cooldownUpdated) {
            secureStorage.saveApiKeys(refreshedKeys)
            refreshState()
        }

        // Clean up expired in-memory model cooldowns
        modelCooldowns.entries.removeIf { it.value <= currentTime }

        // Available keys: ACTIVE or expired cooldown
        val availableKeys = refreshedKeys.filter { it.isAvailable(currentTime) }

        if (availableKeys.isEmpty()) {
            val cooldownKeys = refreshedKeys.filter { it.status == KeyStatus.COOLDOWN && it.remainingCooldownSeconds(currentTime) > 0 }
            val invalidKeys = refreshedKeys.filter { it.status in listOf(KeyStatus.INVALID, KeyStatus.PERMISSION_ERROR, KeyStatus.ERROR) }

            val errorDetail = when {
                cooldownKeys.isNotEmpty() -> {
                    val shortestSec = cooldownKeys.minOf { it.remainingCooldownSeconds(currentTime) }.coerceAtLeast(1)
                    "AI service temporarily unavailable. Rate limits reached across all keys. Auto-resuming in $shortestSec seconds."
                }
                invalidKeys.isNotEmpty() -> {
                    "AI service temporarily unavailable. Configured API keys encountered authentication or permission errors. Please update your keys in Settings."
                }
                else -> {
                    "AI service temporarily unavailable. No active keys available. Please check your API keys in Settings."
                }
            }
            return@withContext Result.failure(Exception(errorDetail))
        }

        // Round-Robin key selection: prioritize healthy keys sorted by oldest lastUsedTimestamp
        val candidateKeys = availableKeys.sortedBy { it.lastUsedTimestamp }

        // Retrieve task-specific model priority pool
        val taskModelPool: List<GeminiModelInfo> = GeminiModelRegistry.getModelsForTask(taskType)

        var lastException: Throwable? = null
        var keyIndex = 0

        for (keyItem in candidateKeys) {
            keyIndex++
            val currentFreshKeys = secureStorage.getApiKeys()
            var currentKey = currentFreshKeys.firstOrNull { it.id == keyItem.id } ?: keyItem
            if (!currentKey.isAvailable(System.currentTimeMillis())) {
                continue
            }

            var hadRateLimitOrCooldown = false
            var hadOnly503 = false
            var allModelsExhaustedForThisKey = true
            var keyShouldBeSkipped = false

            // Level 1: Iterate through the Task-Specific Model Priority Pool (Instant Model Shift)
            for (modelInfo in taskModelPool) {
                val modelId = modelInfo.modelId
                val cooldownKey = "${currentKey.id}_$modelId"
                val modelCooldownUntil = modelCooldowns[cooldownKey] ?: 0L

                // If this specific model on this specific key is in cooldown, step down to next model
                if (System.currentTimeMillis() < modelCooldownUntil) {
                    hadRateLimitOrCooldown = true
                    continue
                }

                try {
                    val response = block(currentKey.key, modelId)

                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            // Mark model cooldown cleared & key healthy
                            modelCooldowns.remove(cooldownKey)
                            val updatedKey = currentKey.copy(
                                status = KeyStatus.ACTIVE,
                                lastUsedTimestamp = System.currentTimeMillis(),
                                successCount = currentKey.successCount + 1,
                                successfulRequests = currentKey.successfulRequests + 1,
                                errorMessage = null,
                                cooldownUntilTimestamp = 0L
                            )
                            currentKey = updatedKey
                            secureStorage.updateApiKey(updatedKey)
                            refreshState()
                            return@withContext Result.success(body)
                        } else {
                            lastException = Exception("Gemini returned an empty response.")
                            // Continue to next model
                        }
                    } else {
                        val code = response.code()
                        val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                        // 1. HTTP 503 / 500 / 504 / 408: Transient Service / Server Overloaded
                        if (code in listOf(503, 500, 504, 408)) {
                            val updatedMetrics = if (code == 503) {
                                currentKey.copy(serviceUnavailableErrors503 = currentKey.serviceUnavailableErrors503 + 1)
                            } else {
                                currentKey
                            }
                            currentKey = updatedMetrics
                            secureStorage.updateApiKey(updatedMetrics)

                            // Cooldown this specific model on this key for 60s
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                            hadOnly503 = true
                            lastException = Exception("Gemini service temporarily overloaded on $modelId (HTTP $code).")
                            // Level 1: Instant Model Shift to next Flash model on same key without delay
                            continue
                        }

                        // 2. HTTP 429: Rate Limit / Quota Reached on this specific model + key
                        val isRateLimitOrQuota = code == 429 ||
                            errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                            errorBody.contains("quota", ignoreCase = true) ||
                            errorBody.contains("rate limit", ignoreCase = true)

                        if (isRateLimitOrQuota) {
                            // Cache exhausted (key, model) combination with cooldown timestamp (60 seconds)
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                            val updatedMetrics = currentKey.copy(
                                rateLimitErrors429 = currentKey.rateLimitErrors429 + 1,
                                failureCount = currentKey.failureCount + 1
                            )
                            currentKey = updatedMetrics
                            secureStorage.updateApiKey(updatedMetrics)
                            refreshState()

                            hadRateLimitOrCooldown = true
                            hadOnly503 = false
                            lastException = Exception("Quota / Rate limit reached on $modelId (HTTP 429).")
                            // Level 1: Instant Model Shift to NEXT Flash model on the SAME key without delay
                            continue
                        }

                        // 3. HTTP 401: Invalid / Expired Authentication
                        val isAuthInvalid = code == 401 || (code == 400 && (
                            errorBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                            errorBody.contains("API key not valid", ignoreCase = true) ||
                            errorBody.contains("UNAUTHENTICATED", ignoreCase = true)
                        ))

                        if (isAuthInvalid) {
                            val updatedKey = currentKey.copy(
                                status = KeyStatus.INVALID,
                                authenticationErrors401 = currentKey.authenticationErrors401 + 1,
                                failureCount = currentKey.failureCount + 1,
                                errorMessage = "Invalid or expired API key (HTTP $code)"
                            )
                            currentKey = updatedKey
                            secureStorage.updateApiKey(updatedKey)
                            refreshState()
                            keyShouldBeSkipped = true
                            allModelsExhaustedForThisKey = false
                            lastException = Exception("Gemini API key is invalid or expired (HTTP $code).")
                            break
                        }

                        // 4. HTTP 403: Permission / Access Error
                        if (code == 403) {
                            val updatedKey = currentKey.copy(
                                status = KeyStatus.PERMISSION_ERROR,
                                permissionErrors403 = currentKey.permissionErrors403 + 1,
                                failureCount = currentKey.failureCount + 1,
                                errorMessage = "Permission issue (HTTP 403). Check API enablement."
                            )
                            currentKey = updatedKey
                            secureStorage.updateApiKey(updatedKey)
                            refreshState()
                            keyShouldBeSkipped = true
                            allModelsExhaustedForThisKey = false
                            lastException = Exception("Gemini rejected this API key due to permission/billing issue (HTTP 403).")
                            break
                        }

                        // 5. HTTP 404: Model or Endpoint Not Found
                        if (code == 404) {
                            val updatedKey = currentKey.copy(
                                configurationErrors404 = currentKey.configurationErrors404 + 1,
                                errorMessage = extractGeminiErrorMessage(404, errorBody)
                            )
                            currentKey = updatedKey
                            secureStorage.updateApiKey(updatedKey)
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                            refreshState()
                            lastException = Exception(extractGeminiErrorMessage(404, errorBody))
                            continue
                        }

                        // 6. HTTP 400: Client Bad Request
                        if (code == 400) {
                            val updatedKey = currentKey.copy(
                                badRequestErrors400 = currentKey.badRequestErrors400 + 1,
                                errorMessage = extractGeminiErrorMessage(400, errorBody)
                            )
                            currentKey = updatedKey
                            secureStorage.updateApiKey(updatedKey)
                            refreshState()
                            return@withContext Result.failure(Exception("Invalid request: ${extractGeminiErrorMessage(400, errorBody)}"))
                        }

                        // Other general HTTP error
                        modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                        lastException = Exception("Gemini service error (HTTP $code).")
                    }
                } catch (e: UnknownHostException) {
                    return@withContext Result.failure(Exception("Could not connect to Gemini. Check your internet connection."))
                } catch (e: SocketTimeoutException) {
                    modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                    lastException = Exception("Connection timed out on $modelId. Check your internet connection.")
                } catch (e: IOException) {
                    modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                    lastException = Exception("Network error while contacting Gemini ($modelId): ${e.message}")
                } catch (e: Exception) {
                    lastException = e
                }
            }

            // If key was invalidated (401/403), move to next key immediately
            if (keyShouldBeSkipped) {
                continue
            }

            // If key failed purely due to 503 / transient server errors across all models and not quota/cooldown,
            // fail gracefully without cascading and exhausting other healthy keys
            if (hadOnly503 && !hadRateLimitOrCooldown) {
                val final503Msg = lastException?.message ?: "Gemini service temporarily overloaded (HTTP 503)."
                return@withContext Result.failure(Exception(final503Msg))
            }

            // Level 2: Only when ALL models in the task-specific pool are exhausted for the current key,
            // rotate to the NEXT eligible API key from the pool, reset model selection to Priority 1, and execute immediately.
            if (allModelsExhaustedForThisKey) {
                // Check if all models across this task pool or all pools are in cooldown for this key
                val allTaskModelsExhausted = taskModelPool.all { model ->
                    val cooldownUntil = modelCooldowns["${currentKey.id}_${model.modelId}"] ?: 0L
                    System.currentTimeMillis() < cooldownUntil
                }

                if (allTaskModelsExhausted && hadRateLimitOrCooldown) {
                    val updatedKey = currentKey.copy(
                        status = KeyStatus.COOLDOWN,
                        cooldownUntilTimestamp = System.currentTimeMillis() + 60_000L,
                        errorMessage = "All ${taskType.name} models quota reached (HTTP 429). Cooldown active."
                    )
                    currentKey = updatedKey
                    secureStorage.updateApiKey(updatedKey)
                    refreshState()
                }
            }
        }

        val finalMsg = lastException?.message ?: "AI service temporarily unavailable. Please try again later."
        Result.failure(Exception(finalMsg))
    }

    override fun refreshState() {
        _connectionState.value = determineInitialState()
    }

    private fun extractGeminiErrorMessage(code: Int, rawErrorBody: String): String {
        val cleanMessage = try {
            val regex = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
            val match = regex.find(rawErrorBody)
            match?.groupValues?.get(1)?.replace(Regex("(?i)key\\s*=\\s*[^&\\s]+"), "key=•••")
        } catch (_: Exception) {
            null
        }

        return when (code) {
            404 -> {
                if (!cleanMessage.isNullOrBlank()) {
                    "Model or endpoint not found (HTTP 404): $cleanMessage"
                } else {
                    "Gemini model or endpoint not found (HTTP 404). Please verify model availability and API configuration."
                }
            }
            429 -> {
                "Gemini API quota or rate limit exceeded (HTTP 429)."
            }
            400 -> {
                if (rawErrorBody.contains("API_KEY_INVALID", ignoreCase = true) || rawErrorBody.contains("API key not valid", ignoreCase = true)) {
                    "Gemini rejected this API key as invalid (HTTP 400). Please check your key."
                } else if (!cleanMessage.isNullOrBlank()) {
                    "Invalid request (HTTP 400): $cleanMessage"
                } else {
                    "Gemini rejected request (HTTP 400)."
                }
            }
            401, 403 -> {
                if (!cleanMessage.isNullOrBlank()) {
                    "Gemini authentication failed (HTTP $code): $cleanMessage"
                } else {
                    "Gemini rejected this API key (HTTP $code). Check key permissions and API enablement."
                }
            }
            500, 503 -> {
                "Gemini service temporarily unavailable (HTTP $code). Please try again in a few moments."
            }
            else -> {
                if (!cleanMessage.isNullOrBlank()) {
                    "Gemini returned error ($code): $cleanMessage"
                } else {
                    "Gemini returned error code $code."
                }
            }
        }
    }
}
