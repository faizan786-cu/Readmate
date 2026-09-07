package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WordTranslationResult
import com.example.ui.screens.chat.WordTranslationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordTranslationBottomSheet(
    state: WordTranslationState,
    onDismiss: () -> Unit,
    onSaveToVault: (WordTranslationResult, String) -> Unit,
    onRetry: (String, String, String?) -> Unit,
    onNavigateToOriginPassage: ((chapterId: Long, messageId: Long?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (state is WordTranslationState.Idle) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D11),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3F3F46))
            )
        },
        modifier = modifier.testTag("word_translation_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            when (state) {
                is WordTranslationState.Loading -> {
                    LoadingTranslationContent(
                        word = state.word,
                        sentence = state.sentence,
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is WordTranslationState.Success -> {
                    SuccessTranslationContent(
                        translation = state.translation,
                        originalSentence = state.originalSentence,
                        isSaved = state.isSaved,
                        isSaving = state.isSaving,
                        hasExistingVaultEntry = state.existingVaultEntry != null,
                        originBookTitle = state.originBookTitle,
                        originChapterTitle = state.originChapterTitle,
                        originChapterNumber = state.originChapterNumber,
                        originChapterId = state.originChapterId,
                        originMessageId = state.originMessageId,
                        isFromDifferentPassage = state.isFromDifferentPassage,
                        onNavigateToOrigin = {
                            if (state.originChapterId != null) {
                                onDismiss()
                                onNavigateToOriginPassage?.invoke(state.originChapterId, state.originMessageId)
                            }
                        },
                        onSaveToVault = { onSaveToVault(state.translation, state.originalSentence) },
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is WordTranslationState.Error -> {
                    ErrorTranslationContent(
                        word = state.word,
                        sentence = state.sentence,
                        errorMessage = state.errorMessage,
                        onRetry = { onRetry(state.word, state.sentence, state.surroundingContext) },
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                WordTranslationState.Idle -> { /* Unreachable */ }
            }
        }
    }
}

@Composable
private fun LoadingTranslationContent(
    word: String,
    sentence: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("word_translation_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = CircleShape,
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF27272A)),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = Color(0xFFFFFFFF)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Word pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF27272A))
        ) {
            Text(
                text = "\"$word\"",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFFFFFFF),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Samajh raha hun...",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = Color(0xFFFFFFFF)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sentence ke context ke mutabiq asaan matlab nikaal rahe hain",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp
            ),
            color = Color(0xFFA1A1AA)
        )

        if (sentence.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF121215),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\"$sentence\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFFA1A1AA),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun SuccessTranslationContent(
    translation: WordTranslationResult,
    originalSentence: String,
    isSaved: Boolean,
    isSaving: Boolean,
    hasExistingVaultEntry: Boolean,
    originBookTitle: String? = null,
    originChapterTitle: String? = null,
    originChapterNumber: Int? = null,
    originChapterId: Long? = null,
    originMessageId: Long? = null,
    isFromDifferentPassage: Boolean = false,
    onNavigateToOrigin: () -> Unit = {},
    onSaveToVault: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isAutoSavedOrExisting = isSaved || hasExistingVaultEntry

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .testTag("translation_success_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Previously Learned Origin Banner (if loaded from another passage or chapter)
        if (isFromDifferentPassage && (!originBookTitle.isNullOrBlank() || !originChapterTitle.isNullOrBlank())) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("previously_learned_from_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PREVIOUSLY LEARNED FROM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 10.5.sp
                            ),
                            color = Color(0xFFA1A1AA)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val breadcrumb = buildString {
                        if (!originBookTitle.isNullOrBlank()) {
                            append(originBookTitle)
                        }
                        if (!originChapterTitle.isNullOrBlank()) {
                            if (isNotEmpty()) append(" → ")
                            if (originChapterNumber != null) {
                                append("Ch. $originChapterNumber: ")
                            }
                            append(originChapterTitle)
                        }
                    }

                    Text(
                        text = breadcrumb,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color(0xFFE4E4E7),
                        modifier = Modifier.testTag("origin_breadcrumb_text")
                    )

                    if (originChapterId != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = onNavigateToOrigin,
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF121215),
                            border = BorderStroke(1.dp, Color(0xFF27272A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("view_original_passage_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFFFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "View Original Passage",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFFFFFFFF)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Header Row: Word Title (Left) & Auto-Save Status Pill / Action (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = translation.word.ifBlank { "Selected Word" },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                ),
                color = Color(0xFFFFFFFF),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("translation_word_header")
            )

            Spacer(modifier = Modifier.width(10.dp))

            if (isAutoSavedOrExisting) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            tint = Color(0xFFFFFFFF),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Saved to Vault",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFE4E4E7)
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onSaveToVault,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A)),
                    modifier = Modifier.testTag("translation_save_vault_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFFFFFFFF)
                            )
                            Text(
                                text = "Saving...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFE4E4E7)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Save to Word Vault",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Save to Vault",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                }
            }
        }

        // 2. Section: Contextual Meaning (Roman Urdu)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF27272A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "MEANING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF71717A)
                    )
                }

                val primaryMeaning = translation.contextMeaning.ifBlank { translation.simpleMeaning }
                Text(
                    text = primaryMeaning.ifBlank { "Roman Urdu translation available" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFFFAFAFA),
                    modifier = Modifier.testTag("translation_context_meaning")
                )

                if (translation.simpleMeaning.isNotBlank() && translation.simpleMeaning != translation.contextMeaning) {
                    Text(
                        text = "Basic: ${translation.simpleMeaning}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color(0xFFA1A1AA),
                        modifier = Modifier.testTag("translation_simple_meaning")
                    )
                }
            }
        }

        // Minimal 1px Zinc Divider
        HorizontalDivider(color = Color(0xFF27272A))

        // 3. Section: In Context (Original Sentence from snip/passage with highlighted target word)
        val targetSentence = translation.originalSentence.ifBlank { originalSentence }
        if (targetSentence.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "IN CONTEXT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                    }

                    val highlightedSentence = remember(targetSentence, translation.word) {
                        highlightWordInSentence(targetSentence, translation.word)
                    }

                    Text(
                        text = highlightedSentence,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 22.sp
                        ),
                        color = Color(0xFFE4E4E7),
                        modifier = Modifier.testTag("translation_original_sentence")
                    )

                    if (translation.sentenceUrduExplanation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF121215),
                            border = BorderStroke(1.dp, Color(0xFF27272A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Column {
                                    Text(
                                        text = "Asaan Samjh:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFFA1A1AA)
                                    )
                                    Text(
                                        text = translation.sentenceUrduExplanation,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.sp
                                        ),
                                        color = Color(0xFFE4E4E7),
                                        modifier = Modifier.testTag("translation_sentence_explanation")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Section: Phrase / Idiom (if present)
        if (translation.phraseOrIdiomExplanation.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "PHRASE / IDIOM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                    }

                    Text(
                        text = translation.phraseOrIdiomExplanation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFFFAFAFA),
                        modifier = Modifier.testTag("translation_idiom_explanation")
                    )
                }
            }
        }

        // 5. Section: Practical Everyday Usage
        if (translation.simpleExample.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "EXAMPLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                    }

                    Text(
                        text = "\"${translation.simpleExample}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFFA1A1AA),
                        modifier = Modifier.testTag("translation_simple_example")
                    )

                    if (translation.exampleUrduExplanation.isNotBlank()) {
                        Text(
                            text = translation.exampleUrduExplanation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color(0xFFE4E4E7),
                            modifier = Modifier.testTag("translation_example_explanation")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 6. Dismiss Action (Full-width tactile monochrome button)
        Surface(
            onClick = onClose,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("translation_dialog_close_btn")
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF0D0D11)
                )
            }
        }
    }
}

@Composable
private fun ErrorTranslationContent(
    word: String,
    sentence: String,
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF27272A)),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Word ko samajhne mein problem aa gayi. Dobara try karo.",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFFFFFFFF),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFFEF4444),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onClose,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("translation_dialog_close_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFA1A1AA)
                    )
                }
            }

            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFFFFF),
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .testTag("translation_retry_button")
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF0D0D11),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0D0D11)
                    )
                }
            }
        }
    }
}

/**
 * Builds an AnnotatedString that highlights occurrences of target word in bold high-contrast pure white.
 */
private fun highlightWordInSentence(sentence: String, targetWord: String): androidx.compose.ui.text.AnnotatedString {
    if (targetWord.isBlank()) return androidx.compose.ui.text.AnnotatedString(sentence)

    return buildAnnotatedString {
        val cleanTarget = targetWord.trim()
        val regex = Regex("(?i)\\b${Regex.escape(cleanTarget)}\\b")
        var lastIndex = 0

        val matches = regex.findAll(sentence).toList()
        if (matches.isEmpty()) {
            val lowerSentence = sentence.lowercase()
            val lowerTarget = cleanTarget.lowercase()
            var fallbackIndex = lowerSentence.indexOf(lowerTarget)
            var start = 0
            while (fallbackIndex >= 0) {
                append(sentence.substring(start, fallbackIndex))
                withStyle(
                    SpanStyle(
                        color = Color(0xFFFFFFFF),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFF27272A)
                    )
                ) {
                    append(sentence.substring(fallbackIndex, fallbackIndex + cleanTarget.length))
                }
                start = fallbackIndex + cleanTarget.length
                fallbackIndex = lowerSentence.indexOf(lowerTarget, start)
            }
            if (start < sentence.length) {
                append(sentence.substring(start))
            }
        } else {
            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) {
                    append(sentence.substring(lastIndex, start))
                }
                withStyle(
                    SpanStyle(
                        color = Color(0xFFFFFFFF),
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFF27272A)
                    )
                ) {
                    append(sentence.substring(start, end))
                }
                lastIndex = end
            }
            if (lastIndex < sentence.length) {
                append(sentence.substring(lastIndex))
            }
        }
    }
}
