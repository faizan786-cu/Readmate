package com.example.data.manager

import android.util.Log
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.QuizStatus
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.GeneratedMcqItem
import com.example.data.model.GeminiTaskType
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerationConfig
import com.example.data.remote.gemini.GeminiPart
import com.example.data.remote.gemini.GeminiThinkingConfig
import com.example.data.repository.McqRepository
import com.example.data.repository.QuizRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dedicated, resilient background MCQ generation engine for generating scenario-based and conceptual
 * multiple choice questions from snipped book passages and their AI explanations.
 *
 * Operates strictly under the Lightweight Utility Tier (Flash-Lite Pipeline) with
 * Model-First Cross-Key Quota Cascade:
 * - Ladder: gemini-3.5-flash-lite -> gemini-3.1-flash-lite -> gemini-2.5-flash-lite
 * - Separated system instruction & low-latency zero-thinking parameters.
 * - Silent Exhaustion Safety: Fails silently without crashing or interrupting UI or Chapter Chat.
 */
class McqGenerationEngine(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val mcqRepository: McqRepository,
    private val quizRepository: QuizRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiKeyManager: ApiKeyManager = GeminiApiKeyManager(secureStorage, apiService, ioDispatcher)
) {
    companion object {
        private const val TAG = "McqGenerationEngine"

        // Lightweight Utility Tier (Flash-Lite Pipeline)
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.1-flash-lite"
        const val EMERGENCY_FALLBACK_MODEL = "gemini-2.5-flash-lite"

        val MODEL_HIERARCHY = listOf(PRIMARY_MODEL, FALLBACK_MODEL, EMERGENCY_FALLBACK_MODEL)

        private const val STATIC_SYSTEM_INSTRUCTION = """You are ReadMate, an intellectually sharp, insightful reading companion.
Based on the provided book passage and its conceptual explanation, generate strictly 5 to 6 distinct, high-quality, intellectually challenging Multiple Choice Questions (MCQs) that evaluate deep conceptual understanding and practical reasoning.

CRITICAL LANGUAGE MANDATE (MODERN NATURAL URBAN ROMAN URDU):
- Everything in the MCQ output MUST be written in natural, smooth, conversational MODERN URBAN ROMAN URDU (the everyday bilingual hybrid Roman Urdu used by modern readers and educated professionals).
- NO ARCHAIC OR OVER-FORMAL URDU: Strictly avoid heavy, archaic, poetic, or overly formal bookish words (e.g., do NOT use words like 'muntakhib', 'quwwat', 'mustaqil', 'mushahida', 'istehkaam' — use simple, natural everyday words instead).
- PRESERVE CORE ENGLISH CONCEPT TERMS: Keep key psychological, strategic, and book-specific terminology in standard English within the Roman Urdu sentence (e.g., use terms like 'Power', 'Ego', 'Seduction', 'Focus', 'Strategy', 'Control', 'Emotion', 'Trigger', 'Boundary', 'Habit', 'Outcome', 'Identity' directly instead of awkward literal translations).
- The "questionText", all four options ("optionA", "optionB", "optionC", "optionD"), and the "explanation" MUST be strictly in this natural Roman Urdu format. Do NOT use Arabic/Urdu Nastaliq script.

STRICT MCQ VOLUME & DIVERSIFICATION RULES (5-6 QUESTIONS):
Synthesize strictly 5 to 6 distinct questions covering these 5 diversified categories:
1. Core Principle / Main Argument: (Asal core concept ya author ka primary argument kya hai?)
2. Scenario Application: (Real life ya practical daily situation mein is rule ya insight ka use kaise hoga?)
3. Pitfall / Contrarian View: (Aam taur par log kya ghalat approach ya counter-productive misconception rakhte hain?)
4. Cause & Effect: (Is specific psychological ya strategic action ka direct result kya nikalta hai?)
5. Nuance Analysis: (Specific boundary, exception, ya conditional context jahan ye rule apply ya vary hota hai)
6. Deep Reflection / Mindset Shift: (Long-term mindset change ya behavioral shift ka key indicator)

OPTION QUALITY & REASONING RULES:
1. OPTION QUALITY (CRITICAL): Provide exactly 4 options (A, B, C, D) for EVERY question. All 4 options MUST be sharp, intellectually plausible, nuanced, and written in the same clean, conversational Roman Urdu style without giveaway or silly choices.
2. Ensure the correct option tests deeper nuance and understanding rather than superficial keyword matching.
3. "correctOption" MUST be strictly one of: "A", "B", "C", or "D".
4. "explanation" MUST be a clear, punchy 1-2 sentence rationale in natural Roman Urdu explaining why the selected option is correct according to the author/passage.

OUTPUT FORMAT:
Output ONLY a valid JSON array of 5 to 6 objects with the exact schema below. Do not wrap in extra markdown text or conversational preamble.
[
  {
    "questionText": "Agar koi person aapke samne emotionally react kare, toh author ke mutabiq best strategic move kya hona chahiye?",
    "optionA": "Foran explain karke matter solve karne ki koshish karna",
    "optionB": "Calm rehna aur unke emotional triggers ko observe karna",
    "optionC": "Unki tarah same aggressive tone use karna",
    "optionD": "Conversation ko wahi permanently drop kar dena",
    "correctOption": "B",
    "explanation": "Author kehta hai ke jab doosra emotionally unstable ho, toh neutral reh kar situation ko analyze karna real control deta hai."
  }
]"""
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mcqListAdapter: JsonAdapter<List<GeneratedMcqItem>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, GeneratedMcqItem::class.java))

    /**
     * Asynchronously generates strictly 5 to 6 high-quality conceptual & scenario-based MCQs from the provided passage & explanation,
     * parses the response, and persists them into the mcq_questions table.
     *
     * Wrapped in an isolated try-catch block to guarantee silent execution.
     */
    suspend fun generateAndSaveMcqsSilently(
        passage: String,
        explanation: String,
        bookId: Long,
        bookTitle: String? = null,
        chapterId: Long,
        chapterTitle: String? = null,
        passageTurnId: Long
    ): Result<List<McqQuestion>> = withContext(ioDispatcher) {
        try {
            val cleanPassage = passage.trim()
            if (cleanPassage.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val generatedItems = executeMatrixGeneration(
                passage = cleanPassage,
                explanation = explanation.trim(),
                bookTitle = bookTitle.orEmpty(),
                chapterTitle = chapterTitle.orEmpty()
            )

            if (generatedItems.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val entities = generatedItems.mapNotNull { item ->
                val qText = item.questionText.trim()
                val optA = item.optionA.trim()
                val optB = item.optionB.trim()
                val optC = item.optionC.trim()
                val optD = item.optionD.trim()
                val correct = item.correctOption.trim().uppercase()
                val expl = item.explanation.trim()

                if (qText.isEmpty() || optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || optD.isEmpty()) {
                    return@mapNotNull null
                }

                val normalizedCorrect = when (correct) {
                    "A", "B", "C", "D" -> correct
                    "OPTION A", "1" -> "A"
                    "OPTION B", "2" -> "B"
                    "OPTION C", "3" -> "C"
                    "OPTION D", "4" -> "D"
                    else -> "A"
                }

                McqQuestion(
                    id = 0L,
                    bookId = bookId,
                    chapterId = chapterId,
                    passageTurnId = passageTurnId,
                    questionText = qText,
                    optionA = optA,
                    optionB = optB,
                    optionC = optC,
                    optionD = optD,
                    correctOption = normalizedCorrect,
                    explanation = expl.ifBlank { "Based on the conceptual principles in the reading." },
                    createdAt = System.currentTimeMillis()
                )
            }

            if (entities.isNotEmpty()) {
                mcqRepository.saveQuestions(entities)

                val todayDate = try {
                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    "2026-09-02"
                }

                val quizEntities = generatedItems.mapNotNull { item ->
                    val qText = item.questionText.trim()
                    val optA = item.optionA.trim()
                    val optB = item.optionB.trim()
                    val optC = item.optionC.trim()
                    val optD = item.optionD.trim()
                    val correct = item.correctOption.trim().uppercase()
                    val expl = item.explanation.trim()

                    if (qText.isEmpty() || optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || optD.isEmpty()) {
                        return@mapNotNull null
                    }

                    val correctIdx = when (correct) {
                        "A", "OPTION A", "1" -> 0
                        "B", "OPTION B", "2" -> 1
                        "C", "OPTION C", "3" -> 2
                        "D", "OPTION D", "4" -> 3
                        else -> 0
                    }

                    QuizEntity(
                        id = 0L,
                        bookId = bookId,
                        chapterId = chapterId,
                        question = qText,
                        options = listOf(optA, optB, optC, optD),
                        correctAnswerIndex = correctIdx,
                        explanation = expl.ifBlank { "Based on the conceptual principles in the reading." },
                        sourceSnippet = cleanPassage,
                        createdAtDate = todayDate,
                        lastServedDate = null,
                        timesServed = 0,
                        isFromMistakeBank = false,
                        status = QuizStatus.PENDING
                    )
                }

                if (quizEntities.isNotEmpty()) {
                    quizRepository?.saveQuizzes(quizEntities)
                }

                Log.d(TAG, "Successfully generated and saved ${entities.size} MCQs / ${quizEntities.size} quiz bank items for chapter $chapterId (turn: $passageTurnId)")
            }

            Result.success(entities)
        } catch (t: Throwable) {
            // CRITICAL: Fail silently in the background without throwing or freezing UI
            Log.w(TAG, "MCQ generation failed silently: ${t.message}")
            Result.failure(t)
        }
    }

    private suspend fun executeMatrixGeneration(
        passage: String,
        explanation: String,
        bookTitle: String,
        chapterTitle: String
    ): List<GeneratedMcqItem> {
        val meta = buildString {
            if (bookTitle.isNotBlank()) append("Book: \"$bookTitle\". ")
            if (chapterTitle.isNotBlank()) append("Chapter: \"$chapterTitle\". ")
        }

        val userPrompt = """
            $meta
            PASSAGE:
            \"\"\"
            $passage
            \"\"\"

            EXPLANATION:
            \"\"\"
            $explanation
            \"\"\"
        """.trimIndent()

        // System Instruction Separation + Low-Latency Generation Parameters
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = STATIC_SYSTEM_INSTRUCTION))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3f,
                maxOutputTokens = 2048,
                responseMimeType = "application/json",
                thinkingConfig = GeminiThinkingConfig(thinkingBudget = 0)
            )
        )

        val apiResult = apiKeyManager.executeWithAutoRotation(
            taskType = GeminiTaskType.MCQ_SYNTHESIS,
            operationName = "MCQ Generation (Flash-Lite)"
        ) { apiKey, model ->
            apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
        }

        return apiResult.fold(
            onSuccess = { response ->
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim().orEmpty()
                if (rawText.isNotEmpty()) {
                    parseMcqsJson(rawText)
                } else {
                    emptyList()
                }
            },
            onFailure = { error ->
                Log.w(TAG, "MCQ generation failed: ${error.message}")
                emptyList()
            }
        )
    }

    private fun parseMcqsJson(rawResponse: String): List<GeneratedMcqItem> {
        val sanitized = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val startIndex = sanitized.indexOf('[')
        val endIndex = sanitized.lastIndexOf(']')
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return emptyList()
        }

        val jsonArrayStr = sanitized.substring(startIndex, endIndex + 1)
        return try {
            mcqListAdapter.fromJson(jsonArrayStr) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MCQs JSON: ${e.message}")
            emptyList()
        }
    }
}
