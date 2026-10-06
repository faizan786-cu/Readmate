package com.example.data.manager

import com.example.data.local.database.entity.ChapterMessage

/**
 * Manages bounded historical context for ReadMate passage explanations.
 * Preserves up to the latest 5 completed turns with substantially increased depth allowances,
 * governed by a strict global character budget (~12k-18k chars) with deterministic trimming priorities.
 */
object HistoricalContextManager {

    const val MAX_TURNS = 5
    const val GLOBAL_CONTEXT_BUDGET_CHARS = 16_000

    // Per-turn section allowances (increased substantially from previous restrictive caps)
    const val MAX_SOURCE_CHARS = 900           // Suggested: 700–1000 chars
    const val MAX_UNDERSTANDING_CHARS = 1800   // Suggested: 1500–2000 chars
    const val MAX_MAIN_LESSON_CHARS = 1000     // Suggested: 800–1200 chars
    const val MAX_KEY_INSIGHTS_CHARS = 1400    // Suggested: 1200–1600 chars
    const val MAX_EXAMPLE_CHARS = 1400         // Suggested: 1200–1600 chars
    const val MAX_FALLBACK_CHARS = 1800

    data class TurnContext(
        val turnNumber: Int,
        var source: String,
        var understanding: String?,
        var mainLesson: String?,
        var keyInsights: String?,
        var example: String?,
        var fallbackExplanation: String?
    ) {
        fun render(): String {
            val sb = StringBuilder()
            sb.append("Previous Passage #$turnNumber:\n")
            if (source.isNotBlank()) {
                sb.append("Source: $source\n")
            }
            if (!understanding.isNullOrBlank()) {
                sb.append("Understanding: $understanding\n")
            }
            if (!mainLesson.isNullOrBlank()) {
                sb.append("Core Takeaway: $mainLesson\n")
            }
            if (!keyInsights.isNullOrBlank()) {
                sb.append("Key Insights: $keyInsights\n")
            }
            if (!example.isNullOrBlank()) {
                sb.append("Example: $example\n")
            }
            if (understanding.isNullOrBlank() && mainLesson.isNullOrBlank() && !fallbackExplanation.isNullOrBlank()) {
                sb.append("Explanation: $fallbackExplanation\n")
            }
            return sb.toString().trim()
        }
    }

    /**
     * Builds the formatted <RECENT_READING_CONTEXT> from conversation history.
     * Retains at most the latest 5 turns.
     * Enforces the global character budget without ever modifying or truncating the current passage.
     */
    fun buildRecentContext(
        conversationHistory: List<ChapterMessage>,
        globalBudget: Int = GLOBAL_CONTEXT_BUDGET_CHARS
    ): String {
        val eligibleMessages = conversationHistory.filter { it.aiResponse.isNotBlank() }
        if (eligibleMessages.isEmpty()) {
            return "(No previous passages yet in this chapter. This is the first passage.)"
        }

        // Rule: At most latest 5 turns
        val latestFive = eligibleMessages.takeLast(MAX_TURNS)

        val turns = latestFive.mapIndexed { index, msg ->
            val sections = PassageExplanationValidator.extractSections(msg.aiResponse)
            val understanding = sections[PassageSectionType.UNDERSTANDING]?.trim()?.take(MAX_UNDERSTANDING_CHARS)
            val mainLesson = sections[PassageSectionType.MAIN_LESSON]?.trim()?.take(MAX_MAIN_LESSON_CHARS)
            val keyInsights = sections[PassageSectionType.KEY_POINTS]?.trim()?.take(MAX_KEY_INSIGHTS_CHARS)
            val example = sections[PassageSectionType.REAL_LIFE_EXAMPLE]?.trim()?.take(MAX_EXAMPLE_CHARS)
            val fallback = if (understanding.isNullOrBlank() && mainLesson.isNullOrBlank()) {
                msg.aiResponse.trim().take(MAX_FALLBACK_CHARS)
            } else null

            TurnContext(
                turnNumber = index + 1,
                source = msg.originalText.trim().take(MAX_SOURCE_CHARS),
                understanding = understanding,
                mainLesson = mainLesson,
                keyInsights = keyInsights,
                example = example,
                fallbackExplanation = fallback
            )
        }

        // Apply Global Context Budget trimming according to strict priority order
        applyBudgetTrimming(turns, globalBudget)

        return turns.joinToString("\n\n") { it.render() }
    }

    /**
     * Trimming priority order:
     * 1. newest previous turn (last in list) is preserved with highest priority
     * 2. next newest
     * 3. older turns (trimmed first)
     *
     * Within each turn preserve in priority:
     * 1. Understanding (preserved highest)
     * 2. Main Lesson
     * 3. Key Insights
     * 4. Example
     * 5. Source excerpt (trimmed first)
     */
    fun applyBudgetTrimming(turns: List<TurnContext>, budget: Int) {
        fun totalLength(): Int = turns.sumOf { it.render().length } + (turns.size - 1) * 2

        if (totalLength() <= budget) return

        // Step 1: Trim source excerpts from oldest to newest turns
        for (i in turns.indices) {
            if (totalLength() <= budget) return
            if (turns[i].source.length > 150) {
                turns[i].source = turns[i].source.take(150) + "..."
            }
        }

        // Step 2: Trim examples from oldest to newest turns
        for (i in turns.indices) {
            if (totalLength() <= budget) return
            val ex = turns[i].example
            if (ex != null && ex.length > 300) {
                turns[i].example = ex.take(300) + "..."
            }
        }

        // Step 3: Trim key insights from oldest to newest turns
        for (i in turns.indices) {
            if (totalLength() <= budget) return
            val ki = turns[i].keyInsights
            if (ki != null && ki.length > 350) {
                turns[i].keyInsights = ki.take(350) + "..."
            }
        }

        // Step 4: Trim main lesson from oldest to newest turns
        for (i in turns.indices) {
            if (totalLength() <= budget) return
            val ml = turns[i].mainLesson
            if (ml != null && ml.length > 250) {
                turns[i].mainLesson = ml.take(250) + "..."
            }
        }

        // Step 5: Trim understanding from oldest to newest turns (preserving newest)
        for (i in 0 until (turns.size - 1)) {
            if (totalLength() <= budget) return
            val u = turns[i].understanding
            if (u != null && u.length > 500) {
                turns[i].understanding = u.take(500) + "..."
            }
        }

        // Step 6: If still exceeding budget, aggressively strip lowest priority fields on oldest turns
        for (i in 0 until (turns.size - 1)) {
            if (totalLength() <= budget) return
            turns[i].source = ""
            turns[i].example = null
            if (totalLength() <= budget) return
            turns[i].keyInsights = null
        }
    }
}
