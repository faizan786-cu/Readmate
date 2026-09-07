package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import com.example.data.local.database.entity.QuizStatus
import com.example.data.notification.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull

class DailyRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DailyRetentionWorker"
        const val KEY_TRIGGER_TYPE = "key_trigger_type"
        const val TRIGGER_EVENING_RECALL = "trigger_evening_recall"
        const val TRIGGER_FINAL_WARNING = "trigger_final_warning"
    }

    override suspend fun doWork(): Result {
        try {
            val app = applicationContext as? ReadMateApplication ?: run {
                Log.w(TAG, "Application context is not ReadMateApplication")
                return Result.success()
            }

            val triggerType = inputData.getString(KEY_TRIGGER_TYPE) ?: TRIGGER_EVENING_RECALL
            val retentionManager = app.dailyRetentionManager
            val quizRepo = app.quizRepository
            val bookRepo = app.bookRepository

            val todayDate = retentionManager.getTodayDateString()
            val todayState = retentionManager.checkAndRollOverDailyStatus(todayDate)

            // Rule 1: First Day Grace Period Suppression
            if (todayState.isFirstDayGracePeriod) {
                Log.d(TAG, "First day grace period is active. Suppressing retention notification.")
                return Result.success()
            }

            // Rule 2: Already completed today's quizzes
            if (todayState.quizzesCompleted) {
                Log.d(TAG, "Today's daily recall quizzes already completed. Suppressing retention notification.")
                return Result.success()
            }

            when (triggerType) {
                TRIGGER_EVENING_RECALL -> {
                    // Check if pending questions exist
                    val servedQuizzes = quizRepo.getQuizzesServedOnDate(todayDate)
                    val pendingServed = servedQuizzes.count { it.status == QuizStatus.SERVED }
                    val mistakeQuizzes = quizRepo.getMistakeBankQuizzes(10)
                    val allQuizzes = quizRepo.getAllQuizzes()
                    val unservedPending = allQuizzes.count { it.status == QuizStatus.PENDING }

                    val totalPending = pendingServed + mistakeQuizzes.size + unservedPending
                    if (totalPending == 0 && servedQuizzes.isEmpty()) {
                        Log.d(TAG, "No pending quizzes in the system. Suppressing evening recall alert.")
                        return Result.success()
                    }

                    val recentBook = bookRepo.allBooks.firstOrNull()?.maxByOrNull { it.updatedAt }
                    val bookTitle = recentBook?.title

                    NotificationHelper.sendEveningRecallNotification(
                        context = applicationContext,
                        pendingCount = totalPending,
                        bookTitle = bookTitle
                    )
                }

                TRIGGER_FINAL_WARNING -> {
                    // High-stakes alert 45 min before midnight
                    NotificationHelper.sendFinalWarningNotification(
                        context = applicationContext
                    )
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing DailyRetentionWorker", e)
            return Result.success()
        }
    }
}
