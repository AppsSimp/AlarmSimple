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
     * Вычисляет следующий миллисекундный таймстемп для будильника с учетом часов, минут, дней повторения (1..7: Пн..Вс)
     * и интервала недель (1 = каждую неделю, 2 = через неделю, 3 = через 2 недели).
     */
    fun calculateNextTriggerTime(
        hour: Int,
        minute: Int,
        repeatDays: Set<Int>,
        repeatIntervalWeeks: Int = 1
    ): Long {
        val now = java.util.Calendar.getInstance()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (repeatDays.isEmpty()) {
            if (calendar.timeInMillis <= now.timeInMillis) {
                // Если интервал повтора больше 1 недели
                if (repeatIntervalWeeks > 1) {
                    calendar.add(java.util.Calendar.WEEK_OF_YEAR, repeatIntervalWeeks)
                } else {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            return calendar.timeInMillis
        }

        // Повторение по дням недели (1 = Пн, 2 = Вт, ..., 7 = Вс)
        val maxDaysSearch = if (repeatIntervalWeeks > 1) 7 * repeatIntervalWeeks else 7
        for (i in 0..maxDaysSearch) {
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

        // Запасной fallback
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /**
     * Ставит страховочный системный будильник через стандартный Android AlarmClock Intent (+5 минут).
     */
    fun setSystemBackupAlarm(triggerTimeMs: Long, label: String = "AlarmSimple (Предохранитель)") {
        try {
            val backupCal = java.util.Calendar.getInstance().apply {
                timeInMillis = triggerTimeMs + 5 * 60_000L
            }
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, backupCal.get(java.util.Calendar.HOUR_OF_DAY))
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, backupCal.get(java.util.Calendar.MINUTE))
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Отключает системный будильник, когда будильник приложения благополучно сработал.
     */
    fun dismissSystemBackupAlarm(label: String = "AlarmSimple (Предохранитель)") {
        try {
            val intent = Intent(android.provider.AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_ALARM_SEARCH_MODE, android.provider.AlarmClock.ALARM_SEARCH_MODE_LABEL)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Запасной вариант без параметров, если производитель не поддерживает фильтрацию по лейблу
            try {
                val fallbackIntent = Intent(android.provider.AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
        }
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
