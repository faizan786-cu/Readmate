package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WisdomReminderScheduler {

    private const val TAG = "WisdomReminderScheduler"
    private const val UNIQUE_WORK_NAME = "readmate_wisdom_spaced_reminder_work"

    /**
     * Schedules periodic wisdom spaced repetition reminders to run approximately
     * 5 to 6 times daily (every 4 hours), constrained to battery-not-low and
     * executing inside the 8:00 AM - 10:00 PM daytime window.
     */
    fun scheduleDailyReminders(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // 4 hours repeat interval (~6 times per 24-hour cycle)
            val workRequest = PeriodicWorkRequestBuilder<WisdomReminderWorker>(
                repeatInterval = 4,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Successfully enqueued periodic wisdom reminder work.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic wisdom reminders", e)
        }
    }
}
