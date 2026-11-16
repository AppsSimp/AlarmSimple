package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import kotlinx.coroutines.flow.Flow

/** Количество эпох по сессии: сколько было AWAKE и сколько всего записано. */
data class SessionEpochStats(
    val sessionId: Long,
    val awakeEpochs: Int,
    val totalEpochs: Int
)

@Dao
interface SleepEpochDao {
    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getEpochsForSession(sessionId: Long): Flow<List<SleepEpochEntity>>
    
    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getEpochsForSessionOnce(sessionId: Long): List<SleepEpochEntity>

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestEpochForSession(sessionId: Long): SleepEpochEntity?

    /** Статистика по всем сессиям: сколько эпох сна и сколько всего. */
    @Query(
        "SELECT sessionId, " +
            "SUM(CASE WHEN phase = 'AWAKE' THEN 1 ELSE 0 END) AS awakeEpochs, " +
            "COUNT(*) AS totalEpochs " +
            "FROM sleep_epochs GROUP BY sessionId"
    )
    fun getEpochStats(): Flow<List<SessionEpochStats>>

    @Query(
        "SELECT sessionId, " +
            "SUM(CASE WHEN phase = 'AWAKE' THEN 1 ELSE 0 END) AS awakeEpochs, " +
            "COUNT(*) AS totalEpochs " +
            "FROM sleep_epochs WHERE sessionId = :sessionId GROUP BY sessionId"
    )
    suspend fun getEpochStatsForSession(sessionId: Long): SessionEpochStats?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpoch(epoch: SleepEpochEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpochs(epochs: List<SleepEpochEntity>)
    
    @Query("DELETE FROM sleep_epochs WHERE sessionId = :sessionId")
    suspend fun deleteEpochsForSession(sessionId: Long)
}
