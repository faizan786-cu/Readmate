package com.example.ui.util

object SentenceExtractor {

    /**
     * Extracts the complete English sentence containing the selected word/phrase from the passage.
     * Uses English sentence boundary delimiters (. ! ? and newlines) while respecting punctuation.
     */
    fun extractSentence(passage: String, selectedWordOrPhrase: String): String {
        val cleanPassage = passage.trim()
        val cleanWord = selectedWordOrPhrase.trim()
        if (cleanPassage.isEmpty() || cleanWord.isEmpty()) return cleanPassage

        // First attempt: search for whole phrase / word in the text (case-insensitive)
        val wordIndex = cleanPassage.indexOf(cleanWord, ignoreCase = true)
        if (wordIndex == -1) {
            // Try matching individual words if it's a multi-word phrase or trimmed differently
            return cleanPassage
        }

        // Search backward for sentence start
        var startIndex = 0
        for (i in (wordIndex - 1) downTo 0) {
            val c = cleanPassage[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Skip common abbreviations like Mr. Dr. etc if followed immediately
                val isAbbr = (i >= 2 && cleanPassage.substring(i - 2, i + 1).equals("mr.", ignoreCase = true)) ||
                             (i >= 2 && cleanPassage.substring(i - 2, i + 1).equals("dr.", ignoreCase = true)) ||
                             (i >= 3 && cleanPassage.substring(i - 3, i + 1).equals("mrs.", ignoreCase = true))
                if (!isAbbr) {
                    startIndex = i + 1
                    break
                }
            }
        }

        // Search forward for sentence end
        var endIndex = cleanPassage.length
        val phraseEnd = (wordIndex + cleanWord.length).coerceAtMost(cleanPassage.length)
        for (i in phraseEnd until cleanPassage.length) {
            val c = cleanPassage[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Include closing quotes or parentheses if right next to sentence end
                var end = i + 1
                while (end < cleanPassage.length && (cleanPassage[end] == '"' || cleanPassage[end] == '\'' || cleanPassage[end] == '’' || cleanPassage[end] == '”' || cleanPassage[end] == ')')) {
                    end++
                }
                endIndex = end
                break
            }
        }

        val extracted = cleanPassage.substring(startIndex, endIndex).trim()
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("“").removeSuffix("”")
            .removePrefix("'").removeSuffix("'")
            .trim()

        return if (extracted.isNotBlank()) extracted else cleanPassage
    }
}
