package com.example.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "user_gamification_stats")
data class UserGamificationStats(
    @PrimaryKey
    val id: Long = 1L,
    val totalXp: Int = 0,
    val todaySnippetQuizCompleted: Boolean = false,
    val todayWordQuizCompleted: Boolean = false,
    val lastActiveDate: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
