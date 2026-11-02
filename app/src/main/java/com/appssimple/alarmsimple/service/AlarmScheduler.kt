package com.appssimple.alarmsimple.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.receiver.AlarmReceiver
import com.appssimple.alarmsimple.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val userPreferences: UserPreferences
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

    companion object {
        const val FAILSAFE_ALARM_ID = 999999L
        const val ACTION_FIRE_FAILSAFE = "com.appssimple.alarmsimple.ACTION_FIRE_FAILSAFE"
        const val BACKUP_ALARM_LABEL = "AlarmSimple (Предохранитель)"
    }

    /**
     * Ставит страховочный системный будильник (+5 минут).
     * 1. Внутренний аппаратный RTC будильник в Android AlarmManager (гарантия пробуждения устройства).
     * 2. Системный будильник в стандартных часах Android (ACTION_SET_ALARM).
     * Гарантирует, что пользователя НЕ выкинет из AlarmSimple (или мгновенно возвращает его назад).
     */
    fun setSystemBackupAlarm(triggerTimeMs: Long, label: String = BACKUP_ALARM_LABEL) {
        val backupTimeMs = triggerTimeMs + 5 * 60_000L
        val backupCal = java.util.Calendar.getInstance().apply {
            timeInMillis = backupTimeMs
        }

        // 1. Аппаратный предохранитель в AlarmManager
        scheduleInternalFailsafe(backupTimeMs)

        // 2. Системный предохранитель в стандартных часах Android
        try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, backupCal.get(java.util.Calendar.HOUR_OF_DAY))
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, backupCal.get(java.util.Calendar.MINUTE))
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(android.provider.AlarmClock.EXTRA_VIBRATE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }

            val clockPackage = findSystemClockPackage()
            if (clockPackage != null) {
                intent.setPackage(clockPackage)
            }

            context.startActivity(intent)

            // Защита от вылета: мгновенно возвращаем AlarmSimple на передний план
            bringAppToFront()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Отключает системный будильник, когда будильник приложения благополучно сработал или отключен.
     */
    fun dismissSystemBackupAlarm(label: String = BACKUP_ALARM_LABEL) {
        cancelInternalFailsafe()

        try {
            val intent = Intent(android.provider.AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_ALARM_SEARCH_MODE, android.provider.AlarmClock.ALARM_SEARCH_MODE_LABEL)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            val clockPackage = findSystemClockPackage()
            if (clockPackage != null) {
                intent.setPackage(clockPackage)
            }
            context.startActivity(intent)

            bringAppToFront()
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(android.provider.AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                val clockPackage = findSystemClockPackage()
                if (clockPackage != null) {
                    fallbackIntent.setPackage(clockPackage)
                }
                context.startActivity(fallbackIntent)
                bringAppToFront()
            } catch (ignored: Exception) {}
        }
    }

    private fun findSystemClockPackage(): String? {
        try {
            val testIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
            val matches = context.packageManager.queryIntentActivities(
                testIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            if (matches.isEmpty()) return null

            val systemMatch = matches.firstOrNull {
                val appInfo = it.activityInfo.applicationInfo
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            }
            return systemMatch?.activityInfo?.packageName ?: matches.firstOrNull()?.activityInfo?.packageName
        } catch (e: Exception) {
            return null
        }
    }

    private fun bringAppToFront() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            kotlinx.coroutines.delay(250)
            launchBringBackIntent()
            kotlinx.coroutines.delay(400)
            launchBringBackIntent()
        }
    }

    private fun launchBringBackIntent() {
        try {
            val bringBackIntent = Intent(context, com.appssimple.alarmsimple.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(bringBackIntent)
        } catch (ignored: Exception) {}
    }

    private fun scheduleInternalFailsafe(triggerTimeMs: Long) {
        try {
            val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE_FAILSAFE
            }
            val operationPendingIntent = PendingIntent.getBroadcast(
                context,
                FAILSAFE_ALARM_ID.toInt(),
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                FAILSAFE_ALARM_ID.toInt(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, operationPendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelInternalFailsafe() {
        try {
            val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE_FAILSAFE
            }
            val operationPendingIntent = PendingIntent.getBroadcast(
                context,
                FAILSAFE_ALARM_ID.toInt(),
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(operationPendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncBackupAlarmState(enabled: Boolean) {
        if (!enabled) {
            dismissSystemBackupAlarm()
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val activeAlarms = alarmDao.getActiveAlarms().firstOrNull() ?: emptyList()
            val validAlarms = activeAlarms.filter { it.isActive && it.targetTimeMs != null }
            val nearest = validAlarms.minByOrNull { it.targetTimeMs ?: Long.MAX_VALUE }
            if (nearest?.targetTimeMs != null && nearest.targetTimeMs > System.currentTimeMillis()) {
                setSystemBackupAlarm(nearest.targetTimeMs)
            }
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

    fun scheduleSleepReminderAt(triggerTimeMs: Long) {
        try {
            val now = System.currentTimeMillis()
            val target = if (triggerTimeMs < now + 60_000L) now + 60_000L else triggerTimeMs
            val intent = Intent(context, com.appssimple.alarmsimple.receiver.SleepReminderReceiver::class.java).apply {
                action = com.appssimple.alarmsimple.receiver.SleepReminderReceiver.ACTION_SLEEP_REMINDER_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, target, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelSleepReminder() {
        try {
            val intent = Intent(context, com.appssimple.alarmsimple.receiver.SleepReminderReceiver::class.java).apply {
                action = com.appssimple.alarmsimple.receiver.SleepReminderReceiver.ACTION_SLEEP_REMINDER_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Полное восстановление всех активных будильников из БД.
     * Протухшие повторяющиеся будильники пересчитываются на следующее срабатывание,
     * разовые в прошлом деактивируются. Вызывается после загрузки телефона, обновления
     * APK (Android стирает все AlarmManager-алармы) и при открытии приложения.
     */
    suspend fun rescheduleAllAlarms() {
        try {
            val activeAlarms = alarmDao.getActiveAlarms().firstOrNull() ?: emptyList()
            val now = System.currentTimeMillis()

            for (alarm in activeAlarms) {
                if (alarm.mode == AlarmMode.CLASSIC.name && alarm.targetTimeMs != null) {
                    val repeatDays = runCatching {
                        val arr = org.json.JSONArray(alarm.repeatDays)
                        (0 until arr.length()).map { arr.getInt(it) }.toSet()
                    }.getOrDefault(emptySet())

                    val target = if (alarm.targetTimeMs > now) {
                        alarm.targetTimeMs
                    } else if (repeatDays.isNotEmpty()) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = alarm.targetTimeMs }
                        val next = calculateNextTriggerTime(
                            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                            minute = cal.get(java.util.Calendar.MINUTE),
                            repeatDays = repeatDays,
                            repeatIntervalWeeks = alarm.repeatIntervalWeeks
                        )
                        alarmDao.updateAlarm(alarm.copy(targetTimeMs = next, isActive = true))
                        next
                    } else {
                        // Разовый будильник в прошлом — выключаем
                        alarmDao.setAlarmActive(alarm.id, false)
                        null
                    }

                    if (target != null) {
                        scheduleAlarm(target, alarm.id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Армирует проверку-напоминание об анализе сна через минуту (если включено в настройках).
     * Приёмник сам выстроит дальнейшую цепочку. Безопасно вызывать повторно.
     */
    suspend fun ensureSleepReminderScheduled() {
        try {
            val enabled = userPreferences.autoSleepTrackingEnabled.firstOrNull() ?: false
            if (enabled) {
                scheduleSleepReminderAt(System.currentTimeMillis() + 60_000L)
            } else {
                cancelSleepReminder()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
