package com.example.ui.screens.vault

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.WordVaultEntry
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.WordVaultFilterScope
import com.example.ui.viewmodel.WordVaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordVaultScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWordQuiz: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WordVaultViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val words by viewModel.filteredWords.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val filterScope by viewModel.filterScope.collectAsStateWithLifecycle()
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()

    var wordToDelete by remember { mutableStateOf<WordVaultEntry?>(null) }
    var inspectedWord by remember { mutableStateOf<WordVaultEntry?>(null) }

    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E)),
        containerColor = Color(0xFF0B0B0E),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Word Vault",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFFFFFFF)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("word_vault_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFFFFF)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToWordQuiz,
                        modifier = Modifier.testTag("word_vault_quiz_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Word Vault Quiz",
                            tint = Color(0xFFFFFFFF)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141418),
                        border = BorderStroke(1.dp, Color(0xFF27272F)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "$totalCount ${if (totalCount == 1) "Word" else "Words"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFA1A1AA),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("word_vault_count_badge")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0B0E)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0B0B0E))
                .navigationBarsPadding()
        ) {
            // 1. Filter Scope Tabs (Minimalist Dark Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chapter != null) {
                    val isSelected = filterScope == WordVaultFilterScope.CURRENT_CHAPTER
                    Surface(
                        onClick = { viewModel.onFilterScopeSelected(WordVaultFilterScope.CURRENT_CHAPTER) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF141418),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFFFFF) else Color(0xFF27272F)),
                        modifier = Modifier.testTag("word_vault_filter_chapter")
                    ) {
                        Text(
                            text = "This Chapter",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) Color(0xFF0B0B0E) else Color(0xFFA1A1AA),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }

                if (book != null) {
                    val isSelected = filterScope == WordVaultFilterScope.CURRENT_BOOK
                    Surface(
                        onClick = { viewModel.onFilterScopeSelected(WordVaultFilterScope.CURRENT_BOOK) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF141418),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFFFFF) else Color(0xFF27272F)),
                        modifier = Modifier.testTag("word_vault_filter_book")
                    ) {
                        Text(
                            text = "This Book",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) Color(0xFF0B0B0E) else Color(0xFFA1A1AA),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }

                val isAllSelected = filterScope == WordVaultFilterScope.ALL_BOOKS
                Surface(
                    onClick = { viewModel.onFilterScopeSelected(WordVaultFilterScope.ALL_BOOKS) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isAllSelected) Color(0xFFFFFFFF) else Color(0xFF141418),
                    border = BorderStroke(1.dp, if (isAllSelected) Color(0xFFFFFFFF) else Color(0xFF27272F)),
                    modifier = Modifier.testTag("word_vault_filter_all")
                ) {
                    Text(
                        text = "All Books",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = if (isAllSelected) Color(0xFF0B0B0E) else Color(0xFFA1A1AA),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            // 2. Obsidian Search Pod (#141418 background, 1dp #27272F border, rounded 12dp, padding 12dp)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(18.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.searchQuery.isEmpty()) {
                            Text(
                                text = "Search vocabulary, Roman Urdu meaning...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                color = Color(0xFF71717A)
                            )
                        }
                        BasicTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color(0xFFFFFFFF),
                                fontSize = 13.5.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("word_vault_search_input")
                        )
                    }
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onSearchQueryChanged("") },
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("word_vault_search_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                color = Color(0xFF27272F),
                thickness = 1.dp
            )

            // 3. Structured Word Item Cards List
            if (words.isEmpty()) {
                WordVaultEmptyState(
                    searchQuery = viewModel.searchQuery,
                    onClearSearch = { viewModel.onSearchQueryChanged("") },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("word_vault_list"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 10.dp,
                        end = 16.dp,
                        bottom = 96.dp // Generous bottom clearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = words,
                        key = { it.id }
                    ) { entry ->
                        CompactWordCard(
                            entry = entry,
                            chapter = chapter,
                            onClick = { inspectedWord = entry },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 4. Dedicated Word Inspection Modal Dialog (Pop-up Polish)
    inspectedWord?.let { entry ->
        WordInspectionDialog(
            entry = entry,
            onDismiss = { inspectedWord = null },
            onPronounce = {
                tts?.speak(entry.word, TextToSpeech.QUEUE_FLUSH, null, null)
            },
            onDelete = {
                wordToDelete = entry
                inspectedWord = null
            },
            onPracticeInDrill = {
                inspectedWord = null
                onNavigateToWordQuiz()
            }
        )
    }

    // 5. Delete Confirmation Dialog
    wordToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { wordToDelete = null },
            containerColor = Color(0xFF141418),
            title = {
                Text(
                    text = "Delete Word",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${entry.word}\" from your Word Vault?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD4D4D8)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWord(entry)
                        wordToDelete = null
                    },
                    modifier = Modifier.testTag("word_vault_confirm_delete")
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { wordToDelete = null },
                    modifier = Modifier.testTag("word_vault_cancel_delete")
                ) {
                    Text("Cancel", color = Color(0xFFA1A1AA))
                }
            },
            modifier = Modifier.testTag("word_vault_delete_dialog")
        )
    }
}

/**
 * Phonetic guide & part-of-speech classification helper.
 */
fun getWordPhoneticAndPartOfSpeech(entry: WordVaultEntry): Pair<String, String> {
    val word = entry.word.trim().lowercase()
    val pos = when {
        entry.phraseOrIdiomExplanation.isNotBlank() || word.contains(" ") -> "phrase"
        word.endsWith("ly") -> "adverb"
        word.endsWith("tion") || word.endsWith("sion") || word.endsWith("ment") ||
                word.endsWith("ness") || word.endsWith("ity") || word.endsWith("ism") ||
                word.endsWith("ance") || word.endsWith("ence") || word.endsWith("ship") -> "noun"
        word.endsWith("ate") || word.endsWith("ify") || word.endsWith("ize") ||
                word.endsWith("ise") || word.endsWith("ed") || word.endsWith("ing") -> "verb"
        word.endsWith("able") || word.endsWith("ible") || word.endsWith("ous") ||
                word.endsWith("ful") || word.endsWith("less") || word.endsWith("ive") ||
                word.endsWith("ic") || word.endsWith("al") || word.endsWith("ish") -> "adj."
        else -> "term"
    }
    val phonetic = "/${word}/"
    return Pair(phonetic, pos)
}

/**
 * Structured Word Item Card:
 * - Container: #141418, 1dp #27272F border, rounded 12dp, vertical padding 12dp, horizontal padding 16dp.
 * - Primary Row: Word (bold white 16sp) + phonetic guide/POS (#71717A, 11sp, italic) | Retention status badge in Obsidian micro-capsule.
 * - Secondary Row: One-line preview of definition (#A1A1AA, 12sp) | Source attribution (e.g. Ch. 1 • p. 19, #71717A, 10sp).
 */
@Composable
private fun CompactWordCard(
    entry: WordVaultEntry,
    chapter: Chapter?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceAttribution = remember(entry.chapterId, entry.messageId, chapter) {
        val chNum = if (chapter != null && chapter.id == entry.chapterId && (chapter.chapterNumber ?: 0) > 0) {
            chapter.chapterNumber.toString()
        } else if (entry.chapterId > 0) {
            entry.chapterId.toString()
        } else {
            "1"
        }
        val msgId = entry.messageId
        if (msgId != null && msgId > 0) {
            "Ch. $chNum • p. $msgId"
        } else {
            "Ch. $chNum"
        }
    }

    val (_, pos) = remember(entry) {
        getWordPhoneticAndPartOfSpeech(entry)
    }

    val retentionStatus = remember(entry.isFlaggedForSpacedReview, entry.mistakeCount, entry.createdAt) {
        when {
            entry.isFlaggedForSpacedReview -> "Review Due"
            entry.mistakeCount > 0 -> "Learning"
            System.currentTimeMillis() - entry.createdAt < 48 * 3600 * 1000L -> "Learning"
            else -> "Retained"
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141418),
        border = BorderStroke(1.dp, Color(0xFF27272F)),
        modifier = modifier.testTag("word_vault_item_${entry.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Primary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFFFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("word_vault_word_text_${entry.id}")
                    )
                    Text(
                        text = "• $pos",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color(0xFF71717A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Obsidian Micro-Capsule for Retention Status
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E1E24),
                    border = BorderStroke(1.dp, Color(0xFF27272F))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val dotColor = when (retentionStatus) {
                            "Review Due" -> Color(0xFFF59E0B)
                            "Learning" -> Color(0xFFA1A1AA)
                            else -> Color(0xFFFFFFFF)
                        }
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = retentionStatus,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFE4E4E7)
                        )
                    }
                }
            }

            // Secondary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val preview = entry.meaning.ifBlank {
                    entry.contextMeaning.ifBlank {
                        entry.explanation.ifBlank { "Saved Vocabulary" }
                    }
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFFA1A1AA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sourceAttribution,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color(0xFF71717A),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Dedicated Executive Term Inspection Dialog:
 * - Scrim: Centered animated dialog with frosted dark scrim (#000000 at 65% opacity).
 * - Modal Surface: #141418, 1.5dp #27272F border, rounded 20dp, padding 20dp, width clamped to 90% (max 360dp).
 * - Header: Word (#FFFFFF, 22sp bold), Phonetic + POS (#71717A, 12sp), close icon anchored in top-right.
 * - Divider: Subtle rule (#27272F, 1dp) with 12dp vertical margins.
 * - Meaning: DEFINITION (#71717A, 10sp bold uppercase) + English definition (#FFFFFF, 13sp, line height 19sp).
 * - Urdu Nuance: URDU TAFSEEL (#71717A, 10sp bold uppercase) + conversational Roman Urdu (#A1A1AA, 12sp, line height 18sp).
 * - Original Book Context: Indented quote with subtle left border (#27272F).
 * - Action Bar: "Remove" text button (#71717A, 12sp) | "Practice in Drill →" high-contrast button (#0B0B0E on #FFFFFF).
 * - Touch Feedback & Scaling: scaleIn(0.90f) + fadeIn(), inner vertical scroll container preventing vertical clipping.
 */
@Composable
private fun WordInspectionDialog(
    entry: WordVaultEntry,
    onDismiss: () -> Unit,
    onPronounce: () -> Unit,
    onDelete: () -> Unit,
    onPracticeInDrill: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.90f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = scaleOut(
                    targetScale = 0.90f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF141418),
                    border = BorderStroke(1.5.dp, Color(0xFF27272F)),
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .widthIn(max = 360.dp)
                        .heightIn(max = 580.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* prevent dismissing when tapping inside dialog surface */ }
                        )
                        .testTag("word_inspection_dialog_${entry.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header Section: Inspected Word, phonetic / pos, audio button, close icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.word,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = Color(0xFFFFFFFF),
                                    modifier = Modifier.testTag("word_inspection_title_${entry.id}")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val (phonetic, pos) = getWordPhoneticAndPartOfSpeech(entry)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$phonetic • $pos",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFF71717A)
                                    )
                                    IconButton(
                                        onClick = onPronounce,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Pronounce Word",
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("word_inspection_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF71717A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Divider: Subtle horizontal rule with 12dp vertical margins
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = Color(0xFF27272F)
                        )

                        // Core Meaning & Definition
                        Text(
                            text = "DEFINITION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val definition = entry.meaning.ifBlank {
                            entry.contextMeaning.ifBlank { "Vocabulary term recorded in ReadMate." }
                        }
                        Text(
                            text = definition,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            ),
                            color = Color(0xFFFFFFFF),
                            modifier = Modifier.testTag("word_vault_meaning_${entry.id}")
                        )

                        // Roman Urdu Translation & Contextual Nuance
                        val urduNuance = when {
                            entry.explanation.isNotBlank() -> entry.explanation
                            entry.contextMeaning.isNotBlank() -> entry.contextMeaning
                            else -> "Context ke mutabiq asaan Roman Urdu tashreeh."
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "URDU TAFSEEL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF71717A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = urduNuance,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color(0xFFA1A1AA),
                            modifier = Modifier.testTag("word_vault_context_meaning_${entry.id}")
                        )

                        // Original Book Context
                        if (entry.originalSentence.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "ORIGINAL CONTEXT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color(0xFF71717A)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF0F0F13),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E1E24)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color(0xFF27272F))
                                    )
                                    val highlightedSentence = remember(entry.originalSentence, entry.word) {
                                        highlightWordInSentence(entry.originalSentence, entry.word)
                                    }
                                    Text(
                                        text = highlightedSentence,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            lineHeight = 17.sp
                                        ),
                                        color = Color(0xFFD4D4D8),
                                        modifier = Modifier.testTag("word_vault_sentence_${entry.id}")
                                    )
                                }
                            }
                        }

                        // Action Bar: Two-action bottom row
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDelete,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("word_inspection_delete")
                            ) {
                                Text(
                                    text = "Remove",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF71717A)
                                )
                            }

                            Surface(
                                onClick = onPracticeInDrill,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFFFF),
                                modifier = Modifier.testTag("word_inspection_practice")
                            ) {
                                Text(
                                    text = "Practice in Drill →",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF0B0B0E),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
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
                        background = Color(0xFF27272F)
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
                        background = Color(0xFF27272F)
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

@Composable
fun WordVaultEmptyState(
    searchQuery: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(32.dp)
            .testTag("word_vault_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFFFFFFFF),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "No matching words found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try searching with a different keyword.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onClearSearch) {
                    Text("Clear Search", color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Your Word Vault is Empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Long-press any word in the English passage in Chapter Chat and tap 'Translate' to get a contextual Roman Urdu meaning and save it to your Word Vault!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
