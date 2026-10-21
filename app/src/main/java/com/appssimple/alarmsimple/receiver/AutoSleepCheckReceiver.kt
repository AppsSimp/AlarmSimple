package com.appssimple.alarmsimple.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.service.AlarmScheduler
import com.appssimple.alarmsimple.service.SleepTrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AutoSleepCheckReceiver : BroadcastReceiver() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    companion object {
        const val ACTION_CHECK_AUTO_SLEEP = "com.appssimple.alarmsimple.ACTION_CHECK_AUTO_SLEEP"
        private const val TEN_HOURS_MS = 10 * 3600_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isAutoEnabled = userPreferences.autoSleepTrackingEnabled.first()
                if (!isAutoEnabled) return@launch

                if (SleepTrackingService.isTracking.value) return@launch

                val now = System.currentTimeMillis()
                val activeAlarms = alarmDao.getActiveAlarms().firstOrNull() ?: emptyList()
                val nextAlarm = activeAlarms
                    .filter { (it.targetTimeMs ?: 0L) > now }
                    .minByOrNull { it.targetTimeMs!! }
                    ?: return@launch

                val timeUntilAlarm = nextAlarm.targetTimeMs!! - now

                if (timeUntilAlarm > TEN_HOURS_MS) {
                    // Еще рано (больше 10 часов до будильника) - ставим проверку ровно за 10 часов
                    alarmScheduler.scheduleAutoSleepCheckAt(nextAlarm.targetTimeMs!! - TEN_HOURS_MS)
                    return@launch
                }

                if (timeUntilAlarm <= 15 * 60_000L) {
                    // Меньше 15 минут до будильника - нет смысла запускать трекинг
                    return@launch
                }

                // В пределах 10 часов до будильника. Проверяем, выключен ли экран.
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = powerManager?.isInteractive ?: false
                if (isInteractive) {
                    // Пользователь всё ещё пользуется телефоном. Повторим проверку через 15 минут.
                    alarmScheduler.scheduleAutoSleepCheckAt(now + 15 * 60_000L)
                    return@launch
                }

                // Экран выключен. Проверяем акселерометр в течение 3 секунд на неподвижность.
                val isStationary = checkStillness(context, 3000L)

                if (isStationary) {
                    // Телефон лежит неподвижно в кровати — автоматически запускаем сервис трекинга!
                    val serviceIntent = Intent(context, SleepTrackingService::class.java).apply {
                        action = SleepTrackingService.ACTION_START_TRACKING
                        putExtra(SleepTrackingService.EXTRA_ALARM_ID, nextAlarm.id)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    // Телефон еще перемещается — повторная проверка через 15 минут
                    alarmScheduler.scheduleAutoSleepCheckAt(now + 15 * 60_000L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkStillness(context: Context, durationMs: Long): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return true
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return true

        var maxDelta = 0f
        var lastX = Float.NaN
        var lastY = Float.NaN
        var lastZ = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (!lastX.isNaN()) {
                    val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                    if (delta > maxDelta) {
                        maxDelta = delta
                    }
                }
                lastX = x
                lastY = y
                lastZ = z
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        try {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            delay(durationMs)
        } finally {
            sensorManager.unregisterListener(listener)
        }

        // Если максимальное отклонение за 3 секунды меньше 0.25 м/с² — телефон лежит неподвижно
        return maxDelta < 0.25f
    }
}
