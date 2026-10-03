package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Workload categories across ReadMate:
 * - PASSAGE_ANALYSIS: Heavy Analysis Tier (Flash Pipeline) dedicated exclusively to Chapter Chat passage explanations & takeaways
 * - WORD_TRANSLATION, WISDOM_QUOTE, MCQ_SYNTHESIS, PDF_PARSING, VISION_EXTRACTION: Lightweight Utility Tier (Flash-Lite Pipeline)
 */
enum class GeminiTaskType {
    PASSAGE_ANALYSIS,
    WORD_TRANSLATION,
    WISDOM_QUOTE,
    MCQ_SYNTHESIS,
    PDF_PARSING,
    VISION_EXTRACTION
}

/**
 * Metadata definition for Gemini models in the Priority Pools.
 */
@JsonClass(generateAdapter = true)
data class GeminiModelInfo(
    val modelId: String,
    val displayName: String,
    val priority: Int,
    val rpd: Int, // Requests Per Day
    val rpm: Int, // Requests Per Minute
    val taskType: GeminiTaskType = GeminiTaskType.PASSAGE_ANALYSIS,
    val retirementTimestampEpochMs: Long? = null
) {
    /**
     * Epoch-Based Sunset Validation:
     * Validates model availability dynamically at runtime against retirement timestamps so
     * deprecated models are silently omitted from the pipeline without throwing HTTP 404 errors.
     */
    fun isRetired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return retirementTimestampEpochMs != null && currentTime >= retirementTimestampEpochMs
    }
}

enum class ModelQuotaState {
    PENDING,
    TESTING,
    ACTIVE,
    DAILY_LIMIT_EXHAUSTED,
    RATE_LIMITED,
    ERROR,
    UNAVAILABLE
}

@JsonClass(generateAdapter = true)
data class ModelTestResult(
    val modelId: String,
    val displayName: String,
    val priority: Int,
    val rpd: Int,
    val rpm: Int,
    val taskType: GeminiTaskType = GeminiTaskType.PASSAGE_ANALYSIS,
    val httpStatusCode: Int,
    val httpStatusText: String,
    val quotaState: ModelQuotaState,
    val latencyMs: Long,
    val errorMessage: String? = null,
    val isCooldown: Boolean = false,
    val cooldownRemainingSec: Long = 0L
)

object GeminiModelRegistry {
    // Epoch timestamp for gemini-3.1-flash-lite retirement: May 7, 2027 00:00:00 UTC
    const val RETIREMENT_GEMINI_3_1_FLASH_LITE_MS: Long = 1809648000000L

    /**
     * Lightweight Utility Tier (Flash-Lite Pipeline):
     * Dedicated exclusively to high-frequency tasks: Word translations, Wisdom quote extraction,
     * scenario MCQ generation, and PDF document parsing.
     *
     * Exact model ladder in priority order:
     * 1. gemini-3.5-flash-lite (Primary fast utility)
     * 2. gemini-3.1-flash-lite (Secondary fast utility, active until May 7, 2027)
     * 3. gemini-2.5-flash-lite (Emergency fast fallback, active until its announced sunset date)
     */
    val TRANSLATION_POOL: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            modelId = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite",
            priority = 1,
            rpd = 500,
            rpm = 15,
            taskType = GeminiTaskType.WORD_TRANSLATION
        ),
        GeminiModelInfo(
            modelId = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash Lite",
            priority = 2,
            rpd = 500,
            rpm = 15,
            taskType = GeminiTaskType.WORD_TRANSLATION,
            retirementTimestampEpochMs = RETIREMENT_GEMINI_3_1_FLASH_LITE_MS
        ),
        GeminiModelInfo(
            modelId = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            priority = 3,
            rpd = 500,
            rpm = 15,
            taskType = GeminiTaskType.WORD_TRANSLATION
        )
    )

    val UTILITY_POOL: List<GeminiModelInfo> = TRANSLATION_POOL

    /**
     * Heavy Analysis Tier (Flash Pipeline):
     * Dedicated exclusively to Chapter Chat passage explanations and deep takeaways.
     *
     * Exact model ladder in priority order:
     * 1. gemini-3.8-flash (Primary production workhorse)
     * 2. gemini-3.6-flash (First fallback)
     * 3. gemini-3.5-flash (Secondary fallback)
     * 4. gemini-3-flash-preview (Preview tier fallback)
     * 5. gemini-2.5-flash (Emergency legacy fallback, active until its announced sunset date)
     */
    val PASSAGE_POOL: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            modelId = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            priority = 1,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-3.6-flash",
            displayName = "Gemini 3.6 Flash",
            priority = 2,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            priority = 3,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-3-flash-preview",
            displayName = "Gemini 3 Flash Preview",
            priority = 4,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            priority = 5,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        )
    )

    /**
     * Complete list of all models across both tiers.
     */
    val ALL_MODELS: List<GeminiModelInfo> = TRANSLATION_POOL + PASSAGE_POOL
    val PRIORITY_POOL: List<GeminiModelInfo> = ALL_MODELS

    val DEFAULT_TRANSLATION_MODEL: String = TRANSLATION_POOL.first().modelId // gemini-3.5-flash-lite
    val DEFAULT_PASSAGE_MODEL: String = PASSAGE_POOL.first().modelId // gemini-3.8-flash
    val DEFAULT_MODEL: String = DEFAULT_TRANSLATION_MODEL

    /**
     * Returns the model hierarchy for a task, silently omitting models past their sunset retirement epoch.
     */
    fun getModelsForTask(
        taskType: GeminiTaskType,
        currentTime: Long = System.currentTimeMillis()
    ): List<GeminiModelInfo> {
        val pool = when (taskType) {
            GeminiTaskType.PASSAGE_ANALYSIS -> PASSAGE_POOL
            GeminiTaskType.WORD_TRANSLATION,
            GeminiTaskType.WISDOM_QUOTE,
            GeminiTaskType.MCQ_SYNTHESIS,
            GeminiTaskType.PDF_PARSING,
            GeminiTaskType.VISION_EXTRACTION -> UTILITY_POOL
        }
        return pool.filterNot { it.isRetired(currentTime) }
    }

    /**
     * Returns true if the model belongs to the Lightweight Utility Tier (Flash-Lite Pipeline).
     * Flash-Lite models must NEVER receive thinkingConfig or thinkingBudget parameters in their payloads.
     */
    fun isFlashLite(modelId: String): Boolean {
        return modelId.contains("flash-lite", ignoreCase = true) ||
            UTILITY_POOL.any { it.modelId.equals(modelId, ignoreCase = true) }
    }
}
