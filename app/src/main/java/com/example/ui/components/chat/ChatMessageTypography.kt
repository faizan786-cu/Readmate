package com.example.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.database.entity.WordVaultEntry
import com.example.ui.components.SelectablePassageView

/**
 * Data holder for sanitized excerpt content and its associated page metadata.
 */
data class SanitizedPassageInfo(
    val cleanText: String,
    val pageLabel: String,
    val pageNumber: Int? = null,
    val heading: String? = null,
    val bodyText: String = cleanText
)

/**
 * Comprehensive text sanitization utility designed specifically for book OCR excerpts
 * and AI Markdown responses.
 */
object PassageSanitizer {

    /**
     * Sanitizes raw book OCR passages:
     * - Extracts distinct bold titles or headings
     * - Rejoins hyphenated word breaks (e.g. "dis-\ncover" -> "discover", "infor- mation" -> "information")
     * - Removes OCR noise characters, stray pipe characters, unparsed markdown symbols (###, **, __)
     * - Merges single-line hard line wraps inside sentences while preserving paragraph breaks
     * - Detects and formats page annotations (e.g., "[Page 42]" -> pageLabel "ORIGINAL PASSAGE • PAGE 42")
     */
    fun sanitizeSnippet(rawText: String): SanitizedPassageInfo {
        if (rawText.isBlank()) {
            return SanitizedPassageInfo(cleanText = "", pageLabel = "ORIGINAL PASSAGE")
        }

        var text = rawText.trim()

        // 1. Detect and extract page number if present in markers like [Page 12], [Pages 12-13], (Page 12), Page 12:, Pg. 12, p. 12, etc.
        var extractedPage: Int? = null
        val pageRegex = Regex("(?i)(?:\\[|\\(|\\b)(?:Pages?|Pg\\.?|p\\.)\\s*(\\d+)(?:\\]|\\)|:|\\s*[-—])?")
        val pageMatch = pageRegex.find(text)
        if (pageMatch != null) {
            extractedPage = pageMatch.groupValues[1].toIntOrNull()
            // Remove the raw page header prefix if it appears at the start of the snippet
            text = text.replaceFirst(pageRegex, "").trim()
        }

        // 2. Remove markdown header tokens and preamble at the start (e.g. "### ## 📖 Original English Passage:", "## Excerpt")
        text = text.replace(Regex("(?i)^\\s*(?:#|\\s|📖)*(?:Original\\s+)?(?:English\\s+)?(?:Passage|Excerpt|Quote|Snippet)?[:\\s]*\\r?\\n*"), "").trim()
        text = text.replace(Regex("^(?:[#>\\s]|📖)+"), "").trim()

        // Detect if snippet starts with a distinct title/heading before cleaning markdown asterisks
        var explicitHeading: String? = null
        val headingMatch = Regex("^(?:#+\\s*|\\*\\*|__)([^\n*_#]+)(?:\\*\\*|__)?(?:\\r?\\n)+").find(text)
        if (headingMatch != null) {
            val potential = headingMatch.groupValues[1].trim()
            if (potential.length in 2..80 && !potential.endsWith(".") && !potential.contains("\n")) {
                explicitHeading = potential
            }
        }

        // 3. Rejoin hyphenated line breaks (e.g. "fundamen-\n tal" -> "fundamental")
        text = text.replace(Regex("([a-zA-Z]{2,})-\\r?\\n\\s*([a-zA-Z]{2,})")) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}"
        }

        // 4. Rejoin hyphenated words split by spaces (e.g. "infor- mation" -> "information")
        text = text.replace(Regex("([a-zA-Z]{2,})-\\s+([a-zA-Z]{2,})")) { match ->
            val w1 = match.groupValues[1]
            val w2 = match.groupValues[2]
            if (w2[0].isLowerCase()) "$w1$w2" else "$w1 - $w2"
        }

        // 5. Remove stray pipe characters and OCR noise glyphs
        text = text.replace(Regex("[|~§†‡]"), " ")

        // 6. Clean unparsed markdown bold/header asterisks/hashtags within snippet
        text = text.replace(Regex("(?m)^#+\\s*"), "")
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")

        // 7. Normalize multiple newlines vs single line breaks:
        // Merge single line breaks in continuous sentences, while preserving double newlines (paragraphs)
        val paragraphs = text.split(Regex("\\r?\\n\\s*\\r?\\n+"))
        val normalizedParagraphs = paragraphs.map { paragraph ->
            paragraph
                .replace(Regex("\\r?\\n"), " ")
                .replace(Regex("[ \\t]+"), " ")
                .trim()
        }.filter { it.isNotBlank() }

        val label = if (extractedPage != null) {
            "ORIGINAL PASSAGE • PAGE $extractedPage"
        } else {
            "ORIGINAL PASSAGE"
        }

        val heading: String?
        val bodyText: String
        val cleanText: String

        if (normalizedParagraphs.size > 1 && explicitHeading != null) {
            heading = explicitHeading
            bodyText = normalizedParagraphs.drop(1).joinToString("\n\n")
            cleanText = "$heading\n\n$bodyText"
        } else if (normalizedParagraphs.size > 1 && normalizedParagraphs.first().length <= 60 &&
            !normalizedParagraphs.first().endsWith(".") && !normalizedParagraphs.first().endsWith(",")
        ) {
            heading = normalizedParagraphs.first()
            bodyText = normalizedParagraphs.drop(1).joinToString("\n\n")
            cleanText = "$heading\n\n$bodyText"
        } else {
            heading = null
            bodyText = normalizedParagraphs.joinToString("\n\n").trim()
            cleanText = bodyText
        }

        return SanitizedPassageInfo(
            cleanText = cleanText,
            pageLabel = label,
            pageNumber = extractedPage,
            heading = heading,
            bodyText = bodyText
        )
    }

    /**
     * Sanitizes AI Markdown text responses by cleaning clutter tokens, normalizing headers,
     * and ensuring consistent paragraph formatting.
     */
    fun sanitizeMarkdown(rawMarkdown: String): String {
        if (rawMarkdown.isBlank()) return ""
        var text = rawMarkdown.trim()

        // Strip trailing original passage if present in raw AI response to avoid duplicate rendering
        val passageDelimiter = Regex("(?i)(?:\\n\\s*(?:---|\\*\\*\\*|___))?\\s*\\n+##+\\s*(?:📖\\s*)?Original(?:\\s+English)?\\s+Passage[\\s\\S]*$")
        text = text.replace(passageDelimiter, "").trim()

        // Remove horizontal divider markers
        text = text.replace(Regex("(?m)^\\s*(?:---|\\*\\*\\*|___)\\s*$"), "")

        // Normalize bullet points: convert `- `, `* `, `+ ` to standard `• `
        text = text.replace(Regex("(?m)^[ \\t]*[-*+][ \\t]+"), "• ")

        // Collapse excessive newlines (> 2) to 2
        text = text.replace(Regex("\\n{3,}"), "\n\n")

        return text.trim()
    }
}

/**
 * Parses markdown into an [AnnotatedString] supporting bold (**text**), italics (*text*),
 * inline code (`code`), clean bullet points with accent dots, and cleanly formatted headers.
 * Uses a refined black-and-white / grayscale editorial palette.
 */
fun parseMarkdownToAnnotatedString(
    text: String,
    baseColor: Color = Color(0xFFCBD5E1),
    accentColor: Color = Color(0xFFA1A1AA),
    boldColor: Color = Color(0xFFFFFFFF)
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = text.lines()

    for ((lineIndex, rawLine) in lines.withIndex()) {
        var line = rawLine

        // Clean leading markdown header hashes if present
        if (line.trimStart().startsWith("#")) {
            val trimmedHeader = line.trimStart('#').trim()
            val start = builder.length
            builder.append(trimmedHeader)
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = boldColor),
                start,
                builder.length
            )
            if (lineIndex < lines.size - 1) builder.append("\n")
            continue
        }

        // Handle bullet points with sleek monochrome accent dots and clean indentation
        val trimmed = line.trimStart()
        val isBullet = trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                (trimmed.length > 2 && trimmed[0].isDigit() && (trimmed[1] == '.' || (trimmed.length > 3 && trimmed[2] == '.')))

        if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val bulletContent = trimmed.removePrefix("• ").removePrefix("- ").removePrefix("* ").trim()
            val dotStart = builder.length
            builder.append("  •  ")
            builder.addStyle(
                SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                dotStart,
                builder.length
            )
            line = bulletContent
        } else if (isBullet) {
            val numEnd = trimmed.indexOf('.')
            val numPrefix = trimmed.substring(0, numEnd + 1)
            val numContent = trimmed.substring(numEnd + 1).trim()
            val numStart = builder.length
            builder.append("  $numPrefix  ")
            builder.addStyle(
                SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                numStart,
                builder.length
            )
            line = numContent
        }

        var i = 0
        while (i < line.length) {
            // Bold with ** or __
            if ((i + 1 < line.length && line[i] == '*' && line[i + 1] == '*') ||
                (i + 1 < line.length && line[i] == '_' && line[i + 1] == '_')
            ) {
                val delimiter = line.substring(i, i + 2)
                val closingIdx = line.indexOf(delimiter, i + 2)
                if (closingIdx != -1) {
                    val boldContent = line.substring(i + 2, closingIdx)
                    val start = builder.length
                    builder.append(boldContent)
                    builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.SemiBold, color = boldColor),
                        start,
                        builder.length
                    )
                    i = closingIdx + 2
                    continue
                }
            }

            // Italic with * or _
            if (line[i] == '*' || (line[i] == '_' && (i == 0 || line[i - 1].isWhitespace()) && (i + 1 < line.length && !line[i + 1].isWhitespace()))) {
                val delimiter = line[i].toString()
                val closingIdx = line.indexOf(delimiter, i + 1)
                if (closingIdx != -1) {
                    val italicContent = line.substring(i + 1, closingIdx)
                    val start = builder.length
                    builder.append(italicContent)
                    builder.addStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFE2E8F0)
                        ),
                        start,
                        builder.length
                    )
                    i = closingIdx + 1
                    continue
                }
            }

            // Inline Code with `
            if (line[i] == '`') {
                val closingIdx = line.indexOf('`', i + 1)
                if (closingIdx != -1) {
                    val codeContent = line.substring(i + 1, closingIdx)
                    val start = builder.length
                    builder.append(codeContent)
                    builder.addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            background = Color(0xFF27272A),
                            color = boldColor
                        ),
                        start,
                        builder.length
                    )
                    i = closingIdx + 1
                    continue
                }
            }

            builder.append(line[i])
            i++
        }

        if (lineIndex < lines.size - 1) {
            builder.append("\n")
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Rich Markdown Text composable for formatted AI output in a minimal, high-contrast palette.
 */
@Composable
fun RichMarkdownText(
    text: String,
    style: TextStyle,
    color: Color = Color(0xFFCBD5E1),
    accentColor: Color = Color(0xFFA1A1AA),
    boldColor: Color = Color(0xFFFFFFFF),
    modifier: Modifier = Modifier
) {
    val cleanMarkdown = remember(text) { PassageSanitizer.sanitizeMarkdown(text) }
    val annotatedString = remember(cleanMarkdown, color, accentColor, boldColor) {
        parseMarkdownToAnnotatedString(
            cleanMarkdown,
            baseColor = color,
            accentColor = accentColor,
            boldColor = boldColor
        )
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * Dedicated Container for Snipped Book Excerpts (Original Passage).
 * Redesigned with a premium black-and-white editorial aesthetic:
 * - Clean subtle 1dp border and muted dark obsidian surface
 * - Monochrome "Original Passage" header label with page number
 * - English passage rendered in Poppins font with selectable text
 * - Sleek monochrome reader actions and vocabulary chips
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DedicatedSnippedPassageQuoteCard(
    messageId: Long,
    rawText: String,
    savedTags: List<WordVaultEntry>,
    onTranslateWord: (selectedWord: String, sentence: String, surroundingContext: String, messageId: Long) -> Unit,
    onTagClicked: (WordVaultEntry) -> Unit,
    onCopyText: (String) -> Unit,
    isPdfAvailable: Boolean = false,
    onOpenPdfAtPage: ((pageNumber: Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f
) {
    val passageInfo = remember(rawText) { PassageSanitizer.sanitizeSnippet(rawText) }
    if (passageInfo.cleanText.isBlank()) return

    Card(
        modifier = modifier.testTag("attached_original_passage_section_$messageId"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF09090B)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFF27272A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Subtle monochrome left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF52525B))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header Bar: Minimal Monochrome Quote/Book Icon, Label, and Copy Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = passageInfo.pageLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onCopyText(passageInfo.cleanText) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy excerpt",
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Distinct Bold Heading/Title if present
                if (!passageInfo.heading.isNullOrBlank()) {
                    Text(
                        text = passageInfo.heading,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (15f * fontScale).sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color(0xFFF4F4F5),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Refined, Selectable Book Quote Passage Prose (Uses Poppins font)
                SelectablePassageView(
                    text = passageInfo.bodyText,
                    messageId = messageId,
                    onTranslateWord = onTranslateWord,
                    fontSizeSp = 14.5f * fontScale,
                    modifier = Modifier.fillMaxWidth()
                )

                // Interactive "Go to Page X" / "Open in Reader" Monochrome Badge
                if (onOpenPdfAtPage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = { onOpenPdfAtPage(passageInfo.pageNumber) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF18181B),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier.testTag("quote_card_go_to_page_${messageId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFFE4E4E7),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (passageInfo.pageNumber != null) {
                                    "Go to Page ${passageInfo.pageNumber}"
                                } else if (isPdfAvailable) {
                                    "Open in Reader"
                                } else {
                                    "Attach / Read PDF"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.4.sp,
                                    fontSize = 11.5.sp
                                ),
                                color = Color(0xFFF4F4F5)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = null,
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Saved Vocabulary Tags attached to this passage
                if (savedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = Color(0xFF27272A),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAVED VOCABULARY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 10.5.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        savedTags.forEach { entry ->
                            Surface(
                                onClick = { onTagClicked(entry) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF18181B),
                                border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                                modifier = Modifier.testTag("passage_tag_${entry.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = entry.word,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFFFFF)
                                    )
                                    if (entry.meaning.isNotBlank()) {
                                        Text(
                                            text = " · ${entry.meaning}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFA1A1AA),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

