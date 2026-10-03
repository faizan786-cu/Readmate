package com.example.data.manager

import android.util.Log
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.ExtractedQuoteItem
import com.example.data.model.GeminiTaskType
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerationConfig
import com.example.data.remote.gemini.GeminiPart
import com.example.data.remote.gemini.GeminiThinkingConfig
import com.example.data.repository.WisdomQuoteRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated, resilient background extraction engine for extracting universal wisdom principles
 * and life lessons from book passages.
 *
 * Operates strictly under the Lightweight Utility Tier (Flash-Lite Pipeline) with
 * Model-First Cross-Key Quota Cascade:
 * - Ladder: gemini-3.5-flash-lite -> gemini-3.1-flash-lite -> gemini-2.5-flash-lite
 * - Separated system instruction & low-latency zero-thinking parameters.
 * - Silent Exhaustion Safety: Fails silently without crashing or interrupting UI or Chapter Chat.
 */
class QuoteExtractionEngine(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val wisdomQuoteRepository: WisdomQuoteRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiKeyManager: ApiKeyManager = GeminiApiKeyManager(secureStorage, apiService, ioDispatcher)
) {
    companion object {
        private const val TAG = "QuoteExtractionEngine"

        // Lightweight Utility Tier (Flash-Lite Pipeline)
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.1-flash-lite"
        const val EMERGENCY_FALLBACK_MODEL = "gemini-2.5-flash-lite"

        val MODEL_HIERARCHY = listOf(PRIMARY_MODEL, FALLBACK_MODEL, EMERGENCY_FALLBACK_MODEL)

        private const val STATIC_SYSTEM_INSTRUCTION = """Extract between 1 to 5 universal, standalone principles, life lessons, and timeless wisdom from the provided book passage.

STRICT EXTRACTION RULES:
1. NO character names, historical figures, or story-specific names (e.g., do NOT use names like Michael, Basilius, Bardas, Napoleon, Caesar, etc.).
2. Convert narrative events, specific actions, and anecdotes into universal principles of human nature, power, strategy, decision-making, psychology, and mindset.
3. Each principle must stand entirely on its own as a profound life lesson.
4. For each extracted quote provide:
   - "englishQuote": A punchy, powerful, universal principle in English (1-2 sentences).
   - "romanUrduPunchline": A natural, conversational Roman Urdu takeaway with balanced depth (2-3 short lines / sentences).
     * Strictly eliminate archaic, obscure, or poetic Urdu dictionary words.
     * Use natural, conversational Roman Urdu (Urban spoken style).
     * Explain WHY the quote matters and its real-world psychological or strategic application so the takeaway is crystal clear.
     * Keep core strategic and psychological keywords in English (e.g., Power, Focus, Strategy, Control, Calm, Trigger, Emotion, Boundaries, Respect, Mindset).
   - "themeTag": A thematic category hashtag (e.g., "#Strategy", "#Power", "#Mindset", "#Discipline", "#Leadership", "#HumanNature", "#Patience", "#Focus").

OUTPUT FORMAT:
Output ONLY a valid JSON array of objects with the exact schema below. Do not wrap in markdown or include conversational text.
[
  {
    "englishQuote": "Universal principle in English...",
    "romanUrduPunchline": "Roman Urdu punchline...",
    "themeTag": "#Tag"
  }
]"""
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val quoteListAdapter: JsonAdapter<List<ExtractedQuoteItem>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, ExtractedQuoteItem::class.java))

    /**
     * Asynchronously extracts between 1 to 5 universal wisdom principles from the provided passage,
     * parses the response, and persists them into the wisdom_quotes table.
     *
     * Wrapped in an isolated try-catch block to guarantee silent execution.
     */
    suspend fun extractAndSaveQuotesSilently(
        passage: String,
        bookId: Long,
        bookTitle: String,
        author: String? = null,
        chapterId: Long,
        chapterNumber: Int = 1,
        chapterTitle: String,
        messageId: Long? = null
    ): Result<List<WisdomQuote>> = withContext(ioDispatcher) {
        try {
            val cleanPassage = passage.trim()
            if (cleanPassage.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val extractedItems = executeMatrixExtraction(
                passage = cleanPassage,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle,
                author = author
            )

            if (extractedItems.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val entities = extractedItems.mapNotNull { item ->
                val eq = item.englishQuote.trim()
                if (eq.isEmpty()) return@mapNotNull null

                val urdu = item.romanUrduPunchline.trim()
                val rawTag = item.themeTag.trim()
                val tag = when {
                    rawTag.isEmpty() -> "#Wisdom"
                    rawTag.startsWith("#") -> rawTag
                    else -> "#$rawTag"
                }

                WisdomQuote(
                    id = 0L,
                    bookId = bookId,
                    bookTitle = bookTitle.ifBlank { "Untitled Book" },
                    author = author,
                    chapterId = chapterId,
                    chapterNumber = chapterNumber,
                    chapterTitle = chapterTitle.ifBlank { "Chapter $chapterNumber" },
                    englishQuote = eq,
                    romanUrduPunchline = urdu.ifBlank { "Hikmat aur danai ki baat." },
                    themeTag = tag,
                    messageId = messageId,
                    isFavorite = false,
                    createdAt = System.currentTimeMillis()
                )
            }

            if (entities.isNotEmpty()) {
                wisdomQuoteRepository.saveQuotes(entities)
                Log.d(TAG, "Successfully extracted and saved ${entities.size} wisdom quotes for chapter $chapterId")
            }

            Result.success(entities)
        } catch (t: Throwable) {
            // CRITICAL: Fail silently in the background without throwing or freezing UI
            Log.w(TAG, "Quote extraction failed silently: ${t.message}")
            Result.failure(t)
        }
    }

    private suspend fun executeMatrixExtraction(
        passage: String,
        bookTitle: String,
        chapterTitle: String,
        author: String?
    ): List<ExtractedQuoteItem> {
        val meta = buildString {
            if (bookTitle.isNotBlank()) append("Book: \"$bookTitle\". ")
            if (!author.isNullOrBlank()) append("Author: $author. ")
            if (chapterTitle.isNotBlank()) append("Chapter: \"$chapterTitle\". ")
        }

        val userPrompt = """
            $meta
            PASSAGE:
            \"\"\"
            $passage
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
            generationConfig = GeminiGenerationConfig.forFlashLite(
                maxOutputTokens = 1024,
                temperature = 0.2f,
                responseMimeType = "application/json"
            )
        )

        val apiResult = apiKeyManager.executeWithAutoRotation(
            taskType = GeminiTaskType.WISDOM_QUOTE,
            operationName = "Wisdom Quote Extraction (Flash-Lite)"
        ) { apiKey, model ->
            apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request.sanitizedForModel(model)
            )
        }

        return apiResult.fold(
            onSuccess = { response ->
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim().orEmpty()
                if (rawText.isNotEmpty()) {
                    parseQuotesJson(rawText)
                } else {
                    emptyList()
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Wisdom quote extraction failed: ${error.message}")
                emptyList()
            }
        )
    }

    private fun parseQuotesJson(rawResponse: String): List<ExtractedQuoteItem> {
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
            quoteListAdapter.fromJson(jsonArrayStr) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse quotes JSON: ${e.message}")
            emptyList()
        }
    }
}
