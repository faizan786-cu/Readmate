package com.example.ui.screens.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.model.MistakeRecord
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.ChapterMasteryUiState
import com.example.ui.viewmodel.ChapterMasteryViewModel
import com.example.ui.viewmodel.QuizFeedback
import com.example.ui.viewmodel.QuizOption
import com.example.ui.viewmodel.QuizQuestionState

private val CanvasObsidian = Color(0xFF0D0D11)
private val CardSurface = Color(0xFF141419)
private val CardSurfaceElevated = Color(0xFF1C1C24)
private val TextPureWhite = Color(0xFFFFFFFF)
private val TextZincSecondary = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val BorderZincSubtle = Color(0xFF27272A)
private val BorderZincActive = Color(0xFF52525B)

private val GoldAmber = Color(0xFFF59E0B)
private val GoldAmberContainer = Color(0x22F59E0B)
private val GoldAmberBorder = Color(0x66F59E0B)

private val CorrectGreen = Color(0xFF10B981)
private val CorrectGreenContainer = Color(0x1F10B981)
private val CorrectGreenBorder = Color(0xFF059669)

private val WrongRed = Color(0xFFEF4444)
private val WrongRedContainer = Color(0x1FEF4444)
private val WrongRedBorder = Color(0xFFDC2626)

private val RetryAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterMasteryQuizScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToNextChapter: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ChapterMasteryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Intercept system back press when a quiz is actively in progress
    BackHandler(enabled = uiState is ChapterMasteryUiState.ActiveQuiz) {
        showExitConfirmationDialog = true
    }

    if (showExitConfirmationDialog) {
        QuizExitConfirmationDialog(
            onDismiss = { showExitConfirmationDialog = false },
            onSaveAndExit = {
                showExitConfirmationDialog = false
                viewModel.saveAndExit(onSaved = onNavigateBack)
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("chapter_mastery_screen"),
        containerColor = CanvasObsidian,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is ChapterMasteryUiState.ActiveQuiz -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.progressPercent,
                                label = "mastery_progress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .padding(end = 16.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (state.isRetryRound) RetryAmber else GoldAmber,
                                trackColor = CardSurfaceElevated,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        is ChapterMasteryUiState.SummaryScoreCard -> {
                            Text(
                                text = "Grand Mastery Summary",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPureWhite
                            )
                        }
                        else -> {
                            Text(
                                text = "Chapter Grand Mastery",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPureWhite
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState is ChapterMasteryUiState.ActiveQuiz) {
                                showExitConfirmationDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("chapter_mastery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            is ChapterMasteryUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GoldAmber,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            is ChapterMasteryUiState.ResumePrompt -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GoldAmber,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
                QuizResumePromptDialog(
                    questionNumber = state.currentQuestionIndex,
                    totalQuestions = state.totalQuestions,
                    onResume = { viewModel.resumeSavedSession(state.cachedSession) },
                    onStartFresh = { viewModel.startFreshSession() }
                )
            }

            is ChapterMasteryUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderZincSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CardSurfaceElevated,
                                border = BorderStroke(1.dp, BorderZincSubtle),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = GoldAmber,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Grand Mastery Not Ready",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPureWhite
                            )

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = TextZincSecondary,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextPureWhite,
                                    contentColor = CanvasObsidian
                                )
                            ) {
                                Text(
                                    text = "Return to Dashboard",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            is ChapterMasteryUiState.ActiveQuiz -> {
                MasteryActiveQuizContent(
                    state = state,
                    paddingValues = innerPadding,
                    onSelectOption = { optionId -> viewModel.selectOption(optionId) },
                    onSubmitAnswer = { viewModel.submitAnswer() },
                    onNextQuestion = { viewModel.nextQuestion() }
                )
            }

            is ChapterMasteryUiState.SummaryScoreCard -> {
                var showUnlockCelebration by remember(state) {
                    mutableStateOf(state.isMastered)
                }

                if (showUnlockCelebration && state.isMastered) {
                    ChapterUnlockCelebrationDialog(
                        chapterTitle = state.chapterTitle,
                        nextChapterTitle = state.nextChapterTitle,
                        nextChapterId = state.nextChapterId,
                        onBeginNextChapter = { nextId ->
                            showUnlockCelebration = false
                            if (onNavigateToNextChapter != null) {
                                onNavigateToNextChapter(nextId)
                            } else {
                                onNavigateBack()
                            }
                        },
                        onReturnToCurriculum = {
                            showUnlockCelebration = false
                            onNavigateBack()
                        },
                        onDismiss = {
                            showUnlockCelebration = false
                        }
                    )
                }

                MasterySummaryScoreCardContent(
                    state = state,
                    paddingValues = innerPadding,
                    onRetakeQuiz = { viewModel.loadChapterMasterySession() },
                    onViewProgress = onNavigateToProgress,
                    onNavigateBack = onNavigateBack,
                    onBeginNextChapter = if (state.nextChapterId != null && onNavigateToNextChapter != null) {
                        { onNavigateToNextChapter(state.nextChapterId) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun MasteryActiveQuizContent(
    state: ChapterMasteryUiState.ActiveQuiz,
    paddingValues: PaddingValues,
    onSelectOption: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onNextQuestion: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Category Pill & Question Counter
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isRetryRound) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldAmberContainer,
                            border = BorderStroke(1.dp, GoldAmberBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = GoldAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "RETRY ROUND (${state.currentRetryIndex + 1}/${state.totalRetryQuestions})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = GoldAmber
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldAmberContainer,
                            border = BorderStroke(1.dp, GoldAmberBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = GoldAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "GRAND MASTERY (${state.currentIndex + 1}/${state.totalQuestionsInPrimary})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = GoldAmber
                                )
                            }
                        }
                    }

                    Text(
                        text = if (state.isRetryRound) {
                            "Mistake Recovery"
                        } else {
                            "${state.primaryCorrectCount} Correct"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextZincSecondary
                    )
                }
            }

            // Question Stem Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderZincSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.chapterTitle.isNotBlank()) {
                            Text(
                                text = "${state.bookTitle} • ${state.chapterTitle}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = GoldAmber
                            )
                        }

                        Text(
                            text = state.currentQuestion.question.questionText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.5.sp,
                                lineHeight = 24.sp
                            ),
                            color = TextPureWhite
                        )
                    }
                }
            }

            // Options List (A, B, C, D)
            items(state.currentQuestion.options) { option ->
                MasteryOptionCard(
                    option = option,
                    isSelected = state.selectedOptionId == option.id,
                    isAnswerSubmitted = state.isAnswerSubmitted,
                    isOptionCorrect = option.isCorrect,
                    isOptionSelectedWrong = state.isAnswerSubmitted && state.selectedOptionId == option.id && !option.isCorrect,
                    onSelect = { if (!state.isAnswerSubmitted) onSelectOption(option.id) }
                )
            }

            // Feedback Card (Revealed upon Submission)
            if (state.isAnswerSubmitted && state.feedback != null) {
                item {
                    MasteryFeedbackCard(
                        feedback = state.feedback,
                        isRetry = state.isRetryRound
                    )
                }
            }
        }

        // Bottom Fixed Action Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(1.dp, BorderZincSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                if (!state.isAnswerSubmitted) {
                    Button(
                        onClick = onSubmitAnswer,
                        enabled = state.selectedOptionId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("mastery_submit_answer_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAmber,
                            contentColor = CanvasObsidian,
                            disabledContainerColor = CardSurfaceElevated,
                            disabledContentColor = TextZincMuted
                        )
                    ) {
                        Text(
                            text = "Check Answer",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Button(
                        onClick = onNextQuestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("mastery_next_question_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPureWhite,
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isLast = if (!state.isRetryRound) {
                                state.currentIndex >= state.totalQuestionsInPrimary - 1
                            } else {
                                state.currentRetryIndex >= state.totalRetryQuestions - 1
                            }
                            Text(
                                text = if (isLast) "Finish Exam" else "Next Question",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasteryOptionCard(
    option: QuizOption,
    isSelected: Boolean,
    isAnswerSubmitted: Boolean,
    isOptionCorrect: Boolean,
    isOptionSelectedWrong: Boolean,
    onSelect: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isAnswerSubmitted && isOptionCorrect -> CorrectGreenBorder
            isOptionSelectedWrong -> WrongRedBorder
            isSelected -> GoldAmber
            else -> BorderZincSubtle
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "mastery_option_border"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isAnswerSubmitted && isOptionCorrect -> CorrectGreenContainer
            isOptionSelectedWrong -> WrongRedContainer
            isSelected -> GoldAmberContainer
            else -> CardSurface
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "mastery_option_bg"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected || isAnswerSubmitted) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .testTag("mastery_option_${option.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isAnswerSubmitted && isOptionCorrect -> CorrectGreen
                    isOptionSelectedWrong -> WrongRed
                    isSelected -> GoldAmber
                    else -> CardSurfaceElevated
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected || (isAnswerSubmitted && (isOptionCorrect || isOptionSelectedWrong))) Color.Transparent else BorderZincSubtle
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isAnswerSubmitted && isOptionCorrect) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Correct",
                            tint = CanvasObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (isOptionSelectedWrong) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Wrong",
                            tint = TextPureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = option.id,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) CanvasObsidian else TextZincSecondary
                        )
                    }
                }
            }

            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (isSelected || (isAnswerSubmitted && isOptionCorrect)) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = when {
                    isAnswerSubmitted && isOptionCorrect -> TextPureWhite
                    isOptionSelectedWrong -> TextPureWhite
                    isSelected -> TextPureWhite
                    else -> TextZincSecondary
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MasteryFeedbackCard(
    feedback: QuizFeedback,
    isRetry: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (feedback.isCorrect) CorrectGreenContainer else WrongRedContainer,
        border = BorderStroke(1.dp, if (feedback.isCorrect) CorrectGreenBorder else WrongRedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (feedback.isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (feedback.isCorrect) CorrectGreen else WrongRed,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = if (feedback.isCorrect) {
                        if (isRetry) "Great job! Mastered in retry." else "Correct!"
                    } else {
                        "Incorrect — Needs Spaced Review"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    ),
                    color = if (feedback.isCorrect) CorrectGreen else WrongRed
                )
            }

            if (!feedback.isCorrect) {
                Text(
                    text = "Correct Concept: ${feedback.correctOptionText}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = TextPureWhite
                )
            }

            if (feedback.explanation.isNotBlank()) {
                HorizontalDivider(
                    color = if (feedback.isCorrect) CorrectGreen.copy(alpha = 0.2f) else WrongRed.copy(alpha = 0.2f),
                    thickness = 0.8.dp
                )

                Text(
                    text = feedback.explanation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.5.sp
                    ),
                    color = TextZincSecondary
                )
            }
        }
    }
}

@Composable
private fun MasterySummaryScoreCardContent(
    state: ChapterMasteryUiState.SummaryScoreCard,
    paddingValues: PaddingValues,
    onRetakeQuiz: () -> Unit,
    onViewProgress: () -> Unit,
    onNavigateBack: () -> Unit,
    onBeginNextChapter: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Score Result Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardSurface,
                border = BorderStroke(1.5.dp, GoldAmberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = GoldAmberContainer,
                        border = BorderStroke(1.5.dp, GoldAmber),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = GoldAmber,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text = "Grand Mastery Exam Complete!",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPureWhite,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${state.accuracyPercentage.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 46.sp
                        ),
                        color = when {
                            state.accuracyPercentage >= 80f -> GoldAmber
                            state.accuracyPercentage >= 60f -> RetryAmber
                            else -> WrongRed
                        },
                        modifier = Modifier.testTag("mastery_summary_accuracy")
                    )

                    Text(
                        text = "${state.primaryCorrectCount} of ${state.totalPrimaryQuestions} questions mastered in '${state.chapterTitle}'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextZincSecondary,
                        textAlign = TextAlign.Center
                    )

                    // XP Earned Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoldAmberContainer,
                        border = BorderStroke(1.dp, GoldAmberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+${state.totalXpAwarded} XP Awarded",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp
                                    ),
                                    color = GoldAmber
                                )
                            }
                            Text(
                                text = "🏆 Includes +${state.bonusMasteryXp} XP Chapter Mastery Bonus",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GoldAmber,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (state.accuracyPercentage >= 80f) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardSurfaceElevated,
                            border = BorderStroke(1.5.dp, GoldAmber),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = GoldAmber,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "👑", fontSize = 20.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mastery Seal Awarded!",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = GoldAmber
                                    )
                                    Text(
                                        text = "Master Strategist Rank achieved for this chapter.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = TextZincSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (state.retriedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CardSurfaceElevated,
                            border = BorderStroke(0.8.dp, BorderZincSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Retry Loop Recovery",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextZincSecondary
                                )
                                Text(
                                    text = "${state.retryRecoveredCount} of ${state.retriedCount} resolved",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = CorrectGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mistakes Review Section
        if (state.mistakes.isNotEmpty()) {
            item {
                Text(
                    text = "Mistakes Breakdown & Spaced Flagging",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = TextPureWhite
                )
            }

            items(state.mistakes) { mistake ->
                MistakeReviewCard(mistake = mistake)
            }
        }

        // Action Buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isMastered && onBeginNextChapter != null) {
                    Button(
                        onClick = onBeginNextChapter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("mastery_summary_begin_next_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPureWhite,
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Text(
                            text = "Begin Next Chapter →",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = onViewProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mastery_summary_progress_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (state.isMastered && onBeginNextChapter != null) BorderZincSubtle else BorderZincActive),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPureWhite
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Learning Progress",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mastery_summary_back_dashboard_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderZincSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextZincSecondary
                    )
                ) {
                    Text(
                        text = if (state.isMastered) "Return to Curriculum" else "Back to Dashboard",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakeReviewCard(mistake: MistakeRecord) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderZincSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = mistake.questionText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = TextPureWhite
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Your Answer:",
                    style = MaterialTheme.typography.labelSmall,
                    color = WrongRed
                )
                Text(
                    text = mistake.userSelectedOptionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextZincSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Correct Concept:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CorrectGreen
                )
                Text(
                    text = mistake.correctOptionText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPureWhite
                )
            }

            if (mistake.explanation.isNotBlank()) {
                Text(
                    text = mistake.explanation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = TextZincMuted
                )
            }
        }
    }
}

/**
 * Centered Animated Chapter Unlock Modal
 * Triggered upon passing the 50-MCQ Chapter Mastery Quiz.
 * Backdrop: Frosted blur scrim (#000000 at 60% opacity)
 * Card Surface: Obsidian slate (#141418), 1dp border (#27272F), rounded 20dp, padding 24dp
 * Entrance Physics: Spring bounce scale animation (0.85f -> 1.0f) with alpha fade-in
 * Header Icon: Animated trophy glyph with radiating pulse
 * Title: "CHAPTER UNLOCKED" (#FFFFFF, bold tracking, 18sp)
 * Subtitle: "You have officially mastered [Chapter Title]. [Next Chapter Title] is now available in your curriculum." (#A1A1AA, 13sp, centered)
 * Primary Action: "Begin Next Chapter →"
 * Secondary Action: "Return to Curriculum"
 */
@Composable
fun ChapterUnlockCelebrationDialog(
    chapterTitle: String,
    nextChapterTitle: String?,
    nextChapterId: Long?,
    onBeginNextChapter: (Long) -> Unit,
    onReturnToCurriculum: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Entrance physics: scale from 0.85f to 1.0f with spring & alpha fade-in
        var animationTriggered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animationTriggered = true
        }
        val scale by animateFloatAsState(
            targetValue = if (animationTriggered) 1f else 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "dialog_scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (animationTriggered) 1f else 0f,
            animationSpec = tween(300),
            label = "dialog_alpha"
        )

        // Radiating pulse for trophy glyph
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        // Backdrop: Frosted blur scrim (#000000 at 60% opacity)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Card Surface: Obsidian slate (#141418), 1dp border (#27272F), rounded 20dp, padding 24dp
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 340.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // prevent dismissing when clicking card surface
                    )
                    .testTag("chapter_unlock_celebration_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header Icon: Animated trophy glyph with subtle radiating pulse
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .testTag("unlock_dialog_trophy_pulse")
                    ) {
                        // Radiating pulse halo
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    this.alpha = pulseAlpha
                                }
                                .background(Color(0xFFF59E0B), CircleShape)
                        )

                        // Circular base pedestal
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1C1C24),
                            border = BorderStroke(1.dp, Color(0x66F59E0B)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "🏆",
                                    fontSize = 26.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title: "CHAPTER UNLOCKED" (#FFFFFF, bold tracking, 18sp)
                    Text(
                        text = "CHAPTER UNLOCKED",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color(0xFFFFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("unlock_dialog_title")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtitle: "You have officially mastered [Chapter Title]. [Next Chapter Title] is now available in your curriculum." (#A1A1AA, 13sp, centered)
                    val subtitleText = if (!nextChapterTitle.isNullOrBlank()) {
                        "You have officially mastered $chapterTitle. $nextChapterTitle is now available in your curriculum."
                    } else {
                        "You have officially mastered $chapterTitle. The next chapter in your curriculum is now unlocked."
                    }

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        color = Color(0xFFA1A1AA),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("unlock_dialog_subtitle")
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action: "Begin Next Chapter →" (advances immediately to Chapter N+1 reader)
                    if (nextChapterId != null) {
                        Button(
                            onClick = { onBeginNextChapter(nextChapterId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFFFFF),
                                contentColor = Color(0xFF0B0B0E)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("unlock_dialog_begin_next_button")
                        ) {
                            Text(
                                text = "Begin Next Chapter →",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color(0xFF0B0B0E)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Secondary Action: "Return to Curriculum" (dismisses modal, smoothly routing to Book Detail View with Chapter N+1 rendered in Unlocked state)
                    OutlinedButton(
                        onClick = onReturnToCurriculum,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF27272F)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFA1A1AA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("unlock_dialog_return_curriculum_button")
                    ) {
                        Text(
                            text = "Return to Curriculum",
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
    }
}

