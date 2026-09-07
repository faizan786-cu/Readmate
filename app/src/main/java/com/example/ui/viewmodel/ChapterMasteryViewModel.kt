package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.model.MistakeRecord
import com.example.data.manager.CachedChapterMasterySession
import com.example.data.manager.QuizSessionCacheManager
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.McqRepository
import com.example.data.repository.UserGamificationRepository
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

sealed interface ChapterMasteryUiState {
    data object Loading : ChapterMasteryUiState

    data class Empty(
        val message: String = "No MCQs available for this chapter yet. Snip passages and generate AI explanations first!"
    ) : ChapterMasteryUiState

    data class ResumePrompt(
        val cachedSession: CachedChapterMasterySession,
        val currentQuestionIndex: Int,
        val totalQuestions: Int
    ) : ChapterMasteryUiState

    data class ActiveQuiz(
        val chapterTitle: String,
        val bookTitle: String,
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
    ) : ChapterMasteryUiState

    data class SummaryScoreCard(
        val chapterTitle: String,
        val bookTitle: String,
        val totalPrimaryQuestions: Int,
        val primaryCorrectCount: Int,
        val accuracyPercentage: Float,
        val retriedCount: Int,
        val retryRecoveredCount: Int,
        val mistakes: List<MistakeRecord>,
        val attemptId: Long = 0L,
        val earnedBaseXp: Int = 0,
        val bonusMasteryXp: Int = 0,
        val totalXpAwarded: Int = 0,
        val isMastered: Boolean = false,
        val nextChapterId: Long? = null,
        val nextChapterTitle: String? = null
    ) : ChapterMasteryUiState
}

class ChapterMasteryViewModel(
    savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository,
    private val bookRepository: BookRepository,
    private val mcqRepository: McqRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val quizSessionCacheManager: QuizSessionCacheManager? = null,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : ViewModel() {

    companion object {
        private const val TAG = "ChapterMasteryVM"
        const val GRAND_MASTERY_TARGET_SIZE = 50
        const val MASTERY_BONUS_XP = 250
    }

    val chapterId: Long = savedStateHandle.get<Long>("chapterId") ?: -1L

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mistakeListAdapter: JsonAdapter<List<MistakeRecord>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, MistakeRecord::class.java))

    private val _uiState = MutableStateFlow<ChapterMasteryUiState>(ChapterMasteryUiState.Loading)
    val uiState: StateFlow<ChapterMasteryUiState> = _uiState.asStateFlow()

    private var targetChapter: Chapter? = null
    private var targetBook: Book? = null

    // Session queues
    private var primaryQueue: List<QuizQuestionState> = emptyList()
    private var primaryIndex = 0

    private val retryQueue: MutableList<McqQuestion> = mutableListOf()
    private var retryIndex = 0

    private var primaryCorrectCount = 0
    private var primaryAnsweredCount = 0
    private var retryCorrectCount = 0

    private val recordedMistakes = mutableListOf<MistakeRecord>()

    init {
        loadChapterMasterySession()
    }

    fun loadChapterMasterySession() {
        viewModelScope.launch(dispatcher) {
            _uiState.value = ChapterMasteryUiState.Loading

            try {
                if (chapterId <= 0) {
                    _uiState.value = ChapterMasteryUiState.Empty("Invalid chapter selected.")
                    return@launch
                }

                val chapter = chapterRepository.getChapter(chapterId)
                if (chapter == null) {
                    _uiState.value = ChapterMasteryUiState.Empty("Chapter not found.")
                    return@launch
                }
                targetChapter = chapter

                val book = bookRepository.getBook(chapter.bookId)
                targetBook = book

                // Check for saved session cache (<24h)
                val cached = quizSessionCacheManager?.getChapterMasterySession(chapterId)
                if (cached != null && cached.primaryQuestionIds.isNotEmpty()) {
                    val allChapterQuestions = mcqRepository.getQuestionsForChapter(chapterId)
                    val allQuestionsMap = allChapterQuestions.associateBy { it.id }
                    val hasAllQuestions = cached.primaryQuestionIds.all { allQuestionsMap.containsKey(it) }
                    if (hasAllQuestions) {
                        val resumeQNumber = if (cached.isRetryRound) {
                            cached.primaryQuestionIds.size + cached.retryIndex + 1
                        } else {
                            cached.primaryIndex + 1
                        }.coerceAtMost(cached.primaryQuestionIds.size)

                        _uiState.value = ChapterMasteryUiState.ResumePrompt(
                            cachedSession = cached,
                            currentQuestionIndex = resumeQNumber,
                            totalQuestions = cached.primaryQuestionIds.size
                        )
                        return@launch
                    }
                }

                loadFreshChapterMasterySession()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter mastery quiz: ${e.message}", e)
                _uiState.value = ChapterMasteryUiState.Empty(
                    "Could not load mastery quiz: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun startFreshSession() {
        quizSessionCacheManager?.clearChapterMasterySession(chapterId)
        viewModelScope.launch(dispatcher) {
            loadFreshChapterMasterySession()
        }
    }

    fun resumeSavedSession(cached: CachedChapterMasterySession) {
        viewModelScope.launch(dispatcher) {
            _uiState.value = ChapterMasteryUiState.Loading
            try {
                val chapter = chapterRepository.getChapter(chapterId) ?: return@launch
                targetChapter = chapter
                targetBook = bookRepository.getBook(chapter.bookId)

                val allChapterQuestions = mcqRepository.getQuestionsForChapter(chapterId)
                val allQuestionsMap = allChapterQuestions.associateBy { it.id }

                val primaryQuestions = cached.primaryQuestionIds.mapNotNull { allQuestionsMap[it] }
                if (primaryQuestions.isEmpty()) {
                    loadFreshChapterMasterySession()
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

                _uiState.value = ChapterMasteryUiState.ActiveQuiz(
                    chapterTitle = chapter.title,
                    bookTitle = targetBook?.title.orEmpty(),
                    currentQuestion = currentQState,
                    currentIndex = primaryIndex,
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
                Log.e(TAG, "Error resuming chapter mastery quiz: ${e.message}", e)
                loadFreshChapterMasterySession()
            }
        }
    }

    fun saveAndExit(onSaved: () -> Unit = {}) {
        val currentState = _uiState.value as? ChapterMasteryUiState.ActiveQuiz
        if (currentState != null && primaryQueue.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val session = CachedChapterMasterySession(
                chapterId = chapterId,
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
            quizSessionCacheManager?.saveChapterMasterySession(session)
        }
        onSaved()
    }

    private suspend fun loadFreshChapterMasterySession() {
        _uiState.value = ChapterMasteryUiState.Loading

        try {
            val chapter = targetChapter ?: chapterRepository.getChapter(chapterId)
            if (chapter == null) {
                _uiState.value = ChapterMasteryUiState.Empty("Chapter not found.")
                return
            }
            targetChapter = chapter

            val book = targetBook ?: bookRepository.getBook(chapter.bookId)
            targetBook = book

            val allChapterQuestions = mcqRepository.getQuestionsForChapter(chapterId)
            if (allChapterQuestions.isEmpty()) {
                _uiState.value = ChapterMasteryUiState.Empty(
                    "No MCQs found for '${chapter.title}'. Read passages and ask for explanations to generate MCQs first!"
                )
                return
            }

            // Sample up to 50 questions prioritizing Mistake Bank
            val selectedQuestions = composeMasteryQuestions(allChapterQuestions, GRAND_MASTERY_TARGET_SIZE)

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
            _uiState.value = ChapterMasteryUiState.ActiveQuiz(
                chapterTitle = chapter.title,
                bookTitle = book?.title.orEmpty(),
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
            Log.e(TAG, "Error loading fresh chapter mastery quiz: ${e.message}", e)
            _uiState.value = ChapterMasteryUiState.Empty(
                "Could not load mastery quiz: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    fun selectOption(optionId: String) {
        val currentState = _uiState.value as? ChapterMasteryUiState.ActiveQuiz ?: return
        if (currentState.isAnswerSubmitted) return

        _uiState.update { state ->
            if (state is ChapterMasteryUiState.ActiveQuiz) {
                state.copy(selectedOptionId = optionId)
            } else state
        }
    }

    fun submitAnswer() {
        val currentState = _uiState.value as? ChapterMasteryUiState.ActiveQuiz ?: return
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
                recordedMistakes.add(
                    MistakeRecord(
                        questionId = currentQ.question.id,
                        questionText = currentQ.question.questionText,
                        userSelectedOptionText = chosenOption?.text.orEmpty(),
                        correctOptionText = correctOption.text,
                        explanation = currentQ.question.explanation,
                        resolvedInRetry = false
                    )
                )
                retryQueue.add(currentQ.question)
            }

            val progress = (primaryAnsweredCount.toFloat() / primaryQueue.size.toFloat()).coerceIn(0f, 1f)
            _uiState.update { state ->
                if (state is ChapterMasteryUiState.ActiveQuiz) {
                    state.copy(
                        isAnswerSubmitted = true,
                        feedback = feedback,
                        primaryCorrectCount = primaryCorrectCount,
                        primaryAnsweredCount = primaryAnsweredCount,
                        progressPercent = progress
                    )
                } else state
            }
        } else {
            // Retry Round
            val currentRetryQ = currentState.currentQuestion.question
            if (isCorrect) {
                retryCorrectCount++
                val mIdx = recordedMistakes.indexOfFirst { it.questionId == currentRetryQ.id }
                if (mIdx != -1) {
                    val orig = recordedMistakes[mIdx]
                    recordedMistakes[mIdx] = orig.copy(resolvedInRetry = true)
                }
            }

            val answeredRetries = retryIndex + 1
            val retryProgress = (answeredRetries.toFloat() / retryQueue.size.toFloat()).coerceIn(0f, 1f)

            _uiState.update { state ->
                if (state is ChapterMasteryUiState.ActiveQuiz) {
                    state.copy(
                        isAnswerSubmitted = true,
                        feedback = feedback,
                        retryCorrectCount = retryCorrectCount,
                        progressPercent = retryProgress
                    )
                } else state
            }
        }
    }

    fun nextQuestion() {
        val currentState = _uiState.value as? ChapterMasteryUiState.ActiveQuiz ?: return
        if (!currentState.isAnswerSubmitted) return

        if (!currentState.isRetryRound) {
            primaryIndex++
            if (primaryIndex < primaryQueue.size) {
                val nextQ = primaryQueue[primaryIndex]
                val progress = (primaryIndex.toFloat() / primaryQueue.size.toFloat()).coerceIn(0f, 1f)
                _uiState.update {
                    ChapterMasteryUiState.ActiveQuiz(
                        chapterTitle = targetChapter?.title.orEmpty(),
                        bookTitle = targetBook?.title.orEmpty(),
                        currentQuestion = nextQ,
                        currentIndex = primaryIndex,
                        totalQuestionsInPrimary = primaryQueue.size,
                        isRetryRound = false,
                        currentRetryIndex = 0,
                        totalRetryQuestions = 0,
                        selectedOptionId = null,
                        isAnswerSubmitted = false,
                        feedback = null,
                        primaryCorrectCount = primaryCorrectCount,
                        primaryAnsweredCount = primaryAnsweredCount,
                        retryCorrectCount = 0,
                        progressPercent = progress
                    )
                }
            } else {
                // Primary round done! Check if mistakes exist for Retry Loop
                if (retryQueue.isNotEmpty()) {
                    retryIndex = 0
                    val firstRetryQuestion = createQuizQuestionState(retryQueue[0], isRetry = true)
                    _uiState.update {
                        ChapterMasteryUiState.ActiveQuiz(
                            chapterTitle = targetChapter?.title.orEmpty(),
                            bookTitle = targetBook?.title.orEmpty(),
                            currentQuestion = firstRetryQuestion,
                            currentIndex = primaryIndex,
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
                            progressPercent = 0f
                        )
                    }
                } else {
                    finishAndPersistMasteryQuiz()
                }
            }
        } else {
            // Retry Round Next Question
            retryIndex++
            if (retryIndex < retryQueue.size) {
                val nextRetryQ = createQuizQuestionState(retryQueue[retryIndex], isRetry = true)
                val progress = (retryIndex.toFloat() / retryQueue.size.toFloat()).coerceIn(0f, 1f)
                _uiState.update {
                    ChapterMasteryUiState.ActiveQuiz(
                        chapterTitle = targetChapter?.title.orEmpty(),
                        bookTitle = targetBook?.title.orEmpty(),
                        currentQuestion = nextRetryQ,
                        currentIndex = primaryIndex,
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
                finishAndPersistMasteryQuiz()
            }
        }
    }

    private fun finishAndPersistMasteryQuiz() {
        viewModelScope.launch(dispatcher) {
            val totalPrimary = primaryQueue.size.coerceAtLeast(1)
            val accuracy = (primaryCorrectCount.toFloat() / totalPrimary.toFloat()) * 100f
            val scorePercentageInt = accuracy.toInt()
            val isMastered = accuracy >= 80f
            val now = System.currentTimeMillis()

            try {
                chapterRepository.updateMasteryScore(
                    chapterId = chapterId,
                    score = scorePercentageInt,
                    isMastered = isMastered,
                    masteredAt = now
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist chapter mastery score in Chapter entity: ${e.message}")
            }

            val wrongAnswersJson = try {
                if (recordedMistakes.isNotEmpty()) {
                    mistakeListAdapter.toJson(recordedMistakes)
                } else null
            } catch (e: Exception) {
                null
            }

            val attemptRecord = UserMcqAttempt(
                id = 0L,
                bookId = targetChapter?.bookId,
                chapterId = chapterId,
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
                Log.w(TAG, "Failed to persist Chapter Mastery attempt: ${e.message}")
                0L
            }

            // Dual-Fail Mistake Tracking
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
                    Log.w(TAG, "Failed to flag mistake for qId ${mistake.questionId}: ${e.message}")
                }
            }

            // Clean correct questions: resolve if previously flagged
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

            // Award XP: +5 per correct, +250 Grand Mastery completion bonus
            val earnedBaseXp = (primaryCorrectCount * 5) + (retryCorrectCount * 2)
            val bonusMasteryXp = MASTERY_BONUS_XP
            val totalXpAwarded = earnedBaseXp + bonusMasteryXp

            try {
                userGamificationRepository.addXp(totalXpAwarded)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to award Mastery XP: ${e.message}")
            }

            var nextChapterId: Long? = null
            var nextChapterTitle: String? = null
            if (isMastered) {
                val bookId = targetChapter?.bookId
                if (bookId != null) {
                    try {
                        val bookChapters = chapterRepository.getChaptersForBookSync(bookId)
                        val coreChapters = bookChapters.filter {
                            com.example.data.model.ExtractedChapterSection.normalizeSectionType(it.sectionType, it.title) ==
                                com.example.data.model.ExtractedChapterSection.TYPE_CORE_CHAPTER
                        }.sortedWith(
                            compareBy<Chapter> { it.chapterNumber ?: Int.MAX_VALUE }
                                .thenBy { it.startPage }
                                .thenBy { it.id }
                        )
                        val currentIndex = coreChapters.indexOfFirst { it.id == chapterId }
                        if (currentIndex in 0 until coreChapters.lastIndex) {
                            val nextChap = coreChapters[currentIndex + 1]
                            nextChapterId = nextChap.id
                            nextChapterTitle = if (nextChap.chapterNumber != null) {
                                "Chapter ${nextChap.chapterNumber}: ${nextChap.title}"
                            } else {
                                nextChap.title
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve next chapter: ${e.message}")
                    }
                }
            }

            _uiState.value = ChapterMasteryUiState.SummaryScoreCard(
                chapterTitle = targetChapter?.title.orEmpty(),
                bookTitle = targetBook?.title.orEmpty(),
                totalPrimaryQuestions = totalPrimary,
                primaryCorrectCount = primaryCorrectCount,
                accuracyPercentage = accuracy,
                retriedCount = retryQueue.size,
                retryRecoveredCount = retryCorrectCount,
                mistakes = recordedMistakes.toList(),
                attemptId = attemptId,
                earnedBaseXp = earnedBaseXp,
                bonusMasteryXp = bonusMasteryXp,
                totalXpAwarded = totalXpAwarded,
                isMastered = isMastered,
                nextChapterId = nextChapterId,
                nextChapterTitle = nextChapterTitle
            )

            // Clear session cache upon full session completion
            quizSessionCacheManager?.clearChapterMasterySession(chapterId)
        }
    }

    /**
     * Composition Engine for Chapter Grand Mastery:
     * Samples up to targetCount (50) MCQs specifically from this chapter:
     * 1. Mistake Bank questions from this chapter first (isFlaggedForSpacedReview == true)
     * 2. All remaining chapter questions
     */
    private fun composeMasteryQuestions(chapterQuestions: List<McqQuestion>, targetCount: Int): List<McqQuestion> {
        val flaggedMistakes = chapterQuestions.filter { it.isFlaggedForSpacedReview }.sortedByDescending { it.lastFailedTimestamp }
        val otherQuestions = (chapterQuestions - flaggedMistakes.toSet()).shuffled()

        val selectedList = mutableListOf<McqQuestion>()
        val usedIds = mutableSetOf<Long>()

        // 1. Prioritize Mistake Bank questions
        for (q in flaggedMistakes) {
            if (selectedList.size >= targetCount) break
            selectedList.add(q)
            usedIds.add(q.id)
        }

        // 2. Backfill with remaining chapter questions
        for (q in otherQuestions) {
            if (selectedList.size >= targetCount) break
            selectedList.add(q)
            usedIds.add(q.id)
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

        val finalOptions = if (isRetry) rawOptions.shuffled() else rawOptions

        return QuizQuestionState(
            question = question,
            bookTitle = targetBook?.title.orEmpty(),
            chapterTitle = targetChapter?.title.orEmpty(),
            options = finalOptions,
            isRetry = isRetry
        )
    }
}
