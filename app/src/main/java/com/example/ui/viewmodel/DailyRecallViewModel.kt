package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.model.MistakeRecord
import com.example.data.manager.CachedDailyRecallSession
import com.example.data.manager.DailyRetentionManager
import com.example.data.manager.QuizSessionCacheManager
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.McqRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.WordVaultRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class QuizOption(
    val id: String, // "A", "B", "C", "D"
    val text: String,
    val isCorrect: Boolean
)

data class QuizQuestionState(
    val question: McqQuestion,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val options: List<QuizOption>,
    val isRetry: Boolean = false
)

data class QuizFeedback(
    val selectedOptionId: String,
    val isCorrect: Boolean,
    val correctOptionId: String,
    val correctOptionText: String,
    val explanation: String
)

sealed interface DailyRecallUiState {
    object Loading : DailyRecallUiState
    
    data class Empty(
        val message: String = "No snippet questions available yet. Read books and snip passages with AI explanations to generate daily quiz questions!"
    ) : DailyRecallUiState

    data class ResumePrompt(
        val cachedSession: CachedDailyRecallSession,
        val currentQuestionIndex: Int,
        val totalQuestions: Int
    ) : DailyRecallUiState

    data class ActiveQuiz(
        val currentQuestion: QuizQuestionState,
        val currentIndex: Int, // 0-based
        val totalQuestionsInPrimary: Int,
        val isRetryRound: Boolean = false,
        val currentRetryIndex: Int = 0,
        val totalRetryQuestions: Int = 0,
        val selectedOptionId: String? = null,
        val isAnswerSubmitted: Boolean = false,
        val feedback: QuizFeedback? = null,
        val primaryCorrectCount: Int = 0,
        val primaryAnsweredCount: Int = 0,
        val retryCorrectCount: Int = 0,
        val progressPercent: Float = 0f
    ) : DailyRecallUiState

    data class SummaryScoreCard(
        val totalPrimaryQuestions: Int,
        val primaryCorrectCount: Int,
        val accuracyPercentage: Float,
        val retriedCount: Int,
        val retryRecoveredCount: Int,
        val mistakes: List<MistakeRecord>,
        val attemptId: Long = 0L,
        val earnedBaseXp: Int = 0,
        val bonusXp: Int = 0,
        val totalXpAwarded: Int = 0,
        val isDailyDoubleTriggered: Boolean = false,
        val isNextChallengeAvailable: Boolean = false,
        val nextChallengeTitle: String = "Word Vault Quiz",
        val nextChallengeXp: Int = 100
    ) : DailyRecallUiState
}

class DailyRecallViewModel(
    private val mcqRepository: McqRepository,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val wordVaultRepository: WordVaultRepository? = null,
    private val quizSessionCacheManager: QuizSessionCacheManager? = null,
    private val dailyRetentionManager: DailyRetentionManager? = null,
    private val quizRepository: QuizRepository? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    companion object {
        private const val TAG = "DailyRecallViewModel"
        const val DAILY_QUIZ_SIZE = 10
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mistakeListAdapter: JsonAdapter<List<MistakeRecord>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, MistakeRecord::class.java))

    private val _uiState = MutableStateFlow<DailyRecallUiState>(DailyRecallUiState.Loading)
    val uiState: StateFlow<DailyRecallUiState> = _uiState.asStateFlow()

    // Internal session queues
    private var primaryQueue: List<QuizQuestionState> = emptyList()
    private var primaryIndex = 0

    private val retryQueue: MutableList<McqQuestion> = mutableListOf()
    private var retryIndex = 0

    private var primaryCorrectCount = 0
    private var primaryAnsweredCount = 0
    private var retryCorrectCount = 0

    private val recordedMistakes = mutableListOf<MistakeRecord>()

    private var booksMap: Map<Long, Book> = emptyMap()
    private var chaptersMap: Map<Long, Chapter> = emptyMap()

    init {
        loadDailyQuizSession()
    }

    /**
     * Initializes or resets the 10-MCQ session:
     * 1. Checks for a valid saved session cache (< 24h).
     * 2. If available, prompts user to Resume from Question X or Start Fresh.
     * 3. Otherwise, loads a fresh 10-MCQ session.
     */
    fun loadDailyQuizSession() {
        viewModelScope.launch(dispatcher) {
            _uiState.value = DailyRecallUiState.Loading

            try {
                val cachedSession = quizSessionCacheManager?.getDailyRecallSession()
                if (cachedSession != null && cachedSession.primaryQuestionIds.isNotEmpty()) {
                    val allQuestions = mcqRepository.getAllQuestions()
                    val allQuestionsMap = allQuestions.associateBy { it.id }
                    val hasAllQuestions = cachedSession.primaryQuestionIds.all { allQuestionsMap.containsKey(it) }
                    if (hasAllQuestions) {
                        val resumeQNumber = if (cachedSession.isRetryRound) {
                            cachedSession.primaryQuestionIds.size + cachedSession.retryIndex + 1
                        } else {
                            cachedSession.primaryIndex + 1
                        }.coerceAtMost(cachedSession.primaryQuestionIds.size)

                        _uiState.value = DailyRecallUiState.ResumePrompt(
                            cachedSession = cachedSession,
                            currentQuestionIndex = resumeQNumber,
                            totalQuestions = cachedSession.primaryQuestionIds.size
                        )
                        return@launch
                    }
                }

                loadFreshDailyQuizSession()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing daily quiz: ${e.message}", e)
                _uiState.value = DailyRecallUiState.Empty(
                    message = "Could not load quiz questions: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun startFreshSession() {
        quizSessionCacheManager?.clearDailyRecallSession()
        viewModelScope.launch(dispatcher) {
            loadFreshDailyQuizSession()
        }
    }

    fun resumeSavedSession(cached: CachedDailyRecallSession) {
        viewModelScope.launch(dispatcher) {
            _uiState.value = DailyRecallUiState.Loading
            try {
                val books = bookRepository.allBooks.firstOrNull() ?: emptyList()
                booksMap = books.associateBy { it.id }

                val allQuestions = mcqRepository.getAllQuestions()
                val allQuestionsMap = allQuestions.associateBy { it.id }

                val primaryQuestions = cached.primaryQuestionIds.mapNotNull { allQuestionsMap[it] }
                if (primaryQuestions.isEmpty()) {
                    loadFreshDailyQuizSession()
                    return@launch
                }

                primaryQueue = primaryQuestions.map { q ->
                    createQuizQuestionState(q, isRetry = false)
                }
                primaryIndex = cached.primaryIndex.coerceIn(0, (primaryQueue.size - 1).coerceAtLeast(0))

                retryQueue.clear()
                val retryQuestions = cached.retryQuestionIds.mapNotNull { allQuestionsMap[it] }
                retryQueue.addAll(retryQuestions)
                retryIndex = cached.retryIndex.coerceIn(0, (retryQueue.size - 1).coerceAtLeast(0))

                primaryCorrectCount = cached.primaryCorrectCount
                primaryAnsweredCount = cached.primaryAnsweredCount
                retryCorrectCount = cached.retryCorrectCount

                recordedMistakes.clear()
                recordedMistakes.addAll(cached.recordedMistakes)

                val totalPrimary = primaryQueue.size
                val isRetry = cached.isRetryRound && retryQueue.isNotEmpty() && retryIndex < retryQueue.size

                val currentQState = if (isRetry) {
                    createQuizQuestionState(retryQueue[retryIndex], isRetry = true)
                } else {
                    primaryQueue[primaryIndex]
                }

                val currentOverallIndex = if (isRetry) totalPrimary + retryIndex else primaryIndex
                val totalSteps = if (retryQueue.isNotEmpty()) totalPrimary + retryQueue.size else totalPrimary
                val progress = if (totalSteps > 0) currentOverallIndex.toFloat() / totalSteps.toFloat() else 0f

                _uiState.value = DailyRecallUiState.ActiveQuiz(
                    currentQuestion = currentQState,
                    currentIndex = currentOverallIndex,
                    totalQuestionsInPrimary = totalPrimary,
                    isRetryRound = isRetry,
                    currentRetryIndex = if (isRetry) retryIndex else 0,
                    totalRetryQuestions = retryQueue.size,
                    selectedOptionId = null,
                    isAnswerSubmitted = false,
                    feedback = null,
                    primaryCorrectCount = primaryCorrectCount,
                    primaryAnsweredCount = primaryAnsweredCount,
                    retryCorrectCount = retryCorrectCount,
                    progressPercent = progress
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming quiz: ${e.message}", e)
                loadFreshDailyQuizSession()
            }
        }
    }

    fun saveAndExit(onSaved: () -> Unit = {}) {
        val currentState = _uiState.value as? DailyRecallUiState.ActiveQuiz
        if (currentState != null && primaryQueue.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val session = CachedDailyRecallSession(
                primaryQuestionIds = primaryQueue.map { it.question.id },
                primaryIndex = primaryIndex,
                isRetryRound = currentState.isRetryRound,
                retryQuestionIds = retryQueue.map { it.id },
                retryIndex = retryIndex,
                primaryCorrectCount = primaryCorrectCount,
                primaryAnsweredCount = primaryAnsweredCount,
                retryCorrectCount = retryCorrectCount,
                recordedMistakes = recordedMistakes.toList(),
                savedAtTimestamp = now,
                expiresAt = now + QuizSessionCacheManager.EXPIRATION_DURATION_MS
            )
            quizSessionCacheManager?.saveDailyRecallSession(session)
        }
        onSaved()
    }

    private suspend fun loadFreshDailyQuizSession() {
        _uiState.value = DailyRecallUiState.Loading

        try {
            // Fetch books and chapters for attribution
            val books = bookRepository.allBooks.firstOrNull() ?: emptyList()
            booksMap = books.associateBy { it.id }

            // 1. Try assembling quiz through DailyRetentionManager (Midnight Lifecycle & Quota selection)
            val retentionQuizzes = if (dailyRetentionManager != null) {
                dailyRetentionManager.assembleDailyQuizSession()
            } else emptyList()

            val selectedQuestions: List<McqQuestion> = if (retentionQuizzes.isNotEmpty()) {
                retentionQuizzes.map { quiz ->
                    val optA = quiz.options.getOrElse(0) { "" }
                    val optB = quiz.options.getOrElse(1) { "" }
                    val optC = quiz.options.getOrElse(2) { "" }
                    val optD = quiz.options.getOrElse(3) { "" }
                    val correctOpt = when (quiz.correctAnswerIndex) {
                        0 -> "A"
                        1 -> "B"
                        2 -> "C"
                        3 -> "D"
                        else -> "A"
                    }
                    McqQuestion(
                        id = quiz.id,
                        bookId = quiz.bookId,
                        chapterId = quiz.chapterId,
                        passageTurnId = 0L,
                        questionText = quiz.question,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctOption = correctOpt,
                        explanation = quiz.explanation,
                        mistakeCount = if (quiz.isFromMistakeBank) 1 else 0,
                        isFlaggedForSpacedReview = quiz.isFromMistakeBank,
                        createdAt = System.currentTimeMillis()
                    )
                }
            } else {
                val allQuestions = mcqRepository.getAllQuestions()
                if (allQuestions.isEmpty()) {
                    val todayState = dailyRetentionManager?.observeTodayRetentionState()?.firstOrNull()
                    val emptyMsg = if (todayState?.isFirstDayGracePeriod == true) {
                        "Day 0 Grace Period: Newly snipped passage quizzes will unlock at midnight for your first daily retention challenge."
                    } else {
                        "No quizzes due today. Snip passages during reading to build your active retention bank."
                    }
                    _uiState.value = DailyRecallUiState.Empty(message = emptyMsg)
                    return
                }
                sampleDailyQuestions(allQuestions, DAILY_QUIZ_SIZE)
            }

            if (selectedQuestions.isEmpty()) {
                val todayState = dailyRetentionManager?.observeTodayRetentionState()?.firstOrNull()
                val emptyMsg = if (todayState?.isFirstDayGracePeriod == true) {
                    "Day 0 Grace Period: Newly snipped passage quizzes will unlock at midnight for your first daily retention challenge."
                } else {
                    "No quizzes due today. Snip passages during reading to build your active retention bank."
                }
                _uiState.value = DailyRecallUiState.Empty(message = emptyMsg)
                return
            }

            // Reset session counters
            primaryIndex = 0
            retryQueue.clear()
            retryIndex = 0
            primaryCorrectCount = 0
            primaryAnsweredCount = 0
            retryCorrectCount = 0
            recordedMistakes.clear()

            primaryQueue = selectedQuestions.map { q ->
                createQuizQuestionState(q, isRetry = false)
            }

            val firstQuestion = primaryQueue[0]
            _uiState.value = DailyRecallUiState.ActiveQuiz(
                currentQuestion = firstQuestion,
                currentIndex = 0,
                totalQuestionsInPrimary = primaryQueue.size,
                isRetryRound = false,
                currentRetryIndex = 0,
                totalRetryQuestions = 0,
                selectedOptionId = null,
                isAnswerSubmitted = false,
                feedback = null,
                primaryCorrectCount = 0,
                primaryAnsweredCount = 0,
                retryCorrectCount = 0,
                progressPercent = 0f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing daily quiz: ${e.message}", e)
            _uiState.value = DailyRecallUiState.Empty(
                message = "Could not load quiz questions: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Selects an option for the current question before submission.
     */
    fun selectOption(optionId: String) {
        val currentState = _uiState.value as? DailyRecallUiState.ActiveQuiz ?: return
        if (currentState.isAnswerSubmitted) return

        _uiState.update { state ->
            if (state is DailyRecallUiState.ActiveQuiz) {
                state.copy(selectedOptionId = optionId)
            } else state
        }
    }

    /**
     * Submits the chosen answer, computes validation and feedback,
     * updates primary or retry tracking, and enqueues mistakes for retry.
     */
    fun submitAnswer() {
        val currentState = _uiState.value as? DailyRecallUiState.ActiveQuiz ?: return
        val selectedOptionId = currentState.selectedOptionId ?: return
        if (currentState.isAnswerSubmitted) return

        val currentQ = currentState.currentQuestion
        val rawCorrect = currentQ.question.correctOption.trim().uppercase()
        val correctOption = currentQ.options.firstOrNull { it.isCorrect }
            ?: currentQ.options.firstOrNull { it.id.equals(rawCorrect, ignoreCase = true) }
            ?: currentQ.options.first()

        val chosenOption = currentQ.options.firstOrNull { it.id == selectedOptionId }
        val isCorrect = chosenOption?.isCorrect == true || selectedOptionId.equals(rawCorrect, ignoreCase = true)

        val feedback = QuizFeedback(
            selectedOptionId = selectedOptionId,
            isCorrect = isCorrect,
            correctOptionId = correctOption.id,
            correctOptionText = correctOption.text,
            explanation = currentQ.question.explanation
        )

        if (!currentState.isRetryRound) {
            // Primary Round
            primaryAnsweredCount++
            if (isCorrect) {
                primaryCorrectCount++
            } else {
                // Log mistake & append to End-of-Quiz Retry Queue
                recordedMistakes.add(
                    MistakeRecord(
                        questionId = currentQ.question.id,
                        questionText = currentQ.question.questionText,
                        userSelectedOptionText = chosenOption?.text ?: "Option $selectedOptionId",
                        correctOptionText = correctOption.text,
                        explanation = currentQ.question.explanation,
                        resolvedInRetry = false
                    )
                )
                retryQueue.add(currentQ.question)
            }
        } else {
            // Retry Round
            if (isCorrect) {
                retryCorrectCount++
                // Mark mistake resolved
                val mistakeIdx = recordedMistakes.indexOfFirst { it.questionId == currentQ.question.id }
                if (mistakeIdx != -1) {
                    recordedMistakes[mistakeIdx] = recordedMistakes[mistakeIdx].copy(resolvedInRetry = true)
                }
            }
        }

        val totalSessionSteps = primaryQueue.size + retryQueue.size
        val currentStep = if (!currentState.isRetryRound) {
            primaryAnsweredCount
        } else {
            primaryQueue.size + (currentState.currentRetryIndex + 1)
        }
        val progressPercent = if (totalSessionSteps > 0) {
            (currentStep.toFloat() / totalSessionSteps).coerceIn(0f, 1f)
        } else 0f

        _uiState.update { state ->
            if (state is DailyRecallUiState.ActiveQuiz) {
                state.copy(
                    isAnswerSubmitted = true,
                    feedback = feedback,
                    primaryCorrectCount = primaryCorrectCount,
                    primaryAnsweredCount = primaryAnsweredCount,
                    retryCorrectCount = retryCorrectCount,
                    totalRetryQuestions = retryQueue.size,
                    progressPercent = progressPercent
                )
            } else state
        }
    }

    /**
     * Advances to the next question in the primary queue, enters the retry round if mistakes exist,
     * or finishes the session and persists the score.
     */
    fun nextQuestion() {
        val currentState = _uiState.value as? DailyRecallUiState.ActiveQuiz ?: return
        if (!currentState.isAnswerSubmitted) return

        if (!currentState.isRetryRound) {
            // Moving in Primary Queue
            if (primaryIndex + 1 < primaryQueue.size) {
                primaryIndex++
                val nextQ = primaryQueue[primaryIndex]
                val progress = primaryIndex.toFloat() / (primaryQueue.size + retryQueue.size)

                _uiState.update {
                    DailyRecallUiState.ActiveQuiz(
                        currentQuestion = nextQ,
                        currentIndex = primaryIndex,
                        totalQuestionsInPrimary = primaryQueue.size,
                        isRetryRound = false,
                        currentRetryIndex = 0,
                        totalRetryQuestions = retryQueue.size,
                        selectedOptionId = null,
                        isAnswerSubmitted = false,
                        feedback = null,
                        primaryCorrectCount = primaryCorrectCount,
                        primaryAnsweredCount = primaryAnsweredCount,
                        retryCorrectCount = retryCorrectCount,
                        progressPercent = progress
                    )
                }
            } else {
                // Primary round completed
                if (retryQueue.isNotEmpty()) {
                    // Enter Duolingo-style Retry Round with SHUFFLED options!
                    retryIndex = 0
                    val retryQ = retryQueue[0]
                    val retryQuestionState = createQuizQuestionState(retryQ, isRetry = true)
                    val progress = primaryQueue.size.toFloat() / (primaryQueue.size + retryQueue.size)

                    _uiState.update {
                        DailyRecallUiState.ActiveQuiz(
                            currentQuestion = retryQuestionState,
                            currentIndex = primaryQueue.size,
                            totalQuestionsInPrimary = primaryQueue.size,
                            isRetryRound = true,
                            currentRetryIndex = 0,
                            totalRetryQuestions = retryQueue.size,
                            selectedOptionId = null,
                            isAnswerSubmitted = false,
                            feedback = null,
                            primaryCorrectCount = primaryCorrectCount,
                            primaryAnsweredCount = primaryAnsweredCount,
                            retryCorrectCount = retryCorrectCount,
                            progressPercent = progress
                        )
                    }
                } else {
                    // No retry needed, finish quiz!
                    finishAndPersistQuiz()
                }
            }
        } else {
            // In Retry Round
            if (retryIndex + 1 < retryQueue.size) {
                retryIndex++
                val retryQ = retryQueue[retryIndex]
                val retryQuestionState = createQuizQuestionState(retryQ, isRetry = true)
                val progress = (primaryQueue.size + retryIndex).toFloat() / (primaryQueue.size + retryQueue.size)

                _uiState.update {
                    DailyRecallUiState.ActiveQuiz(
                        currentQuestion = retryQuestionState,
                        currentIndex = primaryQueue.size + retryIndex,
                        totalQuestionsInPrimary = primaryQueue.size,
                        isRetryRound = true,
                        currentRetryIndex = retryIndex,
                        totalRetryQuestions = retryQueue.size,
                        selectedOptionId = null,
                        isAnswerSubmitted = false,
                        feedback = null,
                        primaryCorrectCount = primaryCorrectCount,
                        primaryAnsweredCount = primaryAnsweredCount,
                        retryCorrectCount = retryCorrectCount,
                        progressPercent = progress
                    )
                }
            } else {
                // Retry round completed!
                finishAndPersistQuiz()
            }
        }
    }

    /**
     * Calculates final primary accuracy, serializes mistakes, and persists attempt to DB.
     */
    private fun finishAndPersistQuiz() {
        viewModelScope.launch(dispatcher) {
            val totalPrimary = primaryQueue.size.coerceAtLeast(1)
            val accuracy = (primaryCorrectCount.toFloat() / totalPrimary.toFloat()) * 100f

            val wrongAnswersJson = try {
                if (recordedMistakes.isNotEmpty()) {
                    mistakeListAdapter.toJson(recordedMistakes)
                } else null
            } catch (e: Exception) {
                null
            }

            val attemptRecord = UserMcqAttempt(
                id = 0L,
                bookId = primaryQueue.firstOrNull()?.question?.bookId,
                chapterId = primaryQueue.firstOrNull()?.question?.chapterId,
                attemptDate = System.currentTimeMillis(),
                totalQuestions = totalPrimary,
                correctAnswers = primaryCorrectCount,
                scorePercentage = accuracy,
                wrongAnswersJson = wrongAnswersJson,
                createdAt = System.currentTimeMillis()
            )

            val attemptId = try {
                mcqRepository.recordAttempt(attemptRecord)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist MCQ attempt: ${e.message}")
                0L
            }

            // Dual-Fail Mistake Tracking:
            val now = System.currentTimeMillis()
            // Unresolved mistakes: Failed primary AND failed retry -> persistent Mistake Bank flag
            val unresolvedMistakes = recordedMistakes.filter { !it.resolvedInRetry }
            for (mistake in unresolvedMistakes) {
                try {
                    mcqRepository.updateMistakeStatus(
                        questionId = mistake.questionId,
                        isFlagged = true,
                        incrementMistake = 1,
                        timestamp = now
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update mistake status for qId ${mistake.questionId}: ${e.message}")
                }
            }

            // Questions answered correctly on primary attempt: resolve if previously flagged
            val cleanCorrectQuestionIds = primaryQueue.map { it.question.id }.toSet() - recordedMistakes.map { it.questionId }.toSet()
            for (qid in cleanCorrectQuestionIds) {
                val origQ = primaryQueue.firstOrNull { it.question.id == qid }?.question
                if (origQ?.isFlaggedForSpacedReview == true) {
                    try {
                        mcqRepository.resolveMistake(qid)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve mistake for qId $qid: ${e.message}")
                    }
                }
            }

            // Record Retention Engine Update (recalculate retention index, update streakDays, clear FROZEN_DEBT)
            val resultMap = mutableMapOf<Long, Boolean>()
            val wrongQuestionIds = recordedMistakes.map { it.questionId }.toSet()
            for (qState in primaryQueue) {
                resultMap[qState.question.id] = (qState.question.id !in wrongQuestionIds)
            }
            try {
                dailyRetentionManager?.recordDailyQuizCompleted(
                    correctCount = primaryCorrectCount,
                    totalCount = totalPrimary,
                    answeredCorrectMap = resultMap
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record daily retention state: ${e.message}")
            }

            // Award XP: +10 for primary correct, +5 for retry recovered, +50 if Daily Double is achieved
            val earnedBaseXp = (primaryCorrectCount * 10) + (retryCorrectCount * 5)
            val gamificationResult = try {
                userGamificationRepository.recordSnippetQuizCompleted(earnedBaseXp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record gamification stats: ${e.message}")
                null
            }

            val updatedStats = try {
                userGamificationRepository.getStats()
            } catch (e: Exception) {
                null
            }
            val wordCount = wordVaultRepository?.totalWordCount?.firstOrNull() ?: 0
            val isNextChallengeAvailable = (wordCount >= 10) && (updatedStats?.todayWordQuizCompleted != true)

            _uiState.value = DailyRecallUiState.SummaryScoreCard(
                totalPrimaryQuestions = totalPrimary,
                primaryCorrectCount = primaryCorrectCount,
                accuracyPercentage = accuracy,
                retriedCount = retryQueue.size,
                retryRecoveredCount = retryCorrectCount,
                mistakes = recordedMistakes.toList(),
                attemptId = attemptId,
                earnedBaseXp = earnedBaseXp,
                bonusXp = gamificationResult?.bonusXp ?: 0,
                totalXpAwarded = gamificationResult?.totalAwardedXp ?: earnedBaseXp,
                isDailyDoubleTriggered = gamificationResult?.isDailyDoubleTriggered ?: false,
                isNextChallengeAvailable = isNextChallengeAvailable,
                nextChallengeTitle = "Word Vault Quiz",
                nextChallengeXp = 100
            )

            // Clear session cache upon full session completion
            quizSessionCacheManager?.clearDailyRecallSession()
        }
    }

    /**
     * Smart Daily 10-Question Composition Engine:
     * - Slot 1–8 (Primary): Questions generated from Today's Snipped Passages (createdAt >= todayStart)
     * - Slot 9–10 (Spaced Review): 2 persistent questions sampled from Mistake Bank (isFlaggedForSpacedReview == true)
     * - Graceful Backfill: If today's snips < 8, backfill from Mistake Bank first, then from unattempted older chapter bank questions
     */
    private fun sampleDailyQuestions(all: List<McqQuestion>, targetCount: Int): List<McqQuestion> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + 86400000L - 1L

        val todayQuestions = all.filter { it.createdAt in startOfDay..endOfDay }.shuffled()
        val flaggedMistakes = all.filter { it.isFlaggedForSpacedReview }.sortedByDescending { it.lastFailedTimestamp }
        val olderQuestions = (all - todayQuestions.toSet() - flaggedMistakes.toSet()).shuffled()

        val selectedList = mutableListOf<McqQuestion>()
        val usedIds = mutableSetOf<Long>()

        // 1. Slot 1-8: Up to 8 questions from Today's Snipped Passages
        val primaryToday = todayQuestions.take(8)
        for (q in primaryToday) {
            selectedList.add(q)
            usedIds.add(q.id)
        }

        // 2. Slot 9-10: Up to 2 questions from Mistake Bank for Spaced Review
        val spacedMistakes = flaggedMistakes.filter { it.id !in usedIds }.take(2)
        for (q in spacedMistakes) {
            selectedList.add(q)
            usedIds.add(q.id)
        }

        // 3. Graceful Backfill Step 1: If fewer than targetCount, backfill from remaining Mistake Bank
        if (selectedList.size < targetCount) {
            val remainingMistakes = flaggedMistakes.filter { it.id !in usedIds }
            for (q in remainingMistakes) {
                if (selectedList.size >= targetCount) break
                selectedList.add(q)
                usedIds.add(q.id)
            }
        }

        // 4. Graceful Backfill Step 2: If still fewer than targetCount, backfill from older chapter bank questions
        if (selectedList.size < targetCount) {
            val remainingOlder = olderQuestions.filter { it.id !in usedIds }
            for (q in remainingOlder) {
                if (selectedList.size >= targetCount) break
                selectedList.add(q)
                usedIds.add(q.id)
            }
        }

        // 5. Graceful Backfill Step 3: If still fewer than targetCount, take any surplus today's questions
        if (selectedList.size < targetCount) {
            val surplusToday = todayQuestions.filter { it.id !in usedIds }
            for (q in surplusToday) {
                if (selectedList.size >= targetCount) break
                selectedList.add(q)
                usedIds.add(q.id)
            }
        }

        return selectedList
    }

    private fun createQuizQuestionState(question: McqQuestion, isRetry: Boolean): QuizQuestionState {
        val correctKey = question.correctOption.trim().uppercase()

        val rawOptions = listOf(
            QuizOption(id = "A", text = question.optionA, isCorrect = correctKey == "A"),
            QuizOption(id = "B", text = question.optionB, isCorrect = correctKey == "B"),
            QuizOption(id = "C", text = question.optionC, isCorrect = correctKey == "C"),
            QuizOption(id = "D", text = question.optionD, isCorrect = correctKey == "D")
        )

        // On retry round, STRICTLY SHUFFLE the sequence of options so user cannot rely on muscle memory!
        val finalOptions = if (isRetry) {
            rawOptions.shuffled()
        } else {
            rawOptions
        }

        val bookTitle = booksMap[question.bookId]?.title.orEmpty()

        return QuizQuestionState(
            question = question,
            bookTitle = bookTitle,
            chapterTitle = "",
            options = finalOptions,
            isRetry = isRetry
        )
    }
}
