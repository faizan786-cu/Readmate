package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.database.entity.UserGamificationStats
import kotlinx.coroutines.flow.Flow

@Dao
interface UserGamificationDao {

    @Query("SELECT * FROM user_gamification_stats WHERE id = 1 LIMIT 1")
    fun observeStats(): Flow<UserGamificationStats?>

    @Query("SELECT * FROM user_gamification_stats WHERE id = 1 LIMIT 1")
    suspend fun getStats(): UserGamificationStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: UserGamificationStats)

    @Query("UPDATE user_gamification_stats SET totalXp = totalXp + :points, updatedAt = :timestamp WHERE id = 1")
    suspend fun addXp(points: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_gamification_stats SET todaySnippetQuizCompleted = :completed, updatedAt = :timestamp WHERE id = 1")
    suspend fun setTodaySnippetQuizCompleted(completed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_gamification_stats SET todayWordQuizCompleted = :completed, updatedAt = :timestamp WHERE id = 1")
    suspend fun setTodayWordQuizCompleted(completed: Boolean, timestamp: Long = System.currentTimeMillis())
}
