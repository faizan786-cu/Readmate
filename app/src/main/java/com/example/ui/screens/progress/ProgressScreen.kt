package com.example.ui.screens.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ReadMateBrandLogo
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.HeatmapDayUi
import com.example.ui.viewmodel.ProgressUiState
import com.example.ui.viewmodel.ProgressViewModel
import com.example.ui.viewmodel.QuizAttemptItemUi
import com.example.ui.viewmodel.TargetChapterMasteryUi
import com.example.ui.viewmodel.WeakConceptUi
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val CanvasObsidian = Color(0xFF0B0B0E)
private val CardSurface = Color(0xFF141418)
private val CardBorder = Color(0xFF27272F)
private val CapsuleSurface = Color(0xFF101013)
private val TextPureWhite = Color(0xFFFFFFFF)
private val TextZincSecondary = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)

private val HeatmapLevel0 = Color(0xFF1A1A22)
private val HeatmapLevel1 = Color(0xFF3F3F46)
private val HeatmapLevel2 = Color(0xFF71717A)
private val HeatmapLevel3 = Color(0xFFA1A1AA)
private val HeatmapLevel4 = Color(0xFFFFFFFF)

private val CorrectGreen = Color(0xFF10B981)
private val WrongRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDailyRecall: () -> Unit,
    onNavigateToWordQuiz: () -> Unit = {},
    onNavigateToChapterMastery: (Long) -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("progress_screen"),
        containerColor = CanvasObsidian,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReadMateBrandLogo(
                            size = 28.dp,
                            showWordmark = false
                        )
                        Text(
                            text = "Recall & Progress",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = TextPureWhite
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("progress_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = TextPureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasObsidian
                )
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ProgressUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TextPureWhite,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            is ProgressUiState.Empty -> {
                ExecutiveProgressContent(
                    state = ProgressUiState.Success(
                        overallAccuracyRate = 0f,
                        totalQuizzesCompleted = 0,
                        totalQuestionsAnswered = 0,
                        totalQuestionsInVault = state.totalQuestionsInVault,
                        totalXp = state.totalXp,
                        currentRank = state.currentRank,
                        todaySnippetCompleted = state.todaySnippetCompleted,
                        todayWordCompleted = state.todayWordCompleted,
                        chaptersMasteredCount = state.chaptersMasteredCount,
                        totalChaptersCount = state.totalChaptersCount,
                        averageChapterMasteryScore = state.averageChapterMasteryScore,
                        chapterMasteryList = state.chapterMasteryList
                    ),
                    paddingValues = innerPadding,
                    onStartQuiz = onNavigateToDailyRecall,
                    onStartWordQuiz = onNavigateToWordQuiz,
                    onStartChapterMastery = onNavigateToChapterMastery,
                    onNavigateToLibrary = onNavigateToLibrary
                )
            }

            is ProgressUiState.Success -> {
                ExecutiveProgressContent(
                    state = state,
                    paddingValues = innerPadding,
                    onStartQuiz = onNavigateToDailyRecall,
                    onStartWordQuiz = onNavigateToWordQuiz,
                    onStartChapterMastery = onNavigateToChapterMastery,
                    onNavigateToLibrary = onNavigateToLibrary
                )
            }
        }
    }
}

@Composable
private fun ExecutiveProgressContent(
    state: ProgressUiState.Success,
    paddingValues: PaddingValues,
    onStartQuiz: () -> Unit,
    onStartWordQuiz: () -> Unit,
    onStartChapterMastery: (Long) -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    var showMistakesDialog by remember { mutableStateOf(false) }

    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    // Staggered Motion Animators
    val anim1Alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 40, easing = FastOutSlowInEasing),
        label = "heroAlpha"
    )
    val anim1Offset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = tween(durationMillis = 380, delayMillis = 40, easing = FastOutSlowInEasing),
        label = "heroOffset"
    )

    val anim2Alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 110, easing = FastOutSlowInEasing),
        label = "heatAlpha"
    )
    val anim2Offset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = tween(durationMillis = 380, delayMillis = 110, easing = FastOutSlowInEasing),
        label = "heatOffset"
    )

    val anim3Alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "vaultAlpha"
    )
    val anim3Offset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = tween(durationMillis = 380, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "vaultOffset"
    )

    val anim4Alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "masteryAlpha"
    )
    val anim4Offset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = tween(durationMillis = 380, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "masteryOffset"
    )

    val anim5Alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = 320, easing = FastOutSlowInEasing),
        label = "historyAlpha"
    )
    val anim5Offset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = tween(durationMillis = 380, delayMillis = 320, easing = FastOutSlowInEasing),
        label = "historyOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = navBarsPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Executive Identity & XP Progress Pedestal (Hero Card)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = anim1Alpha
                translationY = anim1Offset.dp.toPx()
            }
        ) {
            ExecutiveHeroPedestalCard(
                totalXp = state.totalXp,
                currentRank = state.currentRank,
                nextRankName = state.nextRankName,
                xpToNextRank = state.xpToNextRank,
                rankProgressRatio = state.rankProgressRatio,
                currentStreakDays = state.currentStreakDays,
                overallAccuracy = state.overallAccuracyRate,
                totalQuizzesCompleted = state.totalQuizzesCompleted
            )
        }

        // 2. Consistency Matrix (Monochrome Activity Heatmap)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = anim2Alpha
                translationY = anim2Offset.dp.toPx()
            }
        ) {
            ConsistencyHeatmapCard(
                heatmapDays = state.heatmapDays,
                activeDaysThisMonth = state.activeDaysThisMonth,
                currentStreakDays = state.currentStreakDays
            )
        }

        // 3. Interactive Dual Knowledge Vaults (2-Column Grid)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = anim3Alpha
                translationY = anim3Offset.dp.toPx()
            }
        ) {
            DualKnowledgeVaultsGrid(
                totalQuestionsInVault = state.totalQuestionsInVault,
                totalMissedQuestionsCount = state.totalMissedQuestionsCount,
                onStartQuiz = onStartQuiz,
                onOpenMistakes = { showMistakesDialog = true }
            )
        }

        // 4. Chapter Mastery Readiness Monitor
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = anim4Alpha
                translationY = anim4Offset.dp.toPx()
            }
        ) {
            ChapterMasteryReadinessCard(
                targetChapter = state.targetChapterMastery,
                onStartChapterMastery = onStartChapterMastery,
                onNavigateToLibrary = onNavigateToLibrary
            )
        }

        // 5. Recent Drill History Section (if user has historical attempts)
        if (state.recentAttempts.isNotEmpty()) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = anim5Alpha
                    translationY = anim5Offset.dp.toPx()
                }
            ) {
                RecentDrillHistorySection(
                    recentAttempts = state.recentAttempts
                )
            }
        }
    }

    if (showMistakesDialog) {
        MistakesInspectionDialog(
            weakConcepts = state.weakConcepts,
            totalMissed = state.totalMissedQuestionsCount,
            onDismiss = { showMistakesDialog = false },
            onStartDrill = {
                showMistakesDialog = false
                onStartQuiz()
            }
        )
    }
}

/**
 * 1. Executive Identity & XP Progress Pedestal (Hero Card)
 */
@Composable
private fun ExecutiveHeroPedestalCard(
    totalXp: Int,
    currentRank: String,
    nextRankName: String,
    xpToNextRank: Int,
    rankProgressRatio: Float,
    currentStreakDays: Int,
    overallAccuracy: Float,
    totalQuizzesCompleted: Int
) {
    val formattedXp = remember(totalXp) {
        NumberFormat.getNumberInstance(Locale.US).format(totalXp)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("executive_hero_pedestal_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: XP counter & Sub-label
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$formattedXp XP",
                        color = TextPureWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LIFETIME KNOWLEDGE XP",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Right: Rank Badge Capsule
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CapsuleSurface,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = currentRank,
                            color = TextPureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Middle Progress Gauge
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = rankProgressRatio.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(TextPureWhite)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (xpToNextRank > 0) {
                            "${NumberFormat.getNumberInstance(Locale.US).format(xpToNextRank)} XP to $nextRankName"
                        } else {
                            "Apex Master Polymath Achieved"
                        },
                        color = TextZincSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(rankProgressRatio * 100).roundToInt()}%",
                        color = TextZincMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                color = CardBorder,
                thickness = 1.dp
            )

            // Bottom Stat Anchors (3-column micro-grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Col 1: Streak
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$currentStreakDays Days",
                        color = TextPureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🔥 CURRENT STREAK",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(CardBorder)
                )

                // Col 2: Avg Accuracy
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${overallAccuracy.roundToInt()}%",
                        color = TextPureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🎯 AVG ACCURACY",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(CardBorder)
                )

                // Col 3: Total Drills
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$totalQuizzesCompleted",
                        color = TextPureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⏱ TOTAL DRILLS",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. Consistency Matrix (Monochrome Activity Heatmap)
 */
@Composable
private fun ConsistencyHeatmapCard(
    heatmapDays: List<HeatmapDayUi>,
    activeDaysThisMonth: Int,
    currentStreakDays: Int
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("consistency_heatmap_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONSISTENCY MATRIX",
                    color = TextZincMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = if (activeDaysThisMonth > 0) {
                        "🔥 $activeDaysThisMonth active days this month"
                    } else if (currentStreakDays > 0) {
                        "🔥 $currentStreakDays-day active streak"
                    } else {
                        "Daily Activity Log"
                    },
                    color = TextZincSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Grid Container (7 rows x 12 columns)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day-of-week labels column on the left
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    val dayLabels = listOf("M", "", "W", "", "F", "", "")
                    dayLabels.forEach { label ->
                        Box(
                            modifier = Modifier.size(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (label.isNotEmpty()) {
                                Text(
                                    text = label,
                                    color = TextZincMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 12 Weeks Columns
                for (w in 0 until 12) {
                    val weekCells = heatmapDays.filter { it.weekIndex == w }.sortedBy { it.dayOfWeek }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (weekCells.isNotEmpty()) {
                            weekCells.forEach { cell ->
                                HeatmapCell(cell = cell)
                            }
                        } else {
                            // Fallback if empty
                            for (d in 0..6) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(HeatmapLevel0)
                                        .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Legend Footer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clamped 84-day Knowledge Log",
                    color = TextZincMuted,
                    fontSize = 10.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Less",
                        color = TextZincMuted,
                        fontSize = 10.sp
                    )

                    val legendLevels = listOf(HeatmapLevel0, HeatmapLevel1, HeatmapLevel2, HeatmapLevel3, HeatmapLevel4)
                    legendLevels.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                                .then(
                                    if (idx == 0) Modifier.border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(2.dp))
                                    else Modifier
                                )
                        )
                    }

                    Text(
                        text = "More",
                        color = TextZincMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(cell: HeatmapDayUi) {
    val cellColor = when (cell.level) {
        4 -> HeatmapLevel4
        3 -> HeatmapLevel3
        2 -> HeatmapLevel2
        1 -> HeatmapLevel1
        else -> HeatmapLevel0
    }

    val hasBorder = cell.level == 0 || cell.isToday
    val borderColor = if (cell.isToday) TextPureWhite else CardBorder

    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(cellColor)
            .then(
                if (hasBorder) Modifier.border(BorderStroke(1.dp, borderColor), RoundedCornerShape(3.dp))
                else Modifier
            )
    )
}

/**
 * 3. Interactive Dual Knowledge Vaults (2-Column Grid)
 */
@Composable
private fun DualKnowledgeVaultsGrid(
    totalQuestionsInVault: Int,
    totalMissedQuestionsCount: Int,
    onStartQuiz: () -> Unit,
    onOpenMistakes: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dual_knowledge_vaults_grid"),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vault Card A: Active Quiz Bank
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .weight(1f)
                .clickable { onStartQuiz() }
                .testTag("vault_card_quiz_bank")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Icon & Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CapsuleSurface)
                            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Pulse Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CapsuleSurface,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(CorrectGreen)
                            )
                            Text(
                                text = "Drill Ready",
                                color = TextPureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Numerical Metric
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "$totalQuestionsInVault MCQs",
                        color = TextPureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ACTIVE QUIZ BANK",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // CTA Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Start Drill →",
                        color = TextPureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Vault Card B: Mistakes Bank (Cognitive Debt)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenMistakes() }
                .testTag("vault_card_mistakes_bank")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Icon & Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CapsuleSurface)
                            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CapsuleSurface,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "Weak Concepts",
                            color = TextZincMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Numerical Metric
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "$totalMissedQuestionsCount Missed",
                        color = TextPureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "COGNITIVE DEBT",
                        color = TextZincMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // CTA Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Re-evaluate →",
                        color = TextZincSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 4. Chapter Mastery Readiness Monitor (Clean Status Row)
 */
@Composable
private fun ChapterMasteryReadinessCard(
    targetChapter: TargetChapterMasteryUi?,
    onStartChapterMastery: (Long) -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_mastery_readiness_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Text(
                text = "CHAPTER MASTERY EXAMS (50 MCQs)",
                color = TextZincMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Context Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Active Book thumbnail + Chapter Title
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CapsuleSurface)
                            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!targetChapter?.bookCoverUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(targetChapter?.bookCoverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = TextZincMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = TextZincMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = targetChapter?.chapterTitle ?: "Ch. 1: Getting Started",
                            color = TextPureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = targetChapter?.bookTitle ?: "Read to Unlock",
                            color = TextZincMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Right: Readiness Pill
                if (targetChapter != null && targetChapter.isExamReady) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TextPureWhite,
                        modifier = Modifier
                            .clickable { onStartChapterMastery(targetChapter.chapterId) }
                            .testTag("take_chapter_exam_button")
                    ) {
                        Text(
                            text = "Take 50-MCQ Exam",
                            color = CanvasObsidian,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    val percent = targetChapter?.progressPercent ?: 0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CapsuleSurface,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.clickable {
                            if (targetChapter == null) onNavigateToLibrary()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextZincMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "In Progress ($percent%)",
                                color = TextZincMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. Recent Drill History Section
 */
@Composable
private fun RecentDrillHistorySection(
    recentAttempts: List<QuizAttemptItemUi>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "RECENT DRILL PERFORMANCE",
            color = TextZincMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recentAttempts.take(5).forEach { attempt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = attempt.bookTitle ?: (if (attempt.isWordQuiz) "Word Vault Drill" else "Passage Recall Drill"),
                                color = TextPureWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${attempt.formattedDate} • ${attempt.correctAnswers}/${attempt.totalQuestions} correct (+${attempt.xpEarned} XP)",
                                color = TextZincMuted,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CapsuleSurface,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = "${attempt.scorePercentage.roundToInt()}%",
                                color = if (attempt.scorePercentage >= 70f) TextPureWhite else TextZincSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Executive Modal Dialog for Cognitive Debt & Mistakes Inspection
 */
@Composable
private fun MistakesInspectionDialog(
    weakConcepts: List<WeakConceptUi>,
    totalMissed: Int,
    onDismiss: () -> Unit,
    onStartDrill: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CapsuleSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (weakConcepts.isNotEmpty()) WrongRed else CorrectGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "Mistakes Bank",
                            color = TextPureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextZincMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (weakConcepts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CorrectGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Zero Cognitive Debt",
                            color = TextPureWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All past recall drill questions answered accurately. Read new chapters or launch a drill to generate fresh concepts!",
                            color = TextZincSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "$totalMissed concepts awaiting active retention drill:",
                        color = TextZincSecondary,
                        fontSize = 12.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        weakConcepts.forEach { concept ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CapsuleSurface,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = concept.questionText,
                                        color = TextPureWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (concept.lastWrongOptionText.isNotBlank()) {
                                        Text(
                                            text = "✕ Your Attempt: ${concept.lastWrongOptionText}",
                                            color = WrongRed,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Text(
                                        text = "✓ Correct Answer: ${concept.correctOptionText}",
                                        color = CorrectGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (concept.explanation.isNotBlank()) {
                                        Text(
                                            text = concept.explanation,
                                            color = TextZincMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CapsuleSurface,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDismiss() }
                    ) {
                        Text(
                            text = "Close",
                            color = TextZincSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TextPureWhite,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onStartDrill() }
                    ) {
                        Text(
                            text = "Start Drill →",
                            color = CanvasObsidian,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
