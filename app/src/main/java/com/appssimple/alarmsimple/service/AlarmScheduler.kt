package com.appssimple.alarmsimple.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.appssimple.alarmsimple.receiver.AlarmReceiver
import com.appssimple.alarmsimple.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(triggerTimeMs: Long, alarmId: Long) {
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SleepTrackingService.EXTRA_ALARM_ID, alarmId)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(SleepTrackingService.EXTRA_ALARM_ID, alarmId)
        }
        val operationPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operationPendingIntent)
    }

    /**
     * Вычисляет следующий миллисекундный таймстемп для будильника с учетом часов, минут и дней повторения (1..7: Пн..Вс).
     */
    fun calculateNextTriggerTime(hour: Int, minute: Int, repeatDays: Set<Int>): Long {
        val now = java.util.Calendar.getInstance()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            if (calendar.timeInMillis <= now.timeInMillis) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        // Повторение по дням недели (1 = Пн, 2 = Вт, ..., 7 = Вс)
        // В Calendar: SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        for (i in 0..7) {
            val candidateCal = (calendar.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, i)
            }
            if (candidateCal.timeInMillis > now.timeInMillis) {
                val calDay = candidateCal.get(java.util.Calendar.DAY_OF_WEEK)
                val appDay = if (calDay == java.util.Calendar.SUNDAY) 7 else calDay - 1
                if (repeatDays.contains(appDay)) {
                    return candidateCal.timeInMillis
                }
            }
        }

        // Запасной fallback на завтра
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    fun cancelAlarm(alarmId: Long) {
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
        }
        val operationPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(operationPendingIntent)
    }
}
