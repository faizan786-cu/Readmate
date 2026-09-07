package com.example.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.database.entity.WordVaultEntry
import com.example.ui.util.SentenceExtractor

private const val MENU_ITEM_TRANSLATE_ID = 0x7F0A0099

/**
 * Interactive Selectable Passage View with inline translated word highlighting and direct tap support.
 *
 * - Long-press & select any word/phrase to trigger the contextual "Translate" action mode menu item.
 * - Words/phrases saved in the Word Vault are highlighted inline with a crisp underline and subtle pill background.
 * - Tapping any already-highlighted word directly invokes [onWordClick] to re-open its translation sheet.
 */
@Composable
fun SelectablePassageView(
    text: String,
    messageId: Long,
    savedWords: List<WordVaultEntry> = emptyList(),
    onTranslateWord: (selectedWord: String, sentence: String, surroundingContext: String, messageId: Long) -> Unit,
    onWordClick: ((WordVaultEntry) -> Unit)? = null,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 14.5f
) {
    val textColor = Color(0xFFFFFFFF).toArgb()
    val highlightColor = Color(0xFF52525B).copy(alpha = 0.6f).toArgb()
    val inlineWordColor = Color(0xFFFFFFFF).toArgb()
    val inlineWordBgColor = Color(0xFF27272A).toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val baseTypeface = try {
                ResourcesCompat.getFont(context, R.font.poppins) ?: Typeface.create("sans-serif", Typeface.NORMAL)
            } catch (_: Exception) {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val italicTypeface = Typeface.create(baseTypeface, Typeface.ITALIC)

            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                setHighlightColor(highlightColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                setLineSpacing(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 5f, context.resources.displayMetrics),
                    1.2f
                )
                typeface = italicTypeface

                // Custom Selection Action Mode Callback for "Translate"
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        menu?.let {
                            val translateItem = it.add(
                                Menu.NONE,
                                MENU_ITEM_TRANSLATE_ID,
                                0,
                                "Translate"
                            )
                            translateItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                        }
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        if (item?.itemId == MENU_ITEM_TRANSLATE_ID) {
                            val selStart = selectionStart.coerceAtLeast(0)
                            val selEnd = selectionEnd.coerceAtLeast(0)
                            val fullText = this@apply.text.toString()

                            if (selStart < selEnd && selEnd <= fullText.length) {
                                val selectedText = fullText.substring(selStart, selEnd).trim()
                                if (selectedText.isNotEmpty()) {
                                    val sentence = SentenceExtractor.extractSentence(fullText, selectedText)
                                    onTranslateWord(selectedText, sentence, fullText, messageId)
                                }
                            }
                            mode?.finish()
                            return true
                        }
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        // Standard cleanup
                    }
                }
            }
        },
        update = { textView ->
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            textView.setTextColor(textColor)
            textView.setHighlightColor(highlightColor)

            val formattedSpannable = buildPassageSpannable(
                rawText = text,
                savedWords = savedWords,
                inlineColor = inlineWordColor,
                inlineBgColor = inlineWordBgColor,
                onWordClick = onWordClick
            )
            textView.setText(formattedSpannable, TextView.BufferType.SPANNABLE)
        }
    )
}

/**
 * Builds a Spannable text with inline highlights for translated Word Vault entries.
 */
private fun buildPassageSpannable(
    rawText: String,
    savedWords: List<WordVaultEntry>,
    inlineColor: Int,
    inlineBgColor: Int,
    onWordClick: ((WordVaultEntry) -> Unit)?
): CharSequence {
    if (rawText.isBlank() || savedWords.isEmpty()) {
        return rawText
    }

    val spannable = SpannableStringBuilder(rawText)

    // Sort words by length descending so longer multi-word phrases match before individual sub-words
    val sortedWords = savedWords
        .filter { it.word.isNotBlank() }
        .sortedByDescending { it.word.length }

    val matchedRanges = mutableListOf<IntRange>()

    for (entry in sortedWords) {
        val word = entry.word.trim()
        if (word.isEmpty()) continue

        // Word-boundary case-insensitive search
        val pattern = Regex("(?i)\\b${Regex.escape(word)}\\b")
        val matches = pattern.findAll(rawText).toList()

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            // Prevent overlapping spans from messing up formatting
            val overlaps = matchedRanges.any { existing ->
                maxOf(start, existing.first) < minOf(end, existing.last + 1)
            }

            if (!overlaps) {
                matchedRanges.add(match.range)

                // Bold style
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // Underline
                spannable.setSpan(
                    UnderlineSpan(),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // Custom Rounded/Pill Background or Clickable Span
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            onWordClick?.invoke(entry)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = inlineColor
                            ds.bgColor = inlineBgColor
                            ds.isUnderlineText = true
                        }
                    },
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    return spannable
}
