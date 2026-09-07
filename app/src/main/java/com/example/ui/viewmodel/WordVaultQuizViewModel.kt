package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.WordQuizAttempt
import com.example.data.local.database.model.WordMistakeRecord
import com.example.data.manager.CachedWordVaultSession
import com.example.data.manager.QuizSessionCacheManager
import com.example.data.manager.WordQuizAvailability
import com.example.data.manager.WordQuizQuestion
import com.example.data.manager.WordVaultQuizEngine
import com.example.data.repository.McqRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.WordQuizRepository
import com.example.data.repository.WordVaultRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed interface WordVaultQuizUiState {
    data object Loading : WordVaultQuizUiState

    data class InsufficientWords(
        val currentCount: Int,
        val requiredCount: Int = 4,
        val message: String = "Save at least 4 words in your vault to unlock Word Quizzes"
    ) : WordVaultQuizUiState

    data class ResumePrompt(
        val cachedSession: CachedWordVaultSession,
        val currentQuestionIndex: Int,
        val totalQuestions: Int
    ) : WordVaultQuizUiState

    data class ActiveQuiz(
        val currentQuestion: WordQuizQuestion,
        val currentIndex: Int,
        val totalQuestionsInPrimary: Int,
        val isRetryRound: Boolean = false,
        val currentRetryIndex: Int = 0,
        val totalRetryQuestions: Int = 0,
        val selectedOptionId: String? = null,
        val isAnswerSubmitted: Boolean = false,
        val isCorrect: Boolean = false,
        val correctOptionId: String = "",
        val correctMeaningText: String = "",
        val romanUrduExplanation: String = "",
        val primaryCorrectCount: Int = 0,
        val primaryAnsweredCount: Int = 0,
        val retryCorrectCount: Int = 0,
        val progressPercent: Float = 0f
    ) : WordVaultQuizUiState

    data class SummaryScoreCard(
        val totalPrimaryQuestions: Int,
        val primaryCorrectCount: Int,
        val accuracyPercentage: Float,
        val retriedCount: Int,
        val retryRecoveredCount: Int,
        val mistakes: List<WordMistakeRecord>,
        val attemptId: Long = 0L,
        val earnedBaseXp: Int = 0,
        val bonusXp: Int = 0,
        val totalXpAwarded: Int = 0,
        val isDailyDoubleTriggered: Boolean = false,
        val isNextChallengeAvailable: Boolean = false,
        val nextChallengeTitle: String = "Daily Recall Quiz",
        val nextChallengeXp: Int = 100
    ) : WordVaultQuizUiState
}

class WordVaultQuizViewModel(
    private val wordVaultRepository: WordVaultRepository,
    private val wordVaultQuizEngine: WordVaultQuizEngine,
    private val userGamificationRepository: UserGamificationRepository,
    private val wordQuizRepository: WordQuizRepository,
    private val mcqRepository: McqRepository? = null,
    private val quizSessionCacheManager: QuizSessionCacheManager? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    companion object {
        private const val TAG = "WordVaultQuizViewModel"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val wordMistakeAdapter: JsonAdapter<List<WordMistakeRecord>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, WordMistakeRecord::class.java))

    private val _uiState = MutableStateFlow<WordVaultQuizUiState>(WordVaultQuizUiState.Loading)
    val uiState: StateFlow<WordVaultQuizUiState> = _uiState.asStateFlow()

    private val primaryQueue = mutableListOf<WordQuizQuestion>()
    private val retryQueue = mutableListOf<WordQuizQuestion>()
    private val recordedMistakes = mutableListOf<WordMistakeRecord>()

    private var currentPrimaryIndex = 0
    private var currentRetryIndex = 0
    private var isCurrentlyInRetryRound = false

    private var primaryCorrectCount = 0
    private var primaryAnsweredCount = 0
    private var retryCorrectCount = 0

    init {
        startQuizSession()
    }

    fun startQuizSession() {
        viewModelScope.launch(dispatcher) {
            _uiState.value = WordVaultQuizUiState.Loading

            val cached = quizSessionCacheManager?.getWordVaultSession()
            if (cached != null && cached.questions.isNotEmpty()) {
                val resumeQNumber = if (cached.isRetryRound) {
                    cached.questions.size + cached.currentRetryIndex + 1
                } else {
                    cached.currentIndex + 1
                }.coerceAtMost(cached.questions.size)

                _uiState.value = WordVaultQuizUiState.ResumePrompt(
                    cachedSession = cached,
                    currentQuestionIndex = resumeQNumber,
                    totalQuestions = cached.questions.size
                )
                return@launch
            }

            loadFreshQuizSession()
        }
    }

    fun startFreshSession() {
        quizSessionCacheManager?.clearWordVaultSession()
        viewModelScope.launch(dispatcher) {
            loadFreshQuizSession()
        }
    }

    fun resumeSavedSession(cached: CachedWordVaultSession) {
        viewModelScope.launch(dispatcher) {
            primaryQueue.clear()
            primaryQueue.addAll(cached.questions)

            retryQueue.clear()
            retryQueue.addAll(cached.retryQuestions)

            recordedMistakes.clear()
            recordedMistakes.addAll(cached.recordedMistakes)

            currentPrimaryIndex = cached.currentIndex.coerceIn(0, (primaryQueue.size - 1).coerceAtLeast(0))
            currentRetryIndex = cached.currentRetryIndex.coerceIn(0, (retryQueue.size - 1).coerceAtLeast(0))
            isCurrentlyInRetryRound = cached.isRetryRound && retryQueue.isNotEmpty() && currentRetryIndex < retryQueue.size

            primaryCorrectCount = cached.primaryCorrectCount
            primaryAnsweredCount = cached.primaryAnsweredCount
            retryCorrectCount = cached.retryCorrectCount

            showCurrentQuestion()
        }
    }

    fun saveAndExit(onSaved: () -> Unit = {}) {
        val currentState = _uiState.value as? WordVaultQuizUiState.ActiveQuiz
        if (currentState != null && primaryQueue.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val session = CachedWordVaultSession(
                questions = primaryQueue.toList(),
                currentIndex = currentPrimaryIndex,
                isRetryRound = isCurrentlyInRetryRound,
                retryQuestions = retryQueue.toList(),
                currentRetryIndex = currentRetryIndex,
                primaryCorrectCount = primaryCorrectCount,
                primaryAnsweredCount = primaryAnsweredCount,
                retryCorrectCount = retryCorrectCount,
                recordedMistakes = recordedMistakes.toList(),
                savedAtTimestamp = now,
                expiresAt = now + QuizSessionCacheManager.EXPIRATION_DURATION_MS
            )
            quizSessionCacheManager?.saveWordVaultSession(session)
        }
        onSaved()
    }

    private suspend fun loadFreshQuizSession() {
        _uiState.value = WordVaultQuizUiState.Loading
        primaryQueue.clear()
        retryQueue.clear()
        recordedMistakes.clear()
        currentPrimaryIndex = 0
        currentRetryIndex = 0
        isCurrentlyInRetryRound = false
        primaryCorrectCount = 0
        primaryAnsweredCount = 0
        retryCorrectCount = 0

        val allWords = wordVaultRepository.allWords.firstOrNull().orEmpty()
        val availability = wordVaultQuizEngine.checkAvailability(allWords)

        if (availability is WordQuizAvailability.InsufficientWords) {
            _uiState.value = WordVaultQuizUiState.InsufficientWords(
                currentCount = availability.currentCount,
                requiredCount = availability.requiredCount,
                message = availability.message
            )
            return
        }

        val sessionQuestions = wordVaultQuizEngine.generateQuizSession(allWords, 10)
        if (sessionQuestions.isEmpty()) {
            _uiState.value = WordVaultQuizUiState.InsufficientWords(
                currentCount = allWords.size,
                requiredCount = WordVaultQuizEngine.REQUIRED_WORDS_MINIMUM
            )
            return
        }

        primaryQueue.addAll(sessionQuestions)
        showCurrentQuestion()
    }

    fun selectOption(optionId: String) {
        val currentState = _uiState.value as? WordVaultQuizUiState.ActiveQuiz ?: return
        if (currentState.isAnswerSubmitted) return
        _uiState.value = currentState.copy(selectedOptionId = optionId)
    }

    fun submitAnswer() {
        val currentState = _uiState.value as? WordVaultQuizUiState.ActiveQuiz ?: return
        if (currentState.isAnswerSubmitted || currentState.selectedOptionId == null) return

        val question = currentState.currentQuestion
        val selectedOption = question.options.firstOrNull { it.id == currentState.selectedOptionId }
        val correctOption = question.options.firstOrNull { it.isCorrect }

        val isCorrect = selectedOption?.isCorrect == true
        val correctOptionId = correctOption?.id.orEmpty()
        val correctMeaning = question.correctMeaning
        val romanUrduExplanation = question.romanUrduExplanation

        if (!isCurrentlyInRetryRound) {
            primaryAnsweredCount++
            if (isCorrect) {
                primaryCorrectCount++
            } else {
                // Enqueue for reshuffled retry round
                val reshuffled = wordVaultQuizEngine.reshuffleQuestionForRetry(question)
                retryQueue.add(reshuffled)

                recordedMistakes.add(
                    WordMistakeRecord(
                        wordId = question.wordId,
                        word = question.word,
                        originalSentence = question.originalSentence,
                        userSelectedOptionText = selectedOption?.text.orEmpty(),
                        correctOptionText = correctMeaning,
                        romanUrduExplanation = romanUrduExplanation,
                        resolvedInRetry = false
                    )
                )
            }
        } else {
            // In Retry round
            if (isCorrect) {
                retryCorrectCount++
                val mistakeIndex = recordedMistakes.indexOfFirst { it.wordId == question.wordId }
                if (mistakeIndex != -1) {
                    recordedMistakes[mistakeIndex] = recordedMistakes[mistakeIndex].copy(resolvedInRetry = true)
                }
            }
        }

        _uiState.value = currentState.copy(
            isAnswerSubmitted = true,
            isCorrect = isCorrect,
            correctOptionId = correctOptionId,
            correctMeaningText = correctMeaning,
            romanUrduExplanation = romanUrduExplanation,
            primaryCorrectCount = primaryCorrectCount,
            primaryAnsweredCount = primaryAnsweredCount,
            retryCorrectCount = retryCorrectCount
        )
    }

    fun moveToNextQuestion() {
        if (!isCurrentlyInRetryRound) {
            currentPrimaryIndex++
            if (currentPrimaryIndex < primaryQueue.size) {
                showCurrentQuestion()
            } else {
                // Primary round finished. Check if retry round is needed.
                if (retryQueue.isNotEmpty()) {
                    isCurrentlyInRetryRound = true
                    currentRetryIndex = 0
                    showCurrentQuestion()
                } else {
                    finishAndPersistQuiz()
                }
            }
        } else {
            currentRetryIndex++
            if (currentRetryIndex < retryQueue.size) {
                showCurrentQuestion()
            } else {
                finishAndPersistQuiz()
            }
        }
    }

    private fun showCurrentQuestion() {
        val totalPrimary = primaryQueue.size
        val (question, progress) = if (!isCurrentlyInRetryRound) {
            val q = primaryQueue[currentPrimaryIndex]
            val prog = if (totalPrimary > 0) currentPrimaryIndex.toFloat() / totalPrimary.toFloat() else 0f
            Pair(q, prog)
        } else {
            val q = retryQueue[currentRetryIndex]
            val totalRetry = retryQueue.size
            val prog = if (totalRetry > 0) currentRetryIndex.toFloat() / totalRetry.toFloat() else 0f
            Pair(q, prog)
        }

        _uiState.value = WordVaultQuizUiState.ActiveQuiz(
            currentQuestion = question,
            currentIndex = currentPrimaryIndex,
            totalQuestionsInPrimary = totalPrimary,
            isRetryRound = isCurrentlyInRetryRound,
            currentRetryIndex = currentRetryIndex,
            totalRetryQuestions = retryQueue.size,
            selectedOptionId = null,
            isAnswerSubmitted = false,
            isCorrect = false,
            correctOptionId = "",
            correctMeaningText = "",
            romanUrduExplanation = "",
            primaryCorrectCount = primaryCorrectCount,
            primaryAnsweredCount = primaryAnsweredCount,
            retryCorrectCount = retryCorrectCount,
            progressPercent = progress
        )
    }

    private fun finishAndPersistQuiz() {
        viewModelScope.launch(dispatcher) {
            val totalPrimary = primaryQueue.size.coerceAtLeast(1)
            val accuracy = (primaryCorrectCount.toFloat() / totalPrimary.toFloat()) * 100f

            val wrongAnswersJson = try {
                if (recordedMistakes.isNotEmpty()) {
                    wordMistakeAdapter.toJson(recordedMistakes)
                } else null
            } catch (e: Exception) {
                null
            }

            val earnedBaseXp = (primaryCorrectCount * 10) + (retryCorrectCount * 5)

            val attemptRecord = WordQuizAttempt(
                id = 0L,
                attemptDate = System.currentTimeMillis(),
                totalQuestions = totalPrimary,
                correctAnswers = primaryCorrectCount,
                recoveredCount = retryCorrectCount,
                xpEarned = earnedBaseXp,
                scorePercentage = accuracy,
                wrongAnswersJson = wrongAnswersJson,
                createdAt = System.currentTimeMillis()
            )

            val attemptId = try {
                wordQuizRepository.recordAttempt(attemptRecord)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist Word MCQ attempt: ${e.message}")
                0L
            }

            // Dual-Fail Mistake Tracking for Word Vault:
            val now = System.currentTimeMillis()
            val unresolvedWordMistakes = recordedMistakes.filter { !it.resolvedInRetry }
            for (mistake in unresolvedWordMistakes) {
                try {
                    wordVaultRepository.updateWordMistakeStatus(
                        wordId = mistake.wordId,
                        isFlagged = true,
                        incrementMistake = 1,
                        timestamp = now
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update word mistake status: ${e.message}")
                }
            }

            val cleanCorrectWordIds = primaryQueue.map { it.wordId }.toSet() - recordedMistakes.map { it.wordId }.toSet()
            for (wId in cleanCorrectWordIds) {
                try {
                    wordVaultRepository.resolveWordMistake(wId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve word mistake: ${e.message}")
                }
            }

            val gamificationResult = try {
                userGamificationRepository.recordWordQuizCompleted(earnedBaseXp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record gamification stats: ${e.message}")
                null
            }

            val updatedStats = try {
                userGamificationRepository.getStats()
            } catch (e: Exception) {
                null
            }

            val allMcqs = mcqRepository?.getAllQuestions().orEmpty()
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val allAttempts = mcqRepository?.getAllAttempts().orEmpty()
            val todayAttempt = allAttempts.firstOrNull { it.attemptDate >= startOfDay }
            val unattemptedMcqs = if (todayAttempt != null) {
                allMcqs.filter { it.createdAt > todayAttempt.attemptDate }
            } else {
                allMcqs
            }
            val isNextChallengeAvailable = (unattemptedMcqs.size >= 10) && (updatedStats?.todaySnippetQuizCompleted != true)

            _uiState.value = WordVaultQuizUiState.SummaryScoreCard(
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
                nextChallengeTitle = "Daily Recall Quiz",
                nextChallengeXp = 100
            )

            // Clear session cache upon full session completion
            quizSessionCacheManager?.clearWordVaultSession()
        }
    }
}
