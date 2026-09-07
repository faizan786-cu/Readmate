package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.manager.DailyRetentionManager
import com.example.data.manager.SessionChallengeManager
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.RetentionStreakStatus
import com.example.data.local.database.entity.UserGamificationStats
import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.database.entity.WordQuizAttempt
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.McqRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.WisdomQuoteRepository
import com.example.data.repository.WordQuizRepository
import com.example.data.repository.WordVaultRepository
import com.example.data.util.EbbinghausRetentionCalculator
import com.example.ui.util.StreakEngine
import com.example.ui.util.StreakInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.util.ProgressCalculator
import java.util.Calendar

data class RecentActiveBookUi(
    val bookId: Long,
    val chapterId: Long,
    val title: String,
    val author: String?,
    val coverImageUrl: String?,
    val chapterTitle: String,
    val chapterNumber: Int,
    val progressPercent: Int, // Overall Book Progress % (Weighted Core Pages)
    val chapterProgressPercent: Int, // Chapter-level Progress %
    val chapterPillLabel: String, // Dynamic Pill Label: "Ch 1 • Not Started" / "Ch 1 • 7% Explored" / "Ch 1 • Mastered ✓"
    val lastReadPage: Int,
    val totalPages: Int,
    val hasStartedReading: Boolean = false,
    val totalSnipsCount: Int = 0
)

sealed interface ActiveDailyChallengeModal {
    data object None : ActiveDailyChallengeModal
    data class ChapterMasteryChallengeReady(
        val chapterId: Long,
        val chapterTitle: String,
        val bookTitle: String,
        val availableMcqCount: Int
    ) : ActiveDailyChallengeModal
    data class SnippetChallengeReady(val availableCount: Int) : ActiveDailyChallengeModal
    data class WordVaultChallengeReady(val availableCount: Int) : ActiveDailyChallengeModal
    data class DailyDoubleBonusReady(val totalXpEarned: Int) : ActiveDailyChallengeModal
}

sealed interface DashboardChallengePillUi {
    val title: String
    val subtitle: String?

    data class SnippetChallenge(
        val availableCount: Int,
        override val title: String = "⚡ 10 Snippet MCQs Ready • Tap to Start Challenge",
        override val subtitle: String? = null
    ) : DashboardChallengePillUi

    data class WordVaultChallenge(
        val availableCount: Int,
        override val title: String = "⚡ Word Vault Quiz Ready • Tap to Start Challenge",
        override val subtitle: String? = null
    ) : DashboardChallengePillUi

    data class ChapterMasteryChallenge(
        val chapterId: Long,
        val chapterTitle: String,
        val availableCount: Int,
        override val title: String = "🏆 $chapterTitle Mastery Ready • Tap to Start Challenge",
        override val subtitle: String? = null
    ) : DashboardChallengePillUi
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val streakInfo: StreakInfo,
        val readerRank: String,
        val totalXp: Int = 0,
        val todaySnippetCompleted: Boolean = false,
        val todayWordCompleted: Boolean = false,
        val recentActiveBook: RecentActiveBookUi?,
        val recentWisdomQuotes: List<WisdomQuote>,
        val totalInsightsCount: Int,
        val totalWisdomCount: Int,
        val totalWordsCount: Int,
        val activeBooksCount: Int = 0,
        val totalBooksCount: Int = 0,
        val totalMcqsCount: Int = 0,
        val unattemptedMcqsCount: Int = 0,
        val pendingQuizzesCount: Int = 0,
        val isCognitiveDebtActive: Boolean = false,
        val showDailyRecallCard: Boolean = false,
        val todayAttempt: UserMcqAttempt? = null,
        val todayAccuracy: Float? = null,
        val activeChallengeModal: ActiveDailyChallengeModal = ActiveDailyChallengeModal.None,
        val eligiblePillBanner: DashboardChallengePillUi? = null,
        val masteredChaptersCount: Int = 0,
        val pendingMistakesCount: Int = 0,
        val retentionRatePercent: Int? = null,
        val retentionRateDisplay: String = "--%",
        val dailyRetentionState: DailyRetentionStateEntity? = null,
        val readingTimestamps: List<Long> = emptyList(),
        val allRetentionStates: List<DailyRetentionStateEntity> = emptyList()
    ) : DashboardUiState
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class MessageStats(
    val timestamps: List<Long>,
    val totalMessages: Int,
    val latestMessage: ChapterMessage?
)

private data class VaultAndQuoteStats(
    val quotes: List<WisdomQuote>,
    val totalQuotes: Int,
    val totalWords: Int,
    val books: List<Book>,
    val allChapters: List<Chapter>
)

private data class McqStats(
    val allQuestions: List<McqQuestion>,
    val attempts: List<UserMcqAttempt>,
    val wordAttempts: List<WordQuizAttempt> = emptyList()
)

private data class ChallengeEvaluation(
    val todayAttempt: UserMcqAttempt?,
    val unattemptedCount: Int,
    val activeModal: ActiveDailyChallengeModal,
    val eligiblePill: DashboardChallengePillUi? = null,
    val masteredChaptersCount: Int = 0,
    val pendingMistakesCount: Int = 0
)

class DashboardViewModel(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterMessageRepository: ChapterMessageRepository,
    private val wisdomQuoteRepository: WisdomQuoteRepository,
    private val wordVaultRepository: WordVaultRepository,
    private val mcqRepository: McqRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val sessionChallengeManager: SessionChallengeManager = SessionChallengeManager(),
    private val dailyRetentionManager: DailyRetentionManager? = null,
    private val quizRepository: QuizRepository? = null,
    private val wordQuizRepository: WordQuizRepository? = null
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                quizRepository?.deleteOrphanedQuizzes()
            } catch (_: Exception) {}
        }
    }

    fun dismissChallengeModal() {
        val currentModal = (uiState.value as? DashboardUiState.Success)?.activeChallengeModal
        when (currentModal) {
            is ActiveDailyChallengeModal.ChapterMasteryChallengeReady -> {
                sessionChallengeManager.dismissMasteryChapter(currentModal.chapterId)
            }
            is ActiveDailyChallengeModal.SnippetChallengeReady -> {
                sessionChallengeManager.dismissSnippetChallenge()
            }
            is ActiveDailyChallengeModal.WordVaultChallengeReady -> {
                sessionChallengeManager.dismissWordVaultChallenge()
            }
            is ActiveDailyChallengeModal.DailyDoubleBonusReady -> {
                sessionChallengeManager.dismissDailyDoubleBonus()
            }
            else -> {}
        }
    }

    private val messageStatsFlow = combine(
        chapterMessageRepository.allMessageTimestamps,
        chapterMessageRepository.totalMessageCount,
        chapterMessageRepository.latestMessage
    ) { timestamps, totalCount, latest ->
        MessageStats(timestamps, totalCount, latest)
    }

    private val vaultAndQuoteStatsFlow = combine(
        wisdomQuoteRepository.observeAllQuotes(),
        wisdomQuoteRepository.observeTotalQuoteCount(),
        wordVaultRepository.totalWordCount,
        combine(
            bookRepository.allBooks,
            chapterRepository.observeAllChapters()
        ) { books, chapters -> Pair(books, chapters) }
    ) { quotes, totalQuotes, totalWords, (books, chapters) ->
        VaultAndQuoteStats(quotes, totalQuotes, totalWords, books, chapters)
    }

    private val wordAttemptsFlow: Flow<List<WordQuizAttempt>> =
        wordQuizRepository?.allAttempts ?: flowOf(emptyList())

    private val mcqStatsFlow = combine(
        mcqRepository.observeAllQuestions(),
        mcqRepository.observeAllAttempts(),
        wordAttemptsFlow
    ) { questions, attempts, wordAttempts ->
        McqStats(questions, attempts, wordAttempts)
    }

    private val activeChallengeFlow = combine(
        mcqStatsFlow,
        chapterRepository.observeCompletedChapters(),
        vaultAndQuoteStatsFlow,
        userGamificationRepository.userStats,
        combine(
            sessionChallengeManager.dismissedMasteryChapterIds,
            sessionChallengeManager.dismissedSnippetChallenge,
            sessionChallengeManager.dismissedWordVaultChallenge,
            sessionChallengeManager.dismissedDailyDoubleBonus
        ) { dChapters, dSnippet, dWord, dDouble ->
            Tuple4(dChapters, dSnippet, dWord, dDouble)
        }
    ) { mcqStats, completedChapters, vaultStats, userStats, dismissalTuple ->
        val (dismissedChapters, dismissedSnippet, dismissedWordVault, dismissedDoubleBonus) = dismissalTuple

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val todayAttempt = mcqStats.attempts.firstOrNull { it.attemptDate >= startOfDay }

        val unattemptedQuestions = if (todayAttempt != null) {
            mcqStats.allQuestions.filter { it.createdAt > todayAttempt.attemptDate }
        } else {
            mcqStats.allQuestions
        }
        val unattemptedCount = unattemptedQuestions.size

        val unattemptedMasteryChapter = completedChapters.firstOrNull { chapter ->
            val chapterMcqs = mcqStats.allQuestions.filter { it.chapterId == chapter.id }
            if (chapterMcqs.isEmpty()) return@firstOrNull false

            val completionTime = chapter.completedAt ?: 0L
            val hasMasteryAttemptAfterCompletion = mcqStats.attempts.any {
                it.chapterId == chapter.id && it.attemptDate >= completionTime
            }
            !hasMasteryAttemptAfterCompletion
        }

        val isSnippetQualifying = (unattemptedCount >= 10) && !userStats.todaySnippetQuizCompleted
        val isWordVaultQualifying = (vaultStats.totalWords >= 10) && !userStats.todayWordQuizCompleted

        var modal: ActiveDailyChallengeModal = ActiveDailyChallengeModal.None
        var pill: DashboardChallengePillUi? = null

        when {
            unattemptedMasteryChapter != null -> {
                val bookTitle = vaultStats.books.firstOrNull { it.id == unattemptedMasteryChapter.bookId }?.title.orEmpty()
                val chapterMcqCount = mcqStats.allQuestions.count { it.chapterId == unattemptedMasteryChapter.id }
                if (unattemptedMasteryChapter.id !in dismissedChapters) {
                    modal = ActiveDailyChallengeModal.ChapterMasteryChallengeReady(
                        chapterId = unattemptedMasteryChapter.id,
                        chapterTitle = unattemptedMasteryChapter.title,
                        bookTitle = bookTitle,
                        availableMcqCount = chapterMcqCount
                    )
                } else {
                    pill = DashboardChallengePillUi.ChapterMasteryChallenge(
                        chapterId = unattemptedMasteryChapter.id,
                        chapterTitle = unattemptedMasteryChapter.title,
                        availableCount = chapterMcqCount
                    )
                }
            }
            isSnippetQualifying -> {
                if (!dismissedSnippet) {
                    modal = ActiveDailyChallengeModal.SnippetChallengeReady(unattemptedCount)
                } else {
                    pill = DashboardChallengePillUi.SnippetChallenge(unattemptedCount)
                }
            }
            isWordVaultQualifying -> {
                if (!dismissedWordVault) {
                    modal = ActiveDailyChallengeModal.WordVaultChallengeReady(vaultStats.totalWords)
                } else {
                    pill = DashboardChallengePillUi.WordVaultChallenge(vaultStats.totalWords)
                }
            }
            userStats.todaySnippetQuizCompleted && userStats.todayWordQuizCompleted && !dismissedDoubleBonus -> {
                modal = ActiveDailyChallengeModal.DailyDoubleBonusReady(userStats.totalXp)
            }
        }

        val totalIncorrectAttempts = mcqStats.attempts.sumOf { (it.totalQuestions - it.correctAnswers).coerceAtLeast(0) }
        val pendingMistakes = if (totalIncorrectAttempts > 0) totalIncorrectAttempts else unattemptedCount

        ChallengeEvaluation(
            todayAttempt = todayAttempt,
            unattemptedCount = unattemptedCount,
            activeModal = modal,
            eligiblePill = pill,
            masteredChaptersCount = completedChapters.size,
            pendingMistakesCount = pendingMistakes
        )
    }

    private val dailyRetentionFlow: Flow<DailyRetentionStateEntity?> =
        dailyRetentionManager?.observeTodayRetentionState() ?: flowOf(null)

    private val allRetentionStatesFlow: Flow<List<DailyRetentionStateEntity>> =
        dailyRetentionManager?.observeAllRetentionStates() ?: flowOf(emptyList())

    private val retentionCombinedFlow: Flow<Pair<DailyRetentionStateEntity?, List<DailyRetentionStateEntity>>> =
        combine(dailyRetentionFlow, allRetentionStatesFlow) { today, all -> Pair(today, all) }

    private val pendingQuizzesFlow: Flow<Int> =
        quizRepository?.observePendingRetentionQuizzesCount() ?: flowOf(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            messageStatsFlow,
            vaultAndQuoteStatsFlow,
            mcqStatsFlow,
            userGamificationRepository.userStats
        ) { msgStats, vaultStats, mcqStats, userStats ->
            Tuple4(msgStats, vaultStats, mcqStats, userStats)
        },
        activeChallengeFlow,
        retentionCombinedFlow,
        pendingQuizzesFlow
    ) { (msgStats, vaultStats, mcqStats, userStats), challengeEval, (retentionState, allRetentionStates), pendingRetentionQuizzes ->
        withContext(Dispatchers.IO) {
            val totalBooks = vaultStats.books.size
            val recentActiveBook = resolveRecentActiveBook(msgStats.latestMessage?.chapterId, vaultStats.books, vaultStats.allChapters)
            val activeBooks = vaultStats.books.count { it.pdfLastReadPage > 0 || it.id == recentActiveBook?.bookId }

            // Strict database conditions for Cognitive Debt:
            // Condition 1: Total active books count must be greater than 0 (totalBooksCount > 0)
            // Condition 2: Total overdue / pending retention quizzes count must be greater than 0 (pendingQuizzesCount > 0)
            val hasDebtCondition1 = totalBooks > 0
            val hasDebtCondition2 = pendingRetentionQuizzes > 0
            val isReaderLockCandidate = (retentionState?.streakStatus == RetentionStreakStatus.FROZEN_DEBT && !retentionState.quizzesCompleted)
            val isCognitiveDebtActive = hasDebtCondition1 && hasDebtCondition2 && isReaderLockCandidate

            // If debt was previously set in daily_retention_state but books or quizzes were wiped, proactively clear the ghost debt in the DB
            if (isReaderLockCandidate && (!hasDebtCondition1 || !hasDebtCondition2)) {
                try {
                    dailyRetentionManager?.clearOrphanedDebtIfNoBooksOrQuizzes(totalBooks, pendingRetentionQuizzes)
                } catch (_: Exception) {}
            }

            val streakInfo = StreakEngine.calculateStreak(msgStats.timestamps)
            val readerRank = if (isCognitiveDebtActive) {
                DailyRetentionManager.RANK_COGNITIVE_STAGNATION
            } else {
                if (userStats.totalXp == 0 && totalBooks == 0) {
                    "Ready to Read"
                } else {
                    dailyRetentionManager?.getEffectiveRank(
                        totalXp = userStats.totalXp,
                        streakStatus = retentionState?.streakStatus,
                        hasActiveBooks = hasDebtCondition1,
                        hasPendingQuizzes = hasDebtCondition2
                    ) ?: StreakEngine.calculateReaderRank(userStats.totalXp)
                }
            }

            val timeBlock = System.currentTimeMillis() / (15 * 60 * 1000L)
            val randomEngine = kotlin.random.Random(timeBlock)
            val randomizedQuotes = if (vaultStats.quotes.isNotEmpty()) {
                vaultStats.quotes.shuffled(randomEngine)
            } else {
                emptyList()
            }

            val retentionResult = EbbinghausRetentionCalculator.calculateRetentionRate(
                mcqAttempts = mcqStats.attempts,
                wordAttempts = mcqStats.wordAttempts,
                pendingQuizzesCount = pendingRetentionQuizzes,
                isCognitiveDebtActive = isCognitiveDebtActive
            )

            DashboardUiState.Success(
                streakInfo = streakInfo,
                readerRank = readerRank,
                totalXp = userStats.totalXp,
                todaySnippetCompleted = userStats.todaySnippetQuizCompleted,
                todayWordCompleted = userStats.todayWordQuizCompleted,
                recentActiveBook = recentActiveBook,
                recentWisdomQuotes = randomizedQuotes,
                totalInsightsCount = msgStats.totalMessages,
                totalWisdomCount = vaultStats.totalQuotes,
                totalWordsCount = vaultStats.totalWords,
                activeBooksCount = activeBooks,
                totalBooksCount = totalBooks,
                totalMcqsCount = mcqStats.allQuestions.size,
                unattemptedMcqsCount = challengeEval.unattemptedCount,
                pendingQuizzesCount = pendingRetentionQuizzes,
                isCognitiveDebtActive = isCognitiveDebtActive,
                showDailyRecallCard = false,
                todayAttempt = challengeEval.todayAttempt,
                todayAccuracy = challengeEval.todayAttempt?.scorePercentage,
                activeChallengeModal = ActiveDailyChallengeModal.None,
                eligiblePillBanner = challengeEval.eligiblePill,
                masteredChaptersCount = challengeEval.masteredChaptersCount,
                pendingMistakesCount = challengeEval.pendingMistakesCount,
                retentionRatePercent = retentionResult.ratePercentage,
                retentionRateDisplay = retentionResult.displayString,
                dailyRetentionState = retentionState,
                readingTimestamps = msgStats.timestamps,
                allRetentionStates = allRetentionStates
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    private suspend fun resolveRecentActiveBook(
        latestMessageChapterId: Long?,
        books: List<Book>,
        allChapters: List<Chapter>
    ): RecentActiveBookUi? {
        if (books.isEmpty()) return null

        var targetBook: Book? = null
        var targetChapter: Chapter? = null

        // 1. Try finding chapter from latest message
        if (latestMessageChapterId != null && latestMessageChapterId > 0) {
            val ch = allChapters.firstOrNull { it.id == latestMessageChapterId } ?: chapterRepository.getChapter(latestMessageChapterId)
            if (ch != null) {
                val b = books.firstOrNull { it.id == ch.bookId } ?: bookRepository.getBook(ch.bookId)
                if (b != null) {
                    targetBook = b
                    targetChapter = ch
                }
            }
        }

        // 2. If not found via latest message, try latest updated chapter in DB
        if (targetBook == null || targetChapter == null) {
            val latestChapter = allChapters.maxByOrNull { it.updatedAt } ?: chapterRepository.getLatestUpdatedChapter()
            if (latestChapter != null) {
                val b = books.firstOrNull { it.id == latestChapter.bookId } ?: bookRepository.getBook(latestChapter.bookId)
                if (b != null) {
                    targetBook = b
                    targetChapter = latestChapter
                }
            }
        }

        // 3. Fallback to most recently updated book and its first chapter
        if (targetBook == null || targetChapter == null) {
            val b = books.firstOrNull()
            if (b != null) {
                targetBook = b
                val chapters = allChapters.filter { it.bookId == b.id }.ifEmpty { chapterRepository.getChaptersForBookSync(b.id) }
                targetChapter = chapters.firstOrNull()
            }
        }

        val finalBook = targetBook ?: return null
        val finalChapter = targetChapter

        val chapterId = finalChapter?.id ?: -1L
        val chapterTitle = finalChapter?.title ?: "Chapter 1"
        val chapterNumber = finalChapter?.chapterNumber ?: 1

        val bookChapters = allChapters.filter { it.bookId == finalBook.id }.ifEmpty { chapterRepository.getChaptersForBookSync(finalBook.id) }
        val bookCoreProgress = ProgressCalculator.calculateBookCoreProgress(bookChapters, finalBook)

        val chapterProgress = if (finalChapter != null) {
            ProgressCalculator.calculateChapterProgress(finalChapter)
        } else {
            null
        }

        val totalPages = when {
            finalChapter != null && finalChapter.pdfTotalPages > 0 -> finalChapter.pdfTotalPages
            finalBook.pdfTotalPages > 0 -> finalBook.pdfTotalPages
            else -> 0
        }

        val lastReadPage = when {
            finalChapter != null && finalChapter.pdfLastReadPage > 0 -> finalChapter.pdfLastReadPage
            finalBook.pdfLastReadPage > 0 -> finalBook.pdfLastReadPage
            else -> 0
        }

        // Calculate snips for this book and chapters
        val totalSnipsCount = bookChapters.sumOf { ch ->
            chapterMessageRepository.getMessagesForChapter(ch.id).size
        }

        val hasStartedReading = (totalSnipsCount > 0) || (lastReadPage > 0) || (bookCoreProgress.readCorePages > 0)

        // Overall Book Progress % strictly calculated on CORE_CHAPTER sections (weighted)
        val overallBookProgressPercent = if (hasStartedReading) bookCoreProgress.progressPercent else 0

        val chapterProgressPercent = if (hasStartedReading && chapterProgress != null) chapterProgress.progressPercent else 0
        val chapterPillLabel = chapterProgress?.pillLabel ?: "Ch $chapterNumber • Not Started"

        return RecentActiveBookUi(
            bookId = finalBook.id,
            chapterId = chapterId,
            title = finalBook.title,
            author = finalBook.author,
            coverImageUrl = finalBook.coverImageUrl,
            chapterTitle = chapterTitle,
            chapterNumber = chapterNumber,
            progressPercent = overallBookProgressPercent,
            chapterProgressPercent = chapterProgressPercent,
            chapterPillLabel = chapterPillLabel,
            lastReadPage = lastReadPage,
            totalPages = totalPages,
            hasStartedReading = hasStartedReading,
            totalSnipsCount = totalSnipsCount
        )
    }
}
