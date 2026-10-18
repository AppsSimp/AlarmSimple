package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    /**
     * Возвращает только полноценные сессии сна:
     * факт сна был зафиксирован (sleepOnsetMs != null) и длился не менее 10 минут (600_000 мс).
     */
    @Query("SELECT * FROM sleep_sessions WHERE sleepOnsetMs IS NOT NULL AND wakeUpMs IS NOT NULL AND (wakeUpMs - sleepOnsetMs) >= 600000 ORDER BY trackingStartMs DESC")
    fun getAllValidSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions ORDER BY trackingStartMs DESC")
    fun getAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE sleepOnsetMs IS NOT NULL AND wakeUpMs IS NOT NULL AND (wakeUpMs - sleepOnsetMs) >= 600000 ORDER BY trackingStartMs DESC LIMIT 1")
    suspend fun getLatestValidSession(): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions ORDER BY trackingStartMs DESC LIMIT 1")
    suspend fun getLatestSession(): SleepSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Update
    suspend fun updateSession(session: SleepSessionEntity)

    @Delete
    suspend fun deleteSession(session: SleepSessionEntity)

    @Query("DELETE FROM sleep_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sleep_sessions WHERE trackingStartMs < :beforeMs")
    suspend fun deleteSessionsBefore(beforeMs: Long)

    @Query("DELETE FROM sleep_sessions WHERE sleepOnsetMs IS NULL OR wakeUpMs IS NULL OR (wakeUpMs - sleepOnsetMs) < 600000")
    suspend fun cleanDiscardedSessions()
}
