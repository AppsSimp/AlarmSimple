package com.appssimple.alarmsimple.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Domain model for a night's sleep session with computed statistics.
 */
data class SleepSession(
    val id: Long = 0,
    val alarmId: Long,
    val trackingStart: Instant,
    val sleepOnset: Instant? = null,
    val wakeUp: Instant? = null,
    val disturbanceCount: Int = 0,
    val snoreMinutes: Int = 0,
    val epochs: List<SleepEpoch> = emptyList()
) {
    /** Total time in bed from tracking start to wake-up */
    val timeInBed: Duration?
        get() = wakeUp?.let { Duration.between(trackingStart, it) }
    
    /** Actual sleep duration from sleep onset to wake-up */
    val totalSleep: Duration?
        get() {
            val onset = sleepOnset ?: return null
            val wake = wakeUp ?: return null
            return Duration.between(onset, wake)
        }
    
    /** Sleep efficiency: totalSleep / timeInBed as percentage */
    val sleepEfficiency: Float?
        get() {
            val sleep = totalSleep?.toMillis()?.toFloat() ?: return null
            val inBed = timeInBed?.toMillis()?.toFloat() ?: return null
            if (inBed <= 0) return null
            return (sleep / inBed) * 100f
        }
}
