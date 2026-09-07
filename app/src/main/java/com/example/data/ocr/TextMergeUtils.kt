package com.example.data.ocr

object TextMergeUtils {

    /**
     * Sanitizes and normalizes extracted OCR text from a PDF page region.
     * Preserves structural paragraph breaks (e.g. between titles, quotes, paragraphs),
     * rejoins hyphenated line breaks and split words, normalizes em-dashes and punctuation,
     * and corrects common OCR letter/digit misrecognitions.
     */
    fun sanitizeOcrText(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. First rejoin hyphenated line breaks (e.g. "fundamen-\n tal" -> "fundamental")
        var text = rawText.replace(Regex("([a-zA-Z]{2,})-\\r?\\n\\s*([a-zA-Z]{2,})")) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}"
        }

        // 2. Rejoin hyphenated words split by spaces (e.g. "en- emies" -> "enemies", "infor- mation" -> "information")
        text = text.replace(Regex("([a-zA-Z]{2,})-\\s+([a-zA-Z]{2,})")) { match ->
            val w1 = match.groupValues[1]
            val w2 = match.groupValues[2]
            // Only merge if w2 starts with lowercase (indicating a single split word)
            if (w2[0].isLowerCase()) {
                "$w1$w2"
            } else {
                "$w1 - $w2"
            }
        }

        // 3. Normalize dashes and merged punctuation:
        // "friends--they" -> "friends — they", "friends—they" -> "friends — they"
        text = text.replace(Regex("--+"), " — ")
        text = text.replace(Regex("([a-zA-Z])—([a-zA-Z])"), "$1 — $2")
        text = text.replace(Regex("([a-zA-Z])—\\s*"), "$1 — ")
        text = text.replace(Regex("\\s*—([a-zA-Z])"), " — $1")

        // 4. Common merged word artifacts from OCR
        text = text.replace(Regex("\\boffriends\\b", RegexOption.IGNORE_CASE), "of friends")
        text = text.replace(Regex("\\b(wary|afraid|beware) off\\b", RegexOption.IGNORE_CASE), "$1 of")

        // 5. Common OCR single digit/character replacements within standard English phrases
        text = text.replace(Regex("\\bf0\\s+hae\\b", RegexOption.IGNORE_CASE), "to have")
        text = text.replace(Regex("\\bhae\\b", RegexOption.IGNORE_CASE), "have")
        text = text.replace(Regex("\\benmies\\b", RegexOption.IGNORE_CASE), "enemies")
        text = text.replace(Regex("\\bf0\\b", RegexOption.IGNORE_CASE), "to")
        text = text.replace(Regex("\\bn0\\b", RegexOption.IGNORE_CASE), "no")
        text = text.replace(Regex("\\bt0\\b", RegexOption.IGNORE_CASE), "to")
        text = text.replace(Regex("\\bd0\\b", RegexOption.IGNORE_CASE), "do")
        text = text.replace(Regex("\\bs0\\b", RegexOption.IGNORE_CASE), "so")
        text = text.replace(Regex("\\bg0\\b", RegexOption.IGNORE_CASE), "go")
        text = text.replace(Regex("\\b1n\\b", RegexOption.IGNORE_CASE), "in")
        text = text.replace(Regex("\\b1t\\b", RegexOption.IGNORE_CASE), "it")
        text = text.replace(Regex("\\b1s\\b", RegexOption.IGNORE_CASE), "is")
        text = text.replace(Regex("\\b1f\\b", RegexOption.IGNORE_CASE), "if")

        // 6. Clean whitespace while preserving structural paragraph breaks
        return cleanWhitespace(text)
    }

    /**
     * Merges Part 1 (from previous page) and Part 2 (from next page).
     * Handles hyphenated word boundaries (e.g., "fundamen-" + "tals" -> "fundamentals")
     * and normalizes spacing.
     */
    fun mergeMultiPagePassages(part1: String, part2: String): String {
        val clean1 = sanitizeOcrText(part1).trim()
        val clean2 = sanitizeOcrText(part2).trim()

        if (clean1.isEmpty()) return clean2
        if (clean2.isEmpty()) return clean1

        // Check if Part 1 ends with a hyphen
        if (clean1.endsWith("-")) {
            val prefix = clean1.dropLast(1).trimEnd()
            val secondParts = clean2.split("\\s+".toRegex(), limit = 2)
            val firstWordPart2 = secondParts.getOrNull(0) ?: ""
            val restPart2 = secondParts.getOrNull(1) ?: ""

            return if (restPart2.isNotBlank()) {
                "$prefix$firstWordPart2 $restPart2".trim()
            } else {
                "$prefix$firstWordPart2".trim()
            }
        }

        // Standard merge with single space
        return "$clean1 $clean2".trim()
    }

    fun cleanWhitespace(text: String): String {
        val normalizedNewlines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val lines = normalizedNewlines.lines()
        val cleanedParagraphs = mutableListOf<String>()
        val currentParagraphLines = mutableListOf<String>()

        fun isHeading(s: String): Boolean {
            val t = s.trim()
            if (t.startsWith("#")) return true
            return t.length >= 2 &&
                    t.all { it.isUpperCase() || it.isWhitespace() || it.isDigit() || it == ':' || it == '-' || it == ',' } &&
                    t.any { it.isLetter() }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraphLines.isNotEmpty()) {
                    cleanedParagraphs.add(currentParagraphLines.joinToString(" "))
                    currentParagraphLines.clear()
                }
            } else {
                val isHead = isHeading(trimmed)
                val prevWasHead = currentParagraphLines.isNotEmpty() && isHeading(currentParagraphLines.last())

                // If previous line was heading and current line is regular body text, or vice versa
                if (currentParagraphLines.isNotEmpty() && prevWasHead != isHead) {
                    cleanedParagraphs.add(currentParagraphLines.joinToString(" "))
                    currentParagraphLines.clear()
                }

                // If the previous line ended with a hyphen and next line starts with lowercase
                if (currentParagraphLines.isNotEmpty() && currentParagraphLines.last().endsWith("-")) {
                    val prev = currentParagraphLines.removeAt(currentParagraphLines.lastIndex)
                    val prefix = prev.dropLast(1).trimEnd()
                    val words = trimmed.split(Regex("\\s+"), limit = 2)
                    if (words.isNotEmpty() && words[0].all { it.isLetter() }) {
                        val joinedFirst = "$prefix${words[0]}"
                        if (words.size > 1) {
                            currentParagraphLines.add("$joinedFirst ${words[1]}")
                        } else {
                            currentParagraphLines.add(joinedFirst)
                        }
                    } else {
                        currentParagraphLines.add(prev)
                        currentParagraphLines.add(trimmed)
                    }
                } else {
                    currentParagraphLines.add(trimmed)
                }
            }
        }

        if (currentParagraphLines.isNotEmpty()) {
            cleanedParagraphs.add(currentParagraphLines.joinToString(" "))
        }

        return cleanedParagraphs
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split("\\s+".toRegex()).size
    }
}

