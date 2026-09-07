package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ReadMateApplication
import com.example.data.notification.NotificationHelper

class MidnightRolloverWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "MidnightRolloverWorker"
    }

    override suspend fun doWork(): Result {
        try {
            val app = applicationContext as? ReadMateApplication ?: run {
                Log.w(TAG, "Application context is not ReadMateApplication")
                return Result.success()
            }

            val retentionManager = app.dailyRetentionManager
            val todayDate = retentionManager.getTodayDateString()

            Log.d(TAG, "Executing Midnight Rollover for date: $todayDate")

            // 1 & 2 & 3 & 4. Execute rollover math, penalty deductions, and fresh day state
            val newDayState = retentionManager.checkAndRollOverDailyStatus(todayDate)

            // If Day 1+ and not grace period, assemble the day's test queue
            if (!newDayState.isFirstDayGracePeriod) {
                retentionManager.assembleDailyQuizSession(todayDate)
            }

            // 5. Cancel lingering notifications from yesterday's cycle
            NotificationHelper.cancelRetentionNotifications(applicationContext)

            Log.d(TAG, "Midnight Rollover completed successfully for $todayDate. State: $newDayState")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing MidnightRolloverWorker", e)
            return Result.success()
        }
    }
}
