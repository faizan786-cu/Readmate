package com.example.ui.screens.book

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.manager.ExtractionStatus
import com.example.data.manager.PdfExtractionStateManager
import com.example.data.model.ExtractedChapterSection
import com.example.data.util.ProgressCalculator
import com.example.ui.components.DonutProgressChart
import com.example.ui.components.PdfExportDialog
import com.example.ui.components.ReaderLockoutSheet
import kotlinx.coroutines.launch
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.BookDetailViewModel

// Ultra-modern Figma Monochrome Color Palette
private val DarkCanvasBg = Color(0xFF0D0D11)
private val CardSurfaceBg = Color(0xFF141418)
private val CardSurfaceSecondary = Color(0xFF18181D)
private val CardSurfaceTertiary = Color(0xFF1E1E24)
private val FineBorderColor = Color(0xFF27272A)
private val ActiveBorderColor = Color(0xFF3F3F46)
private val PrimaryWhite = Color(0xFFFFFFFF)
private val MutedWhite = Color(0xFFF4F4F5)
private val SecondaryGray = Color(0xFFA1A1AA)
private val TertiaryGray = Color(0xFF71717A)
private val SubtleGray = Color(0xFF52525B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditBook: (Long) -> Unit,
    onNavigateToAddChapter: (Long) -> Unit,
    onNavigateToEditChapter: (Long) -> Unit,
    onNavigateToChapterChat: (Long) -> Unit,
    onNavigateToDailyRecall: () -> Unit = {},
    onNavigateToChapterMastery: (Long) -> Unit = {},
    onNavigateToProgress: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val unlockStatuses by viewModel.unlockStatuses.collectAsStateWithLifecycle()
    val dailyRetentionState by viewModel.dailyRetentionState.collectAsStateWithLifecycle()
    val isReaderLocked by viewModel.isReaderLocked.collectAsStateWithLifecycle()
    val extractionStatus by PdfExtractionStateManager.currentStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteBookDialog by remember { mutableStateOf(false) }
    var showLockoutSheet by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var selectedChapterForMastery by remember { mutableStateOf<Chapter?>(null) }

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

    selectedChapterForMastery?.let { targetChapter ->
        val score = targetChapter.masteryScore
        val isMastered = targetChapter.isMastered || (score != null && score >= 80)
        AlertDialog(
            onDismissRequest = { selectedChapterForMastery = null },
            containerColor = CardSurfaceBg,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isMastered) Color(0x2BF59E0B) else Color(0x2610B981),
                        border = BorderStroke(1.dp, if (isMastered) Color(0xFFF59E0B) else Color(0xFF10B981)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = if (isMastered) "👑" else "🏆", fontSize = 18.sp)
                        }
                    }
                    Text(
                        text = "Chapter Mastery",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryWhite
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = targetChapter.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryWhite
                    )
                    if (score != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardSurfaceTertiary,
                            border = BorderStroke(1.dp, if (isMastered) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$score% Mastery Score",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    color = if (isMastered) Color(0xFFFBBF24) else Color(0xFF34D399)
                                )
                                Text(
                                    text = if (isMastered) "Master Strategist Rank Achieved" else "Passed Grand Mastery Exam",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryGray
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "You haven't completed the 50-MCQ Grand Mastery Exam for this chapter yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryGray
                        )
                    }
                    Text(
                        text = "Take or retake the comprehensive 50-question chapter mastery challenge to solidify your comprehension and earn up to +250 XP bonus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryGray
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cId = targetChapter.id
                        selectedChapterForMastery = null
                        onNavigateToChapterMastery(cId)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFFBBF24)
                    ),
                    modifier = Modifier.testTag("dialog_take_mastery_exam_button")
                ) {
                    Text(if (score != null) "Retake Exam (50 MCQs)" else "Start Exam (50 MCQs)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedChapterForMastery = null
                        onNavigateToProgress()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SecondaryGray
                    ),
                    modifier = Modifier.testTag("dialog_view_progress_button")
                ) {
                    Text("View Progress")
                }
            }
        )
    }

    if (showDeleteBookDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBookDialog = false },
            containerColor = CardSurfaceBg,
            title = {
                Text(
                    text = "Delete this book?",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryWhite
                )
            },
            text = {
                Text(
                    text = "This will also delete all chapters inside this book. This action cannot be undone.",
                    color = SecondaryGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBookDialog = false
                        viewModel.deleteBook(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.testTag("confirm_delete_book_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteBookDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SecondaryGray
                    ),
                    modifier = Modifier.testTag("cancel_delete_book_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    chapterToDelete?.let { chapter ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            containerColor = CardSurfaceBg,
            title = {
                Text(
                    text = "Delete this chapter?",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryWhite
                )
            },
            text = {
                Text(
                    text = "Any content associated with this chapter will also be deleted. This action cannot be undone.",
                    color = SecondaryGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = chapter.id
                        chapterToDelete = null
                        viewModel.deleteChapter(id)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.testTag("confirm_delete_chapter_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { chapterToDelete = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SecondaryGray
                    ),
                    modifier = Modifier.testTag("cancel_delete_chapter_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvasBg),
        containerColor = DarkCanvasBg,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { snackbarData ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272F)),
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
                            tint = Color(0xFFA1A1AA),
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
                    Text(
                        text = book?.title ?: "Book Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = PrimaryWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("book_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryWhite
                        )
                    }
                },
                actions = {
                    if (book != null) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("book_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Book Options",
                                    tint = PrimaryWhite
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(CardSurfaceBg)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Edit Book",
                                            color = PrimaryWhite,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToEditBook(viewModel.bookId)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = SecondaryGray
                                        )
                                    },
                                    modifier = Modifier.testTag("menu_edit_book")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete Book",
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteBookDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444)
                                        )
                                    },
                                    modifier = Modifier.testTag("menu_delete_book")
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCanvasBg,
                    titleContentColor = PrimaryWhite,
                    navigationIconContentColor = PrimaryWhite,
                    actionIconContentColor = PrimaryWhite
                )
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (book != null && chapters.isNotEmpty()) {
                Surface(
                    onClick = { onNavigateToAddChapter(viewModel.bookId) },
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryWhite,
                    contentColor = Color(0xFF0D0D11),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp, end = 6.dp)
                        .height(48.dp)
                        .testTag("add_chapter_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Chapter",
                            tint = Color(0xFF0D0D11),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Add Chapter",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFF0D0D11)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val currentBook = book
        if (currentBook == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryWhite,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                ) {
                    // 1. Modern Hero Overview Card & Reading Progress Dashboard
                    item {
                        BookHeaderSection(
                            book = currentBook,
                            chapters = chapters
                        )
                    }

                    // 1.5 PDF Structure Scanning Status Banner
                    val isScanning = extractionStatus is ExtractionStatus.Processing &&
                            (extractionStatus as ExtractionStatus.Processing).bookId == viewModel.bookId

                    val isSuccessBanner = extractionStatus is ExtractionStatus.Success &&
                            (extractionStatus as ExtractionStatus.Success).bookId == viewModel.bookId

                    if (isScanning) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurfaceBg,
                                border = BorderStroke(1.dp, FineBorderColor),
                                modifier = Modifier.fillMaxWidth()
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
                                            tint = PrimaryWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Analyzing book structure with Flash-Lite...",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = SecondaryGray,
                                            modifier = Modifier.weight(1f)
                                        )
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = PrimaryWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp),
                                        color = PrimaryWhite,
                                        trackColor = CardSurfaceSecondary
                                    )
                                }
                            }
                        }
                    } else if (isSuccessBanner) {
                        val successState = extractionStatus as? ExtractionStatus.Success
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurfaceSecondary,
                                border = BorderStroke(1.dp, FineBorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PrimaryWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = successState?.message ?: "Extracted sections successfully.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = PrimaryWhite,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "DISMISS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SecondaryGray,
                                        modifier = Modifier.clickable {
                                            PdfExtractionStateManager.clearStatus(viewModel.bookId)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Centered Segmented Navigation Strip (3-Tab Filter Row Below Hero Card)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = Color(0xFF18181B),
                                border = BorderStroke(1.dp, Color(0xFF27272F))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.5.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabs = listOf(
                                        Triple(0, "Front Matter", frontMatter.size),
                                        Triple(1, "Chapters", coreChapters.size),
                                        Triple(2, "Back Matter", backMatter.size)
                                    )

                                    tabs.forEach { (tabIndex, title, count) ->
                                        val isSelected = selectedTabIndex == tabIndex
                                        val bgColor by animateColorAsState(
                                            targetValue = if (isSelected) PrimaryWhite else Color.Transparent,
                                            label = "detailTabBg_$tabIndex"
                                        )
                                        val textColor = if (isSelected) Color(0xFF0B0B0E) else TertiaryGray
                                        val badgeBgColor = if (isSelected) Color(0xFFE4E4E7) else Color(0xFF27272A)
                                        val badgeTextColor = if (isSelected) Color(0xFF0B0B0E) else SecondaryGray

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
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedTabIndex = tabIndex
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
                    }

                    // 3. Filtered Chapters List or Clean Minimal Empty State
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
                                        tint = Color(0xFF3F3F46),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "No sections in this category",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = Color(0xFF71717A),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(activeChapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                            val unlockStatus = unlockStatuses[chapter.id]
                            val isLocked = unlockStatus?.isLocked ?: false
                            val prevChapterNumber = unlockStatus?.previousChapterNumber ?: (chapter.chapterNumber?.let { it - 1 } ?: index)

                            ChapterListItem(
                                index = index + 1,
                                chapter = chapter,
                                isLocked = isLocked,
                                previousChapterNumber = prevChapterNumber,
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
                                onExportPdfClick = { viewModel.exportChapterToPdf(context, chapter) },
                                onEditClick = { onNavigateToEditChapter(chapter.id) },
                                onDeleteClick = { chapterToDelete = chapter },
                                onTakeMasteryExamClick = { onNavigateToChapterMastery(chapter.id) },
                                modifier = Modifier.testTag("chapter_item_${chapter.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ultra-Modern Hero Overview Card with integrated Reading Progress Dashboard widget.
 * Follows a sleek monochrome / dark graphite / crisp white Figma design hierarchy.
 */
@Composable
private fun BookHeaderSection(
    book: Book,
    chapters: List<Chapter> = emptyList(),
    modifier: Modifier = Modifier
) {
    val bookProgress = remember(book, chapters) {
        ProgressCalculator.calculateBookCoreProgress(chapters, book)
    }

    val hasPdf = book.pdfTotalPages > 0 || !book.pdfFilePath.isNullOrBlank() || bookProgress.totalCorePages > 0
    val totalPages = bookProgress.totalCorePages
    val readPages = bookProgress.readCorePages
    val progressPercent = bookProgress.progressPercent
    val progressFraction = if (totalPages > 0) (readPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else 0f

    val (statusLabel, statusColor, statusBg, statusIcon) = when {
        progressPercent >= 100 -> Quadruple("Completed", Color(0xFF34D399), Color(0xFF064E3B).copy(alpha = 0.6f), Icons.Default.CheckCircle)
        progressPercent > 0 -> Quadruple("In Progress", Color(0xFFFFFFFF), Color(0xFF27272A), Icons.Default.AutoAwesome)
        else -> Quadruple("Not Started", SecondaryGray, Color(0xFF18181C), Icons.Default.HourglassEmpty)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurfaceBg
        ),
        border = BorderStroke(
            width = 1.dp,
            color = FineBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
        ) {
            // Background Artwork: low-opacity stretched book-cover background layer
            if (!book.coverImageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(book.coverImageUrl) {
                    ImageRequest.Builder(context)
                        .data(book.coverImageUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Ambient dark tint overlay: Deep Obsidian gradient scrim (#0B0B0E at 85% to 92% opacity)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xEB0B0B0E), // ~92% opacity
                                Color(0xD90B0B0E), // ~85% opacity
                                Color(0xEB0B0B0E)  // ~92% opacity
                            )
                        )
                    )
            )

            // Centered Content Hierarchy
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Book Title: Bold, prominent typography (#FFFFFF, 21sp, line height 26sp, centered)
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center
                    ),
                    color = PrimaryWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                // Author Attribution: Centered inline row displaying author icon and name
                if (!book.author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "by ${book.author}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }

                // Description (if present)
                if (!book.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        ),
                        color = Color(0xFF71717A),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Integrated Reading Progress Pod
                if (hasPdf && totalPages > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF141418).copy(alpha = 0.94f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = FineBorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("book_detail_progress_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular progress gauge (crisp white progress stroke, ~54dp diameter)
                            DonutProgressChart(
                                progress = progressFraction,
                                size = 54.dp,
                                strokeWidth = 5.dp,
                                progressColors = listOf(
                                    PrimaryWhite,
                                    Color(0xFFE4E4E7),
                                    Color(0xFFD4D4D8)
                                ),
                                trackColor = Color(0xFF27272A),
                                textColor = PrimaryWhite,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                testTag = "book_header_donut_chart"
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // Expanded flexible space with 4 stacked dedicated lines
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Line 1: Header & Status Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Reading Progress",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            letterSpacing = (-0.1).sp
                                        ),
                                        color = PrimaryWhite,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Clean status capsule with no text clipping
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusBg,
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (progressPercent >= 100) Color(0xFF10B981).copy(alpha = 0.4f) else ActiveBorderColor
                                        ),
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.5.dp))
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = statusColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Line 2: Dedicated Core Pages Read (#FFFFFF, 12sp, medium weight)
                                Text(
                                    text = "$readPages / $totalPages Core Pages Read",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = PrimaryWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Line 3: Dedicated Explored Metric (#A1A1AA, 11sp)
                                Text(
                                    text = "$progressPercent% Explored",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    ),
                                    color = Color(0xFFA1A1AA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                // Line 4: Dedicated Security & Verification Anchor (#71717A, 10sp, zero dot-dot clipping)
                                Text(
                                    text = "Tamper-proof anchor • Verified comprehension",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 13.5.sp
                                    ),
                                    color = Color(0xFF71717A),
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Ultra-Modern Elevated Chapter List Item Card.
 * Crisp numeric badge, clean typography hierarchy, actions menu, and subtle chevron.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterListItem(
    index: Int,
    chapter: Chapter,
    isLocked: Boolean = false,
    previousChapterNumber: Int = 1,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit = {},
    onExportPdfClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTakeMasteryExamClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val normType = remember(chapter.sectionType, chapter.title) {
        com.example.data.model.ExtractedChapterSection.normalizeSectionType(chapter.sectionType, chapter.title)
    }

    val displayNumber = remember(chapter.chapterNumber, index, normType) {
        if (normType == com.example.data.model.ExtractedChapterSection.TYPE_CORE_CHAPTER) {
            val num = chapter.chapterNumber ?: (index + 1)
            if (num < 10) "0$num" else "$num"
        } else {
            ""
        }
    }

    val score = chapter.masteryScore
    val isMastered = chapter.isMastered || (score != null && score >= 80)
    val haptic = LocalHapticFeedback.current

    val cardBgColor by animateColorAsState(
        targetValue = if (chapter.isCompleted) Color(0xFF101013) else CardSurfaceBg,
        animationSpec = tween(250),
        label = "cardContainerBg_${chapter.id}"
    )

    val cardBorderColor = if (chapter.isCompleted) {
        Color(0xFF222226)
    } else if (isLocked) {
        FineBorderColor.copy(alpha = 0.5f)
    } else {
        FineBorderColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.75f else 1.0f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = BorderStroke(1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Part 1: Left Index Box (Compact square badge for chapter index)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    chapter.isCompleted -> Color(0xFF1F1F24)
                    isLocked -> Color(0xFF18181B)
                    else -> CardSurfaceTertiary
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        chapter.isCompleted -> Color(0xFF2E2E35)
                        isLocked -> Color(0xFF27272F)
                        isMastered -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                        else -> ActiveBorderColor
                    }
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        chapter.isCompleted -> {
                            // Completed state glyph: clean vector checkmark (#FFFFFF)
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = PrimaryWhite,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        normType == com.example.data.model.ExtractedChapterSection.TYPE_FRONT_MATTER -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = "Front Matter",
                                tint = if (isLocked) Color(0xFFA1A1AA) else PrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        normType == com.example.data.model.ExtractedChapterSection.TYPE_BACK_MATTER -> {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Back Matter",
                                tint = if (isLocked) Color(0xFFA1A1AA) else PrimaryWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = displayNumber,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = if (isLocked) Color(0xFFA1A1AA) else if (isMastered) Color(0xFFFBBF24) else PrimaryWhite
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Part 2: Center Content Area (Chapter Title & Metadata Row)
            val cleanTitle = remember(chapter.title) {
                ProgressCalculator.cleanChapterTitle(chapter.title)
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (chapter.isCompleted) FontWeight.Medium else FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = when {
                        isLocked -> Color(0xFFA1A1AA)
                        chapter.isCompleted -> Color(0xFFD4D4D8)
                        else -> PrimaryWhite
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                    chapProgress.progressPercent > 0 -> PrimaryWhite
                                    else -> Color(0xFF71717A)
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Single horizontal inline tag for Page Range (Concise scholarly pp. / p. notation)
                    val pgsText = if (chapter.startPage > 0 && chapter.endPage >= chapter.startPage) {
                        if (chapter.startPage == chapter.endPage) "p. ${chapter.startPage}" else "pp. ${chapter.startPage}–${chapter.endPage}"
                    } else if (chapter.pdfTotalPages > 0) {
                        "${chapter.pdfTotalPages} pgs"
                    } else {
                        null
                    }

                    if (pgsText != null) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = CardSurfaceTertiary,
                            border = BorderStroke(0.8.dp, FineBorderColor),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isLocked) Color(0xFF71717A) else SecondaryGray,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.5.dp))
                                Text(
                                    text = pgsText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isLocked) Color(0xFF71717A) else SecondaryGray,
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

            Spacer(modifier = Modifier.width(4.dp))

            // Part 3: Right Action Section (Compact menu + Status glyph / Chevron / Lock)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Options 3-Dot Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("chapter_options_button_${chapter.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Chapter options",
                            tint = if (isLocked) Color(0xFF71717A) else SecondaryGray,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(CardSurfaceBg)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (chapter.isCompleted) "Mark as Incomplete" else "Mark as Completed",
                                    color = PrimaryWhite,
                                    fontWeight = FontWeight.Medium
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
                                    tint = PrimaryWhite
                                )
                            },
                            modifier = Modifier.testTag("menu_toggle_completed_${chapter.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (score != null) "Retake Mastery Exam" else "Take Mastery Exam (50 MCQs)",
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                showMenu = false
                                onTakeMasteryExamClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24)
                                )
                            },
                            modifier = Modifier.testTag("menu_mastery_exam_${chapter.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Export to PDF",
                                    color = PrimaryWhite,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                showMenu = false
                                onExportPdfClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = SecondaryGray
                                )
                            },
                            modifier = Modifier.testTag("menu_export_pdf_${chapter.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit Chapter",
                                    color = PrimaryWhite,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = SecondaryGray
                                )
                            },
                            modifier = Modifier.testTag("menu_edit_chapter_${chapter.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete Chapter",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444)
                                )
                            },
                            modifier = Modifier.testTag("menu_delete_chapter_${chapter.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Trailing Lock, Completed Glyph, or Chevron
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(16.dp)
                    )
                } else if (chapter.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = "Completed",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SecondaryGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern Monochrome Empty State for Chapters.
 */
@Composable
private fun EmptyChaptersView(
    onAddChapterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurfaceBg
        ),
        border = BorderStroke(1.dp, FineBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurfaceTertiary,
                border = BorderStroke(1.dp, ActiveBorderColor),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = PrimaryWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "No chapters yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = PrimaryWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add chapters to organize your reading and passages.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                ),
                color = SecondaryGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            ExtendedFloatingActionButton(
                onClick = onAddChapterClick,
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF000000),
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        "Add Chapter",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF000000)
                    )
                },
                containerColor = PrimaryWhite,
                contentColor = Color(0xFF000000),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_add_chapter_button")
            )
        }
    }
}
