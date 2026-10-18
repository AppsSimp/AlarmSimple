package com.appssimple.alarmsimple.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents one night's sleep tracking session.
 */
@Entity(
    tableName = "sleep_sessions",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("alarmId")]
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** The alarm that initiated this tracking session. Null for manual sleep tracking. */
    val alarmId: Long? = null,
    
    /** When tracking started (user pressed "Start") */
    val trackingStartMs: Long,
    
    /** When the user actually fell asleep (detected by sensors). Null until detected. */
    val sleepOnsetMs: Long? = null,
    
    /** When the user woke up. Null until alarm is dismissed. */
    val wakeUpMs: Long? = null,
    
    /** Number of times the user woke up briefly during the night */
    val disturbanceCount: Int = 0,
    
    /** Total minutes of detected snoring */
    val snoreMinutes: Int = 0
)
