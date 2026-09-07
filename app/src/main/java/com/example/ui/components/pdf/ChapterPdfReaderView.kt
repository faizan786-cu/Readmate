package com.example.ui.components.pdf

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterPdfReaderView(
    pdfFilePath: String,
    totalPages: Int,
    initialPage: Int,
    chapterTitle: String,
    bookTitle: String?,
    startPage: Int = 1,
    endPage: Int = totalPages,
    onCloseReader: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onReattachPdf: () -> Unit,
    onRemovePdf: () -> Unit,
    // Snip & OCR Callbacks
    isSnipActive: Boolean,
    isProcessingOcr: Boolean,
    isMultiPageSecondStep: Boolean,
    part1WordCount: Int,
    ocrErrorMessage: String? = null,
    onClearOcrError: () -> Unit = {},
    onStartSnip: () -> Unit,
    onCancelSnip: () -> Unit,
    onExplainSinglePage: (physicalPageIndex: Int, cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onCapturePart1AndNext: (physicalPageIndex: Int, cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onMergeAndExplain: (physicalPageIndex: Int, cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onStartMasteryQuiz: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sPage = startPage.coerceAtLeast(1)
    val ePage = if (endPage >= sPage) endPage else (sPage + totalPages - 1).coerceAtLeast(sPage)
    val sectionPageCount = (ePage - sPage + 1).coerceAtLeast(1)
    val hasTerminalPage = onStartMasteryQuiz != null
    val totalPagerPages = if (hasTerminalPage) sectionPageCount + 1 else sectionPageCount

    val safeInitialPage = when {
        initialPage >= sPage && initialPage <= ePage -> (initialPage - sPage).coerceIn(0, sectionPageCount - 1)
        initialPage in 1..sectionPageCount -> (initialPage - 1).coerceIn(0, sectionPageCount - 1)
        else -> 0
    }

    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { totalPagerPages }
    )
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showPageSlider by remember { mutableStateOf(false) }

    // Page change listener for auto-saving last read position & auto-dismissing slider on page swipe
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { relativePage ->
                showPageSlider = false
                if (relativePage < sectionPageCount) {
                    val physicalPage = sPage + relativePage
                    onPageChanged(physicalPage)
                } else {
                    // Virtual terminal page: marks chapter reading as 100% Explored
                    onPageChanged(ePage)
                }
            }
    }

    // Auto-dismiss slider when user starts swiping/scrolling between pages
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) {
                    showPageSlider = false
                }
            }
    }

    // Smoothly jump to specific target page if requested (e.g. via "Go to Page X" badge)
    LaunchedEffect(initialPage, sPage, ePage) {
        if (initialPage > 0) {
            val targetRelIndex = when {
                initialPage >= sPage && initialPage <= ePage -> (initialPage - sPage).coerceIn(0, sectionPageCount - 1)
                initialPage in 1..sectionPageCount -> (initialPage - 1).coerceIn(0, sectionPageCount - 1)
                else -> 0
            }
            if (pagerState.currentPage != targetRelIndex) {
                pagerState.scrollToPage(targetRelIndex)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chapter_pdf_reader_view"),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    val isTerminalPage = hasTerminalPage && pagerState.currentPage >= sectionPageCount
                    val currentRelPage = (pagerState.currentPage + 1).coerceAtMost(sectionPageCount)
                    val currentPhysPage = (sPage + pagerState.currentPage).coerceAtMost(ePage)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = chapterTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.1).sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!bookTitle.isNullOrBlank()) {
                                Text(
                                    text = bookTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = if (isTerminalPage) {
                                    "Mastery Gatekeeper"
                                } else if (sectionPageCount == totalPages && sPage == 1) {
                                    "p. $currentRelPage/$sectionPageCount"
                                } else {
                                    "p. $currentRelPage/$sectionPageCount (Doc $currentPhysPage)"
                                },
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("pdf_reader_page_indicator")
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCloseReader,
                        modifier = Modifier.testTag("pdf_reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Chat View"
                        )
                    }
                },
                actions = {
                    // Chat Navigation Icon
                    IconButton(
                        onClick = onCloseReader,
                        modifier = Modifier.testTag("pdf_reader_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Back to Chapter Chat"
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("pdf_reader_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "PDF Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 1. Open Chat View
                            DropdownMenuItem(
                                text = { Text("Open Chat View") },
                                onClick = {
                                    showMenu = false
                                    onCloseReader()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.testTag("pdf_menu_open_chat")
                            )

                            // 2. Replace / Re-attach PDF
                            DropdownMenuItem(
                                text = { Text("Replace / Re-attach PDF") },
                                onClick = {
                                    showMenu = false
                                    onReattachPdf()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.testTag("pdf_menu_replace")
                            )

                            // 3. Remove PDF from Chapter
                            DropdownMenuItem(
                                text = { Text("Remove PDF from Chapter") },
                                onClick = {
                                    showMenu = false
                                    onRemovePdf()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.testTag("pdf_menu_remove")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. PDF Page Pager
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isSnipActive,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pdf_horizontal_pager")
            ) { relativeIndex ->
                if (hasTerminalPage && relativeIndex >= sectionPageCount) {
                    ChapterMasteryGatekeeperPage(
                        chapterTitle = chapterTitle,
                        onStartMasteryQuiz = { onStartMasteryQuiz?.invoke() },
                        onReviewChapter = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((sectionPageCount - 1).coerceAtLeast(0))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val physicalIndex = (sPage - 1) + relativeIndex
                    ZoomablePdfPageView(
                        filePath = pdfFilePath,
                        pageIndex = physicalIndex,
                        isSnipActive = isSnipActive,
                        onCenterTap = {
                            showPageSlider = !showPageSlider
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Error banner if OCR or PDF operation fails
            if (!ocrErrorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth(0.9f)
                        .testTag("pdf_reader_ocr_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ocrErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearOcrError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Layered Bottom Controls: Floating Snip FAB & Full-Width Docked Navigation Bar
            val isTerminalActive = hasTerminalPage && pagerState.currentPage >= sectionPageCount
            AnimatedVisibility(
                visible = !isSnipActive && !isTerminalActive,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Floating Action Button (Snip & Explain) placed at bottom-right
                    ExtendedFloatingActionButton(
                        onClick = {
                            showPageSlider = false
                            onStartSnip()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = "Snip & Explain",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("pdf_reader_snip_fab")
                    )

                    // Row 2: Tap-Toggled Full-Width Translucent Bar with Page Slider and Navigation Arrows
                    AnimatedVisibility(
                        visible = showPageSlider && !isSnipActive,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            tonalElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage > 0,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                        contentDescription = "Previous Page"
                                    )
                                }

                                Slider(
                                    value = (pagerState.currentPage + 1).coerceAtMost(sectionPageCount).toFloat(),
                                    onValueChange = { newVal ->
                                        val targetPage = (newVal.toInt() - 1).coerceIn(0, sectionPageCount - 1)
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(targetPage)
                                        }
                                    },
                                    valueRange = 1f..sectionPageCount.toFloat(),
                                    steps = if (sectionPageCount > 2) sectionPageCount - 2 else 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                        .testTag("pdf_page_slider")
                                )

                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage < sectionPageCount - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage < sectionPageCount - 1,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                        contentDescription = "Next Page"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Visual Crop Overlay for Snipping & Multi-Page Passage Combination
            if (isSnipActive) {
                val currentPhysicalIndex = (sPage - 1) + pagerState.currentPage
                VisualCropOverlay(
                    isMultiPageSecondStep = isMultiPageSecondStep,
                    part1WordCount = part1WordCount,
                    isProcessingOcr = isProcessingOcr,
                    onExplainSinglePage = { cropRect, viewWidth, viewHeight ->
                        onExplainSinglePage(currentPhysicalIndex, cropRect, viewWidth, viewHeight)
                    },
                    onCapturePart1AndNext = { cropRect, viewWidth, viewHeight ->
                        val capturedRelPage = pagerState.currentPage
                        val capturedPhysIndex = (sPage - 1) + capturedRelPage
                        onCapturePart1AndNext(capturedPhysIndex, cropRect, viewWidth, viewHeight)
                        // If there is a next page in section, automatically advance to it
                        if (capturedRelPage < sectionPageCount - 1) {
                            coroutineScope.launch {
                                pagerState.scrollToPage(capturedRelPage + 1)
                            }
                        }
                    },
                    onMergeAndExplain = { cropRect, viewWidth, viewHeight ->
                        onMergeAndExplain(currentPhysicalIndex, cropRect, viewWidth, viewHeight)
                    },
                    onCancel = onCancelSnip,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Terminal "Chapter Mastery Gatekeeper" Page
 * Appended immediately following the final page of the chapter in the PDF Reader.
 * Enforces taking the 50-MCQ Mastery Quiz to unlock "Mastered" status and progress to the next chapter.
 */
@Composable
fun ChapterMasteryGatekeeperPage(
    chapterTitle: String,
    onStartMasteryQuiz: () -> Unit,
    onReviewChapter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .testTag("chapter_mastery_gatekeeper_page")
        ) {
            // Icon: Refined geometric trophy glyph (🏆) housed inside a dark circular pedestal (#141418 with 1dp #27272F border)
            Surface(
                shape = CircleShape,
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                modifier = Modifier
                    .size(76.dp)
                    .testTag("gatekeeper_trophy_icon")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Headline: "Chapter Completed" (#FFFFFF, bold, 20sp)
            Text(
                text = "Chapter Completed",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("gatekeeper_headline")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-text: "To verify retention, unlock the 'Mastered' status, and access the next chapter, complete the comprehensive 50-question mastery challenge." (#A1A1AA, 13sp, centered, maximum 280dp width)
            Text(
                text = "To verify retention, unlock the 'Mastered' status, and access the next chapter, complete the comprehensive 50-question mastery challenge.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .testTag("gatekeeper_subtext")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action Button: "✦ Start Mastery Quiz (50 MCQs)" (#0B0B0E text on solid #FFFFFF surface, rounded 12dp)
            Button(
                onClick = onStartMasteryQuiz,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFFFFF),
                    contentColor = Color(0xFF0B0B0E)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("gatekeeper_start_quiz_button")
            ) {
                Text(
                    text = "✦ Start Mastery Quiz (50 MCQs)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color(0xFF0B0B0E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Action: Review Chapter
            OutlinedButton(
                onClick = onReviewChapter,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFA1A1AA)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("gatekeeper_review_button")
            ) {
                Text(
                    text = "Review Chapter Pages",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = Color(0xFFA1A1AA)
                )
            }
        }
    }
}
