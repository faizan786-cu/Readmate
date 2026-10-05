package com.example.data.manager

enum class PassageSectionType(val displayName: String) {
    UNDERSTANDING("Understanding / Asaan Samjh"),
    MAIN_LESSON("Core Takeaway / Main Lesson"),
    KEY_POINTS("Key Insights / Key Points"),
    REAL_LIFE_EXAMPLE("Real-Life Example")
}

data class QualityValidationResult(
    val isValid: Boolean,
    val isShortPassage: Boolean,
    val missingSections: List<String>,
    val weakSections: List<String>,
    val issues: List<String>,
    val blockingViolations: List<String> = emptyList(),
    val sectionContents: Map<PassageSectionType, String> = emptyMap(),
    val bulletCount: Int = 0
) {
    val defectScore: Int
        get() = (missingSections.size * 10) + (blockingViolations.size * 6) + (weakSections.size * 3)

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

    fun isShortPassage(passage: String): Boolean {
        val words = passage.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size < SHORT_PASSAGE_WORD_THRESHOLD
    }

    fun isMeaningfullyBetter(
        initial: QualityValidationResult,
        repaired: QualityValidationResult
    ): Boolean {
        // 1. If repaired response is fully valid: accept repaired response
        if (repaired.isValid) return true

        // If initial is already valid (defensive guard, repair not needed)
        if (initial.isValid) return false

        // 2. If repair is still invalid: compare initial validation vs repaired validation using quality defects, NOT string length
        // Defect score penalizes: missing sections (10), blocking violations (6), weak sections (3)
        // Accept repaired response ONLY if it is meaningfully better (strictly lower defect penalty score)
        return repaired.defectScore < initial.defectScore
    }

    fun validate(passage: String, response: String): QualityValidationResult {
        val trimmedResponse = response.trim()
        val shortPassage = isShortPassage(passage)

        if (trimmedResponse.isBlank()) {
            val emptyIssues = listOf("Response is completely empty.")
            return QualityValidationResult(
                isValid = false,
                isShortPassage = shortPassage,
                missingSections = listOf("Understanding / Asaan Samjh", "Core Takeaway / Main Lesson", "Key Insights / Key Points", "Real-Life Example"),
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

            if (!shortPassage) {
                if (sentences <= 1 && words < 40) {
                    weak.add(PassageSectionType.UNDERSTANDING.displayName)
                    issues.add("Understanding section is only one short sentence ($words words); should provide deeper explanation of the passage.")
                } else if (paragraphs < 2 && words < 45) {
                    weak.add(PassageSectionType.UNDERSTANDING.displayName)
                    issues.add("Understanding section lacks depth ($words words, $paragraphs paragraph(s)); should contain at least 2 useful paragraphs or equivalent detail.")
                }
            } else {
                if (words < 12 && sentences <= 1) {
                    weak.add(PassageSectionType.UNDERSTANDING.displayName)
                    issues.add("Understanding section is too brief for this passage.")
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
            if (!shortPassage) {
                if (words < 16) {
                    weak.add(PassageSectionType.MAIN_LESSON.displayName)
                    issues.add("Main Lesson is too brief or vague ($words words); should clearly articulate the central lesson.")
                }
            } else {
                if (words < 8) {
                    weak.add(PassageSectionType.MAIN_LESSON.displayName)
                    issues.add("Main Lesson is too brief ($words words).")
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

            if (!shortPassage) {
                if (bullets.size < 3) {
                    weak.add(PassageSectionType.KEY_POINTS.displayName)
                    issues.add("Key Points contains only ${bullets.size} point(s); should normally contain at least 3 distinct insights.")
                } else {
                    val shallowCount = bullets.count { isShallowBullet(it) }
                    if (shallowCount > 1 || (bullets.size == 3 && shallowCount >= 2)) {
                        weak.add(PassageSectionType.KEY_POINTS.displayName)
                        issues.add("Key Points contains shallow one-word or one-clause bullet points ($shallowCount shallow).")
                    }
                }

                // Guardrail against bloated bullet lists (>8 bullets is invalid)
                if (bullets.size > 8) {
                    val violation = "Key Points contains too many bullets (${bullets.size}); exceeds maximum allowed limit of 8."
                    blockingViolations.add(violation)
                    issues.add(violation)
                }
            } else {
                if (bullets.isEmpty()) {
                    weak.add(PassageSectionType.KEY_POINTS.displayName)
                    issues.add("Key Points section has no bullet points.")
                } else if (bullets.size > 8) {
                    val violation = "Key Points contains too many bullets (${bullets.size}); exceeds maximum allowed limit of 8."
                    blockingViolations.add(violation)
                    issues.add(violation)
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

            if (!shortPassage) {
                if (words < 22 || (sentences <= 1 && words < 32)) {
                    weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                    issues.add("Real-Life Example is only one generic sentence ($words words); should present an actual relatable scenario.")
                }
            } else {
                if (words < 10) {
                    weak.add(PassageSectionType.REAL_LIFE_EXAMPLE.displayName)
                    issues.add("Real-Life Example is too brief ($words words).")
                }
            }
        }

        // 5. Guardrail against verbatim repetition of original passage
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

    private fun isShallowBullet(bullet: String): Boolean {
        // Remove bold titles like **Title**:
        val content = bullet
            .replace(Regex("^\\*\\*[^\\*]+\\*\\*[:\\-]?\\s*"), "")
            .replace(Regex("^[^:\\-]+[:\\-]\\s*"), "")
            .trim()
        val contentWords = countWords(content)
        val totalWords = countWords(bullet)
        return contentWords < 4 || totalWords < 6
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
