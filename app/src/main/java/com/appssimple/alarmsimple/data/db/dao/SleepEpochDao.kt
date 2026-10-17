package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepEpochDao {
    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getEpochsForSession(sessionId: Long): Flow<List<SleepEpochEntity>>
    
    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getEpochsForSessionOnce(sessionId: Long): List<SleepEpochEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpoch(epoch: SleepEpochEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpochs(epochs: List<SleepEpochEntity>)
    
    @Query("DELETE FROM sleep_epochs WHERE sessionId = :sessionId")
    suspend fun deleteEpochsForSession(sessionId: Long)
}
