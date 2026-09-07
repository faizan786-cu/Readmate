package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

object RetentionStreakStatus {
    const val ACTIVE = "ACTIVE"
    const val FROZEN_DEBT = "FROZEN_DEBT"
    const val BROKEN = "BROKEN"
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "daily_retention_state")
data class DailyRetentionStateEntity(
    @PrimaryKey
    val date: String, // Formatted YYYY-MM-DD
    val quizzesCompleted: Boolean = false,
    val totalQuizzesServed: Int = 0,
    val correctCount: Int = 0,
    val retentionIndex: Float = 100.0f,
    val streakStatus: String = RetentionStreakStatus.ACTIVE,
    val streakDays: Int = 0,
    val isFirstDayGracePeriod: Boolean = false
)
