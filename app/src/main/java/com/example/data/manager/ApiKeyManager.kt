package com.example.data.manager

import android.util.Log
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
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
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

    /**
     * Server-Sent Events partial streaming execution with Model-First Cross-Key Quota Cascade.
     */
    suspend fun streamWithAutoRotation(
        taskType: GeminiTaskType,
        operationName: String = "Gemini stream",
        request: GeminiGenerateContentRequest,
        onChunk: suspend (accumulated: String, chunk: String) -> Unit
    ): Result<String>

    fun refreshState()
}

class GeminiApiKeyManager(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApiKeyManager {

    companion object {
        private const val TAG = "GeminiApiKeyManager"
        private const val COOLDOWN_DURATION_MS = 60_000L
        private const val MAX_SERVER_ERROR_RETRIES = 2
        private const val RETRY_BACKOFF_BASE_MS = 300L
        private const val DEGRADATION_WINDOW_MS = 60_000L
        private const val DEGRADATION_DISTINCT_KEY_THRESHOLD = 2
    }

    // Thread-safe localized cooldown tracking: "keyId_modelId" -> cooldownUntilTimestamp
    private val modelCooldowns = ConcurrentHashMap<String, Long>()

    // Cross-key upstream degradation tracking: modelId -> (keyId -> timestampOfError)
    private val upstreamModelErrors = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    // Pool-wide model cooldowns: modelId -> cooldownUntilTimestamp
    private val poolWideModelCooldowns = ConcurrentHashMap<String, Long>()

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
        val now = System.currentTimeMillis()
        val poolWide = poolWideModelCooldowns[modelId] ?: 0L
        if (now < poolWide) return true
        val expiry = modelCooldowns["${keyId}_$modelId"] ?: return false
        return now < expiry
    }

    override fun getModelCooldownRemainingSeconds(keyId: String, modelId: String): Long {
        val now = System.currentTimeMillis()
        val poolWide = poolWideModelCooldowns[modelId] ?: 0L
        val expiry = (modelCooldowns["${keyId}_$modelId"] ?: 0L).coerceAtLeast(poolWide)
        return if (expiry > now) ((expiry - now + 999) / 1000).coerceAtLeast(1L) else 0L
    }

    private fun isModelPoolWideDegraded(modelId: String): Boolean {
        val now = System.currentTimeMillis()
        val poolWide = poolWideModelCooldowns[modelId] ?: 0L
        return now < poolWide
    }

    private fun recordUpstreamError(modelId: String, keyId: String) {
        val now = System.currentTimeMillis()
        val keyMap = upstreamModelErrors.computeIfAbsent(modelId) { ConcurrentHashMap() }
        keyMap[keyId] = now

        // Evict stale errors outside degradation window
        keyMap.entries.removeIf { (now - it.value) > DEGRADATION_WINDOW_MS }

        // If threshold distinct keys hit upstream errors on this model within the window, flag pool-wide
        if (keyMap.size >= DEGRADATION_DISTINCT_KEY_THRESHOLD) {
            poolWideModelCooldowns[modelId] = now + COOLDOWN_DURATION_MS
            Log.w(TAG, "Circuit breaker tripped: Model $modelId flagged pool-wide degraded for ${COOLDOWN_DURATION_MS / 1000}s due to errors across multiple keys.")
        }
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
        modelCooldowns.keys.filter { it.startsWith("${id}_") }.forEach { modelCooldowns.remove(it) }
        val removed = secureStorage.removeApiKey(id)
        if (removed) refreshState()
        removed
    }

    override suspend fun resetKeyStatus(id: String): Boolean = withContext(ioDispatcher) {
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
        poolWideModelCooldowns.clear()
        upstreamModelErrors.clear()
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
        poolWideModelCooldowns.clear()
        upstreamModelErrors.clear()
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

        val testResult = testConnection(target.key, GeminiModelRegistry.DEFAULT_TRANSLATION_MODEL)
        val updatedItem = when (testResult) {
            is TestConnectionResult.Success -> {
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
                when (testResult.statusCode) {
                    429 -> {
                        target.copy(
                            status = KeyStatus.COOLDOWN,
                            cooldownUntilTimestamp = System.currentTimeMillis() + COOLDOWN_DURATION_MS,
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
                        target.copy(errorMessage = testResult.message)
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
                    cooldownUntilTimestamp = System.currentTimeMillis() + COOLDOWN_DURATION_MS,
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
     * Workload Isolation Across Keys:
     * Dispatches tasks across active keys in the pool according to the workload isolation slot mapping:
     * - Slot 0 (Key A): Flash passage synthesis (PASSAGE_ANALYSIS)
     * - Slot 1 (Key B): OCR vision and document structure (VISION_EXTRACTION, PDF_PARSING)
     * - Slot 2 (Key C): Scenario-based MCQ generation (MCQ_SYNTHESIS)
     * - Slot 3 (Key D): Wisdom quote extraction and word lookups (WISDOM_QUOTE, WORD_TRANSLATION)
     *
     * When fewer keys than concurrent tasks exist, distributes calls across idle keys using
     * least-recently-used selection without hard thread deadlocks.
     */
    private fun getOrderedKeysForTask(
        taskType: GeminiTaskType,
        candidateKeys: List<GeminiApiKeyItem>
    ): List<GeminiApiKeyItem> {
        if (candidateKeys.size <= 1) return candidateKeys

        val slot = when (taskType) {
            GeminiTaskType.PASSAGE_ANALYSIS -> 0
            GeminiTaskType.VISION_EXTRACTION, GeminiTaskType.PDF_PARSING -> 1
            GeminiTaskType.MCQ_SYNTHESIS -> 2
            GeminiTaskType.WISDOM_QUOTE, GeminiTaskType.WORD_TRANSLATION -> 3
        }

        val preferredIndex = slot % candidateKeys.size
        val primary = candidateKeys[preferredIndex]
        val remaining = candidateKeys.filterIndexed { index, _ -> index != preferredIndex }
            .sortedBy { it.lastUsedTimestamp } // Least-recently-used among remaining keys

        return listOf(primary) + remaining
    }

    /**
     * Model-First, Cross-Key Quota Cascade execution engine:
     *
     * 1. Primary Model Traversal: Attempt the tier's highest-priority model on a chosen key.
     * 2. Quota Exceeded (HTTP 429 / Resource Exhausted): Do NOT immediately abandon that model tier.
     *    Rotate immediately to the next available API key in the pool and attempt the SAME model.
     * 3. Key Exhaustion Check: Continue rotating keys until every active API key in the pool has
     *    exhausted its quota on that specific model.
     * 4. Model Step-Down: Only when the top-priority model has reported quota exhaustion across ALL active keys,
     *    step down to the second-priority model in the hierarchy and repeat the full cross-key rotation.
     * 5. Total Exhaustion Handling: If every model in the tier is exhausted across all configured keys,
     *    surface a clear, graceful error indicating when the shortest cooldown will clear, without freezing UI.
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
        autoResumeCooldownKeys(currentTime, allKeys)

        // Clean up expired in-memory cooldowns
        modelCooldowns.entries.removeIf { it.value <= currentTime }
        poolWideModelCooldowns.entries.removeIf { it.value <= currentTime }

        // Get model hierarchy for this task tier, filtering out models past their sunset epoch
        val modelLadder = GeminiModelRegistry.getModelsForTask(taskType, currentTime)
        if (modelLadder.isEmpty()) {
            return@withContext Result.failure(
                Exception("All models in the ${taskType.name} tier are currently retired or unavailable.")
            )
        }

        var lastException: Throwable? = null

        // MODEL-FIRST: Iterate through the tier's priority ladder
        for (modelInfo in modelLadder) {
            val modelId = modelInfo.modelId

            // Check if model is degraded pool-wide
            if (isModelPoolWideDegraded(modelId)) {
                Log.w(TAG, "Model $modelId is marked degraded pool-wide. Skipping to next model in ladder.")
                continue
            }

            // Fresh check of available candidate keys
            val freshKeys = secureStorage.getApiKeys()
            val now = System.currentTimeMillis()
            val availableKeys = freshKeys.filter { it.isAvailable(now) }
            if (availableKeys.isEmpty()) {
                continue
            }

            // Distribute across keys with workload isolation mapping
            val candidateKeys = getOrderedKeysForTask(taskType, availableKeys)

            // CROSS-KEY ROTATION: Rotate keys for the SAME model
            for (keyItem in candidateKeys) {
                val cooldownKey = "${keyItem.id}_$modelId"
                val cooldownUntil = modelCooldowns[cooldownKey] ?: 0L
                if (System.currentTimeMillis() < cooldownUntil) {
                    continue // This key has exhausted quota on this specific model
                }

                // Up to 2 rapid retries with brief exponential backoff for transient server errors (500/503/504/408)
                var backoff = RETRY_BACKOFF_BASE_MS
                var retryCount = 0

                while (retryCount <= MAX_SERVER_ERROR_RETRIES) {
                    try {
                        val response = block(keyItem.key.trim(), modelId)

                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                // Model & key healthy
                                modelCooldowns.remove(cooldownKey)
                                recordSuccess(keyItem)
                                return@withContext Result.success(body)
                            } else {
                                lastException = Exception("Gemini returned an empty response on $modelId.")
                                break
                            }
                        } else {
                            val code = response.code()
                            val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                            // 1. HTTP 429: Localized Quota Cooldown strictly to this (Key, Model) pair.
                            // Keep the key active for other available models; rotate immediately to NEXT key for SAME model.
                            val isRateLimit = code == 429 ||
                                errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                                errorBody.contains("quota", ignoreCase = true)

                            if (isRateLimit) {
                                modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                                recordRateLimit(keyItem, modelId)
                                lastException = Exception("Quota exceeded on $modelId (HTTP 429).")
                                break // Rotate immediately to NEXT key for the SAME top-priority model
                            }

                            // 2. HTTP 401 / 403: Permanently mark key invalid or permission denied, rotate to next key
                            if (code == 401 || (code == 400 && (errorBody.contains("API_KEY_INVALID", ignoreCase = true) || errorBody.contains("UNAUTHENTICATED", ignoreCase = true)))) {
                                recordKeyInvalid(keyItem, "Invalid or expired API key (HTTP $code)")
                                lastException = Exception("Gemini API key is invalid or expired (HTTP $code).")
                                break
                            }

                            if (code == 403) {
                                recordKeyPermissionError(keyItem, "Permission error (HTTP 403)")
                                lastException = Exception("Gemini API key rejected due to permission/billing issue (HTTP 403).")
                                break
                            }

                            // 3. HTTP 500 / 503 / 504 / 408: Silent rapid retries on same key and model before shifting
                            if (code in listOf(500, 503, 504, 408)) {
                                if (retryCount < MAX_SERVER_ERROR_RETRIES) {
                                    retryCount++
                                    delay(backoff)
                                    backoff *= 2
                                    continue
                                } else {
                                    if (code == 503) {
                                        record503Error(keyItem)
                                        return@withContext Result.failure(Exception("Gemini service temporarily overloaded (HTTP 503)."))
                                    }
                                    recordUpstreamError(modelId, keyItem.id)
                                    modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                                    lastException = Exception("Gemini server error on $modelId (HTTP $code).")
                                    break
                                }
                            }

                            // 4. HTTP 404: Endpoint / model retired or unavailable
                            if (code == 404) {
                                modelCooldowns[cooldownKey] = System.currentTimeMillis() + (COOLDOWN_DURATION_MS * 5)
                                lastException = Exception(extractGeminiErrorMessage(404, errorBody))
                                break // Proceed to next key or step down model
                            }

                            // 5. HTTP 400: Client request error
                            if (code == 400) {
                                val message = extractGeminiErrorMessage(400, errorBody)
                                return@withContext Result.failure(Exception("Invalid request ($modelId): $message"))
                            }

                            // General failure
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            lastException = Exception("Gemini service error (HTTP $code) on $modelId.")
                            break
                        }
                    } catch (e: UnknownHostException) {
                        return@withContext Result.failure(Exception("Could not connect to Gemini. Check your internet connection."))
                    } catch (e: SocketTimeoutException) {
                        recordUpstreamError(modelId, keyItem.id)
                        if (retryCount < MAX_SERVER_ERROR_RETRIES) {
                            retryCount++
                            delay(backoff)
                            backoff *= 2
                            continue
                        } else {
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            lastException = Exception("Connection timed out on $modelId. Check your internet connection.")
                            break
                        }
                    } catch (e: IOException) {
                        if (retryCount < MAX_SERVER_ERROR_RETRIES) {
                            retryCount++
                            delay(backoff)
                            backoff *= 2
                            continue
                        } else {
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            lastException = Exception("Network error while contacting Gemini ($modelId): ${e.message}")
                            break
                        }
                    } catch (e: Exception) {
                        lastException = e
                        break
                    }
                }
            }
            // Step-down condition: top-priority model exhausted across ALL active keys -> loops to next modelId in ladder
        }

        // Total exhaustion handling: surface shortest remaining cooldown
        val shortestRemainingSec = computeShortestCooldownSeconds(modelLadder)
        val finalMsg = if (shortestRemainingSec > 0) {
            "AI service temporarily unavailable. Rate limits reached across all configured keys and models. Resuming in $shortestRemainingSec seconds."
        } else {
            lastException?.message ?: "AI service temporarily unavailable. Please try again in a few moments."
        }
        Result.failure(Exception(finalMsg))
    }

    /**
     * Server-Sent Events partial streaming execution with Model-First Cross-Key Quota Cascade.
     */
    override suspend fun streamWithAutoRotation(
        taskType: GeminiTaskType,
        operationName: String,
        request: GeminiGenerateContentRequest,
        onChunk: suspend (accumulated: String, chunk: String) -> Unit
    ): Result<String> = withContext(ioDispatcher) {
        val currentTime = System.currentTimeMillis()
        val allKeys = secureStorage.getApiKeys()

        if (allKeys.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key connected. Please configure your API key in Settings.")
            )
        }

        autoResumeCooldownKeys(currentTime, allKeys)
        modelCooldowns.entries.removeIf { it.value <= currentTime }
        poolWideModelCooldowns.entries.removeIf { it.value <= currentTime }

        val modelLadder = GeminiModelRegistry.getModelsForTask(taskType, currentTime)
        var lastException: Throwable? = null

        for (modelInfo in modelLadder) {
            val modelId = modelInfo.modelId

            if (isModelPoolWideDegraded(modelId)) {
                continue
            }

            val freshKeys = secureStorage.getApiKeys()
            val now = System.currentTimeMillis()
            val availableKeys = freshKeys.filter { it.isAvailable(now) }
            if (availableKeys.isEmpty()) continue

            val candidateKeys = getOrderedKeysForTask(taskType, availableKeys)

            for (keyItem in candidateKeys) {
                val cooldownKey = "${keyItem.id}_$modelId"
                val cooldownUntil = modelCooldowns[cooldownKey] ?: 0L
                if (System.currentTimeMillis() < cooldownUntil) {
                    continue
                }

                var backoff = RETRY_BACKOFF_BASE_MS
                var retryCount = 0

                while (retryCount <= MAX_SERVER_ERROR_RETRIES) {
                    try {
                        val response: Response<ResponseBody> = apiService.streamGenerateContent(
                            model = modelId,
                            apiKey = keyItem.key.trim(),
                            request = request
                        )

                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                val accumulatedBuilder = StringBuilder()
                                val reader = responseBody.byteStream().bufferedReader()
                                var line: String? = reader.readLine()

                                while (line != null) {
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("data:")) {
                                        val dataJson = trimmed.removePrefix("data:").trim()
                                        if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                                            val chunkText = extractTextChunkFromEventJson(dataJson)
                                            if (chunkText.isNotEmpty()) {
                                                accumulatedBuilder.append(chunkText)
                                                onChunk(accumulatedBuilder.toString(), chunkText)
                                            }
                                        }
                                    }
                                    line = reader.readLine()
                                }

                                val fullText = accumulatedBuilder.toString().trim()
                                if (fullText.isNotEmpty()) {
                                    modelCooldowns.remove(cooldownKey)
                                    recordSuccess(keyItem)
                                    return@withContext Result.success(fullText)
                                } else {
                                    lastException = Exception("Gemini returned empty streaming content on $modelId.")
                                    break
                                }
                            } else {
                                lastException = Exception("Empty streaming body on $modelId.")
                                break
                            }
                        } else {
                            val code = response.code()
                            val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                            if (code == 429 || errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || errorBody.contains("quota", ignoreCase = true)) {
                                modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                                recordRateLimit(keyItem, modelId)
                                lastException = Exception("Quota exceeded on $modelId (HTTP 429).")
                                break // Rotate to NEXT key for same model
                            }

                            if (code in listOf(401, 403)) {
                                if (code == 401) recordKeyInvalid(keyItem, "Invalid API key (HTTP 401)")
                                else recordKeyPermissionError(keyItem, "Permission error (HTTP 403)")
                                lastException = Exception("Authentication error on key (HTTP $code).")
                                break
                            }

                            if (code in listOf(500, 503, 504, 408)) {
                                if (retryCount < MAX_SERVER_ERROR_RETRIES) {
                                    retryCount++
                                    delay(backoff)
                                    backoff *= 2
                                    continue
                                } else {
                                    if (code == 503) {
                                        record503Error(keyItem)
                                        return@withContext Result.failure(Exception("Gemini service temporarily overloaded (HTTP 503)."))
                                    }
                                    recordUpstreamError(modelId, keyItem.id)
                                    modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                                    lastException = Exception("Server error on $modelId (HTTP $code).")
                                    break
                                }
                            }

                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            lastException = Exception("API error $code on $modelId.")
                            break
                        }
                    } catch (e: UnknownHostException) {
                        return@withContext Result.failure(Exception("Could not connect to Gemini. Check your internet connection."))
                    } catch (e: SocketTimeoutException) {
                        recordUpstreamError(modelId, keyItem.id)
                        if (retryCount < MAX_SERVER_ERROR_RETRIES) {
                            retryCount++
                            delay(backoff)
                            backoff *= 2
                            continue
                        } else {
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                            lastException = Exception("Timeout on $modelId.")
                            break
                        }
                    } catch (e: Exception) {
                        lastException = e
                        break
                    }
                }
            }
        }

        // Fallback to standard execution if streaming fails or was exhausted
        val shortestRemainingSec = computeShortestCooldownSeconds(modelLadder)
        val finalMsg = if (shortestRemainingSec > 0) {
            "AI service temporarily unavailable. Rate limits reached across all configured keys and models. Resuming in $shortestRemainingSec seconds."
        } else {
            lastException?.message ?: "AI streaming temporarily unavailable. Please try again."
        }
        Result.failure(Exception(finalMsg))
    }

    private fun extractTextChunkFromEventJson(dataJson: String): String {
        return try {
            val root = org.json.JSONObject(dataJson)
            val candidates = root.optJSONArray("candidates") ?: return ""
            val firstCand = candidates.optJSONObject(0) ?: return ""
            val content = firstCand.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val textBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text", "")
                if (text.isNotEmpty()) textBuilder.append(text)
            }
            textBuilder.toString()
        } catch (_: Exception) {
            val regex = """"text"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
            regex.findAll(dataJson).map { match ->
                match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }.joinToString("")
        }
    }

    private fun computeShortestCooldownSeconds(models: List<GeminiModelInfo>): Long {
        val now = System.currentTimeMillis()
        val allKeys = secureStorage.getApiKeys()
        val cooldowns = mutableListOf<Long>()

        for (model in models) {
            val poolWide = poolWideModelCooldowns[model.modelId] ?: 0L
            if (poolWide > now) {
                cooldowns.add((poolWide - now + 999) / 1000)
            }
            for (key in allKeys) {
                val expiry = modelCooldowns["${key.id}_${model.modelId}"] ?: 0L
                if (expiry > now) {
                    cooldowns.add((expiry - now + 999) / 1000)
                }
            }
        }
        return cooldowns.minOrNull() ?: 0L
    }

    private fun autoResumeCooldownKeys(currentTime: Long, allKeys: List<GeminiApiKeyItem>) {
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
    }

    private fun recordSuccess(keyItem: GeminiApiKeyItem) {
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
            refreshState()
        } catch (_: Exception) {}
    }

    private fun recordRateLimit(keyItem: GeminiApiKeyItem, modelId: String) {
        try {
            val updated = keyItem.copy(
                rateLimitErrors429 = keyItem.rateLimitErrors429 + 1,
                failureCount = keyItem.failureCount + 1
            )
            secureStorage.updateApiKey(updated)
            refreshState()
        } catch (_: Exception) {}
    }

    private fun record503Error(keyItem: GeminiApiKeyItem) {
        try {
            val updated = keyItem.copy(
                status = KeyStatus.ACTIVE,
                serviceUnavailableErrors503 = keyItem.serviceUnavailableErrors503 + 1,
                errorMessage = "Gemini service temporarily overloaded (HTTP 503)"
            )
            secureStorage.updateApiKey(updated)
            refreshState()
        } catch (_: Exception) {}
    }

    private fun recordKeyInvalid(keyItem: GeminiApiKeyItem, reason: String) {
        try {
            val updated = keyItem.copy(
                status = KeyStatus.INVALID,
                authenticationErrors401 = keyItem.authenticationErrors401 + 1,
                failureCount = keyItem.failureCount + 1,
                errorMessage = reason
            )
            secureStorage.updateApiKey(updated)
            refreshState()
        } catch (_: Exception) {}
    }

    private fun recordKeyPermissionError(keyItem: GeminiApiKeyItem, reason: String) {
        try {
            val updated = keyItem.copy(
                status = KeyStatus.PERMISSION_ERROR,
                permissionErrors403 = keyItem.permissionErrors403 + 1,
                failureCount = keyItem.failureCount + 1,
                errorMessage = reason
            )
            secureStorage.updateApiKey(updated)
            refreshState()
        } catch (_: Exception) {}
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
            429 -> "Gemini API quota or rate limit exceeded (HTTP 429)."
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
            500, 503 -> "Gemini service temporarily unavailable (HTTP $code). Please try again in a few moments."
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
