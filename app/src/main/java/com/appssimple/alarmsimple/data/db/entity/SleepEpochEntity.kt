package com.appssimple.alarmsimple.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single 30-second epoch of sleep data within a session.
 * Contains the classified sleep phase and sensor readings.
 */
@Entity(
    tableName = "sleep_epochs",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SleepEpochEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** The sleep session this epoch belongs to */
    val sessionId: Long,
    
    /** Timestamp of this epoch */
    val timestampMs: Long,
    
    /** Sleep phase: AWAKE, LIGHT, DEEP, REM */
    val phase: String,
    
    /** Movement intensity value for this epoch */
    val movementIntensity: Float,
    
    /** Audio event detected: SNORE, NOISE, SPEECH, or null */
    val audioEvent: String? = null
)
