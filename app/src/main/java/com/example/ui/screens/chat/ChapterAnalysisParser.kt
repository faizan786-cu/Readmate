package com.example.ui.screens.chat

import com.example.data.local.database.entity.ChapterMessage
import com.example.ui.components.chat.PassageSanitizer

/**
 * Structured data representation for an analyzed reading passage entry.
 */
data class AnalyzedEntryData(
    val heading: String,
    val pageNumber: Int? = null,
    val understandingParagraphs: List<String>,
    val quote: String,
    val quoteExplanation: String = "",
    val quoteAuthor: String,
    val transitionBridge: String,
    val scenario: String,
    val coreTakeaway: String,
    val keyInsights: List<InsightBullet>,
    val originalPassage: String
)

data class InsightBullet(
    val leadTitle: String,
    val explanation: String
)

object ChapterAnalysisParser {

    /**
     * Extracts and maps a ChapterMessage into the editorial document structure.
     */
    fun parseEntry(
        message: ChapterMessage,
        author: String? = null,
        entryIndex: Int = 0
    ): AnalyzedEntryData {
        val rawResponse = message.aiResponse
        val rawOriginal = message.originalText

        val sections = parseMarkdownSections(rawResponse)

        var understandingRaw = ""
        var lessonRaw = ""
        var insightsRaw = ""
        var exampleRaw = ""
        var passageFromResponse = ""
        val unclassified = mutableListOf<String>()

        for (section in sections) {
            val title = section.title
            val body = section.body.trim()
            when {
                title.contains("Original English Passage", ignoreCase = true) ||
                title.contains("Original Passage", ignoreCase = true) ||
                title.contains("English Passage", ignoreCase = true) -> {
                    if (passageFromResponse.isBlank()) passageFromResponse = body
                }
                title.contains("Asaan Samjh", ignoreCase = true) ||
                title.contains("Samjh", ignoreCase = true) ||
                title.contains("Understanding", ignoreCase = true) ||
                title.contains("Simple Explanation", ignoreCase = true) ||
                title.contains("Explanation", ignoreCase = true) -> {
                    if (understandingRaw.isBlank()) understandingRaw = body else understandingRaw += "\n\n$body"
                }
                title.contains("Main Lesson", ignoreCase = true) ||
                title.contains("Lesson", ignoreCase = true) ||
                title.contains("Core", ignoreCase = true) ||
                title.contains("Sabaq", ignoreCase = true) -> {
                    if (lessonRaw.isBlank()) lessonRaw = body else lessonRaw += "\n\n$body"
                }
                title.contains("Key Points", ignoreCase = true) ||
                title.contains("Points", ignoreCase = true) ||
                title.contains("Insights", ignoreCase = true) ||
                title.contains("Takeaways", ignoreCase = true) ||
                title.contains("Nukaat", ignoreCase = true) -> {
                    if (insightsRaw.isBlank()) insightsRaw = body else insightsRaw += "\n\n$body"
                }
                title.contains("Real-World Example", ignoreCase = true) ||
                title.contains("Real-Life Example", ignoreCase = true) ||
                title.contains("Example", ignoreCase = true) ||
                title.contains("Application", ignoreCase = true) ||
                title.contains("Misaal", ignoreCase = true) -> {
                    if (exampleRaw.isBlank()) exampleRaw = body else exampleRaw += "\n\n$body"
                }
                else -> {
                    unclassified.add(if (title.isNotBlank()) "### $title\n$body" else body)
                }
            }
        }

        if (understandingRaw.isBlank()) {
            if (unclassified.isNotEmpty()) {
                understandingRaw = unclassified.removeAt(0)
            } else {
                understandingRaw = rawResponse
            }
        }

        if (unclassified.isNotEmpty()) {
            if (lessonRaw.isBlank() && unclassified.isNotEmpty()) {
                lessonRaw = unclassified.removeAt(0)
            } else if (insightsRaw.isBlank() && unclassified.isNotEmpty()) {
                insightsRaw = unclassified.removeAt(0)
            } else if (exampleRaw.isBlank() && unclassified.isNotEmpty()) {
                exampleRaw = unclassified.removeAt(0)
            }
        }

        val rawPassage = if (rawOriginal.isNotBlank()) {
            rawOriginal.trim()
        } else if (passageFromResponse.isNotBlank()) {
            passageFromResponse.trim()
        } else {
            ""
        }

        val sanitized = PassageSanitizer.sanitizeSnippet(rawPassage)
        val cleanPassage = sanitized.bodyText.ifBlank { rawPassage }
        val pageNum = sanitized.pageNumber

        // 1. Heading Extraction
        val rawHeading = sanitized.heading?.takeIf { it.isNotBlank() }
            ?: extractHeadingFromAiResponse(rawResponse)
            ?: extractHeadingFromLesson(lessonRaw)
            ?: "Core Synthesis • Entry #${String.format("%02d", entryIndex + 1)}"
        val heading = stripMarkdownAsterisks(rawHeading)

        // 2. Understanding Paragraphs (split into breathable 2-to-3 sentence paragraphs for effortless reading)
        val understandingParas = splitIntoBreathableParagraphs(understandingRaw)
            .map { stripMarkdownAsterisks(it) }
            .filter { it.isNotBlank() }

        // 3. Core Takeaway / Lesson
        val coreTakeaway = stripMarkdownAsterisks(
            lessonRaw
                .replace(Regex("^#+\\s*"), "")
                .replace(Regex("^(?:Main\\s+)?Lesson[:\\s]*", RegexOption.IGNORE_CASE), "")
                .trim()
        )

        // 4. Inline Extracted Book Quote & Attribution
        val quotePair = extractPullQuote(cleanPassage, author)
        val quoteUrduExplanation = extractQuoteExplanation(quotePair.first, understandingParas, coreTakeaway)

        // 5. Conversational Transition Bridge
        val transitionBridge = "Chalo isko ek practical misaal se samajhte hain..."

        // 6. Scenario / Real-Life Example
        val rawScenario = exampleRaw
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^(?:Real-Life|Real-World)?\\s*Example[:\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("“")
            .removeSuffix("”")
            .trim()
        val cleanScenario = stripMarkdownAsterisks(rawScenario)

        // 7. Key Insights (Clean Bullets)
        val insights = parseInsightBullets(insightsRaw)

        val finalPassage = stripMarkdownAsterisks(cleanPassage)

        return AnalyzedEntryData(
            heading = heading,
            pageNumber = pageNum,
            understandingParagraphs = if (understandingParas.isNotEmpty()) understandingParas else listOf(stripMarkdownAsterisks(understandingRaw)),
            quote = quotePair.first,
            quoteExplanation = quoteUrduExplanation,
            quoteAuthor = quotePair.second,
            transitionBridge = transitionBridge,
            scenario = cleanScenario.ifBlank { "Imagine applying this principle in your daily workflow to conquer mental barriers and elevate execution." },
            coreTakeaway = coreTakeaway.ifBlank { "True mastery begins with an internal shift before external results appear." },
            keyInsights = insights,
            originalPassage = finalPassage
        )
    }

    /**
     * Splits raw Roman Urdu text into breathable 2-to-3 sentence paragraphs.
     */
    fun splitIntoBreathableParagraphs(raw: String): List<String> {
        val rawBlocks = raw
            .split(Regex("\\r?\\n\\s*\\r?\\n+"))
            .map { it.replace(Regex("^#+\\s*"), "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("---") }

        if (rawBlocks.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        for (block in rawBlocks) {
            val sentences = block
                .split(Regex("(?<=[.!?])\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (sentences.size <= 3) {
                result.add(block)
            } else {
                var currentChunk = mutableListOf<String>()
                for (s in sentences) {
                    currentChunk.add(s)
                    if (currentChunk.size >= 3) {
                        result.add(currentChunk.joinToString(" "))
                        currentChunk = mutableListOf()
                    }
                }
                if (currentChunk.isNotEmpty()) {
                    if (currentChunk.size == 1 && result.isNotEmpty()) {
                        val last = result.removeAt(result.size - 1)
                        result.add("$last ${currentChunk[0]}")
                    } else {
                        result.add(currentChunk.joinToString(" "))
                    }
                }
            }
        }
        return result
    }

    private fun extractQuoteExplanation(
        quote: String,
        understandingParas: List<String>,
        coreTakeaway: String
    ): String {
        val allSentences = understandingParas.flatMap { para ->
            para.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }
        }.filter { it.length in 20..180 }

        val bestExplanation = allSentences.firstOrNull { s ->
            s.contains("yani", ignoreCase = true) ||
            s.contains("matlab", ignoreCase = true) ||
            s.contains("maqsad", ignoreCase = true) ||
            s.contains("musannif", ignoreCase = true) ||
            s.contains("asal", ignoreCase = true)
        } ?: allSentences.firstOrNull() ?: coreTakeaway.takeIf { it.isNotBlank() } ?: "Iska matlab yeh hai ke jab tak insaan apni andar ki soch ko nahi badalta, bahar ki haqeeqat nahi badal sakti."

        return bestExplanation.replace(Regex("[#*`_]"), "").trim()
    }

    /**
     * Extracts a single-line preview snippet for timeline listing cards.
     */
    fun extractPreviewSnippet(understandingParagraphs: List<String>, fallback: String): String {
        val firstPara = understandingParagraphs.firstOrNull { it.isNotBlank() } ?: fallback
        return firstPara
            .replace(Regex("[#*`_]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractHeadingFromAiResponse(raw: String): String? {
        val match = Regex("(?m)^#+\\s*([^\n]+)").find(raw)
        val title = match?.groupValues?.get(1)?.trim()
            ?.replace(Regex("^[🧠💡🔑🌎📖\\s]+"), "")
            ?.trim()
        if (!title.isNullOrBlank() &&
            !title.contains("Asaan Samjh", ignoreCase = true) &&
            !title.contains("Main Lesson", ignoreCase = true) &&
            !title.contains("Key Points", ignoreCase = true) &&
            title.length in 5..80
        ) {
            return title
        }
        return null
    }

    private fun extractHeadingFromLesson(lesson: String): String? {
        if (lesson.isBlank()) return null
        val sentence = lesson.split(Regex("[.!?\\n]")).firstOrNull { it.isNotBlank() }?.trim()
        if (sentence != null && sentence.length in 10..75) {
            return sentence.replace(Regex("[#*`_]"), "").trim()
        }
        return null
    }

    private fun extractPullQuote(passage: String, author: String?): Pair<String, String> {
        val authorAttribution = author?.takeIf { it.isNotBlank() } ?: "The Author"
        if (passage.isBlank()) {
            return Pair("Nothing splendid has ever been achieved except by those who dared believe that something inside them was superior to circumstance.", authorAttribution)
        }

        // Look for explicitly quoted text inside passage
        val quotedMatch = Regex("\"([^\"]{25,220})\"").find(passage)
        if (quotedMatch != null) {
            val q = quotedMatch.groupValues[1].trim()
            return Pair(q, authorAttribution)
        }

        // Extract the most impactful sentence (between 30 and 180 chars)
        val sentences = passage
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 30..180 && !it.startsWith("Page", ignoreCase = true) }

        val bestSentence = sentences.firstOrNull {
            it.contains("must", ignoreCase = true) ||
            it.contains("believe", ignoreCase = true) ||
            it.contains("never", ignoreCase = true) ||
            it.contains("power", ignoreCase = true) ||
            it.contains("mind", ignoreCase = true) ||
            it.contains("truth", ignoreCase = true)
        } ?: sentences.firstOrNull() ?: passage.take(140).trim()

        return Pair(bestSentence.removePrefix("\"").removeSuffix("\"").trim(), authorAttribution)
    }

    fun stripMarkdownAsterisks(text: String): String {
        return text.replace(Regex("\\*+"), "").trim()
    }

    private fun parseInsightBullets(raw: String): List<InsightBullet> {
        if (raw.isBlank()) {
            return listOf(
                InsightBullet("Mindset Foundation", "Internal visualization precedes tangible execution."),
                InsightBullet("Cognitive Repetition", "Active recall reinforces neural pathways against forgetting."),
                InsightBullet("Behavioral Discipline", "Consistent application yields compound knowledge mastery."),
                InsightBullet("Strategic Retention", "Synthesize principles into practical daily operating habits.")
            )
        }

        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && (it.startsWith("•") || it.startsWith("-") || it.startsWith("*") || it.matches(Regex("^\\d+\\..*"))) }

        val bullets = mutableListOf<InsightBullet>()

        for (line in lines) {
            val stripped = line
                .replace(Regex("^[•\\-*\\d.]+\\s*"), "")
                .trim()

            // Check for **Title**: Explanation pattern
            val boldPattern = Regex("^\\*\\*([^*]+)\\*\\*[:\\s]*(.*)")
            val match = boldPattern.find(stripped)
            if (match != null) {
                val lead = stripMarkdownAsterisks(match.groupValues[1])
                val exp = stripMarkdownAsterisks(match.groupValues[2])
                bullets.add(InsightBullet(leadTitle = lead, explanation = exp))
            } else {
                val colonSplit = stripped.split(":", limit = 2)
                if (colonSplit.size == 2 && colonSplit[0].length in 3..35) {
                    bullets.add(InsightBullet(
                        leadTitle = stripMarkdownAsterisks(colonSplit[0]),
                        explanation = stripMarkdownAsterisks(colonSplit[1])
                    ))
                } else {
                    bullets.add(InsightBullet(
                        leadTitle = "",
                        explanation = stripMarkdownAsterisks(stripped)
                    ))
                }
            }
        }

        if (bullets.isEmpty()) {
            return listOf(
                InsightBullet("Core Concept", stripMarkdownAsterisks(raw.replace(Regex("[#*]"), "")).take(120))
            )
        }

        return bullets.take(4)
    }

    private data class ParsedSection(val title: String, val body: String)

    private fun parseMarkdownSections(raw: String): List<ParsedSection> {
        val lines = raw.lines()
        val sections = mutableListOf<ParsedSection>()
        var currentTitle = ""
        val currentBodyLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") continue
            if (trimmed.startsWith("#")) {
                val headerText = trimmed.trimStart('#').trim()
                if (currentTitle.isNotBlank() || currentBodyLines.isNotEmpty()) {
                    sections.add(
                        ParsedSection(
                            title = currentTitle.trim(),
                            body = currentBodyLines.joinToString("\n").trim()
                        )
                    )
                    currentBodyLines.clear()
                }
                currentTitle = headerText
            } else {
                currentBodyLines.add(line)
            }
        }

        if (currentTitle.isNotBlank() || currentBodyLines.isNotEmpty()) {
            sections.add(
                ParsedSection(
                    title = currentTitle.trim(),
                    body = currentBodyLines.joinToString("\n").trim()
                )
            )
        }

        return sections
    }
}
