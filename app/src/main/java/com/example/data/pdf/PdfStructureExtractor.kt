package com.example.data.pdf

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.model.ExtractedBookPayload
import com.example.data.model.ExtractedChapterSection
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Editorial PDF Structure & Book Metadata Extraction Engine using Flash-Lite models.
 *
 * Extracts bookTitle, author, and structured sections (Front Matter, Core Chapters, Back Matter)
 * in a single unified AI execution with exact 1-based page bounds.
 */
class PdfStructureExtractor(
    private val rotationManager: GeminiKeyRotationManager
) {
    companion object {
        private const val TAG = "PdfStructureExtractor"

        private const val SYSTEM_INSTRUCTION = """You are an expert editorial parser. Examine the cover, title page, and front matter of the complete PDF to identify the official bookTitle and primary author name.
Filter out subtitles, publishing company disclaimers, or degrees (e.g. use clean 'Robert B. Cialdini', not raw legal blurbs).

CRITICAL PAGINATION RULE: Extract absolute physical 1-based page indices of the PDF file. Do NOT use the printed roman or arabic numerals found in the footer/header of the pages. Page 1 must correspond to the very first physical page of the PDF file.

STRICT EXCLUSION BLACKLIST:
Ignore and omit all decorative, legal, and navigational pages from the sections list.
Do NOT extract: Title Page, Cover, Half Title, Copyright, Publisher Info, Table of Contents, Contents, Index, or Blank Pages.

ALLOWED MEANINGFUL SECTIONS ONLY:
1. FRONT_MATTER: Include only readable literary preludes: Foreword, Preface, Introduction, Prologue, Author's Note, Acknowledgments. For FRONT_MATTER, chapterNumber must be null.
2. CORE_CHAPTER: Include all standard numbered or titled main chapters (Chapter 1, Chapter 2, Part 1, etc.). Assign clean 1-based sequential integers to chapterNumber (1, 2, 3...).
3. BACK_MATTER: Include only substantive epilogues and conclusions: Afterword, Epilogue, Conclusion, About the Author. (Do NOT include index or blank ending pages). For BACK_MATTER, chapterNumber must be null.

Extract the exact absolute physical 1-based start and end page numbers based on actual document pagination. Do not approximate.

Output ONLY a valid JSON object matching the schema:
{
  "bookTitle": "Influence: The Psychology of Persuasion",
  "author": "Robert B. Cialdini",
  "sections": [
    {
      "title": "Introduction",
      "sectionType": "FRONT_MATTER",
      "chapterNumber": null,
      "startPage": 5,
      "endPage": 10,
      "orderIndex": 1
    },
    {
      "title": "Chapter 1: Weapons of Influence",
      "sectionType": "CORE_CHAPTER",
      "chapterNumber": 1,
      "startPage": 11,
      "endPage": 23,
      "orderIndex": 2
    },
    {
      "title": "Epilogue: Instant Influence",
      "sectionType": "BACK_MATTER",
      "chapterNumber": null,
      "startPage": 205,
      "endPage": 210,
      "orderIndex": 9
    }
  ]
}"""

        private const val USER_PROMPT = """Examine this book PDF document. Identify the official bookTitle, primary author name, and extract all meaningful readable literary sections (front matter preludes, core numbered chapters, back matter epilogues) with absolute physical 1-based start and end page numbers. Strictly omit decorative covers, copyright, table of contents, and indices. Output ONLY a valid JSON object matching the required schema."""
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(ExtractedBookPayload::class.java)
    private val listType = Types.newParameterizedType(List::class.java, ExtractedChapterSection::class.java)
    private val jsonListAdapter = moshi.adapter<List<ExtractedChapterSection>>(listType)

    /**
     * Extracts unified book identity (title & author) alongside structured sections.
     */
    suspend fun extractBookAndStructure(
        pdfFile: File,
        totalPages: Int = 1,
        fallbackTitle: String = ""
    ): Result<ExtractedBookPayload> = withContext(Dispatchers.IO) {
        try {
            val safeFallbackTitle = fallbackTitle.ifBlank {
                pdfFile.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
            }.ifBlank { "Untitled Book" }

            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                return@withContext Result.success(
                    ExtractedBookPayload(
                        bookTitle = safeFallbackTitle,
                        author = null,
                        sections = createFallbackSections(totalPages, safeFallbackTitle)
                    )
                )
            }

            val fileSizeBytes = pdfFile.length()
            val maxInlineBytes = 18 * 1024 * 1024 // 18MB max inline PDF size for Gemini Flash-Lite

            val request = if (fileSizeBytes <= maxInlineBytes) {
                val pdfBytes = pdfFile.readBytes()
                val base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
                GeminiGenerateContentRequest.forPdfStructure(
                    systemPrompt = SYSTEM_INSTRUCTION,
                    userPrompt = USER_PROMPT,
                    pdfBase64 = base64Pdf
                )
            } else {
                GeminiGenerateContentRequest.forPdfTextStructure(
                    systemPrompt = SYSTEM_INSTRUCTION,
                    userPrompt = "Fallback book hint: $safeFallbackTitle. Total pages: $totalPages. $USER_PROMPT"
                )
            }

            val apiResult = rotationManager.executeFlashLiteRequest(
                request = request,
                operationName = "PDF Book & Chapter Extraction for $safeFallbackTitle"
            )

            if (apiResult.isFailure) {
                Log.w(TAG, "AI extraction failed: ${apiResult.exceptionOrNull()?.message}. Using fallback metadata & sections.")
                return@withContext Result.success(
                    ExtractedBookPayload(
                        bookTitle = safeFallbackTitle,
                        author = null,
                        sections = createFallbackSections(totalPages, safeFallbackTitle)
                    )
                )
            }

            val response = apiResult.getOrNull()
            val rawText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawText.isNullOrBlank()) {
                return@withContext Result.success(
                    ExtractedBookPayload(
                        bookTitle = safeFallbackTitle,
                        author = null,
                        sections = createFallbackSections(totalPages, safeFallbackTitle)
                    )
                )
            }

            val payload = parseAndSanitizePayload(rawText, totalPages, safeFallbackTitle)
            Result.success(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractBookAndStructure: ${e.message}", e)
            val cleanTitle = fallbackTitle.ifBlank { pdfFile.nameWithoutExtension }.ifBlank { "Untitled Book" }
            Result.success(
                ExtractedBookPayload(
                    bookTitle = cleanTitle,
                    author = null,
                    sections = createFallbackSections(totalPages, cleanTitle)
                )
            )
        }
    }

    /**
     * Legacy adapter method: Extracts structured chapter sections from the provided PDF file.
     */
    suspend fun extractStructure(
        pdfFile: File,
        totalPages: Int = 1,
        bookTitle: String = ""
    ): Result<List<ExtractedChapterSection>> = withContext(Dispatchers.IO) {
        val payloadResult = extractBookAndStructure(pdfFile, totalPages, bookTitle)
        payloadResult.map { it.sections }
    }

    /**
     * Parses raw JSON response into ExtractedBookPayload and guarantees valid 1-based page indices.
     */
    fun parseAndSanitizePayload(
        rawJson: String,
        totalPages: Int,
        fallbackTitle: String
    ): ExtractedBookPayload {
        val sanitizedJson = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        var extractedTitle: String? = null
        var extractedAuthor: String? = null
        var rawSections: List<ExtractedChapterSection> = emptyList()

        // 1. Try Moshi parsing as unified payload object
        try {
            val moshiResult = payloadAdapter.fromJson(sanitizedJson)
            if (moshiResult != null) {
                extractedTitle = moshiResult.bookTitle
                extractedAuthor = moshiResult.author
                rawSections = moshiResult.sections
            }
        } catch (e: Exception) {
            Log.w(TAG, "Moshi payload parsing failed, attempting fallback parsers: ${e.message}")
        }

        // 2. Try Manual JSONObject parsing if needed
        if (rawSections.isEmpty() && sanitizedJson.startsWith("{")) {
            try {
                val jsonObject = JSONObject(sanitizedJson)
                if (jsonObject.has("bookTitle") && !jsonObject.isNull("bookTitle")) {
                    extractedTitle = jsonObject.optString("bookTitle")
                }
                if (jsonObject.has("author") && !jsonObject.isNull("author")) {
                    extractedAuthor = jsonObject.optString("author")
                }

                if (jsonObject.has("sections")) {
                    val sectionsArray = jsonObject.optJSONArray("sections")
                    if (sectionsArray != null) {
                        val parsedList = parseJsonArray(sectionsArray)
                        rawSections = parsedList
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Manual JSONObject parsing failed: ${e.message}")
            }
        }

        // 3. Fallback: Check if response was a naked JSON array [...]
        if (rawSections.isEmpty() && sanitizedJson.startsWith("[")) {
            try {
                val arrayResult = jsonListAdapter.fromJson(sanitizedJson)
                if (!arrayResult.isNullOrEmpty()) {
                    rawSections = arrayResult
                } else {
                    val jsonArray = JSONArray(sanitizedJson)
                    rawSections = parseJsonArray(jsonArray)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Array fallback parsing failed: ${e.message}")
            }
        }

        val finalTitle = cleanTitle(extractedTitle, fallbackTitle)
        val finalAuthor = cleanAuthor(extractedAuthor)
        val finalSections = sanitizeSections(rawSections, totalPages).ifEmpty {
            createFallbackSections(totalPages, finalTitle)
        }

        return ExtractedBookPayload(
            bookTitle = finalTitle,
            author = finalAuthor,
            sections = finalSections
        )
    }

    /**
     * Backward-compatible parse function for tests/legacy callers.
     */
    fun parseAndSanitizeJson(
        rawJson: String,
        totalPages: Int,
        bookTitle: String
    ): List<ExtractedChapterSection> {
        return parseAndSanitizePayload(rawJson, totalPages, bookTitle).sections
    }

    private fun parseJsonArray(jsonArray: JSONArray): List<ExtractedChapterSection> {
        val resultList = mutableListOf<ExtractedChapterSection>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            if (title.isEmpty()) continue

            val rawType = obj.optString("sectionType", "")
            val chapterNumber = if (obj.has("chapterNumber") && !obj.isNull("chapterNumber")) {
                obj.optInt("chapterNumber")
            } else {
                null
            }
            val startPage = obj.optInt("startPage", 1)
            val endPage = obj.optInt("endPage", startPage)
            val orderIndex = obj.optInt("orderIndex", i + 1)

            val sectionType = ExtractedChapterSection.normalizeSectionType(rawType, title)

            resultList.add(
                ExtractedChapterSection(
                    title = title,
                    sectionType = sectionType,
                    chapterNumber = if (sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) chapterNumber else null,
                    startPage = startPage.coerceAtLeast(1),
                    endPage = endPage.coerceAtLeast(startPage),
                    orderIndex = orderIndex
                )
            )
        }
        return resultList
    }

    private fun cleanTitle(title: String?, fallback: String): String {
        val candidate = title?.trim()?.removeSurrounding("\"")?.trim() ?: ""
        if (candidate.isNotBlank() && candidate.length > 1 && !candidate.equals("null", ignoreCase = true)) {
            return candidate
        }
        return fallback.trim().ifBlank { "Untitled Book" }
    }

    private fun cleanAuthor(author: String?): String? {
        val candidate = author?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (candidate.isBlank() || candidate.equals("null", ignoreCase = true) || candidate.equals("unknown", ignoreCase = true)) {
            return null
        }
        // Filter out publisher info or legal blurbs if accidentally returned
        if (candidate.startsWith("by ", ignoreCase = true)) {
            val stripped = candidate.substring(3).trim()
            return if (stripped.isNotBlank()) stripped else null
        }
        return candidate
    }

    private fun sanitizeSections(
        sections: List<ExtractedChapterSection>,
        totalPages: Int
    ): List<ExtractedChapterSection> {
        if (sections.isEmpty()) return emptyList()

        val maxPage = totalPages.coerceAtLeast(1)
        var coreChapterSequence = 1

        val validSections = sections.filterNot { ExtractedChapterSection.isBlacklisted(it.title) }
        if (validSections.isEmpty()) return emptyList()

        return validSections.mapIndexed { index, section ->
            val cleanTitle = section.title.trim().ifEmpty { "Chapter ${index + 1}" }
            val sectionType = ExtractedChapterSection.normalizeSectionType(section.sectionType, cleanTitle)
            val sPage = section.startPage.coerceIn(1, maxPage)
            val ePage = section.endPage.coerceIn(sPage, maxPage)
            val chapNum = if (sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) {
                section.chapterNumber ?: coreChapterSequence++
            } else {
                null
            }

            section.copy(
                title = cleanTitle,
                sectionType = sectionType,
                chapterNumber = chapNum,
                startPage = sPage,
                endPage = ePage,
                orderIndex = if (section.orderIndex > 0) section.orderIndex else index + 1
            )
        }.sortedBy { it.orderIndex }
    }

    /**
     * Resilient fallback if AI extraction is unavailable or offline.
     */
    fun createFallbackSections(totalPages: Int, bookTitle: String): List<ExtractedChapterSection> {
        val pages = totalPages.coerceAtLeast(1)
        if (pages <= 20) {
            return listOf(
                ExtractedChapterSection(
                    title = if (bookTitle.isNotBlank()) bookTitle else "Complete Document",
                    sectionType = ExtractedChapterSection.TYPE_CORE_CHAPTER,
                    chapterNumber = 1,
                    startPage = 1,
                    endPage = pages,
                    orderIndex = 1
                )
            )
        }

        // Partition into sensible chapters of ~20-30 pages
        val chapterCount = (pages / 25).coerceIn(2, 10)
        val pagesPerChapter = pages / chapterCount
        val list = mutableListOf<ExtractedChapterSection>()

        for (i in 0 until chapterCount) {
            val start = (i * pagesPerChapter) + 1
            val end = if (i == chapterCount - 1) pages else (i + 1) * pagesPerChapter
            list.add(
                ExtractedChapterSection(
                    title = "Chapter ${i + 1}",
                    sectionType = ExtractedChapterSection.TYPE_CORE_CHAPTER,
                    chapterNumber = i + 1,
                    startPage = start,
                    endPage = end,
                    orderIndex = i + 1
                )
            )
        }
        return list
    }
}
