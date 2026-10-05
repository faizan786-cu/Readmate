package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * Result of local structure extraction indicating whether chapter boundaries
 * were physically verified against actual PDF pages.
 */
data class LocalStructureResult(
    val sections: List<ExtractedChapterSection>,
    val isPhysicallyTrustworthy: Boolean
)

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
     * Uses a fast local-first approach:
     * 1. Inspects PDF metadata locally (Title, Author) in <5ms.
     * 2. Inspects front-matter text (first 15-20 pages) for local TOC / chapter headings.
     * 3. Only if local extraction lacks structured chapters, dispatches compact front-matter text
     *    to Gemini Flash-Lite (bounded tokens: 1000). Never Base64 encodes or uploads the full PDF!
     * 4. Deterministic fallback ensures PDF import never crashes or fails.
     */
    suspend fun extractBookAndStructure(
        pdfFile: File,
        totalPages: Int = 1,
        fallbackTitle: String = ""
    ): Result<ExtractedBookPayload> = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()
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

        try {
            // 1. Fast local metadata extraction (first 64KB and last 64KB)
            val metaStart = System.currentTimeMillis()
            val (localTitle, localAuthor) = extractPdfMetadataLocally(pdfFile)
            val metaTime = System.currentTimeMillis() - metaStart
            Log.d(TAG, "[Timing] Metadata extraction duration: ${metaTime}ms")

            val resolvedTitle = localTitle?.ifBlank { null } ?: safeFallbackTitle
            val resolvedAuthor = localAuthor?.ifBlank { null }

            // 2. Physical page text extraction bounded strictly to pages 0 until min(totalPages, 20)
            val structStart = System.currentTimeMillis()
            val physicalPagesCount = totalPages.coerceIn(1, 20)
            val pagesText = extractPhysicalPagesText(pdfFile, physicalPagesCount)
            val localResult = extractLocalFrontMatterStructure(pagesText, totalPages, resolvedTitle)
            val structTime = System.currentTimeMillis() - structStart
            Log.d(TAG, "[Timing] Structure parsing duration: ${structTime}ms")

            // Local structure extraction may only skip AI if locally produced chapter boundaries are physically trustworthy
            if (localResult.isPhysicallyTrustworthy && localResult.sections.size >= 2) {
                Log.d(TAG, "Local structure extraction found ${localResult.sections.size} physically trustworthy chapters. Skipping AI call.")
                val totalDuration = System.currentTimeMillis() - totalStart
                Log.d(TAG, "[Timing] Total PDF structure resolution duration: ${totalDuration}ms (local-first)")
                return@withContext Result.success(
                    ExtractedBookPayload(
                        bookTitle = resolvedTitle,
                        author = resolvedAuthor,
                        sections = localResult.sections
                    )
                )
            }

            // 3. If local extraction lacks verified physical boundaries, send compact page-indexed front-matter text to Flash-Lite
            val pageIndexedFrontMatter = buildPageIndexedFrontMatter(pagesText)
            if (pageIndexedFrontMatter.isNotBlank()) {
                val aiStart = System.currentTimeMillis()
                val compactPrompt = """
                    Book title: $resolvedTitle. Total pages: $totalPages.
                    Below is the page-indexed front-matter text (first 20 physical pages of the PDF).
                    Each physical page begins with an explicit [PHYSICAL_PAGE=X] marker indicating the exact physical 1-based page index.

                    CRITICAL PHYSICAL PAGINATION RULES:
                    1. Printed page numbers in a Table of Contents (e.g. 'Chapter 1 ... 1') often differ from physical PDF pages due to front-matter (covers, copyright, preface).
                    2. Use the [PHYSICAL_PAGE=X] source evidence to identify where each chapter actually begins in the physical PDF file.
                    3. Return physical 1-based startPage and endPage for all readable literary sections (front matter preludes, core numbered chapters, back matter epilogues) up to page $totalPages.
                    4. Strictly omit decorative covers, copyright, table of contents, and indices.
                    5. Output ONLY valid JSON matching the schema.

                    Page-Indexed Front-Matter:
                    $pageIndexedFrontMatter
                """.trimIndent()

                val request = GeminiGenerateContentRequest.forPdfTextStructure(
                    systemPrompt = SYSTEM_INSTRUCTION,
                    userPrompt = compactPrompt
                )

                val apiResult = rotationManager.executeFlashLiteRequest(
                    request = request,
                    operationName = "Flash-Lite Front-Matter Chapter Extraction for $resolvedTitle"
                )
                val aiTime = System.currentTimeMillis() - aiStart
                Log.d(TAG, "[Timing] AI parsing duration if used: ${aiTime}ms")

                if (apiResult.isSuccess) {
                    val rawText = apiResult.getOrNull()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!rawText.isNullOrBlank()) {
                        val parsed = parseAndSanitizePayload(rawText, totalPages, resolvedTitle)
                        val finalTitle = parsed.bookTitle?.ifBlank { resolvedTitle } ?: resolvedTitle
                        val finalAuthor = parsed.author ?: resolvedAuthor
                        val finalSections = parsed.sections.ifEmpty {
                            if (localResult.isPhysicallyTrustworthy && localResult.sections.isNotEmpty()) {
                                localResult.sections
                            } else if (localResult.sections.isNotEmpty()) {
                                createFallbackSectionsWithTitles(totalPages, localResult.sections.map { it.title }, finalTitle)
                            } else {
                                createFallbackSections(totalPages, finalTitle)
                            }
                        }
                        return@withContext Result.success(
                            ExtractedBookPayload(
                                bookTitle = finalTitle,
                                author = finalAuthor,
                                sections = finalSections
                            )
                        )
                    }
                }
            }

            // 4. Deterministic safe fallback
            val fallbackSections = if (localResult.isPhysicallyTrustworthy && localResult.sections.isNotEmpty()) {
                localResult.sections
            } else if (localResult.sections.isNotEmpty()) {
                createFallbackSectionsWithTitles(totalPages, localResult.sections.map { it.title }, resolvedTitle)
            } else {
                createFallbackSections(totalPages, resolvedTitle)
            }
            Result.success(
                ExtractedBookPayload(
                    bookTitle = resolvedTitle,
                    author = resolvedAuthor,
                    sections = fallbackSections
                )
            )
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
     * Inexpensive local metadata reader: Scans the first 64KB and last 64KB for /Title and /Author.
     */
    fun extractPdfMetadataLocally(pdfFile: File): Pair<String?, String?> {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return Pair(null, null)
        var title: String? = null
        var author: String? = null

        try {
            RandomAccessFile(pdfFile, "r").use { raf ->
                val fileLength = raf.length()
                val headSize = (64 * 1024L).coerceAtMost(fileLength).toInt()
                val headBytes = ByteArray(headSize)
                raf.seek(0)
                raf.readFully(headBytes)
                val headText = String(headBytes, Charsets.ISO_8859_1)

                val tailSize = (64 * 1024L).coerceAtMost(fileLength).toInt()
                val tailBytes = ByteArray(tailSize)
                raf.seek(fileLength - tailSize)
                raf.readFully(tailBytes)
                val tailText = String(tailBytes, Charsets.ISO_8859_1)

                val combined = headText + "\n" + tailText

                val titleMatch = Regex("""/Title\s*\(([^)]+)\)""").find(combined)
                if (titleMatch != null) {
                    title = cleanTitle(sanitizePdfString(titleMatch.groupValues[1]), "")
                }
                val authorMatch = Regex("""/Author\s*\(([^)]+)\)""").find(combined)
                if (authorMatch != null) {
                    author = cleanAuthor(sanitizePdfString(authorMatch.groupValues[1]))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local PDF metadata reading skipped: ${e.message}")
        }
        return Pair(title, author)
    }

    /**
     * Extracts plaintext by actual PHYSICAL PAGE INDEX (1-based, 1..min(totalPages, maxPages)).
     * Inspects only the bounded physical page range (at most the first 20 pages).
     * Never infers page boundaries from raw file byte positions.
     */
    fun extractPhysicalPagesText(
        pdfFile: File,
        maxPages: Int = 20
    ): Map<Int, String> {
        val boundedMax = maxPages.coerceIn(1, 20)
        if (!pdfFile.exists() || pdfFile.length() == 0L) return emptyMap()
        val result = mutableMapOf<Int, String>()

        // 1. Lightweight local text extraction from PDF object streams for physical pages 0 until min(totalPages, 20)
        try {
            RandomAccessFile(pdfFile, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength >= 10) {
                    val objOffsets = scanPdfObjectOffsets(raf, fileLength)
                    val pageObjectIds = resolvePhysicalPageObjectIds(raf, objOffsets, boundedMax)

                    val pagesToProcess = pageObjectIds.take(boundedMax)
                    for (pageIndex in pagesToProcess.indices) {
                        val pageObjId = pagesToProcess[pageIndex]
                        val physicalPageNum = pageIndex + 1 // 1-based physical page number

                        val pageText = extractTextForPageObject(raf, pageObjId, objOffsets)
                        result[physicalPageNum] = pageText
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Physical page text extraction via object streams skipped: ${e.message}")
        }

        // 2. If local object stream extraction produced no pages or all blank, inspect physical pages using Android PdfRenderer
        if (result.isEmpty() || result.values.all { it.isBlank() }) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val total = renderer.pageCount
                val pagesToInspect = boundedMax.coerceAtMost(total)
                for (pageIdx in 0 until pagesToInspect) {
                    val physicalPageNum = pageIdx + 1
                    var page: PdfRenderer.Page? = null
                    var bitmap: Bitmap? = null
                    try {
                        page = renderer.openPage(pageIdx)
                        // Modest resolution thumbnail render: ~300x400
                        val modestW = (page.width / 2).coerceIn(200, 400)
                        val modestH = (page.height / 2).coerceIn(300, 600)
                        bitmap = Bitmap.createBitmap(modestW, modestH, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (!result.containsKey(physicalPageNum) || result[physicalPageNum].isNullOrBlank()) {
                            result[physicalPageNum] = "[Physical Page $physicalPageNum Graphical/Rendered Content]"
                        }
                    } catch (_: Throwable) {
                    } finally {
                        try { bitmap?.recycle() } catch (_: Throwable) {}
                        try { page?.close() } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }

        return result
    }

    /**
     * Builds a compact front-matter representation where every physical page
     * begins with an explicit [PHYSICAL_PAGE=X] marker.
     */
    fun buildPageIndexedFrontMatter(
        pagesText: Map<Int, String>,
        maxTotalChars: Int = 8000
    ): String {
        if (pagesText.isEmpty()) return ""
        val builder = StringBuilder()
        for ((pageIndex, text) in pagesText.toSortedMap()) {
            builder.append("[PHYSICAL_PAGE=$pageIndex]\n")
            val clean = text.trim()
            if (clean.isNotEmpty()) {
                builder.append(clean.take(1000)).append("\n\n")
            } else {
                builder.append("[Cover / Blank / Graphical Page]\n\n")
            }
            if (builder.length >= maxTotalChars) break
        }
        return builder.toString().trim()
    }

    /**
     * Extracts front-matter text using actual physical page indices.
     * Inspects strictly a bounded physical range: page indices 0 until min(totalPages, 20).
     * Never infers physical page range from raw file byte position.
     */
    fun extractFrontMatterText(
        pdfFile: File,
        maxPages: Int = 20
    ): String {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return ""
        val pagesText = extractPhysicalPagesText(pdfFile, maxPages.coerceIn(1, 20))
        return buildPageIndexedFrontMatter(pagesText)
    }

    /**
     * Overload for backward-compatibility with tests or callers providing maxBytesToRead.
     * Note: Ignores maxBytesToRead and strictly performs page-bounded extraction.
     */
    fun extractFrontMatterText(
        pdfFile: File,
        maxPages: Int = 20,
        @Suppress("UNUSED_PARAMETER") maxBytesToRead: Int = 384 * 1024
    ): String {
        return extractFrontMatterText(pdfFile, maxPages)
    }

    /**
     * Local Table of Contents & Chapter Heading Parser from physical pages.
     * Only marks isPhysicallyTrustworthy = true if chapter boundaries are verified
     * against actual physical page locations (preventing blind TOC printed page mapping).
     */
    fun extractLocalFrontMatterStructure(
        pagesText: Map<Int, String>,
        totalPages: Int,
        fallbackTitle: String
    ): LocalStructureResult {
        if (pagesText.isEmpty()) return LocalStructureResult(emptyList(), false)

        val tocPageNumbers = mutableListOf<Int>()
        for ((p, text) in pagesText) {
            if (text.contains("Table of Contents", ignoreCase = true) ||
                (text.contains("Contents", ignoreCase = true) && text.lines().count { it.contains("...") || it.contains("…") || Regex("""\d+$""").containsMatchIn(it.trim()) } >= 2)) {
                tocPageNumbers.add(p)
            }
        }
        val tocPage = tocPageNumbers.firstOrNull()

        // Strategy B: Direct heading discovery on physical pages (excluding covers/TOC page)
        val physicallyObservedHeadings = mutableListOf<Pair<String, Int>>()
        val headingRegex = Regex(
            """^(?:chapter\s+(\d+|[ivxlcdm]+)|part\s+(\d+|[ivxlcdm]+)|prologue|introduction|foreword|preface|epilogue|conclusion)(?:\s*[:.-]\s*(.*))?$""",
            RegexOption.IGNORE_CASE
        )

        for ((p, text) in pagesText.toSortedMap()) {
            if (tocPage != null && p <= tocPage) continue
            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines.take(5)) {
                val match = headingRegex.find(line)
                if (match != null && !line.contains("...") && !line.contains("…")) {
                    val title = line.trim()
                    physicallyObservedHeadings.add(title to p)
                    break
                }
            }
        }

        // Strategy A: Table of Contents parsing & offset correlation
        var tocOffset: Int? = null
        var isTocOffsetVerified = false
        val tocSections = mutableListOf<Pair<String, Int>>()

        if (tocPage != null) {
            val tocText = pagesText[tocPage] ?: ""
            val tocLineRegex = Regex(
                """^(?:chapter\s+(\d+|[ivxlcdm]+)|part\s+(\d+|[ivxlcdm]+)|prologue|introduction|foreword|preface|epilogue|conclusion|\d+[\.\)]\s+)(.*?)[.\s_…-]+(\d{1,4})$""",
                RegexOption.IGNORE_CASE
            )
            for (line in tocText.lines()) {
                val m = tocLineRegex.find(line.trim())
                if (m != null) {
                    val titlePart = line.substringBeforeLast(".").substringBeforeLast("…").trim().trimEnd('.', '-', ' ', '…')
                    val printedPage = m.groupValues[m.groupValues.size - 1].toIntOrNull()
                    if (printedPage != null && printedPage in 1..totalPages) {
                        tocSections.add(titlePart to printedPage)
                    }
                }
            }

            // Verify if Chapter 1's heading was physically observed to determine printed-to-physical offset
            if (tocSections.isNotEmpty()) {
                val firstTocPrinted = tocSections.first().second
                val firstTocTitle = tocSections.first().first.lowercase()
                val matchedObserved = physicallyObservedHeadings.firstOrNull { (obsTitle, _) ->
                    obsTitle.lowercase().contains(firstTocTitle) || firstTocTitle.contains(obsTitle.lowercase()) ||
                        (obsTitle.lowercase().startsWith("chapter 1") && firstTocTitle.startsWith("chapter 1"))
                }
                if (matchedObserved != null) {
                    val physicalStart = matchedObserved.second
                    if (physicalStart > tocPage) {
                        tocOffset = physicalStart - firstTocPrinted
                        isTocOffsetVerified = true
                    }
                }
            }
        }

        // 1. If Strategy B found >= 2 physical headings on distinct physical pages, trust physical pages!
        if (physicallyObservedHeadings.size >= 2) {
            val sections = buildSectionsFromPageEntries(physicallyObservedHeadings, totalPages)
            return LocalStructureResult(sections = sections, isPhysicallyTrustworthy = true)
        }

        // 2. If Strategy A found TOC sections AND verified the offset with Chapter 1 physical location:
        if (isTocOffsetVerified && tocOffset != null && tocSections.size >= 2) {
            val offset = tocOffset
            val adjusted = tocSections.map { (title, printed) ->
                title to (printed + offset).coerceIn(1, totalPages)
            }
            val sections = buildSectionsFromPageEntries(adjusted, totalPages)
            return LocalStructureResult(sections = sections, isPhysicallyTrustworthy = true)
        }

        // 3. Fallback: If TOC sections were parsed but offset was NOT verified (or only printed page was found):
        if (tocSections.size >= 2) {
            // Mapping is uncertain! Return sections with isPhysicallyTrustworthy = FALSE so AI is not skipped
            val unverifiedSections = buildSectionsFromPageEntries(tocSections, totalPages)
            return LocalStructureResult(sections = unverifiedSections, isPhysicallyTrustworthy = false)
        }

        return LocalStructureResult(emptyList(), false)
    }

    /**
     * Local Table of Contents / chapter heading parser from front-matter text.
     */
    fun extractLocalFrontMatterStructure(
        pdfFile: File,
        totalPages: Int,
        fallbackTitle: String
    ): List<ExtractedChapterSection> {
        val pagesText = extractPhysicalPagesText(pdfFile, 20.coerceAtMost(totalPages))
        val result = extractLocalFrontMatterStructure(pagesText, totalPages, fallbackTitle)
        return result.sections
    }

    private fun buildSectionsFromPageEntries(
        entries: List<Pair<String, Int>>,
        totalPages: Int
    ): List<ExtractedChapterSection> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.second }
        val sections = mutableListOf<ExtractedChapterSection>()
        var coreCounter = 1

        for (i in sorted.indices) {
            val (title, start) = sorted[i]
            val cleanTitle = title.trim().ifEmpty { "Chapter ${i + 1}" }
            val end = if (i < sorted.size - 1) {
                (sorted[i + 1].second - 1).coerceAtLeast(start)
            } else {
                totalPages
            }
            val sectionType = ExtractedChapterSection.normalizeSectionType("", cleanTitle)
            val chapNum = if (sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) coreCounter++ else null
            sections.add(
                ExtractedChapterSection(
                    title = cleanTitle,
                    sectionType = sectionType,
                    chapterNumber = chapNum,
                    startPage = start,
                    endPage = end,
                    orderIndex = i + 1
                )
            )
        }
        return sections
    }

    private fun scanPdfObjectOffsets(raf: RandomAccessFile, fileLength: Long): Map<Int, Long> {
        val offsets = mutableMapOf<Int, Long>()

        // 1. Try reading xref table at startxref
        try {
            val tailSize = (8 * 1024L).coerceAtMost(fileLength).toInt()
            val tailBuf = ByteArray(tailSize)
            raf.seek(fileLength - tailSize)
            raf.readFully(tailBuf)
            val tailStr = String(tailBuf, Charsets.ISO_8859_1)
            val startXrefMatch = Regex("""startxref\s+(\d+)""").find(tailStr)
            if (startXrefMatch != null) {
                val xrefOffset = startXrefMatch.groupValues[1].toLongOrNull()
                if (xrefOffset != null && xrefOffset in 0 until fileLength) {
                    raf.seek(xrefOffset)
                    val xrefHeader = (2048L).coerceAtMost(fileLength - xrefOffset).toInt()
                    val xrefBuf = ByteArray(xrefHeader)
                    raf.readFully(xrefBuf)
                    val xrefStr = String(xrefBuf, Charsets.ISO_8859_1)
                    if (xrefStr.startsWith("xref")) {
                        val subsectionMatch = Regex("""xref\s+(\d+)\s+(\d+)""").find(xrefStr)
                        if (subsectionMatch != null) {
                            val startId = subsectionMatch.groupValues[1].toInt()
                            val count = subsectionMatch.groupValues[2].toInt().coerceAtMost(500)
                            val entriesStart = xrefStr.indexOf('\n') + 1
                            val subLines = xrefStr.substring(entriesStart).lines()
                            var currentId = startId
                            for (line in subLines) {
                                val entryMatch = Regex("""^(\d{10})\s+(\d{5})\s+([nf])""").find(line.trim())
                                if (entryMatch != null) {
                                    val off = entryMatch.groupValues[1].toLongOrNull()
                                    val state = entryMatch.groupValues[3]
                                    if (state == "n" && off != null && off in 0 until fileLength) {
                                        offsets[currentId] = off
                                    }
                                    currentId++
                                    if (currentId >= startId + count) break
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Scan across file if xref was incomplete or missing
        if (offsets.size < 5) {
            try {
                val chunkSize = 64 * 1024
                val chunk = ByteArray(chunkSize)
                var filePos = 0L
                val objPattern = Regex("""(\d+)\s+(\d+)\s+obj\b""")

                while (filePos < fileLength) {
                    raf.seek(filePos)
                    val readBytes = (chunkSize.toLong()).coerceAtMost(fileLength - filePos).toInt()
                    raf.readFully(chunk, 0, readBytes)
                    val chunkStr = String(chunk, 0, readBytes, Charsets.ISO_8859_1)

                    for (m in objPattern.findAll(chunkStr)) {
                        val objNum = m.groupValues[1].toIntOrNull()
                        if (objNum != null && !offsets.containsKey(objNum)) {
                            offsets[objNum] = filePos + m.range.first
                        }
                    }
                    if (readBytes < chunkSize) break
                    filePos += (readBytes - 64)
                }
            } catch (_: Exception) {}
        }

        return offsets
    }

    private fun resolvePhysicalPageObjectIds(
        raf: RandomAccessFile,
        objOffsets: Map<Int, Long>,
        maxPages: Int
    ): List<Int> {
        val pages = mutableListOf<Int>()
        val visitedNodes = mutableSetOf<Int>()

        var catalogObjId: Int? = null
        try {
            val fileLength = raf.length()
            val tailSize = (16 * 1024L).coerceAtMost(fileLength).toInt()
            val tailBytes = ByteArray(tailSize)
            raf.seek(fileLength - tailSize)
            raf.readFully(tailBytes)
            val tail = String(tailBytes, Charsets.ISO_8859_1)
            val rootMatch = Regex("""/Root\s+(\d+)\s+(\d+)\s*R""").find(tail)
            if (rootMatch != null) {
                catalogObjId = rootMatch.groupValues[1].toIntOrNull()
            }
        } catch (_: Exception) {}

        if (catalogObjId == null) {
            for ((id, _) in objOffsets) {
                val dict = readObjectDictionary(raf, id, objOffsets)
                if (dict.contains("/Type") && Regex("""/Type\s*/Catalog\b""").containsMatchIn(dict)) {
                    catalogObjId = id
                    break
                }
            }
        }

        if (catalogObjId != null) {
            val catalogDict = readObjectDictionary(raf, catalogObjId, objOffsets)
            val pagesMatch = Regex("""/Pages\s+(\d+)\s+(\d+)\s*R""").find(catalogDict)
            val pagesRootId = pagesMatch?.groupValues?.get(1)?.toIntOrNull()
            if (pagesRootId != null) {
                fun collectKids(nodeId: Int) {
                    if (pages.size >= maxPages || !visitedNodes.add(nodeId)) return
                    val dict = readObjectDictionary(raf, nodeId, objOffsets)
                    val typeMatch = Regex("""/Type\s*/(Pages?)\b""").find(dict)
                    val type = typeMatch?.groupValues?.get(1) ?: ""

                    if (type == "Page" || (!dict.contains("/Pages") && (dict.contains("/Contents") || dict.contains("/MediaBox")))) {
                        pages.add(nodeId)
                        return
                    }

                    val kidsMatch = Regex("""/Kids\s*\[([^\]]+)\]""").find(dict)
                    if (kidsMatch != null) {
                        val refs = Regex("""(\d+)\s+\d+\s+R""").findAll(kidsMatch.groupValues[1])
                        for (ref in refs) {
                            val kidId = ref.groupValues[1].toIntOrNull() ?: continue
                            collectKids(kidId)
                            if (pages.size >= maxPages) break
                        }
                    }
                }
                collectKids(pagesRootId)
            }
        }

        if (pages.isEmpty()) {
            for ((id, _) in objOffsets.toSortedMap()) {
                val dict = readObjectDictionary(raf, id, objOffsets)
                if (Regex("""/Type\s*/Page\b""").containsMatchIn(dict) && !Regex("""/Type\s*/Pages\b""").containsMatchIn(dict)) {
                    pages.add(id)
                    if (pages.size >= maxPages) break
                }
            }
        }

        return pages
    }

    private fun readObjectDictionary(
        raf: RandomAccessFile,
        objId: Int,
        objOffsets: Map<Int, Long>
    ): String {
        val offset = objOffsets[objId] ?: return ""
        return try {
            raf.seek(offset)
            val readLen = (16 * 1024L).coerceAtMost(raf.length() - offset).toInt()
            val buf = ByteArray(readLen)
            raf.readFully(buf)
            val text = String(buf, Charsets.ISO_8859_1)
            val streamIdx = text.indexOf("stream")
            val limit = if (streamIdx != -1) streamIdx else text.indexOf("endobj").takeIf { it != -1 } ?: text.length
            text.substring(0, limit)
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractTextForPageObject(
        raf: RandomAccessFile,
        pageObjId: Int,
        objOffsets: Map<Int, Long>
    ): String {
        val dict = readObjectDictionary(raf, pageObjId, objOffsets)
        val textBuilder = StringBuilder()

        val contentObjIds = mutableListOf<Int>()
        val contentsArrayMatch = Regex("""/Contents\s*\[([^\]]+)\]""").find(dict)
        if (contentsArrayMatch != null) {
            val refs = Regex("""(\d+)\s+\d+\s+R""").findAll(contentsArrayMatch.groupValues[1])
            for (ref in refs) {
                ref.groupValues[1].toIntOrNull()?.let { contentObjIds.add(it) }
            }
        } else {
            val contentsSingleMatch = Regex("""/Contents\s+(\d+)\s+\d+\s*R""").find(dict)
            if (contentsSingleMatch != null) {
                contentsSingleMatch.groupValues[1].toIntOrNull()?.let { contentObjIds.add(it) }
            }
        }

        if (contentObjIds.isNotEmpty()) {
            for (streamObjId in contentObjIds) {
                val streamOffset = objOffsets[streamObjId] ?: continue
                val streamText = readStreamText(raf, streamOffset)
                if (streamText.isNotBlank()) {
                    textBuilder.append(streamText).append("\n")
                }
            }
        } else {
            val pageOffset = objOffsets[pageObjId]
            if (pageOffset != null) {
                val streamText = readStreamText(raf, pageOffset)
                if (streamText.isNotBlank()) {
                    textBuilder.append(streamText).append("\n")
                }
            }
        }

        return textBuilder.toString().trim()
    }

    private fun readStreamText(raf: RandomAccessFile, objOffset: Long): String {
        try {
            raf.seek(objOffset)
            val headerLen = (4096L).coerceAtMost(raf.length() - objOffset).toInt()
            val headerBuf = ByteArray(headerLen)
            raf.readFully(headerBuf)
            val headerStr = String(headerBuf, Charsets.ISO_8859_1)

            val streamKeyword = "stream"
            val startStreamRel = headerStr.indexOf(streamKeyword)
            if (startStreamRel == -1) return ""

            var payloadStartRel = startStreamRel + streamKeyword.length
            if (payloadStartRel < headerBuf.size && headerBuf[payloadStartRel] == '\r'.code.toByte()) payloadStartRel++
            if (payloadStartRel < headerBuf.size && headerBuf[payloadStartRel] == '\n'.code.toByte()) payloadStartRel++

            val actualStreamStart = objOffset + payloadStartRel
            raf.seek(actualStreamStart)

            var length = -1
            val lenMatch = Regex("""/Length\s+(\d+)""").find(headerStr)
            if (lenMatch != null) {
                length = lenMatch.groupValues[1].toIntOrNull() ?: -1
            }

            val streamBytes: ByteArray
            if (length in 1..1048576) {
                streamBytes = ByteArray(length)
                raf.readFully(streamBytes)
            } else {
                val maxScan = (256 * 1024L).coerceAtMost(raf.length() - actualStreamStart).toInt()
                val scanBuf = ByteArray(maxScan)
                raf.readFully(scanBuf)
                val scanStr = String(scanBuf, Charsets.ISO_8859_1)
                val endIdx = scanStr.indexOf("endstream")
                val endStreamRel = if (endIdx != -1) endIdx else scanBuf.size
                streamBytes = ByteArray(endStreamRel)
                System.arraycopy(scanBuf, 0, streamBytes, 0, endStreamRel)
            }

            val isFlate = headerStr.contains("/FlateDecode")
            val content = if (isFlate) {
                decompressFlate(streamBytes)
            } else {
                String(streamBytes, Charsets.ISO_8859_1)
            }

            return extractTextFromPdfContentStream(content)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun decompressFlate(bytes: ByteArray): String {
        return try {
            val inflater = Inflater(false)
            inflater.setInput(bytes)
            val outBuf = ByteArray(16384)
            val outStream = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(outBuf)
                if (count == 0) break
                outStream.write(outBuf, 0, count)
                if (outStream.size() > 65536) break
            }
            inflater.end()
            outStream.toString(Charsets.ISO_8859_1.name())
        } catch (_: Exception) {
            try {
                val inflater = Inflater(true)
                inflater.setInput(bytes)
                val outBuf = ByteArray(16384)
                val outStream = ByteArrayOutputStream()
                while (!inflater.finished()) {
                    val count = inflater.inflate(outBuf)
                    if (count == 0) break
                    outStream.write(outBuf, 0, count)
                    if (outStream.size() > 65536) break
                }
                inflater.end()
                outStream.toString(Charsets.ISO_8859_1.name())
            } catch (_: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }

    private fun extractTextFromPdfContentStream(content: String): String {
        val textBuilder = StringBuilder()

        val tjRegex = Regex("""\(([^)]*)\)\s*Tj""")
        for (m in tjRegex.findAll(content)) {
            val token = sanitizePdfString(m.groupValues[1])
            if (token.isNotBlank()) textBuilder.append(token).append(" ")
        }

        val arrayTjRegex = Regex("""\[(.*?)\]\s*TJ""")
        for (m in arrayTjRegex.findAll(content)) {
            val arrayContent = m.groupValues[1]
            for (item in Regex("""\(([^)]*)\)""").findAll(arrayContent)) {
                val token = sanitizePdfString(item.groupValues[1])
                if (token.isNotBlank()) textBuilder.append(token)
            }
            textBuilder.append("\n")
        }

        val quoteRegex = Regex("""\(([^)]*)\)\s*['"]""")
        for (m in quoteRegex.findAll(content)) {
            val token = sanitizePdfString(m.groupValues[1])
            if (token.isNotBlank()) textBuilder.append(token).append("\n")
        }

        return textBuilder.toString().trim()
    }

    private fun extractRawBytesFallbackText(pdfFile: File, maxBytesToRead: Int): String {
        val textBuilder = StringBuilder()
        try {
            RandomAccessFile(pdfFile, "r").use { raf ->
                val readLen = maxBytesToRead.toLong().coerceAtMost(raf.length()).toInt()
                val buffer = ByteArray(readLen)
                raf.seek(0)
                raf.readFully(buffer)
                val content = String(buffer, Charsets.ISO_8859_1)

                val tjRegex = Regex("""\(([^)]+)\)\s*Tj""")
                for (match in tjRegex.findAll(content)) {
                    val token = sanitizePdfString(match.groupValues[1])
                    if (token.isNotBlank()) textBuilder.append(token).append(" ")
                }

                val arrayTjRegex = Regex("""\[(.*?)\]\s*TJ""")
                for (match in arrayTjRegex.findAll(content)) {
                    val arrayContent = match.groupValues[1]
                    for (item in Regex("""\(([^)]+)\)""").findAll(arrayContent)) {
                        val token = sanitizePdfString(item.groupValues[1])
                        if (token.isNotBlank()) textBuilder.append(token)
                    }
                    textBuilder.append("\n")
                }
            }
        } catch (_: Exception) {}
        return textBuilder.toString().trim()
    }

    private fun sanitizePdfString(raw: String): String {
        return raw.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .trim()
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

    /**
     * Creates deterministic fallback sections preserving detected chapter titles as hints,
     * without fabricating uncertain exact physical page boundaries.
     */
    fun createFallbackSectionsWithTitles(
        totalPages: Int,
        titles: List<String>,
        bookTitle: String
    ): List<ExtractedChapterSection> {
        val pages = totalPages.coerceAtLeast(1)
        val validTitles = titles.map { it.trim() }.filter { it.isNotBlank() }
        if (validTitles.isEmpty()) return createFallbackSections(pages, bookTitle)

        val chapterCount = validTitles.size.coerceIn(1, pages)
        val pagesPerChapter = (pages / chapterCount).coerceAtLeast(1)
        val list = mutableListOf<ExtractedChapterSection>()

        for (i in 0 until chapterCount) {
            val title = validTitles[i]
            val start = (i * pagesPerChapter) + 1
            val end = if (i == chapterCount - 1) pages else ((i + 1) * pagesPerChapter).coerceAtMost(pages)
            val sectionType = ExtractedChapterSection.normalizeSectionType("", title)
            list.add(
                ExtractedChapterSection(
                    title = title,
                    sectionType = sectionType,
                    chapterNumber = if (sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) i + 1 else null,
                    startPage = start,
                    endPage = end.coerceAtLeast(start),
                    orderIndex = i + 1
                )
            )
        }
        return list
    }
}
