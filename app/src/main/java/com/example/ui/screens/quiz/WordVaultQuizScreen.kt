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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.model.WordMistakeRecord
import com.example.data.manager.WordQuizOption
import com.example.data.manager.WordQuizQuestion
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.WordVaultQuizUiState
import com.example.ui.viewmodel.WordVaultQuizViewModel

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
fun WordVaultQuizScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToDailyRecall: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WordVaultQuizViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Intercept system back press when a quiz is actively in progress
    BackHandler(enabled = uiState is WordVaultQuizUiState.ActiveQuiz) {
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
            .background(CanvasObsidian),
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = CanvasObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Word Vault Challenge",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = TextPureWhite
                        )
                        when (val state = uiState) {
                            is WordVaultQuizUiState.ActiveQuiz -> {
                                val subtitle = if (state.isRetryRound) {
                                    "Retry Loop • Question ${state.currentRetryIndex + 1} of ${state.totalRetryQuestions}"
                                } else {
                                    "Word ${state.currentIndex + 1} of ${state.totalQuestionsInPrimary}"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isRetryRound) RetryAmber else TextZincMuted
                                )
                            }
                            is WordVaultQuizUiState.SummaryScoreCard -> {
                                Text(
                                    text = "Session Complete • 100% Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CorrectGreen
                                )
                            }
                            else -> {}
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState is WordVaultQuizUiState.ActiveQuiz) {
                                showExitConfirmationDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("word_quiz_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = TextPureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasObsidian,
                    titleContentColor = TextPureWhite,
                    navigationIconContentColor = TextPureWhite
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is WordVaultQuizUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TextPureWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            is WordVaultQuizUiState.ResumePrompt -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TextPureWhite,
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

            is WordVaultQuizUiState.InsufficientWords -> {
                InsufficientWordsContent(
                    state = state,
                    paddingValues = paddingValues,
                    onNavigateBack = onNavigateBack
                )
            }

            is WordVaultQuizUiState.ActiveQuiz -> {
                ActiveWordQuizContent(
                    state = state,
                    paddingValues = paddingValues,
                    onSelectOption = { viewModel.selectOption(it) },
                    onSubmitAnswer = { viewModel.submitAnswer() },
                    onNextQuestion = { viewModel.moveToNextQuestion() }
                )
            }

            is WordVaultQuizUiState.SummaryScoreCard -> {
                WordSummaryScoreCardContent(
                    state = state,
                    paddingValues = paddingValues,
                    onRetakeQuiz = { viewModel.startQuizSession() },
                    onViewProgress = onNavigateToProgress,
                    onNavigateToNextChallenge = onNavigateToDailyRecall,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun InsufficientWordsContent(
    state: WordVaultQuizUiState.InsufficientWords,
    paddingValues: PaddingValues,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = CardSurfaceElevated,
            border = BorderStroke(1.dp, BorderZincSubtle),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RetryAmber,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Word Quizzes Locked",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = TextPureWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextZincSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderZincSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Words in Vault",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextZincSecondary
                )
                Text(
                    text = "${state.currentCount} / ${state.requiredCount}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = RetryAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("word_quiz_insufficient_back_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPureWhite,
                contentColor = CanvasObsidian
            )
        ) {
            Text(
                text = "Back to Word Vault",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ActiveWordQuizContent(
    state: WordVaultQuizUiState.ActiveQuiz,
    paddingValues: PaddingValues,
    onSelectOption: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onNextQuestion: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progressPercent,
        label = "wordQuizProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Linear Progress Indicator
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = if (state.isRetryRound) RetryAmber else TextPureWhite,
            trackColor = BorderZincSubtle,
            strokeCap = StrokeCap.Round
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Target Word Hero Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderZincSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (state.isRetryRound) RetryAmberContainer else CardSurfaceElevated,
                                border = BorderStroke(0.8.dp, if (state.isRetryRound) RetryAmber else BorderZincSubtle)
                            ) {
                                Text(
                                    text = if (state.isRetryRound) "RETRY ROUND" else "WORD VAULT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = if (state.isRetryRound) RetryAmber else TextZincSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = state.currentQuestion.word,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp
                            ),
                            color = TextPureWhite,
                            modifier = Modifier.testTag("word_quiz_target_word")
                        )

                        if (state.currentQuestion.originalSentence.isNotBlank()) {
                            Text(
                                text = "\"${state.currentQuestion.originalSentence}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = TextZincMuted
                            )
                        }

                        Text(
                            text = "Select the accurate meaning for this word:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = TextZincSecondary
                        )
                    }
                }
            }

            // 4 MCQ Options
            items(state.currentQuestion.options) { option ->
                val isSelected = state.selectedOptionId == option.id
                val isAnswerSubmitted = state.isAnswerSubmitted
                val isCorrect = option.isCorrect

                val backgroundColor = when {
                    !isAnswerSubmitted && isSelected -> CardSurfaceElevated
                    isAnswerSubmitted && isCorrect -> CorrectGreenContainer
                    isAnswerSubmitted && isSelected && !isCorrect -> WrongRedContainer
                    else -> CardSurface
                }

                val borderColor = when {
                    !isAnswerSubmitted && isSelected -> BorderZincActive
                    isAnswerSubmitted && isCorrect -> CorrectGreenBorder
                    isAnswerSubmitted && isSelected && !isCorrect -> WrongRedBorder
                    else -> BorderZincSubtle
                }

                val badgeBg = when {
                    !isAnswerSubmitted && isSelected -> TextPureWhite
                    isAnswerSubmitted && isCorrect -> CorrectGreen
                    isAnswerSubmitted && isSelected && !isCorrect -> WrongRed
                    else -> CardSurfaceElevated
                }

                val badgeText = when {
                    !isAnswerSubmitted && isSelected -> CanvasObsidian
                    isAnswerSubmitted && (isCorrect || isSelected) -> CanvasObsidian
                    else -> TextZincSecondary
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isAnswerSubmitted) {
                            onSelectOption(option.id)
                        }
                        .testTag("word_quiz_option_${option.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isAnswerSubmitted && isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Correct",
                                        tint = CanvasObsidian,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (isAnswerSubmitted && isSelected && !isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Wrong",
                                        tint = CanvasObsidian,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = option.id,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = badgeText
                                    )
                                }
                            }
                        }

                        Text(
                            text = option.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected || (isAnswerSubmitted && isCorrect)) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = TextPureWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Explanation Feedback Card upon Answer Submission
            if (state.isAnswerSubmitted) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.isCorrect) CorrectGreenContainer else WrongRedContainer,
                        border = BorderStroke(1.dp, if (state.isCorrect) CorrectGreenBorder else WrongRedBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("word_quiz_feedback_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (state.isCorrect) CorrectGreen else WrongRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (state.isCorrect) "Correct Definition!" else "Incorrect Choice",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (state.isCorrect) CorrectGreen else WrongRed
                                )
                            }

                            if (!state.isCorrect) {
                                Text(
                                    text = "Correct Meaning: ${state.correctMeaningText}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPureWhite
                                )
                            }

                            if (state.romanUrduExplanation.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardSurfaceElevated.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.romanUrduExplanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextZincSecondary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderZincSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (!state.isAnswerSubmitted) {
                    Button(
                        onClick = onSubmitAnswer,
                        enabled = state.selectedOptionId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("word_quiz_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPureWhite,
                            contentColor = CanvasObsidian,
                            disabledContainerColor = BorderZincSubtle,
                            disabledContentColor = TextZincMuted
                        )
                    ) {
                        Text(
                            text = "Submit Answer",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Button(
                        onClick = onNextQuestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("word_quiz_next_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPureWhite,
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.isRetryRound) "Next Retry Word" else "Next Word",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
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
private fun WordSummaryScoreCardContent(
    state: WordVaultQuizUiState.SummaryScoreCard,
    paddingValues: PaddingValues,
    onRetakeQuiz: () -> Unit,
    onViewProgress: () -> Unit,
    onNavigateToNextChallenge: () -> Unit = {},
    onNavigateBack: () -> Unit
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
                                imageVector = if (state.accuracyPercentage >= 80f) Icons.Default.AutoAwesome else Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = null,
                                tint = if (state.accuracyPercentage >= 80f) CorrectGreen else TextPureWhite,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = if (state.accuracyPercentage >= 80f) "Vocabulary Mastered!" else "Session Completed",
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
                        modifier = Modifier.testTag("word_quiz_summary_accuracy")
                    )

                    Text(
                        text = "${state.primaryCorrectCount} of ${state.totalPrimaryQuestions} words identified correctly",
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
                                        text = "🎯 Daily Double Complete! +50 Bonus XP Awarded",
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

        // Word Mistakes Review Section
        if (state.mistakes.isNotEmpty()) {
            item {
                Text(
                    text = "Words to Review",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = TextPureWhite
                )
            }

            items(state.mistakes) { mistake ->
                WordMistakeReviewCard(mistake = mistake)
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
                            .testTag("word_quiz_next_challenge_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = CanvasObsidian
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Next Challenge: ${state.nextChallengeTitle} (⚡ +${state.nextChallengeXp} XP)",
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
                        .testTag("word_quiz_summary_progress_button"),
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
                            text = "View Learning Progress & XP",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("word_quiz_summary_back_dashboard_button"),
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
                        .testTag("word_quiz_summary_retake_button"),
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
                            tint = TextZincSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Practice Another Word Session",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordMistakeReviewCard(
    mistake: WordMistakeRecord,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderZincSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mistake.word,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPureWhite
                )

                if (mistake.resolvedInRetry) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CorrectGreenContainer,
                        border = BorderStroke(0.8.dp, CorrectGreenBorder)
                    ) {
                        Text(
                            text = "Resolved in Retry",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = CorrectGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (mistake.userSelectedOptionText.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = WrongRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Selected: ${mistake.userSelectedOptionText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WrongRed
                    )
                }
            }

            Row(
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
                    text = "Correct: ${mistake.correctOptionText}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CorrectGreen
                )
            }

            if (mistake.romanUrduExplanation.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CardSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mistake.romanUrduExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextZincSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
