package com.appssimple.alarmsimple.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var sleepSessionDao: SleepSessionDao
    @Inject lateinit var sleepEpochDao: SleepEpochDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Сохранение незавершённой сессии сна при внезапной перезагрузке телефона ночью
                    val latestSession = sleepSessionDao.getLatestSession()
                    if (latestSession != null && latestSession.wakeUpMs == null) {
                        // Сессия была оборвана (например, ночной перезагрузкой ОС)
                        val lastEpoch = sleepEpochDao.getLatestEpochForSession(latestSession.id)
                        val inferredWakeUpMs = lastEpoch?.timestampMs ?: (latestSession.trackingStartMs + 30 * 60_000L)
                        val onset = latestSession.sleepOnsetMs ?: latestSession.trackingStartMs
                        val duration = inferredWakeUpMs - onset

                        // Если отслежено больше 10 минут сна — бережно сохраняем сессию
                        if (duration >= 600_000L) {
                            sleepSessionDao.updateSession(
                                latestSession.copy(
                                    wakeUpMs = inferredWakeUpMs,
                                    sleepOnsetMs = onset
                                )
                            )
                        }
                    }

                    // 2. Восстановление будильников
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
                                val next = alarmScheduler.calculateNextTriggerTime(
                                    hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                                    minute = cal.get(java.util.Calendar.MINUTE),
                                    repeatDays = repeatDays,
                                    repeatIntervalWeeks = alarm.repeatIntervalWeeks
                                )
                                alarmDao.updateAlarm(alarm.copy(targetTimeMs = next))
                                next
                            } else {
                                // Разовый будильник в прошлом - выключаем
                                alarmDao.setAlarmActive(alarm.id, false)
                                null
                            }

                            if (target != null) {
                                alarmScheduler.scheduleAlarm(target, alarm.id)
                            }
                        }
                    }

                    // 3. Запуск проверки авто-трекинга сна
                    alarmScheduler.scheduleAutoSleepCheckAt(System.currentTimeMillis() + 60_000L)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
