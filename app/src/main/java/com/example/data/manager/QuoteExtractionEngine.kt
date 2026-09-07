package com.example.data.manager

import android.util.Log
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.model.ExtractedQuoteItem
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.KeyStatus
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiPart
import com.example.data.repository.WisdomQuoteRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated, resilient background extraction engine for extracting universal wisdom principles
 * and life lessons from book passages.
 *
 * Operates strictly under a Dual-Layer Failover Matrix:
 * - Model Rotation Hierarchy:
 *     Tier 1 (Primary Model): "gemini-3.1-flash-lite"
 *     Tier 2 (Fallback Model): "gemini-3.5-flash-lite"
 * - Multi-API Key Rotation Logic:
 *     Load-balanced key iteration, automatic 429 quota cooldowns, and seamless key cycling.
 * - Silent Exhaustion Safety:
 *     Fails silently without crashing or interrupting the main UI or Chapter Chat.
 */
class QuoteExtractionEngine(
    private val secureStorage: SecureApiKeyStorage,
    private val apiService: GeminiApiService = GeminiApiService.create(),
    private val wisdomQuoteRepository: WisdomQuoteRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "QuoteExtractionEngine"
        const val PRIMARY_MODEL = "gemini-3.1-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
        val MODEL_HIERARCHY = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val quoteListAdapter: JsonAdapter<List<ExtractedQuoteItem>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, ExtractedQuoteItem::class.java))

    // In-memory cooldown tracker: "keyId_modelId" -> cooldownUntilTimestamp
    private val modelCooldowns = ConcurrentHashMap<String, Long>()

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

    /**
     * Executes the Dual-Layer Failover Matrix (Key Pool + Model Hierarchy).
     */
    private suspend fun executeMatrixExtraction(
        passage: String,
        bookTitle: String,
        chapterTitle: String,
        author: String?
    ): List<ExtractedQuoteItem> {
        val prompt = buildExtractionPrompt(
            passage = passage,
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            authorName = author
        )

        val request = GeminiGenerateContentRequest.forText(prompt)

        val currentTime = System.currentTimeMillis()
        val allKeys = secureStorage.getApiKeys()
        if (allKeys.isEmpty()) {
            Log.d(TAG, "No API keys configured; skipping quote extraction.")
            return emptyList()
        }

        // Clean expired in-memory cooldowns
        modelCooldowns.entries.removeIf { it.value <= currentTime }

        // Step through Model Rotation Hierarchy:
        // Tier 1: "gemini-3.1-flash-lite" -> Tier 2: "gemini-3.5-flash-lite"
        for (model in MODEL_HIERARCHY) {
            val freshKeys = secureStorage.getApiKeys()
            val now = System.currentTimeMillis()

            // Candidate keys: active or expired cooldown, sorted by oldest lastUsedTimestamp
            val candidateKeys = freshKeys
                .filter { it.isAvailable(now) }
                .sortedBy { it.lastUsedTimestamp }

            if (candidateKeys.isEmpty()) {
                continue
            }

            for (keyItem in candidateKeys) {
                val cooldownKey = "${keyItem.id}_$model"
                val modelCooldownUntil = modelCooldowns[cooldownKey] ?: 0L
                if (System.currentTimeMillis() < modelCooldownUntil) {
                    continue
                }

                try {
                    val response = apiService.generateContent(
                        model = model,
                        apiKey = keyItem.key,
                        request = request
                    )

                    if (response.isSuccessful) {
                        val body = response.body()
                        val rawText = body?.candidates
                            ?.firstOrNull()
                            ?.content
                            ?.parts
                            ?.mapNotNull { it.text }
                            ?.joinToString("\n")
                            ?.trim()
                            .orEmpty()

                        if (rawText.isNotEmpty()) {
                            // Mark key and model healthy
                            modelCooldowns.remove(cooldownKey)
                            val updatedKey = keyItem.copy(
                                status = KeyStatus.ACTIVE,
                                lastUsedTimestamp = System.currentTimeMillis(),
                                successCount = keyItem.successCount + 1,
                                successfulRequests = keyItem.successfulRequests + 1,
                                errorMessage = null,
                                cooldownUntilTimestamp = 0L
                            )
                            secureStorage.updateApiKey(updatedKey)

                            val parsedQuotes = parseQuotesJson(rawText)
                            if (parsedQuotes.isNotEmpty()) {
                                return parsedQuotes
                            }
                        }
                    } else {
                        val code = response.code()
                        val errorBody = try { response.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                        if (code == 429 || errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || errorBody.contains("quota", ignoreCase = true)) {
                            // Mark 60s cooldown on this (key, model)
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                            val updated = keyItem.copy(
                                rateLimitErrors429 = keyItem.rateLimitErrors429 + 1,
                                failureCount = keyItem.failureCount + 1
                            )
                            secureStorage.updateApiKey(updated)
                            Log.d(TAG, "Key ${keyItem.maskedKey} rate limited (HTTP 429) on $model. Rotating to next key.")
                            continue
                        } else if (code in listOf(401, 400) && (errorBody.contains("API_KEY_INVALID", ignoreCase = true) || errorBody.contains("UNAUTHENTICATED", ignoreCase = true))) {
                            val updated = keyItem.copy(
                                status = KeyStatus.INVALID,
                                authenticationErrors401 = keyItem.authenticationErrors401 + 1,
                                errorMessage = "Invalid API key (HTTP $code)"
                            )
                            secureStorage.updateApiKey(updated)
                            continue
                        } else if (code == 403) {
                            val updated = keyItem.copy(
                                status = KeyStatus.PERMISSION_ERROR,
                                permissionErrors403 = keyItem.permissionErrors403 + 1,
                                errorMessage = "Permission issue (HTTP 403)"
                            )
                            secureStorage.updateApiKey(updated)
                            continue
                        } else {
                            // General transient error: set model cooldown and try next key
                            modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                            continue
                        }
                    }
                } catch (e: Exception) {
                    modelCooldowns[cooldownKey] = System.currentTimeMillis() + 60_000L
                    Log.w(TAG, "Transient network exception on $model with key ${keyItem.maskedKey}: ${e.message}")
                    continue
                }
            }
        }

        // Silent Exhaustion Safety: all keys and models failed
        Log.w(TAG, "All keys and fallback models exhausted for background quote extraction.")
        return emptyList()
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

    private fun buildExtractionPrompt(
        passage: String,
        bookTitle: String,
        chapterTitle: String,
        authorName: String?
    ): String {
        val meta = buildString {
            if (bookTitle.isNotBlank()) append("Book: \"$bookTitle\". ")
            if (!authorName.isNullOrBlank()) append("Author: $authorName. ")
            if (chapterTitle.isNotBlank()) append("Chapter: \"$chapterTitle\". ")
        }

        return """
            $meta
            Extract between 1 to 5 universal, standalone principles, life lessons, and timeless wisdom from the following book passage.

            PASSAGE:
            \"\"\"
            $passage
            \"\"\"

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
            ]
        """.trimIndent()
    }
}
