package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.UserGamificationStats
import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.entity.WordQuizAttempt
import com.example.data.local.database.model.MistakeRecord
import com.example.data.local.database.model.WordMistakeRecord
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.McqRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.WordQuizRepository
import com.example.data.util.ProgressCalculator
import com.example.ui.util.StreakEngine
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

data class QuizAttemptItemUi(
    val id: Long,
    val attemptDate: Long,
    val formattedDate: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Float,
    val bookTitle: String?,
    val mistakesCount: Int,
    val mistakes: List<MistakeRecord>,
    val isWordQuiz: Boolean = false,
    val xpEarned: Int = 0
)

data class WeakConceptUi(
    val questionId: Long,
    val questionText: String,
    val lastWrongOptionText: String,
    val correctOptionText: String,
    val explanation: String,
    val timesMissed: Int,
    val latestResolvedInRetry: Boolean
)

data class ChapterMasteryItemUi(
    val chapterId: Long,
    val bookId: Long,
    val chapterTitle: String,
    val bookTitle: String?,
    val masteryScore: Int?,
    val isMastered: Boolean,
    val masteredAt: Long?,
    val formattedMasteredDate: String?
)

data class HeatmapDayUi(
    val date: LocalDate,
    val dayOfWeek: Int, // 0..6 (Mon..Sun)
    val weekIndex: Int, // 0..11
    val activityCount: Int,
    val level: Int, // 0..4
    val isFuture: Boolean,
    val isToday: Boolean
)

data class TargetChapterMasteryUi(
    val chapterId: Long,
    val bookId: Long,
    val chapterNumber: Int,
    val chapterTitle: String,
    val bookTitle: String,
    val bookCoverUrl: String?,
    val progressPercent: Int,
    val isExamReady: Boolean
)

data class RankTierInfo(
    val currentRank: String,
    val nextRank: String,
    val xpRemaining: Int,
    val progressRatio: Float
)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Empty(
        val totalQuestionsInVault: Int = 0,
        val totalXp: Int = 0,
        val currentRank: String = "Novice Reader",
        val todaySnippetCompleted: Boolean = false,
        val todayWordCompleted: Boolean = false,
        val chaptersMasteredCount: Int = 0,
        val totalChaptersCount: Int = 0,
        val averageChapterMasteryScore: Float = 0f,
        val chapterMasteryList: List<ChapterMasteryItemUi> = emptyList()
    ) : ProgressUiState

    data class Success(
        val overallAccuracyRate: Float,
        val totalQuizzesCompleted: Int,
        val totalQuestionsAnswered: Int,
        val totalQuestionsInVault: Int,
        val totalXp: Int,
        val currentRank: String,
        val nextRankName: String = "Tactical Scholar",
        val xpToNextRank: Int = 250,
        val rankProgressRatio: Float = 0f,
        val currentStreakDays: Int = 0,
        val activeDaysThisMonth: Int = 0,
        val totalMissedQuestionsCount: Int = 0,
        val todaySnippetCompleted: Boolean,
        val todayWordCompleted: Boolean,
        val totalWordQuizzesCompleted: Int = 0,
        val chaptersMasteredCount: Int = 0,
        val totalChaptersCount: Int = 0,
        val averageChapterMasteryScore: Float = 0f,
        val chapterMasteryList: List<ChapterMasteryItemUi> = emptyList(),
        val targetChapterMastery: TargetChapterMasteryUi? = null,
        val heatmapDays: List<HeatmapDayUi> = emptyList(),
        val recentAttempts: List<QuizAttemptItemUi> = emptyList(),
        val weakConcepts: List<WeakConceptUi> = emptyList()
    ) : ProgressUiState
}

private data class CombinedData(
    val snippetAttempts: List<UserMcqAttempt>,
    val wordAttempts: List<WordQuizAttempt>,
    val totalQuestionsInVault: Int,
    val books: List<Book>,
    val userStats: UserGamificationStats,
    val allChapters: List<Chapter>,
    val readingTimestamps: List<Long>
)

class ProgressViewModel(
    private val mcqRepository: McqRepository,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val wordQuizRepository: WordQuizRepository,
    private val chapterMessageRepository: ChapterMessageRepository? = null
) : ViewModel() {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mistakeListAdapter: JsonAdapter<List<MistakeRecord>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, MistakeRecord::class.java))

    private val wordMistakeListAdapter: JsonAdapter<List<WordMistakeRecord>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, WordMistakeRecord::class.java))

    private val dateFormatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    private val timestampsFlow = chapterMessageRepository?.allMessageTimestamps ?: flowOf(emptyList())

    val uiState: StateFlow<ProgressUiState> = combine(
        combine(
            mcqRepository.observeAllAttempts(),
            wordQuizRepository.allAttempts,
            mcqRepository.observeTotalQuestionsCount(),
            bookRepository.allBooks
        ) { snippetAttempts, wordAttempts, totalVaultQuestions, books ->
            ProgressQuad(snippetAttempts, wordAttempts, totalVaultQuestions, books)
        },
        combine(
            userGamificationRepository.userStats,
            chapterRepository.observeAllChapters(),
            timestampsFlow
        ) { userStats, chapters, timestamps ->
            Triple(userStats, chapters, timestamps)
        }
    ) { (snippetAttempts, wordAttempts, totalQuestionsInVault, books), (userStats, allChapters, readingTimestamps) ->
        withContext(Dispatchers.IO) {
            val totalXp = userStats.totalXp
            val rankInfo = calculateRankTierInfo(totalXp)
            val currentRank = rankInfo.currentRank
            val todaySnippetCompleted = userStats.todaySnippetQuizCompleted
            val todayWordCompleted = userStats.todayWordQuizCompleted

            val booksMap = books.associateBy { it.id }

            // Chapter mastery computations
            val totalChaptersCount = allChapters.size
            val attemptedOrMasteredChapters = allChapters.filter { it.masteryScore != null || it.isMastered }
            val chaptersMasteredCount = allChapters.count { it.isMastered || (it.masteryScore != null && it.masteryScore >= 80) }
            val averageChapterMastery = if (attemptedOrMasteredChapters.isNotEmpty()) {
                val scores = attemptedOrMasteredChapters.mapNotNull { it.masteryScore }
                if (scores.isNotEmpty()) scores.average().toFloat() else 0f
            } else 0f

            val chapterMasteryList = attemptedOrMasteredChapters.map { ch ->
                val isM = ch.isMastered || (ch.masteryScore != null && ch.masteryScore >= 80)
                ChapterMasteryItemUi(
                    chapterId = ch.id,
                    bookId = ch.bookId,
                    chapterTitle = ch.title,
                    bookTitle = booksMap[ch.bookId]?.title,
                    masteryScore = ch.masteryScore,
                    isMastered = isM,
                    masteredAt = ch.masteredAt,
                    formattedMasteredDate = ch.masteredAt?.let { dateFormatter.format(Date(it)) }
                )
            }.sortedByDescending { it.masteredAt ?: 0L }

            // 1. Overall stats
            val totalSnippetQuizzes = snippetAttempts.size
            val totalWordQuizzes = wordAttempts.size
            val totalQuizzes = totalSnippetQuizzes + totalWordQuizzes

            val totalQuestionsAnswered = snippetAttempts.sumOf { it.totalQuestions } + wordAttempts.sumOf { it.totalQuestions }
            val totalCorrect = snippetAttempts.sumOf { it.correctAnswers } + wordAttempts.sumOf { it.correctAnswers }
            val overallAccuracy = if (totalQuestionsAnswered > 0) {
                (totalCorrect.toFloat() / totalQuestionsAnswered.toFloat()) * 100f
            } else 0f

            // 2. Recent attempts mapping (both snippet and word quizzes sorted by date)
            val parsedSnippetAttempts = snippetAttempts.map { attempt ->
                val mistakes = parseMistakes(attempt.wrongAnswersJson)
                val bookTitle = attempt.bookId?.let { booksMap[it]?.title }

                QuizAttemptItemUi(
                    id = attempt.id,
                    attemptDate = attempt.attemptDate,
                    formattedDate = dateFormatter.format(Date(attempt.attemptDate)),
                    totalQuestions = attempt.totalQuestions,
                    correctAnswers = attempt.correctAnswers,
                    scorePercentage = attempt.scorePercentage,
                    bookTitle = bookTitle,
                    mistakesCount = mistakes.size,
                    mistakes = mistakes,
                    isWordQuiz = false,
                    xpEarned = attempt.correctAnswers * 10
                )
            }

            val parsedWordAttempts = wordAttempts.map { attempt ->
                val wordMistakes = parseWordMistakes(attempt.wrongAnswersJson)
                val mappedMistakes = wordMistakes.map { wm ->
                    MistakeRecord(
                        questionId = wm.wordId,
                        questionText = "Word: ${wm.word}",
                        userSelectedOptionText = wm.userSelectedOptionText,
                        correctOptionText = wm.correctOptionText,
                        explanation = wm.romanUrduExplanation,
                        resolvedInRetry = wm.resolvedInRetry
                    )
                }

                QuizAttemptItemUi(
                    id = attempt.id,
                    attemptDate = attempt.attemptDate,
                    formattedDate = dateFormatter.format(Date(attempt.attemptDate)),
                    totalQuestions = attempt.totalQuestions,
                    correctAnswers = attempt.correctAnswers,
                    scorePercentage = attempt.scorePercentage,
                    bookTitle = "Word Vault Challenge",
                    mistakesCount = mappedMistakes.size,
                    mistakes = mappedMistakes,
                    isWordQuiz = true,
                    xpEarned = attempt.xpEarned
                )
            }

            val allParsedAttempts = (parsedSnippetAttempts + parsedWordAttempts)
                .sortedByDescending { it.attemptDate }

            // 3. Aggregate Weak Concepts across snippet attempts
            val weakConceptsMap = mutableMapOf<Long, MutableList<MistakeRecord>>()
            parsedSnippetAttempts.forEach { attemptUi ->
                attemptUi.mistakes.forEach { mistake ->
                    weakConceptsMap.getOrPut(mistake.questionId) { mutableListOf() }.add(mistake)
                }
            }

            val weakConcepts = weakConceptsMap.map { (questionId, mistakeList) ->
                val latestMistake = mistakeList.last()
                WeakConceptUi(
                    questionId = questionId,
                    questionText = latestMistake.questionText,
                    lastWrongOptionText = latestMistake.userSelectedOptionText,
                    correctOptionText = latestMistake.correctOptionText,
                    explanation = latestMistake.explanation,
                    timesMissed = mistakeList.size,
                    latestResolvedInRetry = latestMistake.resolvedInRetry
                )
            }.sortedByDescending { it.timesMissed }

            // 4. Heatmap & Streak
            val streakInfo = StreakEngine.calculateStreak(readingTimestamps)
            val (heatmapDays, activeMonthDays) = buildHeatmapMatrix(
                readingTimestamps = readingTimestamps,
                quizAttemptDates = snippetAttempts.map { it.attemptDate },
                wordAttemptDates = wordAttempts.map { it.attemptDate },
                masteryDatesList = allChapters.mapNotNull { it.masteredAt }
            )

            // 5. Target chapter readiness monitor
            val targetChapter = resolveTargetChapter(books, allChapters)

            ProgressUiState.Success(
                overallAccuracyRate = overallAccuracy,
                totalQuizzesCompleted = totalQuizzes,
                totalQuestionsAnswered = totalQuestionsAnswered,
                totalQuestionsInVault = totalQuestionsInVault,
                totalXp = totalXp,
                currentRank = currentRank,
                nextRankName = rankInfo.nextRank,
                xpToNextRank = rankInfo.xpRemaining,
                rankProgressRatio = rankInfo.progressRatio,
                currentStreakDays = streakInfo.activeStreakCount,
                activeDaysThisMonth = activeMonthDays,
                totalMissedQuestionsCount = weakConcepts.size,
                todaySnippetCompleted = todaySnippetCompleted,
                todayWordCompleted = todayWordCompleted,
                totalWordQuizzesCompleted = totalWordQuizzes,
                chaptersMasteredCount = chaptersMasteredCount,
                totalChaptersCount = totalChaptersCount,
                averageChapterMasteryScore = averageChapterMastery,
                chapterMasteryList = chapterMasteryList,
                targetChapterMastery = targetChapter,
                heatmapDays = heatmapDays,
                recentAttempts = allParsedAttempts,
                weakConcepts = weakConcepts
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState.Loading
    )

    private fun resolveTargetChapter(
        books: List<Book>,
        allChapters: List<Chapter>
    ): TargetChapterMasteryUi? {
        if (books.isEmpty() || allChapters.isEmpty()) return null
        val booksMap = books.associateBy { it.id }

        val candidate = allChapters.firstOrNull { ch ->
            val prog = ProgressCalculator.calculateChapterProgress(ch)
            prog.progressPercent in 1..99
        } ?: allChapters.firstOrNull { ch ->
            ch.isMastered || (ch.masteryScore != null && ch.masteryScore >= 80) || ch.isCompleted
        } ?: allChapters.maxByOrNull { it.updatedAt }
        ?: allChapters.firstOrNull() ?: return null

        val book = booksMap[candidate.bookId] ?: books.firstOrNull() ?: return null
        val prog = ProgressCalculator.calculateChapterProgress(candidate)
        val isReady = candidate.isMastered ||
                (candidate.masteryScore != null && candidate.masteryScore >= 80) ||
                candidate.isCompleted ||
                prog.progressPercent >= 100

        val cleanedTitle = ProgressCalculator.cleanChapterTitle(candidate.title)
        val chapNum = candidate.chapterNumber ?: 1
        val displayTitle = "Ch. $chapNum: $cleanedTitle"

        return TargetChapterMasteryUi(
            chapterId = candidate.id,
            bookId = book.id,
            chapterNumber = chapNum,
            chapterTitle = displayTitle,
            bookTitle = book.title,
            bookCoverUrl = book.coverImageUrl,
            progressPercent = prog.progressPercent,
            isExamReady = isReady
        )
    }

    private fun buildHeatmapMatrix(
        readingTimestamps: List<Long>,
        quizAttemptDates: List<Long>,
        wordAttemptDates: List<Long>,
        masteryDatesList: List<Long>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<List<HeatmapDayUi>, Int> {
        val allEventsMap = mutableMapOf<LocalDate, Int>()
        val masteryDaysSet = mutableSetOf<LocalDate>()

        for (ts in readingTimestamps) {
            try {
                val d = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
                allEventsMap[d] = (allEventsMap[d] ?: 0) + 1
            } catch (_: Exception) {}
        }
        for (ts in quizAttemptDates) {
            try {
                val d = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
                allEventsMap[d] = (allEventsMap[d] ?: 0) + 1
            } catch (_: Exception) {}
        }
        for (ts in wordAttemptDates) {
            try {
                val d = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
                allEventsMap[d] = (allEventsMap[d] ?: 0) + 1
            } catch (_: Exception) {}
        }
        for (ts in masteryDatesList) {
            try {
                val d = Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
                allEventsMap[d] = (allEventsMap[d] ?: 0) + 1
                masteryDaysSet.add(d)
            } catch (_: Exception) {}
        }

        val totalWeeks = 12
        val currentDayOfWeek = today.dayOfWeek.value - 1 // 0=Mon, 6=Sun
        val currentWeekMonday = today.minusDays(currentDayOfWeek.toLong())

        val result = mutableListOf<HeatmapDayUi>()
        var activeDaysInCurrentMonth = 0

        for (w in 0 until totalWeeks) {
            val weekMonday = currentWeekMonday.minusWeeks((totalWeeks - 1 - w).toLong())
            for (dayIdx in 0..6) {
                val cellDate = weekMonday.plusDays(dayIdx.toLong())
                val isFuture = cellDate.isAfter(today)
                val isToday = cellDate.isEqual(today)
                val count = if (isFuture) 0 else (allEventsMap[cellDate] ?: 0)
                val hasMastery = masteryDaysSet.contains(cellDate)

                val level = when {
                    isFuture -> 0
                    hasMastery || count >= 8 -> 4
                    count in 5..7 -> 3
                    count in 3..4 -> 2
                    count in 1..2 -> 1
                    else -> 0
                }

                if (!isFuture && cellDate.month == today.month && cellDate.year == today.year && count > 0) {
                    activeDaysInCurrentMonth++
                }

                result.add(
                    HeatmapDayUi(
                        date = cellDate,
                        dayOfWeek = dayIdx,
                        weekIndex = w,
                        activityCount = count,
                        level = level,
                        isFuture = isFuture,
                        isToday = isToday
                    )
                )
            }
        }

        return Pair(result, activeDaysInCurrentMonth)
    }

    private fun calculateRankTierInfo(totalXp: Int): RankTierInfo {
        return when {
            totalXp < 250 -> {
                val needed = (250 - totalXp).coerceAtLeast(1)
                val ratio = (totalXp.toFloat() / 250f).coerceIn(0f, 1f)
                RankTierInfo("Novice Reader", "Tactical Scholar", needed, ratio)
            }
            totalXp in 250..699 -> {
                val needed = (700 - totalXp).coerceAtLeast(1)
                val ratio = ((totalXp - 250).toFloat() / 450f).coerceIn(0f, 1f)
                RankTierInfo("Tactical Scholar", "Analytical Thinker", needed, ratio)
            }
            totalXp in 700..1499 -> {
                val needed = (1500 - totalXp).coerceAtLeast(1)
                val ratio = ((totalXp - 700).toFloat() / 800f).coerceIn(0f, 1f)
                RankTierInfo("Analytical Thinker", "Strategic Mind", needed, ratio)
            }
            totalXp in 1500..2999 -> {
                val needed = (3000 - totalXp).coerceAtLeast(1)
                val ratio = ((totalXp - 1500).toFloat() / 1500f).coerceIn(0f, 1f)
                RankTierInfo("Strategic Mind", "Master Polymath", needed, ratio)
            }
            else -> {
                RankTierInfo("Master Polymath", "Apex Polymath", 0, 1f)
            }
        }
    }

    private fun parseMistakes(json: String?): List<MistakeRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            mistakeListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseWordMistakes(json: String?): List<WordMistakeRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            wordMistakeListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private data class ProgressQuad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
