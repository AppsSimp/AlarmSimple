package com.appssimple.alarmsimple.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Domain model for an alarm.
 */
data class Alarm(
    val id: Long = 0,
    val mode: AlarmMode = AlarmMode.CLASSIC,
    val targetTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val smartWakeEnabled: Boolean = false,
    val smartWakeWindowMinutes: Int = 30,
    val questType: QuestType = QuestType.NONE,
    val questData: String? = null,
    val sirenEnabled: Boolean = false,
    val volumeLockEnabled: Boolean = false,
    val ringtoneUri: String? = null,
    val isActive: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val snoozeDurationMinutes: Int = 5
)
