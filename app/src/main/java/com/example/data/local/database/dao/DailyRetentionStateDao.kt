package com.example.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.database.entity.DailyRetentionStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRetentionStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(state: DailyRetentionStateEntity)

    @Update
    suspend fun updateState(state: DailyRetentionStateEntity)

    @Query("SELECT * FROM daily_retention_state WHERE date = :date")
    suspend fun getStateForDate(date: String): DailyRetentionStateEntity?

    @Query("SELECT * FROM daily_retention_state WHERE date = :date")
    fun observeStateForDate(date: String): Flow<DailyRetentionStateEntity?>

    @Query("SELECT * FROM daily_retention_state WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getLatestStateBeforeDate(date: String): DailyRetentionStateEntity?

    @Query("SELECT * FROM daily_retention_state ORDER BY date DESC LIMIT 1")
    suspend fun getLatestState(): DailyRetentionStateEntity?

    @Query("SELECT * FROM daily_retention_state ORDER BY date DESC LIMIT 1")
    fun observeLatestState(): Flow<DailyRetentionStateEntity?>

    @Query("SELECT * FROM daily_retention_state ORDER BY date DESC")
    suspend fun getAllStates(): List<DailyRetentionStateEntity>

    @Query("SELECT * FROM daily_retention_state ORDER BY date DESC")
    fun observeAllStates(): Flow<List<DailyRetentionStateEntity>>

    @Query("DELETE FROM daily_retention_state")
    suspend fun deleteAllStates()
}
