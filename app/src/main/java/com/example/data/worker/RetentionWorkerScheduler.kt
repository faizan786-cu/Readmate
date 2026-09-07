package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object RetentionWorkerScheduler {

    private const val TAG = "RetentionWorkerScheduler"

    const val WORK_NAME_EVENING_RECALL = "readmate_retention_evening_recall"
    const val WORK_NAME_FINAL_WARNING = "readmate_retention_final_warning"
    const val WORK_NAME_MIDNIGHT_ROLLOVER = "readmate_retention_midnight_rollover"

    /**
     * Calculates the initial delay in milliseconds from the current moment until the target hour, minute, second.
     * If the target time for today has already passed, schedules for tomorrow at the same time.
     */
    fun calculateInitialDelay(targetHour: Int, targetMinute: Int, targetSecond: Int = 0): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, targetSecond)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    /**
     * Enqueues all retention background schedules safely with ExistingPeriodicWorkPolicy.KEEP.
     */
    fun scheduleAllWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)

        scheduleEveningRecall(workManager)
        scheduleFinalWarning(workManager)
        scheduleMidnightRollover(workManager)
    }

    private fun scheduleEveningRecall(workManager: WorkManager) {
        try {
            val initialDelay = calculateInitialDelay(20, 0, 0) // 8:00 PM
            val inputData = Data.Builder()
                .putString(DailyRetentionWorker.KEY_TRIGGER_TYPE, DailyRetentionWorker.TRIGGER_EVENING_RECALL)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DailyRetentionWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_EVENING_RECALL,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled Evening Recall worker with initial delay: ${initialDelay / 1000 / 60} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule evening recall worker", e)
        }
    }

    private fun scheduleFinalWarning(workManager: WorkManager) {
        try {
            val initialDelay = calculateInitialDelay(23, 15, 0) // 11:15 PM (45m before cutoff)
            val inputData = Data.Builder()
                .putString(DailyRetentionWorker.KEY_TRIGGER_TYPE, DailyRetentionWorker.TRIGGER_FINAL_WARNING)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DailyRetentionWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_FINAL_WARNING,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled Final Warning worker with initial delay: ${initialDelay / 1000 / 60} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule final warning worker", e)
        }
    }

    private fun scheduleMidnightRollover(workManager: WorkManager) {
        try {
            val initialDelay = calculateInitialDelay(0, 0, 1) // 00:00:01 Midnight

            val workRequest = PeriodicWorkRequestBuilder<MidnightRolloverWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_MIDNIGHT_ROLLOVER,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled Midnight Rollover worker with initial delay: ${initialDelay / 1000 / 60} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule midnight rollover worker", e)
        }
    }
}
