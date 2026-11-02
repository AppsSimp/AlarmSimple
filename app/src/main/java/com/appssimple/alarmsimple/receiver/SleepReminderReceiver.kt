package com.appssimple.alarmsimple.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.appssimple.alarmsimple.AlarmSimpleApp
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.service.AlarmScheduler
import com.appssimple.alarmsimple.service.SleepTrackingService
import com.appssimple.alarmsimple.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Вместо автозапуска трекинга из фона (Android 14+ запрещает микрофон фоновым приложениям):
 * за N часов до будильника (7/8/9/10, по настройке) присылает уведомление-напоминание.
 * Тап по уведомлению открывает приложение и сразу включает полный анализ сна с микрофоном.
 *
 * Приёмник самоподдерживающийся: после срабатывания будильника планирует следующую проверку
 * и цепочка живёт, пока в БД есть будильники на будущее.
 */
@AndroidEntryPoint
class SleepReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    companion object {
        const val ACTION_SLEEP_REMINDER_CHECK = "com.appssimple.alarmsimple.ACTION_SLEEP_REMINDER_CHECK"
        const val NOTIFICATION_ID = 7001
        private const val THIRTY_MINUTES_MS = 30 * 60_000L
        private const val TEN_HOURS_MS = 10 * 3600_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isReminderEnabled = userPreferences.autoSleepTrackingEnabled.first()
                if (!isReminderEnabled) return@launch

                val now = System.currentTimeMillis()

                var nextAlarm = findNextFutureAlarm()
                if (nextAlarm == null) {
                    // Будильники могли «протухнуть» (телефон был выключен в момент срабатывания) — восстанавливаем
                    alarmScheduler.rescheduleAllAlarms()
                    nextAlarm = findNextFutureAlarm()
                    if (nextAlarm == null) {
                        // Будущих будильников нет — цепочка завершается и перезапустится
                        // при сохранении будильника, открытии приложения или перезагрузке.
                        return@launch
                    }
                }

                val alarmTime = nextAlarm.targetTimeMs!!
                val timeUntilAlarm = alarmTime - now
                val hoursBefore = userPreferences.sleepReminderHours.first()
                val reminderWindowMs = hoursBefore * 3600_000L

                if (timeUntilAlarm > reminderWindowMs) {
                    // Рано: ставим напоминание ровно за N часов до будильника
                    alarmScheduler.scheduleSleepReminderAt(alarmTime - reminderWindowMs)
                    return@launch
                }

                if (timeUntilAlarm <= 15 * 60_000L) {
                    // Будильник вот-вот сработает — напоминание уже неактуально.
                    // Планируем следующую проверку после срабатывания (на следующую ночь).
                    alarmScheduler.scheduleSleepReminderAt(alarmTime + THIRTY_MINUTES_MS)
                    return@launch
                }

                // Мы в окне «пора спать». Показываем напоминание (если давно не показывали).
                val lastShownMs = userPreferences.sleepReminderLastShownMs.first()
                if (now - lastShownMs > TEN_HOURS_MS) {
                    showReminderNotification(context, nextAlarm.id, hoursBefore, timeUntilAlarm)
                    userPreferences.setSleepReminderLastShownMs(now)
                }

                // Следующая проверка — после срабатывания будильника, чтобы покрыть следующую ночь
                alarmScheduler.scheduleSleepReminderAt(alarmTime + THIRTY_MINUTES_MS)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun findNextFutureAlarm() = alarmDao.getActiveAlarms().firstOrNull()
        ?.filter { (it.targetTimeMs ?: 0L) > System.currentTimeMillis() }
        ?.minByOrNull { it.targetTimeMs!! }

    private fun showReminderNotification(
        context: Context,
        alarmId: Long,
        hoursBefore: Int,
        timeUntilAlarm: Long
    ) {
        try {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(SleepTrackingService.EXTRA_AUTO_START_TRACKING, true)
                putExtra(SleepTrackingService.EXTRA_ALARM_ID, alarmId)
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hoursLeft = (timeUntilAlarm / 3600_000L).toInt()
            val notification = NotificationCompat.Builder(context, AlarmSimpleApp.CHANNEL_SLEEP_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Пора готовиться ко сну 🌙")
                .setContentText(
                    "Через $hoursLeft ч будильник. Нажмите, чтобы включить анализ сна — с микрофоном и записью звуков."
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
