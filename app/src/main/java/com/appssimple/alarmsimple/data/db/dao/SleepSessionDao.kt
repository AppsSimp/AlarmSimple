package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY trackingStartMs DESC")
    fun getAllSessions(): Flow<List<SleepSessionEntity>>
    
    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SleepSessionEntity?
    
    @Query("SELECT * FROM sleep_sessions ORDER BY trackingStartMs DESC LIMIT 1")
    suspend fun getLatestSession(): SleepSessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSessionEntity): Long
    
    @Update
    suspend fun updateSession(session: SleepSessionEntity)
    
    @Delete
    suspend fun deleteSession(session: SleepSessionEntity)
    
    @Query("DELETE FROM sleep_sessions WHERE trackingStartMs < :beforeMs")
    suspend fun deleteSessionsBefore(beforeMs: Long)
}
