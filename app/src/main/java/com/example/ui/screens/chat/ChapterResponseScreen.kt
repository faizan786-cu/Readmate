package com.example.ui.screens.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.local.database.entity.WordVaultEntry
import com.example.ui.components.SelectablePassageView
import com.example.ui.components.WordTranslationBottomSheet
import com.example.ui.components.pdf.ChapterPdfReaderView
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.ChapterChatViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun String.cleanAsterisks(): String = this.replace(Regex("\\*+"), "").trim()

/**
 * Full-screen continuous editorial document reading experience for an analyzed reading entry.
 *
 * Strict Monochrome Obsidian Palette:
 * - Background: #0B0B0E
 * - Surface: #141418
 * - Borders: #27272F
 * - High-Contrast Text: #FFFFFF
 * - Muted Headings / Secondary: #A1A1AA / #71717A
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterResponseScreen(
    onNavigateBack: () -> Unit,
    initialIndex: Int = 0,
    onNavigateToWordVault: () -> Unit = {},
    onOpenPdfAtPage: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ChapterChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapterWords by viewModel.chapterWords.collectAsStateWithLifecycle()

    val currentPdfPath = chapter?.pdfFilePath ?: book?.pdfFilePath
    val isPdfAvailable = remember(currentPdfPath) {
        !currentPdfPath.isNullOrBlank() && File(currentPdfPath).exists()
    }
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachPdfUri(context, uri)
        }
    }

    val totalEntries = messages.size
    val safeInitialIndex = remember(initialIndex, totalEntries) {
        if (totalEntries == 0) 0 else initialIndex.coerceIn(0, totalEntries - 1)
    }

    val pagerState = rememberPagerState(initialPage = safeInitialIndex) {
        messages.size
    }

    // Keep pager synchronized when initialIndex or totalEntries updates
    LaunchedEffect(initialIndex, totalEntries) {
        if (totalEntries > 0 && pagerState.currentPage != safeInitialIndex) {
            pagerState.scrollToPage(safeInitialIndex)
        }
    }

    val currentMessage = messages.getOrNull(pagerState.currentPage)
    val currentEntry = remember(currentMessage, pagerState.currentPage, book?.author) {
        currentMessage?.let { ChapterAnalysisParser.parseEntry(it, book?.author, pagerState.currentPage) }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        DeleteEntryConfirmationDialog(
            onConfirm = {
                showDeleteConfirmDialog = false
                currentMessage?.let { msg ->
                    viewModel.deleteMessage(msg, context) {
                        onNavigateBack()
                    }
                }
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chapter_response_screen"),
        containerColor = Color(0xFF0B0B0E),
        topBar = {
            val headerText = remember(currentEntry, chapter) {
                val chapNumber = chapter?.chapterNumber
                val chapPrefix = if (chapNumber != null) "Ch. $chapNumber" else (chapter?.title ?: "Chapter")
                val pageNum = currentEntry?.pageNumber
                if (pageNum != null) "$chapPrefix • Page $pageNum" else chapPrefix
            }

            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = headerText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA1A1AA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("response_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Timeline",
                            tint = Color(0xFFFFFFFF)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val targetPage = currentEntry?.pageNumber
                            if (onOpenPdfAtPage != null) {
                                onOpenPdfAtPage(targetPage)
                            } else if (isPdfAvailable) {
                                viewModel.openPdfReader(targetPage = targetPage)
                            } else {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        },
                        modifier = Modifier.testTag("response_open_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Open in PDF Reader",
                            tint = Color(0xFFFFFFFF)
                        )
                    }

                    // Contextual 3-dot overflow menu
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("response_overflow_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Entry actions",
                                tint = Color(0xFFFFFFFF)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF141418))
                                .border(1.dp, Color(0xFF27272F), RoundedCornerShape(8.dp))
                        ) {
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
                                    currentMessage?.let { msg ->
                                        viewModel.regenerateMessage(msg)
                                    }
                                },
                                modifier = Modifier.testTag("response_menu_regenerate_entry")
                            )

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
                                    showDeleteConfirmDialog = true
                                },
                                modifier = Modifier.testTag("response_menu_delete_entry")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0B0B0E),
                    navigationIconContentColor = Color(0xFFFFFFFF),
                    titleContentColor = Color(0xFFA1A1AA),
                    actionIconContentColor = Color(0xFFFFFFFF)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isCurrentRegenerating = currentMessage != null && viewModel.activeJobState?.targetMessageId == currentMessage.id
            val isCurrentError = currentMessage != null && viewModel.activeErrorState?.targetMessageId == currentMessage.id
            if (messages.isNotEmpty()) {
                // Smooth horizontal swipe paging across all document entries
                HorizontalPager(
                    state = pagerState,
                    key = { page -> messages.getOrNull(page)?.id ?: page.toLong() },
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val messageToRender = messages.getOrNull(pageIndex)
                    if (messageToRender != null) {
                        val parsedEntry = remember(messageToRender.id, messageToRender.aiResponse, messageToRender.originalText) {
                            ChapterAnalysisParser.parseEntry(messageToRender, book?.author, pageIndex)
                        }
                        val tagsForMessage = remember(chapterWords, messageToRender.id) {
                            chapterWords.filter { it.messageId == messageToRender.id }
                        }
                        val scrollState = rememberScrollState()

                        // Automatically reset vertical scroll to the top of the new entry upon page change
                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage == pageIndex) {
                                scrollState.scrollTo(0)
                            }
                        }

                        EditorialDocumentBody(
                            entry = parsedEntry,
                            message = messageToRender,
                            savedTags = tagsForMessage,
                            currentIndex = pageIndex,
                            totalCount = messages.size,
                            onPrevClick = {
                                if (pageIndex > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pageIndex - 1)
                                    }
                                }
                            },
                            onNextClick = {
                                if (pageIndex < messages.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pageIndex + 1)
                                    }
                                }
                            },
                            onTranslateWord = { word, sentence, contextText, msgId ->
                                viewModel.translateWord(word, sentence, contextText, msgId)
                            },
                            onTagClicked = { entry ->
                                viewModel.openSavedWordTranslation(entry)
                            },
                            onOpenPdfReader = { targetPage ->
                                if (onOpenPdfAtPage != null) {
                                    onOpenPdfAtPage(targetPage)
                                } else if (isPdfAvailable) {
                                    viewModel.openPdfReader(targetPage = targetPage)
                                } else {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            scrollState = scrollState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No analysis available",
                        color = Color(0xFF71717A),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Top hairline progress indicator when active regeneration is running for this message
            if (isCurrentRegenerating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                        .testTag("response_regenerating_progress"),
                    color = Color(0xFFFFFFFF),
                    trackColor = Color(0xFF27272F),
                    strokeCap = StrokeCap.Square
                )
            } else if (isCurrentError) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .clickable { viewModel.retryLastExplanation() }
                        .testTag("response_regeneration_error_banner"),
                    color = Color(0xFF141418),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generation Interrupted • Tap to retry",
                            color = Color(0xFFEF4444),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Direct PDF Reader Full-Screen Overlay
    if (viewModel.isPdfReaderOpen && isPdfAvailable && !currentPdfPath.isNullOrBlank()) {
        val sPage = chapter?.startPage ?: 1
        val ePage = chapter?.let { chap ->
            if (chap.endPage >= chap.startPage) chap.endPage
            else (chap.startPage + (if (chap.pdfTotalPages > 0) chap.pdfTotalPages else 1) - 1).coerceAtLeast(chap.startPage)
        } ?: 1
        val formattedTitle = chapter?.let { chap ->
            if (chap.sectionType == "FRONT_MATTER" || chap.sectionType == "BACK_MATTER" || chap.chapterNumber == null) {
                chap.title
            } else {
                "Chapter ${chap.chapterNumber}: ${chap.title}"
            }
        } ?: "Chapter"

        val initialPageToOpen = viewModel.pdfTargetPage ?: currentEntry?.pageNumber ?: (chapter?.startPage ?: 1)

        key(currentPdfPath, viewModel.pdfTargetPage) {
            ChapterPdfReaderView(
                pdfFilePath = currentPdfPath,
                totalPages = chapter?.pdfTotalPages ?: book?.pdfTotalPages ?: 1,
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
                    onNavigateBack()
                },
                onCapturePart1AndNext = { pageIndex, cropRect, viewWidth, viewHeight ->
                    viewModel.capturePart1ForMultiPage(pageIndex, cropRect, viewWidth, viewHeight)
                },
                onMergeAndExplain = { pageIndex, cropRect, viewWidth, viewHeight ->
                    viewModel.mergeAndExplainMultiPage(pageIndex, cropRect, viewWidth, viewHeight)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Word Translation Modal Bottom Sheet (for interactive Word Vault lookups)
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
        onNavigateToOriginPassage = { _, _ ->
            // Already inside origin passage
        }
    )
}

/**
 * Continuous editorial document body.
 *
 * Flow:
 * 1. Document Metadata Row
 * 2. Main Concept Headline
 * 3. UNDERSTANDING (Comfortable paragraphs in crisp white, no quote interleaving)
 * 4. Conversational Transition Bridge leading seamlessly to Real-Life Scenario
 * 5. CORE TAKEAWAY (Elegant pull-quote style with vertical accent bar, no heavy bold)
 * 6. KEY INSIGHTS (Clean indented bullets with dot glyphs and crisp white typography)
 * 7. ORIGINAL PASSAGE (Interactive container for Word Vault text selection)
 * 8. Sequential Navigation Dock (Natural inline component at the bottom of the document)
 */
@Composable
private fun EditorialDocumentBody(
    entry: AnalyzedEntryData,
    message: ChapterMessage,
    savedTags: List<WordVaultEntry>,
    currentIndex: Int,
    totalCount: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onTranslateWord: (selectedWord: String, sentence: String, surroundingContext: String, messageId: Long) -> Unit,
    onTagClicked: (WordVaultEntry) -> Unit,
    onOpenPdfReader: (Int?) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val formattedTime = remember(message.createdAt) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.createdAt))
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Document Metadata Row
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
                    text = "ENTRY",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (entry.pageNumber != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "p. ${entry.pageNumber}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA1A1AA)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = Color(0xFF71717A)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Concept Headline: 18sp to 20sp, bold editorial typography (#FFFFFF)
        Text(
            text = entry.heading.cleanAsterisks(),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF),
            lineHeight = 26.sp,
            modifier = Modifier.testTag("editorial_document_heading")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. UNDERSTANDING: Clean Flow Without Quote Interleaving
        EditorialSectionKicker(title = "UNDERSTANDING")
        Spacer(modifier = Modifier.height(10.dp))

        val paragraphs = entry.understandingParagraphs
        if (paragraphs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (para in paragraphs) {
                    Text(
                        text = para.cleanAsterisks(),
                        fontSize = 15.sp,
                        color = Color(0xFFFFFFFF),
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // 2. Fixed Conversational Transition Bridge: High-contrast crisp white (#FFFFFF), medium-weight italic style
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = entry.transitionBridge.cleanAsterisks(),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            color = Color(0xFFFFFFFF),
            lineHeight = 22.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Scenario / Real-Life Example inside quotation marks in crisp white text (#FFFFFF)
        val cleanScenario = remember(entry.scenario) {
            val s = entry.scenario.trim()
                .removePrefix("“").removeSuffix("”")
                .removePrefix("\"").removeSuffix("\"")
                .trim()
                .cleanAsterisks()
            "“$s”"
        }
        Text(
            text = cleanScenario,
            fontSize = 14.5.sp,
            color = Color(0xFFFFFFFF),
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4. Prominent & Elegant CORE TAKEAWAY (No Heavy Bold)
        // Editorial pull-quote style with vertical accent bar
        EditorialSectionKicker(title = "CORE TAKEAWAY")
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF71717A), shape = RoundedCornerShape(1.dp))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = entry.coreTakeaway.cleanAsterisks(),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFFFFFFFF),
                lineHeight = 24.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // 5. KEY INSIGHTS: Clean Indented Bullets with Crisp White Text (#FFFFFF)
        if (entry.keyInsights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            EditorialSectionKicker(title = "KEY INSIGHTS")
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (bullet in entry.keyInsights) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFA1A1AA),
                            modifier = Modifier.padding(end = 10.dp)
                        )

                        val lead = bullet.leadTitle.cleanAsterisks()
                        val explanation = bullet.explanation.cleanAsterisks()

                        Text(
                            text = if (lead.isNotBlank()) {
                                androidx.compose.ui.text.buildAnnotatedString {
                                    pushStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFFFFFF)
                                        )
                                    )
                                    append(lead)
                                    append(": ")
                                    pop()
                                    pushStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFFFFFFFF)
                                        )
                                    )
                                    append(explanation)
                                    pop()
                                }
                            } else {
                                androidx.compose.ui.text.buildAnnotatedString {
                                    pushStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFFFFFFFF)
                                        )
                                    )
                                    append(explanation)
                                    pop()
                                }
                            },
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 6. Editorial ORIGINAL PASSAGE Formatting
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("interactive_original_passage_container"),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF101013),
            border = BorderStroke(1.dp, Color(0xFF27272F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val pageSuffix = if (entry.pageNumber != null) " • P. ${entry.pageNumber}" else ""
                    Text(
                        text = "ORIGINAL PASSAGE$pageSuffix",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA1A1AA),
                        letterSpacing = 1.5.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(entry.originalPassage.cleanAsterisks()))
                                Toast.makeText(context, "Original passage copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy original text",
                                tint = Color(0xFF71717A),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (entry.pageNumber != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onOpenPdfReader(entry.pageNumber) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Jump to page",
                                    tint = Color(0xFF71717A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive selectable passage with word highlighting and translation action mode
                SelectablePassageView(
                    text = entry.originalPassage.cleanAsterisks(),
                    messageId = message.id,
                    savedWords = savedTags,
                    onTranslateWord = onTranslateWord,
                    onWordClick = onTagClicked,
                    modifier = Modifier.fillMaxWidth(),
                    fontSizeSp = 14.5f
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select any word to look up Roman Urdu & save to Word Vault",
                    fontSize = 11.sp,
                    color = Color(0xFF71717A),
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 7. Sequential Navigation Bar directly under Original Passage
        SequentialBottomDock(
            currentIndex = currentIndex,
            totalCount = totalCount,
            onPrevClick = onPrevClick,
            onNextClick = onNextClick,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("sequential_bottom_dock")
        )

        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * Editorial section kicker in uppercase subtle zinc typography.
 * Formatted with letter-spacing = 1.5sp, 11sp, font weight medium.
 */
@Composable
private fun EditorialSectionKicker(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = Color(0xFFA1A1AA),
        modifier = modifier
    )
}

/**
 * Monochrome pill sequential navigation dock placed directly below the Original Passage container.
 *
 * Left Arrow (←): Navigates to previous entry, disabled with reduced alpha if at first entry.
 * Center Indicator: High-contrast counter (01 / 02).
 * Right Arrow (→): Navigates to next entry, disabled with reduced alpha if at last entry.
 */
@Composable
private fun SequentialBottomDock(
    currentIndex: Int,
    totalCount: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoPrev = currentIndex > 0
    val canGoNext = currentIndex < totalCount - 1

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)),
        color = Color(0xFF141418),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF27272F))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Arrow Button
            IconButton(
                onClick = onPrevClick,
                enabled = canGoPrev,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("sequential_dock_prev")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Entry",
                    tint = if (canGoPrev) Color(0xFFFFFFFF) else Color(0xFF71717A).copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Indicator (01 / 02)
            val counterText = remember(currentIndex, totalCount) {
                String.format(Locale.getDefault(), "%02d / %02d", currentIndex + 1, totalCount)
            }

            Text(
                text = counterText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("sequential_dock_counter")
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right Arrow Button
            IconButton(
                onClick = onNextClick,
                enabled = canGoNext,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("sequential_dock_next")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Entry",
                    tint = if (canGoNext) Color(0xFFFFFFFF) else Color(0xFF71717A).copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
