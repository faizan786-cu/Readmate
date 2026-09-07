package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Two fundamentally different Gemini AI workloads:
 * - WORD_TRANSLATION: Fast, high-volume lightweight word/sentence translations (Flash-Lite models)
 * - PASSAGE_ANALYSIS: Deep, high-quality full passage explanations and lessons (Flash models)
 */
enum class GeminiTaskType {
    WORD_TRANSLATION,
    PASSAGE_ANALYSIS,
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
    val taskType: GeminiTaskType = GeminiTaskType.PASSAGE_ANALYSIS
)

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
    /**
     * Task A: Word / Sentence Translation Models (Fast, High-Volume Lightweight)
     * Priority 1: gemini-3.1-flash-lite (500 RPD, 15 RPM)
     * Priority 2: gemini-3.5-flash-lite (500 RPD, 15 RPM)
     */
    val TRANSLATION_POOL: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            modelId = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash Lite",
            priority = 1,
            rpd = 500,
            rpm = 15,
            taskType = GeminiTaskType.WORD_TRANSLATION
        ),
        GeminiModelInfo(
            modelId = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite",
            priority = 2,
            rpd = 500,
            rpm = 15,
            taskType = GeminiTaskType.WORD_TRANSLATION
        )
    )

    /**
     * Task B: Full Passage Analysis Models (Quality, Deep Explanation & Mentorship)
     * Priority 1: gemini-3.6-flash (20 RPD, 5 RPM) - Confirmed stable
     * Priority 2: gemini-3.5-flash (20 RPD, 5 RPM) - Confirmed stable fallback
     * Priority 3: gemini-3.7-flash (20 RPD, 5 RPM) - Tertiary fallback
     */
    val PASSAGE_POOL: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            modelId = "gemini-3.6-flash",
            displayName = "Gemini 3.6 Flash",
            priority = 1,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            priority = 2,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        ),
        GeminiModelInfo(
            modelId = "gemini-3.7-flash",
            displayName = "Gemini 3.7 Flash",
            priority = 3,
            rpd = 20,
            rpm = 5,
            taskType = GeminiTaskType.PASSAGE_ANALYSIS
        )
    )

    /**
     * Complete list of all models across both task pools.
     */
    val ALL_MODELS: List<GeminiModelInfo> = TRANSLATION_POOL + PASSAGE_POOL
    val PRIORITY_POOL: List<GeminiModelInfo> = ALL_MODELS

    val DEFAULT_TRANSLATION_MODEL: String = TRANSLATION_POOL.first().modelId
    val DEFAULT_PASSAGE_MODEL: String = PASSAGE_POOL.first().modelId
    val DEFAULT_MODEL: String = DEFAULT_TRANSLATION_MODEL

    fun getModelsForTask(taskType: GeminiTaskType): List<GeminiModelInfo> = when (taskType) {
        GeminiTaskType.WORD_TRANSLATION,
        GeminiTaskType.VISION_EXTRACTION -> TRANSLATION_POOL
        GeminiTaskType.PASSAGE_ANALYSIS -> PASSAGE_POOL
    }
}
