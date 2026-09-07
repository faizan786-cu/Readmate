package com.example.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_ID_RETENTION = "retention_alerts"
    const val CHANNEL_NAME_RETENTION = "Retention & Recall Protocol"
    const val CHANNEL_DESC_RETENTION = "Critical memory drift alerts and daily recall reminders"

    const val NOTIFICATION_ID_EVENING_RECALL = 3001
    const val NOTIFICATION_ID_FINAL_WARNING = 3002

    private const val OBSIDIAN_ACCENT_COLOR = 0xFF141418.toInt()

    fun createRetentionNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_RETENTION,
                CHANNEL_NAME_RETENTION,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_RETENTION
                enableLights(true)
                lightColor = Color.WHITE
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Trigger 1: Evening Recall Dispatch (8:00 PM Local Time)
     * Title: Memory Drift Detected
     * Content: Active concepts from your recent readings are decaying. 120 seconds required to retain.
     */
    fun sendEveningRecallNotification(
        context: Context,
        pendingCount: Int = 0,
        bookTitle: String? = null
    ) {
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "Notification permission not granted. Suppressing Evening Recall alert.")
            return
        }

        createRetentionNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_DAILY_RECALL)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_EVENING_RECALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Memory Drift Detected"
        val content = if (!bookTitle.isNullOrBlank()) {
            "Active concepts from $bookTitle are decaying. 120 seconds required to retain."
        } else {
            "Active concepts from your recent readings are decaying. 120 seconds required to retain."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RETENTION)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setColor(OBSIDIAN_ACCENT_COLOR)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EVENING_RECALL, notification)
            Log.d(TAG, "Dispatched Evening Recall notification.")
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException while sending notification: ${se.message}")
        }
    }

    /**
     * Trigger 2: High-Stakes Final Warning (11:15 PM Local Time - 45 min before midnight)
     * Title: Retention Rate Dropping in 45m
     * Content: Inaction will lock reading access and transfer unattempted items to the Mistake Bank.
     */
    fun sendFinalWarningNotification(context: Context) {
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "Notification permission not granted. Suppressing Final Warning alert.")
            return
        }

        createRetentionNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_DAILY_RECALL)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_FINAL_WARNING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Retention Rate Dropping in 45m"
        val content = "Inaction will lock reading access and transfer unattempted items to the Mistake Bank."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RETENTION)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setColor(OBSIDIAN_ACCENT_COLOR)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FINAL_WARNING, notification)
            Log.d(TAG, "Dispatched Final Warning high-stakes notification.")
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException while sending notification: ${se.message}")
        }
    }

    /**
     * Cancels any active retention alerts when tests are completed or at midnight rollover.
     */
    fun cancelRetentionNotifications(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID_EVENING_RECALL)
            notificationManager.cancel(NOTIFICATION_ID_FINAL_WARNING)
            Log.d(TAG, "Cancelled active retention notifications.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel retention notifications: ${e.message}")
        }
    }
}
