package com.appssimple.alarmsimple.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.appssimple.alarmsimple.AlarmSimpleApp
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.domain.model.SleepPhase
import com.appssimple.alarmsimple.sensor.AudioAnalyzer
import com.appssimple.alarmsimple.sensor.SensorFusion
import com.appssimple.alarmsimple.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service that runs overnight with FOREGROUND_SERVICE_TYPE_MICROPHONE.
 * Collects sleep sensor data, records snippets, and triggers the alarm when target duration / light phase is reached.
 */
@AndroidEntryPoint
class SleepTrackingService : Service() {

    @Inject lateinit var sensorFusion: SensorFusion
    @Inject lateinit var audioAnalyzer: AudioAnalyzer
    @Inject lateinit var sleepSessionDao: SleepSessionDao
    @Inject lateinit var sleepEpochDao: SleepEpochDao
    @Inject lateinit var audioSnippetDao: AudioSnippetDao
    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentSessionId: Long = 0
    private var targetAlarmId: Long = 0
    private var isDurationMode: Boolean = false
    private var targetDurationMinutes: Int = 0
    private var isSmartWakeEnabled: Boolean = false
    private var smartWakeWindowMinutes: Int = 30
    private var scheduledAlarmTimeMs: Long = 0

    private var sleepOnsetMs: Long? = null
    private var disturbanceCount: Int = 0
    private var snoreMinutesCount: Int = 0

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_TRACKING = "com.appssimple.alarmsimple.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.appssimple.alarmsimple.ACTION_STOP_TRACKING"
        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmSimple::SleepTrackingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(11 * 60 * 60 * 1000L) // 11 hours maximum safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                startTracking(alarmId)
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    private fun startTracking(alarmId: Long) {
        targetAlarmId = alarmId
        startForegroundServiceWithNotification()

        serviceScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm != null) {
                isDurationMode = alarm.mode == AlarmMode.DURATION.name
                targetDurationMinutes = alarm.durationMinutes ?: (7 * 60 + 30)
                isSmartWakeEnabled = alarm.smartWakeEnabled
                smartWakeWindowMinutes = alarm.smartWakeWindowMinutes
                scheduledAlarmTimeMs = alarm.targetTimeMs ?: 0L
            }

            // Create new SleepSession in DB
            val session = SleepSessionEntity(
                alarmId = alarmId,
                trackingStartMs = System.currentTimeMillis()
            )
            currentSessionId = sleepSessionDao.insertSession(session)

            // Start sensors
            sensorFusion.start(currentSessionId)

            // Listen for sensor events
            launch {
                sensorFusion.fusionEpochFlow.collect { epochState ->
                    handleEpochState(epochState)
                }
            }

            // Listen for audio snippets recorded
            launch {
                audioAnalyzer.snippetFlow.collect { snippetReq ->
                    audioSnippetDao.insertSnippet(
                        AudioSnippetEntity(
                            sessionId = currentSessionId,
                            timestampMs = snippetReq.timestampMs,
                            type = snippetReq.type.name,
                            durationSeconds = snippetReq.durationSeconds,
                            filePath = snippetReq.audioFile.absolutePath,
                            peakAmplitude = snippetReq.peakAmplitude
                        )
                    )
                }
            }
        }
    }

    private suspend fun handleEpochState(epochState: com.appssimple.alarmsimple.sensor.FusionEpochState) {
        // Record epoch in database
        sleepEpochDao.insertEpoch(
            SleepEpochEntity(
                sessionId = currentSessionId,
                timestampMs = epochState.timestampMs,
                phase = epochState.phase.name,
                movementIntensity = epochState.movementIntensity,
                audioEvent = epochState.dominantAudioClass?.name
            )
        )

        if (epochState.dominantAudioClass == com.appssimple.alarmsimple.domain.model.AudioClass.SNORE) {
            snoreMinutesCount += 1 // ~30 sec
        }
        if (epochState.phase == SleepPhase.AWAKE && sleepOnsetMs != null) {
            disturbanceCount++
        }

        // Sleep onset detection
        if (epochState.isSleepOnsetDetected && sleepOnsetMs == null) {
            val now = epochState.timestampMs
            sleepOnsetMs = now

            // Update session in DB
            val existing = sleepSessionDao.getSessionById(currentSessionId)
            if (existing != null) {
                sleepSessionDao.updateSession(existing.copy(sleepOnsetMs = now))
            }

            // In DURATION mode: real sleep detected -> schedule alarm now!
            if (isDurationMode) {
                val wakeTimeMs = now + (targetDurationMinutes * 60_000L)
                scheduledAlarmTimeMs = wakeTimeMs
                alarmScheduler.scheduleAlarm(wakeTimeMs, targetAlarmId)
            }
        }

        // Smart Wake-Up evaluation
        if (isSmartWakeEnabled && scheduledAlarmTimeMs > 0) {
            val now = epochState.timestampMs
            val windowStartMs = scheduledAlarmTimeMs - (smartWakeWindowMinutes * 60_000L)

            // Are we inside the smart wake window?
            if (now in windowStartMs..scheduledAlarmTimeMs) {
                // If light sleep phase or movement detected -> trigger alarm right now!
                if (epochState.isLightPhaseForWakeUp) {
                    triggerAlarmEarly()
                }
            }
        }
    }

    private fun triggerAlarmEarly() {
        val alarmIntent = Intent(this, AlarmFiringService::class.java).apply {
            putExtra(AlarmFiringService.EXTRA_ALARM_ID, targetAlarmId)
            putExtra(AlarmFiringService.EXTRA_EARLY_WAKE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(alarmIntent)
        } else {
            startService(alarmIntent)
        }
    }

    private fun stopTracking() {
        serviceScope.launch {
            sensorFusion.stop()

            val existing = sleepSessionDao.getSessionById(currentSessionId)
            if (existing != null) {
                sleepSessionDao.updateSession(
                    existing.copy(
                        wakeUpMs = System.currentTimeMillis(),
                        disturbanceCount = disturbanceCount,
                        snoreMinutes = snoreMinutesCount / 2
                    )
                )
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AlarmSimpleApp.CHANNEL_SLEEP_TRACKING)
            .setContentTitle(getString(R.string.channel_tracking_name))
            .setContentText(getString(R.string.channel_tracking_description))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
