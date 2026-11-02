package com.appssimple.alarmsimple.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var sleepSessionDao: SleepSessionDao
    @Inject lateinit var sleepEpochDao: SleepEpochDao

    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED критичен: Android стирает ВСЕ AlarmManager-алармы при
        // обновлении APK (в т.ч. через adb install -r), а BOOT_COMPLETED при этом НЕ приходит.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Сохранение незавершённой сессии сна при внезапной перезагрузке телефона
                    val latestSession = sleepSessionDao.getLatestSession()
                    if (latestSession != null && latestSession.wakeUpMs == null) {
                        // Сессия была оборвана (например, ночной перезагрузкой ОС)
                        val lastEpoch = sleepEpochDao.getLatestEpochForSession(latestSession.id)
                        val inferredWakeUpMs = lastEpoch?.timestampMs ?: (latestSession.trackingStartMs + 30 * 60_000L)
                        val onset = latestSession.sleepOnsetMs ?: latestSession.trackingStartMs
                        val duration = inferredWakeUpMs - onset

                        // Сохраняем сессию только если отслежено 10 минут..14 часов сна.
                        // Дольше — это зависший сервис и фейковый «суточник», удаляем.
                        if (duration in 600_000L..14 * 3600_000L) {
                            sleepSessionDao.updateSession(
                                latestSession.copy(
                                    wakeUpMs = inferredWakeUpMs,
                                    sleepOnsetMs = onset
                                )
                            )
                        } else if (duration > 14 * 3600_000L) {
                            sleepSessionDao.deleteSession(latestSession)
                        }
                    }

                    // 2. Восстановление будильников (протухшие повторяющиеся пересчитываются)
                    alarmScheduler.rescheduleAllAlarms()

                    // 3. Запуск цепочки напоминаний об анализе сна
                    alarmScheduler.ensureSleepReminderScheduled()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
