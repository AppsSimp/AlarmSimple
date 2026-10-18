package com.appssimple.alarmsimple.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a configured alarm with all its settings.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** CLASSIC or DURATION */
    val mode: String,
    
    /** Target time in milliseconds since midnight (for CLASSIC mode) */
    val targetTimeMs: Long? = null,
    
    /** Sleep duration in minutes (for DURATION mode) */
    val durationMinutes: Int? = null,
    
    /** Whether smart wake-up is enabled */
    val smartWakeEnabled: Boolean = false,
    
    /** Smart wake window in minutes (e.g., 30 means alarm can fire 30 min before target) */
    val smartWakeWindowMinutes: Int = 30,
    
    /** Quest type: NONE, NFC, BARCODE, MATH */
    val questType: String = "NONE",
    
    /** JSON string with quest-specific data (tagId, barcodeValue, etc.) */
    val questData: String? = null,
    
    /** Whether siren mode is enabled (loud alarm after 5 min quest timeout) */
    val sirenEnabled: Boolean = false,
    
    /** Whether hardware volume buttons are blocked during alarm */
    val volumeLockEnabled: Boolean = false,
    
    /** URI of the selected ringtone */
    val ringtoneUri: String? = null,
    
    /** Whether this alarm is currently active/scheduled */
    val isActive: Boolean = true,
    
    /** JSON array of repeat days: ["MON","TUE",...] or empty for one-time */
    val repeatDays: String = "[]",

    /** Interval in weeks: 1 = every week, 2 = every 2 weeks, 3 = every 3 weeks */
    val repeatIntervalWeeks: Int = 1,
    
    /** User-facing label for the alarm */
    val label: String = "",
    
    /** Snooze duration in minutes (5, 10, or 15). Only used when quests are off. */
    val snoozeDurationMinutes: Int = 5,
    
    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis()
)
