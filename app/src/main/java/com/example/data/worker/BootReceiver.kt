package com.example.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                RetentionWorkerScheduler.scheduleAllWorkers(context)
                WisdomReminderScheduler.scheduleDailyReminders(context)
                Log.d(TAG, "Successfully rescheduled all background workers upon reboot/package replace.")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling workers on boot", e)
            }
        }
    }
}
