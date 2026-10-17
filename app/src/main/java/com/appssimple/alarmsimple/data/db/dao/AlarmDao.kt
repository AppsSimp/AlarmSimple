package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY CASE WHEN mode = 'CLASSIC' THEN targetTimeMs ELSE createdAt END ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>
    
    @Query("SELECT * FROM alarms WHERE isActive = 1")
    fun getActiveAlarms(): Flow<List<AlarmEntity>>
    
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): AlarmEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long
    
    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)
    
    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)
    
    @Query("UPDATE alarms SET isActive = :isActive WHERE id = :id")
    suspend fun setAlarmActive(id: Long, isActive: Boolean)
}
