package com.example.data.manager

enum class PassageSectionType(val displayName: String) {
    UNDERSTANDING("Understanding / Asaan Samjh"),
    MAIN_LESSON("Core Takeaway / Main Lesson"),
    KEY_POINTS("Key Insights / Key Points"),
    REAL_LIFE_EXAMPLE("Real-Life Example")
}

enum class PassageLengthBand {
    TINY,   // < 25 words (short quotes, aphorisms, 1-line passages)
    NORMAL, // 25..120 words (standard book snippets)
    LONG    // > 120 words (long multi-paragraph or multi-page snippets)
}

data class QualityValidationResult(
    val isValid: Boolean,
    val isShortPassage: Boolean,
    val passageBand: PassageLengthBand = if (isShortPassage) PassageLengthBand.TINY else PassageLengthBand.NORMAL,
    val missingSections: List<String>,
    val weakSections: List<String>,
    val issues: List<String>,
    val blockingViolations: List<String> = emptyList(),
    val sectionContents: Map<PassageSectionType, String> = emptyMap(),
    val bulletCount: Int = 0
) {
    val defectScore: Int
        get() = (missingSections.size * 15) + (blockingViolations.size * 6) + (weakSections.size * 3)

    fun generateRepairInstructions(): String {
        val sb = StringBuilder()
        if (missingSections.isNotEmpty()) {
            sb.append("• Missing required section(s): ").append(missingSections.joinToString(", "))
                .append(". You must include these with complete depth.\n")
        }
        if (blockingViolations.isNotEmpty()) {
            for (violation in blockingViolations) {
                sb.append("• ").append(violation).append("\n")
            }
        }
        if (weakSections.isNotEmpty()) {
            sb.append("• Section(s) needing greater pedagogical depth: ").append(weakSections.joinToString(", "))
                .append(". Expand these sections thoroughly.\n")
        }
        for (issue in issues) {
            if (issue !in blockingViolations) {
                sb.append("• ").append(issue).append("\n")
            }
        }
        return sb.toString().trim()
    }
}

object PassageExplanationValidator {

    private const val SHORT_PASSAGE_WORD_THRESHOLD = 25
    private const val LONG_PASSAGE_WORD_THRESHOLD = 120

    fun getPassageBand(passage: String): PassageLengthBand {
        val words = countWords(passage)
        return when {
            words < SHORT_PASSAGE_WORD_THRESHOLD -> PassageLengthBand.TINY
            words <= LONG_PASSAGE_WORD_THRESHOLD -> PassageLengthBand.NORMAL
            else -> PassageLengthBand.LONG
        }
    }

    fun isShortPassage(passage: String): Boolean {
        return getPassageBand(passage) == PassageLengthBand.TINY
    }

    fun isMeaningfullyBetter(
        initial: QualityValidationResult,
        repaired: QualityValidationResult
    ): Boolean {
        // 1. If repaired response is fully valid: accept repaired response
        if (repaired.isValid) return true

        // If initial is already valid (defensive guard, repair not needed)
        if (initial.isValid) return false

        // A repair that has more missing sections than the initial draft is a structural regression
        if (repaired.missingSections.size > initial.missingSections.size) return false

        // 2. If repair is still invalid: compare initial validation vs repaired validation using quality defects
        // Defect score penalizes: missing sections (15), blocking violations (6), weak sections (3)
        // Accept repaired response ONLY if it is meaningfully better (strictly lower defect penalty score)
        return repaired.defectScore < initial.defectScore
    }

    fun validate(passage: String, response: String): QualityValidationResult {
        val trimmedResponse = response.trim()
        val band = getPassageBand(passage)
        val shortPassage = band == PassageLengthBand.TINY

        if (trimmedResponse.isBlank()) {
            val emptyIssues = listOf("Response is completely empty.")
            return QualityValidationResult(
                isValid = false,
                isShortPassage = shortPassage,
                passageBand = band,
                missingSections = listOf(
                    PassageSectionType.UNDERSTANDING.displayName,
                    PassageSectionType.MAIN_LESSON.displayName,
                    PassageSectionType.KEY_POINTS.displayName,
                    PassageSectionType.REAL_LIFE_EXAMPLE.displayName
                ),
                weakSections = emptyList(),
                issues = emptyIssues,
                blockingViolations = emptyIssues
            )
        }

        val sections = extractSections(trimmedResponse)
        val missing = mutableListOf<String>()
        val weak = mutableListOf<String>()
        val issues = mutableListOf<String>()
        val blockingViolations = mutableListOf<String>()

        // 1. Validate Understanding / Asaan Samjh
        val understanding = sections[PassageSectionType.UNDERSTANDING]?.trim()
        if (understanding.isNullOrBlank()) {
            missing.add(PassageSectionType.UNDERSTANDING.displayName)
            issues.add("Missing '${PassageSectionType.UNDERSTANDING.displayName}' section.")
        } else {
            val words = countWords(understanding)
            val sentences = countSentences(understanding)
            val paragraphs = countParagraphs(understanding)

            when (band) {
                PassageLengthBand.TINY -> {
                    if (words < 12 || sentences < 1) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh is too short ($words words); provide at least one clear explanatory explanation.")
                    }
                }
                PassageLengthBand.NORMAL -> {
                    if (paragraphs < 4) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh is too short: expand step-by-step explanation into at least 4 substantive paragraphs ($paragraphs paragraph(s) provided).")
                    } else if (words < 180) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh is too short: expand step-by-step explanation ($words words, minimum ~180-250 words).")
                    } else if (sentences < 5) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh contains short fragments or too few sentences ($sentences sentence(s)); provide multiple explanatory sentences.")
                    }
                }
                PassageLengthBand.LONG -> {
                    if (paragraphs < 4) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh is too short for a long/complex passage: expand into at least 4-5 substantive paragraphs ($paragraphs paragraph(s) provided).")
                    } else if (words < 240) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh lacks depth for a long/complex passage ($words words, minimum 240 words).")
                    } else if (sentences < 6) {
                        weak.add(PassageSectionType.UNDERSTANDING.displayName)
                        issues.add("Asaan Samjh contains too few explanatory sentences for this passage ($sentences sentences).")
                    }
                }
            }
        }

        // 2. Validate Core Takeaway / Main Lesson
        val mainLesson = sections[PassageSectionType.MAIN_LESSON]?.trim()
        if (mainLesson.isNullOrBlank()) {
            missing.add(PassageSectionType.MAIN_LESSON.displayName)
            issues.add("Missing '${PassageSectionType.MAIN_LESSON.displayName}' section.")
        } else {
            val words = countWords(mainLesson)
            val paragraphs = countParagraphs(mainLesson)

            when (band) {
                PassageLengthBand.TINY -> {
                    if (words < 6) {
                        weak.add(PassageSectionType.MAIN_LESSON.displayName)
                        issues.add("Main Lesson is too brief ($words words).")
                    }
                }
                PassageLengthBand.NORMAL -> {
                    if (words < 80) {
                        weak.add(PassageSectionType.MAIN_LESSON.displayName)
                        issues.add("Main Lesson lacks depth: explain why the lesson matters ($words words, minimum ~80-120 words).")
                    } else if (paragraphs < 2) {
                        weak.add(PassageSectionType.MAIN_LESSON.displayName)
                        issues.add("Main Lesson lacks depth: explain the core takeaway across at least 2 meaningful paragraphs ($paragraphs paragraph(s) provided).")
                    }
                }
                PassageLengthBand.LONG -> {
                    if (words < 90) {
                        weak.add(PassageSectionType.MAIN_LESSON.displayName)
                        issues.add("Main Lesson lacks depth for complex passage ($words words, minimum 90-120 words).")
                    } else if (paragraphs < 2) {
                        weak.add(PassageSectionType.MAIN_LESSON.displayName)
                        issues.add("Main Lesson lacks depth: explain across at least 2 meaningful paragraphs.")
                    }
                }
            }
        }

        // 3. Validate Key Insights / Key Points
        val keyPoints = sections[PassageSectionType.KEY_POINTS]?.trim()
        var totalBullets = 0
        if (keyPoints.isNullOrBlank()) {
            missing.add(PassageSectionType.KEY_POINTS.displayName)
            issues.add("Missing '${PassageSectionType.KEY_POINTS.displayName}' section.")
        } else {
            val bullets = extractBullets(keyPoints)
            totalBullets = bullets.size

            when (band) {
                PassageLengthBand.TINY -> {
                    if (bullets.isEmpty()) {
                        weak.add(PassageSectionType.KEY_POINTS.displayName)
                        issues.add("Key Points section has no bullet points.")
                    } else if (bullets.size > 8) {
                        val violation = "Key Points contains too many bullets (${bullets.size}); exceeds maximum allowed limit of 8."
                        blockingViolations.add(violation)
                        issues.add(violation)
                    }
                }
                PassageLengthBand.NORMAL, PassageLengthBand.LONG -> {
                    if (bullets.size < 4) {
                        weak.add(PassageSectionType.KEY_POINTS.displayName)
                        issues.add("Key Insights are under-explained: provide at least 4 distinct insights (currently ${bullets.size} point(s)).")
                    } else {
                        val shallowBullets = bullets.filter { isShallowBullet(it) }
                        if (shallowBullets.isNotEmpty()) {
                            weak.add(PassageSectionType.KEY_POINTS.displayName)
                            issues.add("Key Insights are under-explained: headline-only bullets without explanation must be expanded (each point must contain actual explanation >= 20 words; ${shallowBullets.size} shallow point(s) found).")
                        }
                    }

                    if (bullets.size > 8) {
                        val violation = "Key Points contains too many bullets (${bullets.size}); exceeds maximum allowed limit of 8."
                        blockingViolations.add(violation)
                        issues.add(violation)
                    }
                }
            }
        }

        // 4. Validate Real-Life Example
        val example = sections[PassageSectionType.REAL_LIFE_EXAMPLE]?.trim()
        if (example.isNullOrBlank()) {
            missing.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
            issues.add("Missing '${PassageSectionType.REAL_LIFE_EXAMPLE.displayName}' section.")
        } else {
            val words = countWords(example)
            val sentences = countSentences(example)
            val paragraphs = countParagraphs(example)

            when (band) {
                PassageLengthBand.TINY -> {
                    if (words < 15) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example is too brief ($words words).")
                    }
                }
                PassageLengthBand.NORMAL -> {
                    if (words < 120) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example is too brief: provide a full realistic scenario with situation, action/decision, outcome, and explicit link back to the passage ($words words, minimum ~120-180 words).")
                    } else if (paragraphs < 2) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example lacks depth: provide across at least 2-3 meaningful paragraphs ($paragraphs paragraph(s) provided).")
                    } else if (sentences < 3) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example contains too few sentences to establish situation, action, and outcome ($sentences sentence(s)).")
                    }
                }
                PassageLengthBand.LONG -> {
                    if (words < 140) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example is too brief for a complex passage ($words words, minimum 140 words).")
                    } else if (paragraphs < 2) {
                        weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                        issues.add("Real-Life Example lacks depth: provide across at least 2-3 meaningful paragraphs.")
                    }
                }
            }
        }

        // 5. Overall Underdevelopment check (scaled adaptively)
        val totalWords = countWords(trimmedResponse)
        when (band) {
            PassageLengthBand.TINY -> {
                if (totalWords < 45) {
                    weak.add("Overall Explanation Depth")
                    issues.add("Total response is underdeveloped for this passage ($totalWords words).")
                }
            }
            PassageLengthBand.NORMAL -> {
                if (totalWords < 480) {
                    weak.add("Overall Explanation Depth")
                    issues.add("Total response is underdeveloped ($totalWords words; minimum ~500 words for normal passages).")
                }
            }
            PassageLengthBand.LONG -> {
                if (totalWords < 550) {
                    weak.add("Overall Explanation Depth")
                    issues.add("Total response lacks depth for a long/complex passage ($totalWords words; minimum 550-600 words).")
                }
            }
        }

        // 6. Guardrail against verbatim repetition of original passage
        val normalizedPassage = passage.replace(Regex("\\s+"), " ").trim()
        val normalizedResponse = trimmedResponse.replace(Regex("\\s+"), " ").trim()
        if (normalizedPassage.length >= 30 && normalizedResponse.contains(normalizedPassage, ignoreCase = true)) {
            val violation = "Response repeats the original English passage verbatim, which is prohibited by ReadMate mandate."
            blockingViolations.add(violation)
            issues.add(violation)
        }

        val isValid = missing.isEmpty() && weak.isEmpty() && blockingViolations.isEmpty()

        return QualityValidationResult(
            isValid = isValid,
            isShortPassage = shortPassage,
            passageBand = band,
            missingSections = missing,
            weakSections = weak,
            issues = issues,
            blockingViolations = blockingViolations,
            sectionContents = sections,
            bulletCount = totalBullets
        )
    }

    fun extractSections(text: String): Map<PassageSectionType, String> {
        val result = mutableMapOf<PassageSectionType, StringBuilder>()
        var currentSection: PassageSectionType? = null

        val lines = text.lines()
        for (line in lines) {
            val detected = detectHeadingType(line)
            if (detected != null) {
                currentSection = detected
                result.getOrPut(detected) { StringBuilder() }
            } else if (currentSection != null) {
                result[currentSection]?.appendLine(line)
            }
        }

        return result.mapValues { it.value.toString().trim() }
    }

    private fun detectHeadingType(line: String): PassageSectionType? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Must look like a markdown header (##), bold title line (**Header**), or short colon title (Header:)
        val isMarkdownHeader = trimmed.startsWith("#")
        val isBoldHeader = trimmed.startsWith("**") && (trimmed.endsWith("**") || trimmed.contains("**:") || trimmed.contains("**:"))
        val isColonHeader = (trimmed.endsWith(":") || trimmed.endsWith(":-")) && countWords(trimmed) <= 5

        if (!isMarkdownHeader && !isBoldHeader && !isColonHeader) {
            return null
        }

        // Clean out emojis, markdown symbols, and leading/trailing punctuation for detection
        val cleaned = trimmed
            .replace(Regex("^[#*\\-\\s]+"), "")
            .replace(Regex("[*#:\\s]+$"), "")
            .replace(Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]"), "") // remove emoji surrogate pairs
            .trim()
            .lowercase()

        return when {
            cleaned.contains("asaan samjh") ||
                cleaned.contains("asan samjh") ||
                cleaned.contains("understanding") ||
                cleaned.contains("wazahat") ||
                cleaned.startsWith("explanation") -> PassageSectionType.UNDERSTANDING

            cleaned.contains("main lesson") ||
                cleaned.contains("core takeaway") ||
                cleaned.contains("takeaway") ||
                cleaned.contains("markazi sabaq") ||
                cleaned.contains("sabaq") ||
                cleaned.startsWith("lesson") -> PassageSectionType.MAIN_LESSON

            cleaned.contains("key points") ||
                cleaned.contains("key insights") ||
                cleaned.contains("important points") ||
                cleaned.contains("nukaat") ||
                cleaned.contains("main points") -> PassageSectionType.KEY_POINTS

            cleaned.contains("real-life example") ||
                cleaned.contains("real life example") ||
                cleaned.contains("practical example") ||
                cleaned.contains("misaal") ||
                cleaned.contains("example") -> PassageSectionType.REAL_LIFE_EXAMPLE

            else -> null
        }
    }

    private fun extractBullets(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = mutableListOf<String>()
        val bulletRegex = Regex("^([•*\\-]\\s*|\\d+[.)]\\s*)")

        var currentBullet: StringBuilder? = null
        for (line in lines) {
            if (bulletRegex.containsMatchIn(line)) {
                if (currentBullet != null && currentBullet.isNotBlank()) {
                    bullets.add(currentBullet.toString().trim())
                }
                currentBullet = StringBuilder(line.replace(bulletRegex, "").trim())
            } else if (currentBullet != null) {
                currentBullet.append(" ").append(line)
            }
        }
        if (currentBullet != null && currentBullet.isNotBlank()) {
            bullets.add(currentBullet.toString().trim())
        }

        return bullets
    }

    fun isShallowBullet(bullet: String): Boolean {
        // Strip bold label, e.g., "**Behavior Banam Ilm**:" or "**Point 1**:"
        val content = bullet
            .replace(Regex("^\\*\\*[^\\*]+\\*\\*[:\\-]?\\s*"), "")
            .replace(Regex("^[^:\\-]+[:\\-]\\s*"), "")
            .trim()
        val contentWords = countWords(content)
        val totalWords = countWords(bullet)
        // A substantive bullet has explanation, not just a headline or short phrase
        return contentWords < 14 || totalWords < 18
    }

    fun countWords(text: String): Int {
        return text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    fun countSentences(text: String): Int {
        return text.split(Regex("[.!?]+|\\n{2,}")).map { it.trim() }.filter { it.length > 5 }.size
    }

    fun countParagraphs(text: String): Int {
        return text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }.size
    }
}
