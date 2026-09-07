package com.example.data.worker

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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.ReadMateApplication
import com.example.data.local.database.entity.WisdomQuote
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class WisdomReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "WisdomReminderWorker"
        const val CHANNEL_ID = "wisdom_reminders"
        const val CHANNEL_NAME = "Daily Wisdom & Spaced Recall"
        const val CHANNEL_DESCRIPTION = "Periodic life lessons, quotes, and reflections from your reading library"
        const val NOTIFICATION_ID = 2001
        private const val RUBY_ACCENT_COLOR = 0xFFE53935.toInt()
    }

    override suspend fun doWork(): Result {
        try {
            // 1. Enforce daytime window constraints (8:00 AM to 10:00 PM)
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour < 8 || currentHour >= 22) {
                Log.d(TAG, "Skipping reminder: outside daytime window (8 AM - 10 PM), current hour = $currentHour")
                return Result.success()
            }

            // 2. Safely check notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Skipping reminder: POST_NOTIFICATIONS permission not granted")
                    return Result.success()
                }
            }

            // 3. Query repository for quotes
            val app = applicationContext as? ReadMateApplication ?: run {
                Log.w(TAG, "ApplicationContext is not ReadMateApplication")
                return Result.success()
            }

            val quotes = app.wisdomQuoteRepository.observeAllQuotes().firstOrNull() ?: emptyList()
            if (quotes.isEmpty()) {
                Log.d(TAG, "No wisdom quotes in database yet. Completing silently.")
                return Result.success()
            }

            val randomQuote = quotes.random()
            postWisdomNotification(randomQuote)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing WisdomReminderWorker", e)
            return Result.success() // Return success to avoid unnecessary retry loops
        }
    }

    private fun postWisdomNotification(quote: WisdomQuote) {
        val context = applicationContext
        createNotificationChannel(context)

        // Deep-link intent targeting Wisdom Reels
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_WISDOM_REELS)
            putExtra(MainActivity.EXTRA_QUOTE_ID, quote.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (quote.id % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build rich multi-line styled quote content
        val authorPart = quote.author?.takeIf { it.isNotBlank() } ?: "Timeless Wisdom"
        val bigText = buildString {
            append(quote.englishQuote)
            if (quote.romanUrduPunchline.isNotBlank()) {
                append("\n\n“")
                append(quote.romanUrduPunchline)
                append("”")
            }
            append("\n\n— ")
            append(authorPart)
            append(", Ch. ")
            append(quote.chapterNumber)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Daily Wisdom • ${quote.bookTitle}")
            .setContentText(quote.englishQuote)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(RUBY_ACCENT_COLOR)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException when posting notification: ${se.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
