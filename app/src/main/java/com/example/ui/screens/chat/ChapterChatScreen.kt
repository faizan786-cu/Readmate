package com.example.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.model.GeminiConnectionState
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.SelectablePassageView
import com.example.ui.components.WordTranslationBottomSheet
import com.example.ui.components.chat.DedicatedSnippedPassageQuoteCard
import com.example.ui.components.chat.PassageSanitizer
import com.example.ui.components.chat.RichMarkdownText
import com.example.ui.components.pdf.ChapterPdfReaderView
import com.example.ui.theme.ReadMateGoldContainer
import com.example.ui.theme.ReadMateOnGoldContainer
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.ChapterChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWordVault: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToChapter: ((Long) -> Unit)? = null,
    onNavigateToResponse: ((Int) -> Unit)? = null,
    onNavigateToChapterMasteryQuiz: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ChapterChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chapterWords by viewModel.chapterWords.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val responseFontSizePercent by viewModel.responseFontSizePercent.collectAsStateWithLifecycle()
    val fontScale = remember(responseFontSizePercent) { responseFontSizePercent / 100f }
    val initialIndex = viewModel.savedScrollIndex
    val initialOffset = viewModel.savedScrollOffset
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex ?: 0,
        initialFirstVisibleItemScrollOffset = initialOffset ?: 0
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showChatMenu by remember { mutableStateOf(false) }

    // Ensure the exact scroll position is restored when messages flow in
    var hasRestoredInitialScroll by remember { mutableStateOf(false) }

    var messagePendingDeletion by remember { mutableStateOf<ChapterMessage?>(null) }
    var showCancelGenerationDialog by remember { mutableStateOf(false) }

    messagePendingDeletion?.let { msg ->
        DeleteEntryConfirmationDialog(
            onConfirm = {
                messagePendingDeletion = null
                viewModel.deleteMessage(msg, context)
            },
            onDismiss = { messagePendingDeletion = null }
        )
    }

    if (showCancelGenerationDialog) {
        DeleteEntryConfirmationDialog(
            onConfirm = {
                showCancelGenerationDialog = false
                viewModel.cancelActiveGeneration()
            },
            onDismiss = { showCancelGenerationDialog = false }
        )
    }

    // Reverse-chronological timeline list: newest entry at the very top (index 0)
    val timelineMessages = remember(messages) {
        messages.sortedWith(compareByDescending<ChapterMessage> { it.createdAt }.thenByDescending { it.id })
    }

    LaunchedEffect(timelineMessages) {
        if (timelineMessages.isNotEmpty() && !hasRestoredInitialScroll) {
            val targetMsgId = viewModel.targetMessageId
            if (targetMsgId != null && targetMsgId > 0L) {
                val targetIndex = timelineMessages.indexOfFirst { it.id == targetMsgId }
                if (targetIndex != -1) {
                    listState.scrollToItem(targetIndex, 0)
                    hasRestoredInitialScroll = true
                    return@LaunchedEffect
                }
            }
            val savedIdx = viewModel.savedScrollIndex
            val savedOff = viewModel.savedScrollOffset ?: 0
            if (savedIdx != null) {
                val targetIndex = savedIdx.coerceIn(0, (timelineMessages.size - 1).coerceAtLeast(0))
                listState.scrollToItem(targetIndex, savedOff)
            } else {
                // First time ever opening this chapter chat: open directly at the latest message (top)
                listState.scrollToItem(0, 0)
            }
            hasRestoredInitialScroll = true
        }
    }

    // Persist current scroll position to ViewModel as the user scrolls
    LaunchedEffect(listState, timelineMessages.size, hasRestoredInitialScroll) {
        if (hasRestoredInitialScroll && timelineMessages.isNotEmpty()) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.updateScrollPosition(index, offset)
                }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachPdfUri(context, uri)
        }
    }

    PdfExportDialog(
        state = viewModel.exportUiState,
        onDismiss = viewModel::dismissExportDialog
    )

    val currentPdfPath = chapter?.pdfFilePath ?: book?.pdfFilePath
    val isPdfAvailable = remember(currentPdfPath) {
        !currentPdfPath.isNullOrBlank() && java.io.File(currentPdfPath).let { it.exists() && it.canRead() && it.length() > 0 }
    }
    val totalPages = (if (!chapter?.pdfFilePath.isNullOrBlank()) chapter?.pdfTotalPages else book?.pdfTotalPages)?.coerceAtLeast(1) ?: 1
    val lastReadPage = if (!chapter?.pdfFilePath.isNullOrBlank()) {
        chapter?.pdfLastReadPage ?: 0
    } else {
        book?.pdfLastReadPage ?: chapter?.pdfLastReadPage ?: 0
    }

    // Scroll to top immediately when a new analysis starts
    LaunchedEffect(viewModel) {
        viewModel.scrollToTopEvent.collect {
            listState.animateScrollToItem(0)
        }
    }

    // Auto-scroll when user actively submits a message, when a response arrives, or when a message is highlighted
    LaunchedEffect(viewModel) {
        viewModel.scrollToBottomEvent.collect {
            val highlightId = viewModel.highlightedMessageId
            val targetIndex = if (highlightId != null) {
                val idx = timelineMessages.indexOfFirst { it.id == highlightId }
                if (idx != -1) idx else 0
            } else {
                0
            }
            if (targetIndex >= 0 && timelineMessages.isNotEmpty()) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    // Auto-scroll smoothly directly to newly generated response card at the top
    LaunchedEffect(timelineMessages.size, viewModel.highlightedMessageId) {
        val highlightId = viewModel.highlightedMessageId
        if (highlightId != null && timelineMessages.isNotEmpty()) {
            val targetIndex = timelineMessages.indexOfFirst { it.id == highlightId }
            if (targetIndex != -1) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF0B0B0E),
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            val chapterHeading = chapter?.let { chap ->
                                if (chap.sectionType == "FRONT_MATTER" || chap.sectionType == "BACK_MATTER" || chap.chapterNumber == null) {
                                    chap.title
                                } else {
                                    "Chapter ${chap.chapterNumber}: ${chap.title}"
                                }
                            } ?: "Chapter Chat"

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = chapterHeading,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFFFFF),
                                    lineHeight = 20.sp
                                )
                                book?.let {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = it.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF71717A),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("chapter_chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFFFFFFFF)
                                )
                            }
                        },
                        actions = {
                            // Direct PDF Reader Navigation Icon Button
                            IconButton(
                                onClick = {
                                    if (isPdfAvailable) {
                                        viewModel.openPdfReader()
                                    } else {
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                },
                                modifier = Modifier.testTag("chapter_chat_open_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = if (isPdfAvailable) "Read PDF" else "Attach PDF",
                                    tint = Color(0xFFFFFFFF)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showChatMenu = true },
                                    modifier = Modifier.testTag("chapter_chat_overflow_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = Color(0xFFA1A1AA)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showChatMenu,
                                    onDismissRequest = { showChatMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF141418))
                                        .border(1.dp, Color(0xFF27272F), RoundedCornerShape(8.dp))
                                ) {
                                    // 1. Read PDF / Attach PDF
                                    DropdownMenuItem(
                                        text = { Text(if (isPdfAvailable) "Read PDF" else "Attach PDF", color = Color(0xFFF4F4F5)) },
                                        onClick = {
                                            showChatMenu = false
                                            if (isPdfAvailable) {
                                                viewModel.openPdfReader()
                                            } else {
                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = Color(0xFFE4E4E7)
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_menu_read_pdf")
                                    )

                                    // 2. Word Vault
                                    DropdownMenuItem(
                                        text = { Text("Word Vault", color = Color(0xFFF4F4F5)) },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToWordVault()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                                contentDescription = null,
                                                tint = Color(0xFFE4E4E7)
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_menu_word_vault")
                                    )

                                    // 3. Mark Chapter as Complete / In-Progress
                                    val isChapterDone = chapter?.isCompleted == true
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isChapterDone) "Mark as In-Progress" else "Mark Chapter as Complete",
                                                color = if (isChapterDone) Color(0xFFF59E0B) else Color(0xFF10B981)
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.toggleChapterCompletion()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isChapterDone) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = if (isChapterDone) Color(0xFFF59E0B) else Color(0xFF10B981)
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_menu_toggle_completion")
                                    )

                                    // 4. Export / Share
                                    DropdownMenuItem(
                                        text = { Text("Export / Share", color = Color(0xFFF4F4F5)) },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.exportCurrentChapterToPdf(context)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFFE4E4E7)
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_menu_export_pdf")
                                    )

                                    // 4. Replace / Remove PDF options (if PDF available)
                                    if (!currentPdfPath.isNullOrBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Replace PDF", color = Color(0xFFF4F4F5)) },
                                            onClick = {
                                                showChatMenu = false
                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = Color(0xFFA1A1AA)
                                                )
                                            },
                                            modifier = Modifier.testTag("chat_menu_attach_pdf")
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Remove PDF", color = Color(0xFFEF4444)) },
                                            onClick = {
                                                showChatMenu = false
                                                viewModel.removeChapterPdf(context)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444)
                                                )
                                            },
                                            modifier = Modifier.testTag("chat_menu_remove_pdf")
                                        )
                                    }

                                    // 5. Settings
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = Color(0xFFF4F4F5)) },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToSettings()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = Color(0xFFA1A1AA)
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_menu_settings")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0B0B0E),
                            titleContentColor = Color(0xFFFFFFFF),
                            navigationIconContentColor = Color(0xFFFFFFFF),
                            actionIconContentColor = Color(0xFFFFFFFF)
                        )
                    )
                    HorizontalDivider(
                        color = Color(0xFF27272F),
                        thickness = 0.8.dp
                    )
                }
            }
        ) { innerPadding ->
        val currentChapter = chapter
        if (currentChapter == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                // Warning Banner if Gemini API Key is Not Connected
                AnimatedVisibility(
                    visible = connectionState !is GeminiConnectionState.Connected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = ReadMateGoldContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unconfigured_gemini_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ReadMateOnGoldContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Gemini API key is not connected.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = ReadMateOnGoldContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = ReadMateOnGoldContainer
                                ),
                                modifier = Modifier.testTag("banner_configure_gemini_button")
                            ) {
                                Text("Configure", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Chat Messages Stream (Scrollable Area)
                val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (messages.isEmpty() && !viewModel.isLoading && viewModel.errorMessage == null) {
                        EmptyConversationPlaceholder(
                            onOpenPdf = {
                                if (isPdfAvailable) {
                                    viewModel.openPdfReader()
                                } else {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            isPdfAvailable = isPdfAvailable,
                            onNavigateToSettings = onNavigateToSettings,
                            isKeyConnected = connectionState is GeminiConnectionState.Connected,
                            modifier = Modifier.padding(bottom = navBarBottomInset)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chapter_messages_list"),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 16.dp + navBarBottomInset
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Optimistic Provisional Entry Card prepended at the very top (index 0)
                            val activeJob = viewModel.activeJobState
                            val activeError = viewModel.activeErrorState
                            val isProvisionalActive = (viewModel.isLoading || activeJob != null || (activeError != null && activeError.targetMessageId == null)) &&
                                    (activeJob?.isRegeneration != true)

                            if (isProvisionalActive) {
                                item(key = "provisional_active_entry") {
                                    val nextEntryNumber = messages.size + 1
                                    val badgeText = String.format(Locale.getDefault(), "ENTRY #%02d", nextEntryNumber)
                                    val isErrorState = activeError != null && activeError.targetMessageId == null
                                    val pageNum = if (isErrorState) {
                                        activeError?.pageNumber ?: viewModel.lastSnippetPayload?.let { it.pageIndex + 1 }
                                    } else {
                                        activeJob?.pageNumber ?: viewModel.lastSnippetPayload?.let { it.pageIndex + 1 }
                                    }
                                    val statusLabel = if (isErrorState) "Generation Interrupted" else (activeJob?.statusLabel ?: "Analyzing...")
                                    val headline = if (isErrorState) "Tap to retry explanation" else "Analyzing Core Insight..."

                                    ChapterProvisionalEntryCard(
                                        entryBadgeText = badgeText,
                                        pageNumber = pageNum,
                                        statusLabel = statusLabel,
                                        placeholderHeadline = headline,
                                        isError = isErrorState,
                                        onRetry = { viewModel.retryLastExplanation() },
                                        onCancel = { showCancelGenerationDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 2. Reverse-Chronological Timeline Entries (Newest First)
                            itemsIndexed(
                                items = timelineMessages,
                                key = { _, msg -> msg.id }
                            ) { index, message ->
                                val chronologicalIndex = messages.indexOfFirst { it.id == message.id }
                                val entryNumber = if (chronologicalIndex != -1) chronologicalIndex + 1 else messages.size - index
                                val isRegenerating = activeJob?.targetMessageId == message.id
                                val isRegenError = activeError?.targetMessageId == message.id

                                ChapterTimelineEntryCard(
                                    entryIndex = index,
                                    entryNumber = entryNumber,
                                    message = message,
                                    author = book?.author,
                                    isNewlyGenerated = viewModel.highlightedMessageId == message.id,
                                    isTargetHighlighted = viewModel.targetMessageId == message.id,
                                    isRegenerating = isRegenerating,
                                    isRegenError = isRegenError,
                                    onRegenerate = { viewModel.regenerateMessage(message) },
                                    onDelete = { messagePendingDeletion = message },
                                    onRetry = { viewModel.retryLastExplanation() },
                                    onClick = {
                                        val targetNavIndex = if (chronologicalIndex != -1) chronologicalIndex else index
                                        onNavigateToResponse?.invoke(targetNavIndex)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Error card with retry functionality
                            viewModel.errorMessage?.let { errorMsg ->
                                item(key = "error_message_turn") {
                                    ErrorFeedbackItem(
                                        errorMessage = errorMsg,
                                        onRetry = { viewModel.retrySend() },
                                        onDismiss = viewModel::clearError,
                                        onNavigateToSettings = onNavigateToSettings,
                                        isKeyMissing = connectionState !is GeminiConnectionState.Connected,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Word Translation Modal Bottom Sheet (Triggered by manual Translate action or tapping saved tags)
    WordTranslationBottomSheet(
        state = viewModel.translationState,
        onDismiss = viewModel::dismissTranslation,
        onSaveToVault = { translation, fallbackSentence ->
            viewModel.saveWordToVault(translation, fallbackSentence)
        },
        onRetry = { word, sentence, context ->
            val targetMsgId = (viewModel.translationState as? WordTranslationState.Error)?.targetMessageId ?: 0L
            viewModel.translateWord(word, sentence, context, targetMsgId)
        },
        onNavigateToOriginPassage = { targetChapterId, targetMsgId ->
            if (targetChapterId != chapter?.id) {
                onNavigateToChapter?.invoke(targetChapterId)
            } else if (targetMsgId != null) {
                val targetIndex = messages.indexOfFirst { it.id == targetMsgId }
                if (targetIndex >= 0) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            }
        }
    )

    // PDF Reader overlay when opened - maintains chat listState underneath without destroying scroll position
    if (viewModel.isPdfReaderOpen && isPdfAvailable && !currentPdfPath.isNullOrBlank()) {
        val lastReadFallback = if (lastReadPage > 0) lastReadPage else 1
        val initialPageToOpen = viewModel.pdfTargetPage ?: lastReadFallback
        val sPage = chapter?.startPage ?: 1
        val ePage = chapter?.let { chap ->
            if (chap.endPage >= chap.startPage) chap.endPage
            else (chap.startPage + (if (chap.pdfTotalPages > 0) chap.pdfTotalPages else totalPages) - 1).coerceAtLeast(chap.startPage)
        } ?: totalPages
        val formattedTitle = chapter?.let { chap ->
            if (chap.sectionType == "FRONT_MATTER" || chap.sectionType == "BACK_MATTER" || chap.chapterNumber == null) {
                chap.title
            } else {
                "Chapter ${chap.chapterNumber}: ${chap.title}"
            }
        } ?: "Chapter"

        key(currentPdfPath, viewModel.pdfTargetPage) {
            ChapterPdfReaderView(
                pdfFilePath = currentPdfPath,
                totalPages = totalPages,
                initialPage = initialPageToOpen,
                chapterTitle = formattedTitle,
                bookTitle = book?.title,
                startPage = sPage,
                endPage = ePage,
                onCloseReader = viewModel::closePdfReader,
                onPageChanged = viewModel::updatePdfLastReadPage,
                onReattachPdf = {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                },
                onRemovePdf = {
                    viewModel.removeChapterPdf(context)
                },
                isSnipActive = viewModel.isSnipModeActive,
                isProcessingOcr = viewModel.isProcessingOcr,
                isMultiPageSecondStep = viewModel.part1CapturedText != null,
                part1WordCount = viewModel.part1WordCount,
                ocrErrorMessage = viewModel.ocrErrorMessage,
                onClearOcrError = viewModel::clearOcrError,
                onStartSnip = viewModel::startSnipMode,
                onCancelSnip = viewModel::cancelSnipMode,
                onExplainSinglePage = { pageIndex, cropRect, viewWidth, viewHeight ->
                    viewModel.explainSinglePageSnippet(pageIndex, cropRect, viewWidth, viewHeight)
                },
                onCapturePart1AndNext = { pageIndex, cropRect, viewWidth, viewHeight ->
                    viewModel.capturePart1ForMultiPage(pageIndex, cropRect, viewWidth, viewHeight)
                },
                onMergeAndExplain = { pageIndex, cropRect, viewWidth, viewHeight ->
                    viewModel.mergeAndExplainMultiPage(pageIndex, cropRect, viewWidth, viewHeight)
                },
                onStartMasteryQuiz = onNavigateToChapterMasteryQuiz?.let { callback ->
                    {
                        chapter?.id?.let(callback)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
}

/**
 * Timeline entry card representing an analyzed reading snip.
 * Displays Entry #, Page Ref, relative Timestamp, primary Concept Heading,
 * 2-line preview excerpt of understanding, contextual 3-dot overflow menu (Regenerate / Delete),
 * and subtle navigation chevron.
 */
@Composable
private fun ChapterTimelineEntryCard(
    entryIndex: Int,
    entryNumber: Int = entryIndex + 1,
    message: ChapterMessage,
    author: String?,
    isNewlyGenerated: Boolean = false,
    isTargetHighlighted: Boolean = false,
    isRegenerating: Boolean = false,
    isRegenError: Boolean = false,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedEntry = remember(message.id, message.aiResponse, message.originalText) {
        ChapterAnalysisParser.parseEntry(message, author, entryIndex)
    }

    val formattedTime = remember(message.createdAt) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.createdAt))
    }

    val previewExcerpt = remember(parsedEntry.understandingParagraphs, parsedEntry.coreTakeaway) {
        ChapterAnalysisParser.extractPreviewSnippet(
            parsedEntry.understandingParagraphs,
            parsedEntry.coreTakeaway
        )
    }

    val headlineText = remember(parsedEntry.heading, parsedEntry.coreTakeaway, previewExcerpt) {
        if (parsedEntry.heading.isNotBlank() &&
            !parsedEntry.heading.startsWith("Core Synthesis", ignoreCase = true) &&
            !parsedEntry.heading.startsWith("Core Insight", ignoreCase = true)
        ) {
            parsedEntry.heading
        } else if (parsedEntry.coreTakeaway.isNotBlank() &&
            !parsedEntry.coreTakeaway.startsWith("True mastery", ignoreCase = true)
        ) {
            val firstSentence = parsedEntry.coreTakeaway
                .split(Regex("[.!?\\n]"))
                .firstOrNull { it.isNotBlank() }
                ?.trim()
            if (!firstSentence.isNullOrBlank() && firstSentence.length in 5..65) {
                firstSentence
            } else {
                "Core Concept • Entry #${String.format(Locale.getDefault(), "%02d", entryNumber)}"
            }
        } else {
            "Core Concept • Entry #${String.format(Locale.getDefault(), "%02d", entryNumber)}"
        }
    }

    val displayHeadline = when {
        isRegenError -> "Tap to retry explanation"
        isRegenerating -> "Synthesizing alternative breakdown..."
        else -> headlineText
    }

    val understandingPreview = remember(parsedEntry.understandingParagraphs, parsedEntry.coreTakeaway, previewExcerpt) {
        val firstPara = parsedEntry.understandingParagraphs.firstOrNull { it.isNotBlank() }
        if (!firstPara.isNullOrBlank()) {
            firstPara.replace(Regex("[#*`_]"), "").replace(Regex("\\s+"), " ").trim()
        } else if (parsedEntry.coreTakeaway.isNotBlank()) {
            parsedEntry.coreTakeaway.replace(Regex("[#*`_]"), "").replace(Regex("\\s+"), " ").trim()
        } else {
            previewExcerpt
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                if (isRegenError) {
                    onRetry()
                } else {
                    onClick()
                }
            })
            .testTag("timeline_entry_card_$entryIndex"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141418),
        border = BorderStroke(
            1.dp,
            when {
                isRegenError -> Color(0xFFEF4444)
                isNewlyGenerated || isTargetHighlighted -> Color(0xFF71717A)
                else -> Color(0xFF27272F)
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Row 1 (Metadata Header): Entry Badge + Page Tag on left; Timestamp + 3-Dot on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF101013),
                        border = BorderStroke(1.dp, Color(0xFF27272F))
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "ENTRY #%02d", entryNumber),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA1A1AA),
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    if (parsedEntry.pageNumber != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Page ${parsedEntry.pageNumber}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA1A1AA)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isRegenError) {
                        Text(
                            text = "Generation Interrupted",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    } else if (isRegenerating) {
                        Text(
                            text = "Regenerating...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA1A1AA)
                        )
                    } else {
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = Color(0xFF71717A)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Contextual 3-Dot Overflow Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("entry_overflow_menu_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Entry actions",
                                tint = Color(0xFF71717A),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF141418))
                                .border(1.dp, Color(0xFF27272F), RoundedCornerShape(8.dp))
                        ) {
                            if (isRegenError) {
                                DropdownMenuItem(
                                    text = { Text("Retry Synthesis", color = Color(0xFFFFFFFF), fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color(0xFFFFFFFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRetry()
                                    },
                                    modifier = Modifier.testTag("menu_retry_entry_${message.id}")
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Regenerate Insight", color = Color(0xFFFFFFFF), fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRegenerate()
                                    },
                                    modifier = Modifier.testTag("menu_regenerate_entry_${message.id}")
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Delete Entry", color = Color(0xFFEF4444), fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                modifier = Modifier.testTag("menu_delete_entry_${message.id}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2 (Concept Headline): Bold prominent theme title with trailing sleek chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayHeadline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "View breakdown",
                        tint = if (isRegenError) Color(0xFFEF4444) else Color(0xFF52525B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Row 3 (Editorial Understanding Preview): 2 lines of key insight
                if (!isRegenError && understandingPreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = understandingPreview,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFFA1A1AA),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Edge hairline indicator
            if (isRegenerating) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF27272F))
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .testTag("regenerating_progress_bar_${message.id}"),
                        color = Color(0xFFFFFFFF),
                        trackColor = Color(0xFF27272F),
                        strokeCap = StrokeCap.Square
                    )
                }
            } else if (isRegenError) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFEF4444))
                        .testTag("regenerating_error_line_${message.id}")
                )
            }
        }
    }
}

/**
 * Optimistic provisional timeline entry card displayed at the top of Chapter Chat
 * while background OCR extraction and Gemini AI synthesis are in flight, or in error state.
 */
@Composable
private fun ChapterProvisionalEntryCard(
    entryBadgeText: String,
    pageNumber: Int?,
    statusLabel: String,
    placeholderHeadline: String = "Analyzing Core Insight...",
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isError && onRetry != null, onClick = { onRetry?.invoke() })
            .testTag("timeline_provisional_entry_card"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141418),
        border = BorderStroke(1.dp, if (isError) Color(0xFFEF4444) else Color(0xFF27272F))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Top Metadata Row: Dynamic entry badge + target page tag, status aligned right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF101013),
                        border = BorderStroke(1.dp, Color(0xFF27272F))
                    ) {
                        Text(
                            text = entryBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA1A1AA),
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    if (pageNumber != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Page $pageNumber",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA1A1AA)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isError) Color(0xFFEF4444) else Color(0xFF71717A)
                    )

                    if (onCancel != null || onRetry != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("provisional_overflow_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Actions",
                                    tint = if (isError) Color(0xFFEF4444) else Color(0xFF71717A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .background(Color(0xFF141418))
                                    .border(1.dp, Color(0xFF27272F), RoundedCornerShape(8.dp))
                            ) {
                                if (isError && onRetry != null) {
                                    DropdownMenuItem(
                                        text = { Text("Retry", color = Color(0xFFFFFFFF), fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFFFFFFFF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onRetry()
                                        }
                                    )
                                }
                                if (onCancel != null) {
                                    DropdownMenuItem(
                                        text = { Text("Cancel / Dismiss", color = Color(0xFFEF4444), fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onCancel()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card Body: Primary Headline with breathing opacity animation when active
                val infiniteTransition = rememberInfiniteTransition(label = "provisional_breathing")
                val breathingAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.40f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "headlineAlpha"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = placeholderHeadline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) Color(0xFFFFFFFF) else Color(0xFFFFFFFF).copy(alpha = breathingAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isError) {
                        "Generation interrupted. Tap anywhere to retry or cancel from the menu."
                    } else {
                        "Analyzing passage and synthesizing core understanding with Gemini AI..."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFA1A1AA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Edge Indicator: LinearProgressIndicator when loading, red hairline when error
            if (isError) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFEF4444))
                        .testTag("provisional_error_line")
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF27272F))
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .testTag("provisional_progress_bar"),
                        color = Color(0xFFFFFFFF),
                        trackColor = Color(0xFF27272F),
                        strokeCap = StrokeCap.Square
                    )
                }
            }
        }
    }
}

/**
 * High-contrast Obsidian confirmation modal for deleting an insight entry.
 */
@Composable
fun DeleteEntryConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141418),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "Delete this insight entry?",
                color = Color(0xFFFFFFFF),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Text(
                text = "This will permanently remove this insight and reading breakdown.",
                color = Color(0xFFA1A1AA),
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_entry_button")
            ) {
                Text(
                    text = "Delete",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_entry_button")
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp
                )
            }
        },
        modifier = Modifier.border(1.dp, Color(0xFF27272F), RoundedCornerShape(12.dp))
    )
}

/**
 * Structured insight item holder for parsed key principles.
 */
private data class ParsedInsightItem(
    val number: String,
    val title: String,
    val detail: String
)

/**
 * Parses raw insight lines into clean structured items with numbers and titles.
 */
private fun parseInsightItems(rawBody: String): List<ParsedInsightItem> {
    val items = mutableListOf<ParsedInsightItem>()
    val lines = rawBody.lines().map { it.trim() }.filter { it.isNotBlank() }

    var counter = 1
    var currentTitle = ""
    val currentDetails = mutableListOf<String>()

    for (line in lines) {
        val isBulletOrNum = line.startsWith("•") || line.startsWith("-") || line.startsWith("*") ||
                (line.length > 2 && line[0].isDigit() && (line[1] == '.' || line[1] == ')'))

        if (isBulletOrNum) {
            if (currentTitle.isNotBlank() || currentDetails.isNotEmpty()) {
                items.add(
                    ParsedInsightItem(
                        number = String.format(Locale.getDefault(), "%02d", counter++),
                        title = currentTitle.ifBlank { "Insight $counter" },
                        detail = currentDetails.joinToString(" ").trim()
                    )
                )
                currentDetails.clear()
            }

            val cleanLine = line
                .replace(Regex("^[-*•\\d.)\\s]+"), "")
                .trim()

            val boldMatch = Regex("^\\*\\*([^*]+)\\*\\*[:\\s-]*(.*)").find(cleanLine)
            if (boldMatch != null) {
                currentTitle = boldMatch.groupValues[1].trim()
                val rest = boldMatch.groupValues[2].trim()
                if (rest.isNotBlank()) currentDetails.add(rest)
            } else if (cleanLine.contains(":")) {
                val parts = cleanLine.split(":", limit = 2)
                currentTitle = parts[0].trim().replace("**", "")
                if (parts.size > 1 && parts[1].trim().isNotBlank()) {
                    currentDetails.add(parts[1].trim())
                }
            } else {
                currentTitle = cleanLine
            }
        } else {
            currentDetails.add(line)
        }
    }

    if (currentTitle.isNotBlank() || currentDetails.isNotEmpty()) {
        items.add(
            ParsedInsightItem(
                number = String.format(Locale.getDefault(), "%02d", counter),
                title = currentTitle.ifBlank { "Insight $counter" },
                detail = currentDetails.joinToString(" ").trim()
            )
        )
    }

    return items
}

/**
 * Cleanly renders the structured insights list with numbered badges.
 */
@Composable
internal fun InsightsSectionView(
    body: String,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f
) {
    val items = remember(body) { parseInsightItems(body) }
    if (items.isEmpty()) {
        RichMarkdownText(
            text = body,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = (26f * fontScale).sp,
                fontSize = (16.5f * fontScale).sp
            ),
            color = Color(0xFFE4E4E7),
            accentColor = Color(0xFFA1A1AA),
            boldColor = Color(0xFFFFFFFF),
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Custom obsidian dot (#27272A fill with solid #FFFFFF center dot)
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF27272A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFFFFF))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (item.title.isNotBlank()) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (17f * fontScale).sp,
                                        lineHeight = (26f * fontScale).sp
                                    ),
                                    color = Color(0xFFFFFFFF)
                                )
                            }
                            if (item.detail.isNotBlank()) {
                                if (item.title.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                RichMarkdownText(
                                    text = item.detail,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (16f * fontScale).sp,
                                        lineHeight = (26f * fontScale).sp
                                    ),
                                    color = Color(0xFFD4D4D8),
                                    accentColor = Color(0xFFA1A1AA),
                                    boldColor = Color(0xFFFFFFFF)
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
 * Cleanly renders the structured Roman Urdu explanation with single-word English headings,
 * monochrome minimalist vector icons, and psychological contrast typography.
 */
@Composable
internal fun FormattedRomanUrduContent(
    content: String,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f
) {
    val allSections = remember(content) { parseResponseSections(content) }
    val sections = remember(allSections) {
        allSections.filterNot { section ->
            section.title.contains("Original English Passage", ignoreCase = true) ||
            section.title.contains("Original Passage", ignoreCase = true) ||
            section.title.contains("English Passage", ignoreCase = true)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (sections.isEmpty()) {
            val cleanedContent = remember(content) { stripOriginalPassageSection(content) }
            RichMarkdownText(
                text = cleanedContent,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = (25f * fontScale).sp,
                    fontSize = (14.5f * fontScale).sp
                ),
                color = Color(0xFFCBD5E1),
                accentColor = Color(0xFFA1A1AA),
                boldColor = Color(0xFFFFFFFF)
            )
        } else {
            sections.forEachIndexed { index, section ->
                if (index > 0) {
                    HorizontalDivider(
                        color = Color(0xFF27272A),
                        thickness = 0.75.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                when {
                    section.title.contains("Asaan Samjh", ignoreCase = true) ||
                    section.title.contains("Samjh", ignoreCase = true) ||
                    section.title.contains("Understanding", ignoreCase = true) ||
                    section.title.contains("Simple Explanation", ignoreCase = true) ||
                    section.title.contains("Roman Urdu Explanation", ignoreCase = true) ||
                    section.title.contains("Explanation", ignoreCase = true) ||
                    section.title.contains("Overview", ignoreCase = true) -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Psychology,
                                title = "Understanding"
                            )
                            RichMarkdownText(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = (25f * fontScale).sp,
                                    fontSize = (14.5f * fontScale).sp
                                ),
                                color = Color(0xFFCBD5E1),
                                accentColor = Color(0xFFA1A1AA),
                                boldColor = Color(0xFFFFFFFF)
                            )
                        }
                    }
                    section.title.contains("Main Lesson", ignoreCase = true) ||
                    section.title.contains("Lesson", ignoreCase = true) ||
                    section.title.contains("Core", ignoreCase = true) ||
                    section.title.contains("Sabaq", ignoreCase = true) -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C0C0E),
                            border = BorderStroke(1.dp, Color(0xFF27272A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SectionHeader(
                                    icon = Icons.Default.Lightbulb,
                                    title = "Lesson"
                                )
                                RichMarkdownText(
                                    text = section.body,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = (25f * fontScale).sp,
                                        fontSize = (14.5f * fontScale).sp
                                    ),
                                    color = Color(0xFFE2E8F0),
                                    accentColor = Color(0xFFA1A1AA),
                                    boldColor = Color(0xFFFFFFFF)
                                )
                            }
                        }
                    }
                    section.title.contains("Key Points", ignoreCase = true) ||
                    section.title.contains("Points", ignoreCase = true) ||
                    section.title.contains("Insights", ignoreCase = true) ||
                    section.title.contains("Takeaways", ignoreCase = true) ||
                    section.title.contains("Nukaat", ignoreCase = true) -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Explore,
                                title = "Insights"
                            )
                            InsightsSectionView(
                                body = section.body,
                                fontScale = fontScale,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    section.title.contains("Real-World Example", ignoreCase = true) ||
                    section.title.contains("Real-Life Example", ignoreCase = true) ||
                    section.title.contains("Example", ignoreCase = true) ||
                    section.title.contains("Application", ignoreCase = true) ||
                    section.title.contains("Misaal", ignoreCase = true) -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C0C0E),
                            border = BorderStroke(1.dp, Color(0xFF27272A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SectionHeader(
                                    icon = Icons.Default.Public,
                                    title = "Example"
                                )
                                RichMarkdownText(
                                    text = section.body,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = (25f * fontScale).sp,
                                        fontSize = (14.5f * fontScale).sp
                                    ),
                                    color = Color(0xFFCBD5E1),
                                    accentColor = Color(0xFFA1A1AA),
                                    boldColor = Color(0xFFFFFFFF)
                                )
                            }
                        }
                    }
                    section.title.contains("Vocabulary", ignoreCase = true) ||
                    section.title.contains("Alfaaz", ignoreCase = true) ||
                    section.title.contains("Punchline", ignoreCase = true) ||
                    section.title.contains("Lexicon", ignoreCase = true) -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Translate,
                                title = "Lexicon"
                            )
                            RichMarkdownText(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = (25f * fontScale).sp,
                                    fontSize = (14.5f * fontScale).sp
                                ),
                                color = Color(0xFFCBD5E1),
                                accentColor = Color(0xFFA1A1AA),
                                boldColor = Color(0xFFFFFFFF)
                            )
                        }
                    }
                    else -> {
                        val cleanTitle = section.title.removePrefix("#").trim()
                        val singleWordTitle = cleanTitle.split(" ").firstOrNull()?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        } ?: "Insight"
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cleanTitle.isNotBlank()) {
                                SectionHeader(
                                    icon = Icons.Default.AutoAwesome,
                                    title = singleWordTitle
                                )
                            }
                            RichMarkdownText(
                                text = section.body,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = (25f * fontScale).sp,
                                    fontSize = (14.5f * fontScale).sp
                                ),
                                color = Color(0xFFCBD5E1),
                                accentColor = Color(0xFFA1A1AA),
                                boldColor = Color(0xFFFFFFFF)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun stripOriginalPassageSection(raw: String): String {
    val delimiterRegex = Regex("(?i)(?:\\n\\s*(?:---|\\*\\*\\*|___))?\\s*\\n+##+\\s*(?:📖\\s*)?Original(?:\\s+English)?\\s+Passage[\\s\\S]*$")
    return raw.replace(delimiterRegex, "").trim()
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = Color(0xFF27272A),
            border = BorderStroke(1.dp, Color(0xFF3F3F46)),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFF4F4F5),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = Color(0xFFF4F4F5)
        )
    }
}

private data class ResponseSection(
    val title: String,
    val body: String
)

private fun parseResponseSections(raw: String): List<ResponseSection> {
    val lines = raw.lines()
    val sections = mutableListOf<ResponseSection>()
    var currentTitle = ""
    val currentBodyLines = mutableListOf<String>()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            continue
        }
        if (trimmed.startsWith("#")) {
            val headerText = trimmed.trimStart('#').trim()
            if (currentTitle.isNotBlank() || currentBodyLines.isNotEmpty()) {
                sections.add(
                    ResponseSection(
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
            ResponseSection(
                title = currentTitle.trim(),
                body = currentBodyLines.joinToString("\n").trim()
            )
        )
    }

    return sections
}

@Composable
private fun AiThinkingItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.testTag("ai_thinking_indicator"),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF000000)
            ),
            border = BorderStroke(1.dp, Color(0xFF2E2E34)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFF4F4F5)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ReadMate is generating explanation...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp
                    ),
                    color = Color(0xFFA1A1AA)
                )
            }
        }
    }
}

@Composable
private fun ErrorFeedbackItem(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    isKeyMissing: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("chat_error_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A1515)
        ),
        border = BorderStroke(1.dp, Color(0xFF4C1D1D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Unable to complete request",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFECACA)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFEE2E2)
                    ),
                    modifier = Modifier.testTag("chat_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }

                if (isKeyMissing) {
                    Button(
                        onClick = onNavigateToSettings,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFFFFF),
                            contentColor = Color(0xFF000000)
                        ),
                        modifier = Modifier.testTag("chat_error_configure_button")
                    ) {
                        Text("Configure Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationPlaceholder(
    onOpenPdf: () -> Unit,
    isPdfAvailable: Boolean,
    onNavigateToSettings: () -> Unit,
    isKeyConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF000000),
            border = BorderStroke(1.dp, Color(0xFF2E2E34)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color(0xFFF4F4F5),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Chapter Explanation Stream",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Open the chapter PDF in the reader and snip any English passage. ReadMate will explain it in natural Pakistani Roman Urdu with main lessons, key points, and real-life examples.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onOpenPdf,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFFFFF),
                contentColor = Color(0xFF000000)
            ),
            modifier = Modifier.testTag("empty_placeholder_open_pdf_button")
        ) {
            Icon(
                imageVector = if (isPdfAvailable) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isPdfAvailable) "Open PDF Reader" else "Attach Chapter PDF")
        }

        if (!isKeyConnected) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF4F4F5)
                ),
                modifier = Modifier.testTag("empty_placeholder_connect_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Gemini API Key")
            }
        }
    }
}
