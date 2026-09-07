package com.example.ui.screens.chapter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.manager.DailyRetentionManager
import com.example.data.manager.ExtractionStatus
import com.example.data.manager.PdfExtractionStateManager
import com.example.data.model.ChapterUnlockStatus
import com.example.data.model.ExtractedChapterSection
import com.example.data.util.ProgressCalculator
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.ReaderLockoutSheet
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.ChapterListViewModel
import kotlinx.coroutines.launch

// Strict Monochrome Palette
private val JetObsidian = Color(0xFF0B0B0E)
private val CardSurface = Color(0xFF141418)
private val CardSubSurface = Color(0xFF1C1C22)
private val PillTrackBg = Color(0xFF18181B)
private val PerimeterZinc = Color(0xFF27272F)
private val CrispWhite = Color(0xFFFFFFFF)
private val SilverMuted = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val LockSlate = Color(0xFF3F3F46)
private val LockedTextMuted = Color(0xFF52525B)
private val EmptyStateZinc = Color(0xFF3F3F46)
private val ActivePillBadgeBg = Color(0xFFE4E4E7)
private val InactivePillBadgeBg = Color(0xFF27272A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    bookId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToChapterChat: (Long) -> Unit,
    onNavigateToAddChapter: (Long) -> Unit = {},
    onNavigateToDailyRecall: () -> Unit = {},
    viewModel: ChapterListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val unlockStatuses by viewModel.unlockStatuses.collectAsState()
    val retentionState by viewModel.dailyRetentionState.collectAsState()
    val isReaderLocked by viewModel.isReaderLocked.collectAsState()
    val extractionStatus by PdfExtractionStateManager.currentStatus.collectAsState()

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showLockoutSheet by remember { mutableStateOf(false) }

    val isScanning = extractionStatus is ExtractionStatus.Processing &&
            (extractionStatus as ExtractionStatus.Processing).bookId == bookId

    val isSuccessBanner = extractionStatus is ExtractionStatus.Success &&
            (extractionStatus as ExtractionStatus.Success).bookId == bookId

    // Group chapters into Front Matter, Core Chapters, and Back Matter
    val frontMatter = remember(chapters) {
        chapters.filter {
            ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) == ExtractedChapterSection.TYPE_FRONT_MATTER
        }
    }

    val coreChapters = remember(chapters) {
        chapters.filter {
            ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) == ExtractedChapterSection.TYPE_CORE_CHAPTER
        }
    }

    val backMatter = remember(chapters) {
        chapters.filter {
            ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) == ExtractedChapterSection.TYPE_BACK_MATTER
        }
    }

    // 0: Front Matter, 1: Chapters, 2: Back Matter (Default to Chapters)
    var selectedTabIndex by remember { mutableIntStateOf(1) }

    PdfExportDialog(
        state = viewModel.exportUiState,
        onDismiss = viewModel::dismissExportDialog
    )

    if (showLockoutSheet) {
        ReaderLockoutSheet(
            onStartDailyRecall = {
                showLockoutSheet = false
                onNavigateToDailyRecall()
            },
            onDismiss = { showLockoutSheet = false }
        )
    }

    Scaffold(
        containerColor = JetObsidian,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { snackbarData ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PillTrackBg,
                    border = BorderStroke(1.dp, PerimeterZinc),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = SilverMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = snackbarData.visuals.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.1.sp
                            ),
                            color = Color(0xFFFAFAFA),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book?.title ?: "Book Chapters",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = CrispWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chapters.size} sections • AI Structure",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = SilverMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("chapter_list_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CrispWhite
                        )
                    }
                },
                actions = {
                    val hasPdf = book?.pdfFilePath?.isNotBlank() == true || (book?.pdfTotalPages ?: 0) > 0
                    if (hasPdf && chapters.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val firstChapter = coreChapters.firstOrNull() ?: chapters.firstOrNull()
                                if (firstChapter != null) {
                                    if (isReaderLocked) {
                                        showLockoutSheet = true
                                    } else {
                                        onNavigateToChapterChat(firstChapter.id)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("read_pdf_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = "Read PDF",
                                tint = CrispWhite
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JetObsidian,
                    navigationIconContentColor = CrispWhite,
                    titleContentColor = CrispWhite,
                    actionIconContentColor = CrispWhite
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddChapter(bookId) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Chapter",
                        tint = JetObsidian,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = "Add Chapter",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = JetObsidian
                    )
                },
                containerColor = CrispWhite,
                contentColor = JetObsidian,
                shape = RoundedCornerShape(14.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp
                ),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_chapter_fab")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. Scanning Overlay Banner
            if (isScanning) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, PerimeterZinc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Analyzing book structure with Flash-Lite...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = SilverMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = CrispWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = CrispWhite,
                                trackColor = PillTrackBg
                            )
                        }
                    }
                }
            }

            // 1.5 Success Banner
            if (isSuccessBanner) {
                item {
                    val successStatus = extractionStatus as? ExtractionStatus.Success
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardSubSurface,
                        border = BorderStroke(1.dp, PerimeterZinc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CrispWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = successStatus?.message ?: "Extracted sections successfully.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = CrispWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "DISMISS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SilverMuted,
                                modifier = Modifier.clickable {
                                    PdfExtractionStateManager.clearStatus(bookId)
                                }
                            )
                        }
                    }
                }
            }

            // 2. Hero Card (Book Details + Reading Progress Bar/Stats)
            item {
                book?.let { currentBook ->
                    BookReadingProgressHero(
                        book = currentBook,
                        totalChapters = chapters.size,
                        completedChapters = chapters.count { it.isCompleted },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // 3. Segmented Navigation Strip: Centered 3-Tab Filter Row Below Hero Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CenteredSegmentedTabBar(
                        selectedTabIndex = selectedTabIndex,
                        frontMatterCount = frontMatter.size,
                        chaptersCount = coreChapters.size,
                        backMatterCount = backMatter.size,
                        onTabSelected = { tabIndex ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTabIndex = tabIndex
                        }
                    )
                }
            }

            // 4. Filtered Section List
            val activeChapters = when (selectedTabIndex) {
                0 -> frontMatter
                1 -> coreChapters
                2 -> backMatter
                else -> coreChapters
            }

            if (activeChapters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOff,
                                contentDescription = null,
                                tint = EmptyStateZinc,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "No sections in this category",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextZincMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = activeChapters,
                    key = { _, ch -> "tab_${selectedTabIndex}_${ch.id}" }
                ) { index, chapter ->
                    val unlockStatus = unlockStatuses[chapter.id]
                    val isLocked = unlockStatus?.isLocked ?: false
                    val prevChapterNumber = unlockStatus?.previousChapterNumber ?: (chapter.chapterNumber?.let { it - 1 } ?: index)

                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TabSectionChapterCard(
                            chapter = chapter,
                            isLocked = isLocked,
                            previousChapterNumber = prevChapterNumber,
                            tabIndex = selectedTabIndex,
                            displayIndex = index + 1,
                            onClick = {
                                if (isLocked) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = "Prerequisite Incomplete • Complete Chapter $prevChapterNumber to unlock this section.",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } else if (isReaderLocked) {
                                    showLockoutSheet = true
                                } else {
                                    onNavigateToChapterChat(chapter.id)
                                }
                            },
                            onToggleCompleted = {
                                viewModel.toggleChapterCompletion(chapter.id, !chapter.isCompleted)
                            },
                            onExportPdf = {
                                viewModel.exportChapterToPdf(context, chapter)
                            },
                            onDelete = {
                                viewModel.deleteChapter(chapter.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Centered Chrome-Style Segmented 3-Tab Filter Component.
 */
@Composable
fun CenteredSegmentedTabBar(
    selectedTabIndex: Int,
    frontMatterCount: Int,
    chaptersCount: Int,
    backMatterCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(11.dp),
        color = PillTrackBg,
        border = BorderStroke(1.dp, PerimeterZinc)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(0, "Front Matter", frontMatterCount),
                Triple(1, "Chapters", chaptersCount),
                Triple(2, "Back Matter", backMatterCount)
            )

            tabs.forEach { (tabIndex, title, count) ->
                val isSelected = selectedTabIndex == tabIndex
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) CrispWhite else Color.Transparent,
                    label = "tabBg_$tabIndex"
                )
                val textColor = if (isSelected) JetObsidian else TextZincMuted
                val badgeBgColor = if (isSelected) ActivePillBadgeBg else InactivePillBadgeBg
                val badgeTextColor = if (isSelected) JetObsidian else SilverMuted

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tabIndex)
                        }
                        .testTag("chapter_tab_$tabIndex")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = (-0.2).sp
                            ),
                            color = textColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = badgeBgColor,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = badgeTextColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-density Hero Overview Card displaying reading progress, completion fraction, and book metadata.
 */
@Composable
private fun BookReadingProgressHero(
    book: Book,
    totalChapters: Int,
    completedChapters: Int,
    modifier: Modifier = Modifier
) {
    val totalPages = book.pdfTotalPages
    val lastReadPage = book.pdfLastReadPage
    val progressFraction = if (totalPages > 0) (lastReadPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else 0f
    val progressPercent = (progressFraction * 100).toInt().coerceIn(0, 100)

    val (statusLabel, statusColor, statusBg, statusIcon) = when {
        progressPercent >= 100 -> Quadruple("Completed", CrispWhite, Color(0xFF27272F), Icons.Default.CheckCircle)
        progressPercent > 0 -> Quadruple("In Progress", CrispWhite, CardSubSurface, Icons.Default.AutoAwesome)
        else -> Quadruple("Not Started", SilverMuted, PillTrackBg, Icons.Default.HourglassEmpty)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, PerimeterZinc),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = CrispWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!book.author.isNullOrBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SilverMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Reading Status Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, PerimeterZinc)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = statusColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar and Stat Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Progress",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = SilverMuted
                )
                Text(
                    text = if (totalPages > 0) "Page $lastReadPage of $totalPages ($progressPercent%)" else "$completedChapters/$totalChapters completed",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CrispWhite
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = {
                    if (totalPages > 0) progressFraction
                    else if (totalChapters > 0) (completedChapters.toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = CrispWhite,
                trackColor = PillTrackBg,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

/**
 * Tab Section Chapter Card with Sequential Lockout and Monochrome Completed Treatment:
 * - Unlocked & Active: #141418 surface, Crisp White title, numeric/matter badge, chevron trailing icon.
 * - Completed: #101013 surface with #222226 border, #D4D4D8 title, #1F1F24 badge with checkmark, inline COMPLETED pill, #71717A CheckCircleOutline trailing icon.
 * - Locked: Dimmed card surface (alpha 0.45f), muted container (#18181B) with text #52525B, padlock vector trailing icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabSectionChapterCard(
    chapter: Chapter,
    isLocked: Boolean,
    previousChapterNumber: Int,
    tabIndex: Int,
    displayIndex: Int,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val cardBgColor by animateColorAsState(
        targetValue = if (chapter.isCompleted) Color(0xFF101013) else CardSurface,
        animationSpec = tween(250),
        label = "cardContainerBg_${chapter.id}"
    )

    val cardBorderColor = if (chapter.isCompleted) {
        Color(0xFF222226)
    } else if (isLocked) {
        PerimeterZinc.copy(alpha = 0.6f)
    } else {
        PerimeterZinc
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.75f else 1.0f)
            .clickable(onClick = onClick)
            .testTag("chapter_item_${chapter.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badge container: Completed Checkmark, Vector icon for Front/Back Matter, or Zero-Padded number for Core Chapters
            Surface(
                shape = CircleShape,
                color = when {
                    chapter.isCompleted -> Color(0xFF1F1F24)
                    isLocked -> PillTrackBg
                    else -> CardSubSurface
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        chapter.isCompleted -> Color(0xFF2E2E35)
                        isLocked -> PerimeterZinc.copy(alpha = 0.5f)
                        else -> PerimeterZinc
                    }
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        chapter.isCompleted -> {
                            // Completed state glyph: clean vector checkmark (#FFFFFF)
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = CrispWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        tabIndex == 0 -> {
                            // Front Matter Tab: Clean document vector icon
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Article,
                                contentDescription = "Front Matter",
                                tint = if (isLocked) Color(0xFFA1A1AA) else SilverMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        tabIndex == 2 -> {
                            // Back Matter Tab: Clean flag vector icon
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = "Back Matter",
                                tint = if (isLocked) Color(0xFFA1A1AA) else SilverMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        else -> {
                            // Core Chapter Tab: Sequential numeric badge (01, 02, etc.)
                            val num = chapter.chapterNumber ?: displayIndex
                            val numStr = if (num < 10) "0$num" else "$num"
                            Text(
                                text = numStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isLocked) Color(0xFFA1A1AA) else CrispWhite
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                val cleanTitle = remember(chapter.title) {
                    ProgressCalculator.cleanChapterTitle(chapter.title)
                }
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (chapter.isCompleted) FontWeight.Medium else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    color = when {
                        isLocked -> Color(0xFFA1A1AA)
                        chapter.isCompleted -> Color(0xFFD4D4D8)
                        else -> CrispWhite
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val pageRangeText = if (chapter.startPage > 0 && chapter.endPage >= chapter.startPage) {
                    val count = (chapter.endPage - chapter.startPage + 1).coerceAtLeast(1)
                    if (chapter.startPage == chapter.endPage) "p. ${chapter.startPage} (1 pg)" else "pp. ${chapter.startPage}–${chapter.endPage} ($count pgs)"
                } else if (chapter.pdfTotalPages > 0) {
                    "${chapter.pdfTotalPages} pgs"
                } else {
                    null
                }

                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val chapProgress = remember(chapter) { ProgressCalculator.calculateChapterProgress(chapter) }

                    // Silent, Passive Chapter Status Badge: "Ch 1 • Not Started" / "Ch 1 • X% Explored" / "Ch 1 • Mastered"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            chapProgress.isMastered -> Color(0x2EF59E0B)
                            else -> Color(0xFF101013)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                chapProgress.isMastered -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                                else -> Color(0xFF27272F)
                            }
                        ),
                        modifier = Modifier
                            .wrapContentWidth()
                            .testTag("chapter_status_badge_${chapter.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (chapProgress.isMastered) {
                                Text(text = "🏆", fontSize = 10.sp)
                            }
                            Text(
                                text = chapProgress.pillLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = when {
                                    chapProgress.isMastered -> Color(0xFFFBBF24)
                                    chapProgress.progressPercent > 0 -> CrispWhite
                                    else -> Color(0xFF71717A)
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (pageRangeText != null) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = CardSubSurface,
                            border = BorderStroke(0.8.dp, PerimeterZinc),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isLocked) Color(0xFF71717A) else SilverMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.5.dp))
                                Text(
                                    text = pageRangeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isLocked) Color(0xFF71717A) else SilverMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (chapter.isCompleted) {
                        // Inline "COMPLETED" Pill Badge
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF18181B),
                            border = BorderStroke(1.dp, Color(0xFF27272F)),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = "COMPLETED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFFFAFAFA),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Options 3-Dot Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp).testTag("chapter_menu_${chapter.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = if (isLocked) LockedTextMuted else TextZincMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(CardSurface)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (chapter.isCompleted) "Mark as Incomplete" else "Mark as Completed",
                                color = CrispWhite,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = false
                            onToggleCompleted()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (chapter.isCompleted) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = null,
                                tint = CrispWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Export to PDF",
                                color = SilverMuted,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            onExportPdf()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = SilverMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete Chapter",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Trailing Indicator: Lock padlock vector if locked, CheckCircleOutline if completed, Chevron if unlocked
            if (isLocked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    tint = LockedTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            } else if (chapter.isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = "Completed",
                    tint = TextZincMuted,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Open",
                    tint = TextZincMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
