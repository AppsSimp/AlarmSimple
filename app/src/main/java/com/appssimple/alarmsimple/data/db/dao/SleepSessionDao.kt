package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    /**
     * Возвращает только полноценные сессии сна:
     * факт сна зафиксирован (sleepOnsetMs != null), длился не менее 10 минут (600_000 мс)
     * и не более 14 часов (50_400_000 мс) — «суточники» от зависшего сервиса отфильтрованы.
     */
    @Query("SELECT * FROM sleep_sessions WHERE sleepOnsetMs IS NOT NULL AND wakeUpMs IS NOT NULL AND (wakeUpMs - sleepOnsetMs) >= 600000 AND (wakeUpMs - sleepOnsetMs) <= 50400000 ORDER BY trackingStartMs DESC")
    fun getAllValidSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE wakeUpMs >= :fromMs AND wakeUpMs < :toMs ORDER BY wakeUpMs ASC")
    suspend fun getSessionsByWakeUpRange(fromMs: Long, toMs: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_sessions ORDER BY trackingStartMs DESC")
    fun getAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE sleepOnsetMs IS NOT NULL AND wakeUpMs IS NOT NULL AND (wakeUpMs - sleepOnsetMs) >= 600000 AND (wakeUpMs - sleepOnsetMs) <= 50400000 ORDER BY trackingStartMs DESC LIMIT 1")
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

    @Query("DELETE FROM sleep_sessions WHERE sleepOnsetMs IS NULL OR wakeUpMs IS NULL OR (wakeUpMs - sleepOnsetMs) < 600000 OR (wakeUpMs - sleepOnsetMs) > 50400000")
    suspend fun cleanDiscardedSessions()

    /** Завершённые сессии, в которых не было ни одной эпохи сна — только пробуждения. */
    @Query(
        "DELETE FROM sleep_sessions WHERE wakeUpMs IS NOT NULL AND id IN (" +
            "SELECT sessionId FROM sleep_epochs GROUP BY sessionId " +
            "HAVING SUM(CASE WHEN phase = 'AWAKE' THEN 1 ELSE 0 END) = COUNT(*))"
    )
    suspend fun deleteAwakeOnlySessions()
}
