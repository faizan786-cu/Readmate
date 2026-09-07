package com.example.ui.screens.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.model.MistakeRecord
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.DailyRecallUiState
import com.example.ui.viewmodel.DailyRecallViewModel
import com.example.ui.viewmodel.QuizFeedback
import com.example.ui.viewmodel.QuizOption
import com.example.ui.viewmodel.QuizQuestionState

private val CanvasObsidian = Color(0xFF09090B)
private val CardSurface = Color(0xFF141417)
private val CardSurfaceElevated = Color(0xFF1C1C21)
private val TextPureWhite = Color(0xFFFAFAFA)
private val TextZincSecondary = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val BorderZincSubtle = Color(0xFF27272A)
private val BorderZincActive = Color(0xFF52525B)

private val CorrectGreen = Color(0xFF10B981)
private val CorrectGreenContainer = Color(0x1F10B981)
private val CorrectGreenBorder = Color(0xFF059669)

private val WrongRed = Color(0xFFEF4444)
private val WrongRedContainer = Color(0x1FEF4444)
private val WrongRedBorder = Color(0xFFDC2626)

private val RetryAmber = Color(0xFFF59E0B)
private val RetryAmberContainer = Color(0x1FF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRecallScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToWordQuiz: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DailyRecallViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Intercept system back press when a quiz is actively in progress
    BackHandler(enabled = uiState is DailyRecallUiState.ActiveQuiz) {
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
            .testTag("daily_recall_screen"),
        containerColor = CanvasObsidian,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is DailyRecallUiState.ActiveQuiz -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.progressPercent,
                                label = "quiz_progress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .padding(end = 16.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (state.isRetryRound) RetryAmber else TextPureWhite,
                                trackColor = CardSurfaceElevated,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        is DailyRecallUiState.SummaryScoreCard -> {
                            Text(
                                text = "Session Summary",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = TextPureWhite
                            )
                        }
                        else -> {
                            Text(
                                text = "Daily Active Recall",
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
                            if (uiState is DailyRecallUiState.ActiveQuiz) {
                                showExitConfirmationDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("daily_recall_back_button")
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
            is DailyRecallUiState.Loading -> {
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

            is DailyRecallUiState.ResumePrompt -> {
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
                QuizResumePromptDialog(
                    questionNumber = state.currentQuestionIndex,
                    totalQuestions = state.totalQuestions,
                    onResume = { viewModel.resumeSavedSession(state.cachedSession) },
                    onStartFresh = { viewModel.startFreshSession() }
                )
            }

            is DailyRecallUiState.Empty -> {
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
                                        tint = TextPureWhite,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Text(
                                text = "No Questions Ready Yet",
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

            is DailyRecallUiState.ActiveQuiz -> {
                ActiveQuizContent(
                    state = state,
                    paddingValues = innerPadding,
                    onSelectOption = { optionId -> viewModel.selectOption(optionId) },
                    onSubmitAnswer = { viewModel.submitAnswer() },
                    onNextQuestion = { viewModel.nextQuestion() }
                )
            }

            is DailyRecallUiState.SummaryScoreCard -> {
                SummaryScoreCardContent(
                    state = state,
                    paddingValues = innerPadding,
                    onRetakeQuiz = { viewModel.loadDailyQuizSession() },
                    onViewProgress = onNavigateToProgress,
                    onNavigateToNextChallenge = onNavigateToWordQuiz,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun ActiveQuizContent(
    state: DailyRecallUiState.ActiveQuiz,
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
            // Header Pill Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Question Count Indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isRetryRound) RetryAmberContainer else CardSurfaceElevated,
                        border = BorderStroke(
                            0.8.dp,
                            if (state.isRetryRound) RetryAmber.copy(alpha = 0.5f) else BorderZincSubtle
                        )
                    ) {
                        Text(
                            text = if (state.isRetryRound) {
                                "Retry Round (${state.currentRetryIndex + 1}/${state.totalRetryQuestions})"
                            } else {
                                "Question ${state.currentIndex + 1} of ${state.totalQuestionsInPrimary}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = if (state.isRetryRound) RetryAmber else TextPureWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (state.currentQuestion.bookTitle.isNotBlank()) {
                        Text(
                            text = state.currentQuestion.bookTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = TextZincMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            // Question Text
            item {
                Text(
                    text = state.currentQuestion.question.questionText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        lineHeight = 27.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = TextPureWhite,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 4 Contextual Option Cards
            items(state.currentQuestion.options) { option ->
                QuizOptionCard(
                    option = option,
                    isSelected = state.selectedOptionId == option.id,
                    isAnswerSubmitted = state.isAnswerSubmitted,
                    feedback = state.feedback,
                    onClick = {
                        if (!state.isAnswerSubmitted) {
                            onSelectOption(option.id)
                        }
                    }
                )
            }

            // Post-submission Feedback Card
            if (state.isAnswerSubmitted && state.feedback != null) {
                item {
                    QuizFeedbackCard(
                        feedback = state.feedback,
                        isRetry = state.isRetryRound
                    )
                }
            }
        }

        // Bottom Fixed Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderZincSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                if (!state.isAnswerSubmitted) {
                    Button(
                        onClick = onSubmitAnswer,
                        enabled = state.selectedOptionId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("daily_recall_check_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPureWhite,
                            contentColor = CanvasObsidian,
                            disabledContainerColor = CardSurfaceElevated,
                            disabledContentColor = TextZincMuted
                        )
                    ) {
                        Text(
                            text = "Check Answer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = onNextQuestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("daily_recall_next_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.feedback?.isCorrect == true) CorrectGreen else TextPureWhite,
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
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
private fun QuizOptionCard(
    option: QuizOption,
    isSelected: Boolean,
    isAnswerSubmitted: Boolean,
    feedback: QuizFeedback?,
    onClick: () -> Unit
) {
    val isOptionCorrect = option.isCorrect || (feedback?.correctOptionId == option.id)
    val isOptionSelectedWrong = isAnswerSubmitted && isSelected && feedback?.isCorrect == false

    val backgroundColor = when {
        isAnswerSubmitted && isOptionCorrect -> CorrectGreenContainer
        isOptionSelectedWrong -> WrongRedContainer
        isSelected -> CardSurfaceElevated
        else -> CardSurface
    }

    val borderColor = when {
        isAnswerSubmitted && isOptionCorrect -> CorrectGreenBorder
        isOptionSelectedWrong -> WrongRedBorder
        isSelected -> TextPureWhite
        else -> BorderZincSubtle
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected || (isAnswerSubmitted && isOptionCorrect)) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isAnswerSubmitted, onClick = onClick)
            .testTag("daily_recall_option_${option.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Option Key Badge (A, B, C, D)
            Surface(
                shape = CircleShape,
                color = when {
                    isAnswerSubmitted && isOptionCorrect -> CorrectGreen
                    isOptionSelectedWrong -> WrongRed
                    isSelected -> TextPureWhite
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

            // Option Text
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
private fun QuizFeedbackCard(
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
                        if (isRetry) "Great job! Concept mastered in retry." else "Correct!"
                    } else {
                        "Not quite — Let's review this concept"
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
private fun SummaryScoreCardContent(
    state: DailyRecallUiState.SummaryScoreCard,
    paddingValues: PaddingValues,
    onRetakeQuiz: () -> Unit,
    onViewProgress: () -> Unit,
    onNavigateToNextChallenge: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

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
                border = BorderStroke(1.dp, BorderZincSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CardSurfaceElevated,
                        border = BorderStroke(1.dp, BorderZincSubtle),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.accuracyPercentage >= 80f) Icons.Default.CheckCircle else Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = null,
                                tint = if (state.accuracyPercentage >= 80f) CorrectGreen else TextPureWhite,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = if (state.accuracyPercentage >= 80f) "Outstanding Recall" else "Session Completed",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPureWhite
                    )

                    Text(
                        text = "${state.accuracyPercentage.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 44.sp
                        ),
                        color = when {
                            state.accuracyPercentage >= 80f -> CorrectGreen
                            state.accuracyPercentage >= 60f -> RetryAmber
                            else -> WrongRed
                        },
                        modifier = Modifier.testTag("daily_recall_summary_accuracy")
                    )

                    // Streak Status Restored Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x1F10B981),
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CorrectGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Streak Active • Reader Lock Lifted",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = CorrectGreen
                            )
                        }
                    }

                    Text(
                        text = "${state.primaryCorrectCount}/${state.totalPrimaryQuestions} Retained" +
                                if (state.mistakes.isNotEmpty()) " • ${state.mistakes.size} Transferred to Mistake Vault" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextZincSecondary,
                        textAlign = TextAlign.Center
                    )

                    // XP Earned & Daily Double Banner
                    if (state.totalXpAwarded > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RetryAmberContainer,
                            border = BorderStroke(1.dp, RetryAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                                        tint = RetryAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+${state.totalXpAwarded} XP Earned",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = RetryAmber
                                    )
                                }
                                if (state.isDailyDoubleTriggered) {
                                    Text(
                                        text = "Daily Double Complete! +50 Bonus XP Awarded",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = CorrectGreen,
                                        textAlign = TextAlign.Center
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
                    text = "Mistakes Breakdown",
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
                // Next Chained Challenge Button
                if (state.isNextChallengeAvailable) {
                    Button(
                        onClick = onNavigateToNextChallenge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("daily_recall_next_challenge_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Next Challenge: ${state.nextChallengeTitle} (+${state.nextChallengeXp} XP)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp
                                )
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

                Button(
                    onClick = onViewProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("daily_recall_summary_progress_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isNextChallengeAvailable) CardSurfaceElevated else TextPureWhite,
                        contentColor = if (state.isNextChallengeAvailable) TextPureWhite else CanvasObsidian
                    ),
                    border = if (state.isNextChallengeAvailable) BorderStroke(1.dp, BorderZincSubtle) else null
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
                            text = "View Learning Progress & Weak Concepts",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("daily_recall_summary_back_dashboard_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderZincSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPureWhite
                    )
                ) {
                    Text(
                        text = "Back to Dashboard",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedButton(
                    onClick = onRetakeQuiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("daily_recall_summary_retake_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderZincSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextZincSecondary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Practice Again (Shuffled)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
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
