package com.appssimple.alarmsimple.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.appssimple.alarmsimple.service.SleepTrackingService

/**
 * Высокоприоритетный Wakeup-приемник системного таймера (AlarmManager RTC_WAKEUP).
 * Каждые 5 минут будит процессор из глубокого энергосбережения Android (Doze Mode),
 * продлевает WakeLock сервиса трекинга сна и гарантирует, что сенсоры не засыпают ночью.
 */
class SleepHeartbeatReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SLEEP_HEARTBEAT = "com.appssimple.alarmsimple.ACTION_SLEEP_HEARTBEAT"
        const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L // 5 минут

        fun scheduleNext(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SleepHeartbeatReceiver::class.java).apply {
                    action = ACTION_SLEEP_HEARTBEAT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9090,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancel(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SleepHeartbeatReceiver::class.java).apply {
                    action = ACTION_SLEEP_HEARTBEAT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9090,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            } catch (ignored: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SLEEP_HEARTBEAT) {
            if (SleepTrackingService.isTracking.value) {
                // Поддерживаем процессор кратковременным WakeLock приемника на 15 секунд
                try {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val tempWakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "AlarmSimple::HeartbeatWakeLock"
                    )
                    tempWakeLock?.acquire(15_000L)
                } catch (ignored: Exception) {}

                // Оповещаем сервис трекинга об успешном пульсе
                try {
                    val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
                        action = SleepTrackingService.ACTION_HEARTBEAT
                    }
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Планируем следующий пульс через 5 минут
                scheduleNext(context)
            }
        }
    }
}
