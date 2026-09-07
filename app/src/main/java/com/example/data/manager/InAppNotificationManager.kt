package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.InAppNotification
import com.example.data.model.NotificationType
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WordVaultRepository
import com.example.ui.navigation.Screen
import com.example.ui.util.StreakEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InAppNotificationManager(
    context: Context,
    private val quizRepository: QuizRepository,
    private val dailyRetentionManager: DailyRetentionManager,
    private val wordVaultRepository: WordVaultRepository,
    private val bookRepository: BookRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userGamificationRepository: UserGamificationRepository,
    private val chapterMessageRepository: ChapterMessageRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("readmate_in_app_notifications", Context.MODE_PRIVATE)

    private val _activeNotifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val activeNotifications: StateFlow<List<InAppNotification>> = _activeNotifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private fun getReadIds(): MutableSet<String> {
        val set = prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    private fun getDismissedIds(): MutableSet<String> {
        val set = prefs.getStringSet(KEY_DISMISSED_IDS, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    suspend fun evaluateNotifications() = withContext(ioDispatcher) {
        val readIds = getReadIds()
        val dismissedIds = getDismissedIds()
        val candidates = mutableListOf<InAppNotification>()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 1. Retention Quiz Ready
        try {
            val retentionState = dailyRetentionManager.observeTodayRetentionState(today).firstOrNull()
            val unservedCount = quizRepository.observeUnservedPendingBeforeDateCount(today).firstOrNull() ?: 0
            val isQuizPending = (retentionState != null && !retentionState.quizzesCompleted && retentionState.totalQuizzesServed > 0) || (unservedCount > 0)
            if (isQuizPending) {
                val id = "retention_quiz_$today"
                candidates.add(
                    InAppNotification(
                        id = id,
                        type = NotificationType.RETENTION_QUIZ,
                        title = "Retention Test Ready",
                        message = "Spaced repetition assessment due for active books.",
                        actionLabel = "Start Review →",
                        targetRoute = Screen.Quiz.route,
                        isRead = readIds.contains(id)
                    )
                )
            }
        } catch (_: Exception) {}

        // 2. Word Vault Quiz Ready
        try {
            val allWords = wordVaultRepository.allWords.firstOrNull() ?: emptyList()
            val reviewWords = allWords.filter { it.isFlaggedForSpacedReview }
            val count = if (reviewWords.isNotEmpty()) reviewWords.size else if (allWords.size >= 3) minOf(allWords.size, 5) else 0
            if (count > 0) {
                val id = "word_vault_quiz_$today"
                candidates.add(
                    InAppNotification(
                        id = id,
                        type = NotificationType.WORD_VAULT_QUIZ,
                        title = "Vocabulary Recall Due",
                        message = "$count words in your Vault are scheduled for memory consolidation.",
                        actionLabel = "Inspect Vault →",
                        targetRoute = Screen.WordVault.route,
                        isRead = readIds.contains(id)
                    )
                )
            }
        } catch (_: Exception) {}

        // 3. Streak & Milestone
        try {
            val streakCount = if (chapterMessageRepository != null) {
                val timestamps = chapterMessageRepository.allMessageTimestamps.firstOrNull() ?: emptyList()
                StreakEngine.calculateStreak(timestamps).activeStreakCount
            } else {
                val stats = userGamificationRepository.getStats()
                if (stats.todaySnippetQuizCompleted || stats.todayWordQuizCompleted) 1 else 0
            }
            if (streakCount > 0) {
                val id = "streak_milestone_${streakCount}_$today"
                candidates.add(
                    InAppNotification(
                        id = id,
                        type = NotificationType.STREAK_MILESTONE,
                        title = "Streak Milestone Active",
                        message = if (streakCount == 1) {
                            "First day of your reading streak started! Maintain momentum today."
                        } else {
                            "$streakCount-day reading streak! Keep the cognitive momentum going."
                        },
                        actionLabel = "View Progress →",
                        targetRoute = Screen.Progress.route,
                        isRead = readIds.contains(id)
                    )
                )
            }
        } catch (_: Exception) {}

        // 4. Inactive Book Recall
        try {
            val allBooks = bookRepository.allBooks.firstOrNull() ?: emptyList()
            val fourDaysAgo = System.currentTimeMillis() - (4L * 24 * 60 * 60 * 1000L)
            val inactiveBook = allBooks.firstOrNull { book ->
                book.pdfLastReadPage > 0 && book.updatedAt <= fourDaysAgo
            }
            if (inactiveBook != null) {
                val id = "inactive_recall_${inactiveBook.id}"
                candidates.add(
                    InAppNotification(
                        id = id,
                        type = NotificationType.INACTIVE_RECALL,
                        title = "Pick Up Where You Left Off",
                        message = "You haven't read \"${inactiveBook.title}\" in 4+ days. A few pages will keep retention sharp.",
                        actionLabel = "Resume Reading →",
                        targetRoute = Screen.BookDetail.createRoute(inactiveBook.id),
                        isRead = readIds.contains(id)
                    )
                )
            }
        } catch (_: Exception) {}

        val filtered = candidates.filterNot { dismissedIds.contains(it.id) }
        _activeNotifications.value = filtered
        _unreadCount.value = filtered.count { !it.isRead }
    }

    fun markAllAsRead() {
        val current = _activeNotifications.value
        if (current.isEmpty()) return
        val readIds = getReadIds()
        current.forEach { readIds.add(it.id) }
        prefs.edit().putStringSet(KEY_READ_IDS, readIds).apply()
        _activeNotifications.value = current.map { it.copy(isRead = true) }
        _unreadCount.value = 0
    }

    fun clearNotification(id: String) {
        val dismissedIds = getDismissedIds()
        dismissedIds.add(id)
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, dismissedIds).apply()
        val updated = _activeNotifications.value.filter { it.id != id }
        _activeNotifications.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    companion object {
        private const val KEY_READ_IDS = "read_notification_ids"
        private const val KEY_DISMISSED_IDS = "dismissed_notification_ids"
    }
}
