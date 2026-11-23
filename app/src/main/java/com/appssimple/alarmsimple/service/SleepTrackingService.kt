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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @Inject lateinit var sleepDebugLogger: com.appssimple.alarmsimple.sensor.SleepDebugLogger
    @Inject lateinit var silentAudioPlayer: com.appssimple.alarmsimple.sensor.SilentAudioPlayer

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var epochCollectorJob: Job? = null

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
    private var alarmTriggered = false
    private var lastRecordedPhase: SleepPhase? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_TRACKING = "com.appssimple.alarmsimple.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.appssimple.alarmsimple.ACTION_STOP_TRACKING"
        const val ACTION_DISARM_DURATION_ALARM = "com.appssimple.alarmsimple.ACTION_DISARM_DURATION_ALARM"
        const val ACTION_HEARTBEAT = "com.appssimple.alarmsimple.ACTION_HEARTBEAT"
        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"

        /**
         * Приложение запущено из уведомления-напоминания — можно сразу стартовать трекинг
         * с микрофоном (приложение на экране, ограничений фона нет).
         */
        const val EXTRA_AUTO_START_TRACKING = "EXTRA_AUTO_START_TRACKING"

        /** Used when the user tracks sleep without configuring an alarm. */
        const val NO_ALARM_ID = -1L

        /** Если эпох нет дольше 30 минут, считаем прежний трекинг «повисшим». */
        private const val STALE_SESSION_GAP_MS = 30 * 60_000L

        /** Таймаут короткого пакета удержания WakeLock (90-120 сек) вместо удержания на всю ночь. */
        const val WAKE_LOCK_BURST_TIMEOUT_MS = 100_000L

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _trackingStartMs = MutableStateFlow(0L)
        val trackingStartMs: StateFlow<Long> = _trackingStartMs.asStateFlow()

        private val _armedAlarmId = MutableStateFlow<Long?>(null)
        val armedAlarmId: StateFlow<Long?> = _armedAlarmId.asStateFlow()

        private val _armedDurationMinutes = MutableStateFlow<Int?>(null)
        val armedDurationMinutes: StateFlow<Int?> = _armedDurationMinutes.asStateFlow()

        private val _scheduledWakeTimeMs = MutableStateFlow<Long?>(null)
        val scheduledWakeTimeMs: StateFlow<Long?> = _scheduledWakeTimeMs.asStateFlow()

        fun disarm(context: Context) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_DISARM_DURATION_ALARM
            }
            context.startService(intent)
        }
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
        }
    }

    private fun acquireWakeLock(timeoutMs: Long = 12 * 3600_000L) {
        try {
            wakeLock?.acquire(timeoutMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLockBurst(timeoutMs: Long = WAKE_LOCK_BURST_TIMEOUT_MS) {
        acquireWakeLock(timeoutMs)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM_ID)
                startTracking(alarmId)
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
            ACTION_DISARM_DURATION_ALARM -> {
                disarmDurationAlarm()
            }
            ACTION_HEARTBEAT -> {
                if (_isTracking.value) {
                    if (wakeLock?.isHeld != true) {
                        acquireWakeLock(12 * 3600_000L)
                    }
                    com.appssimple.alarmsimple.receiver.SleepHeartbeatReceiver.scheduleNext(this)

                    // Сторож: сессия не может длиться больше 14 часов. Если сервис пережил день
                    // (не был остановлен), завершаем её — иначе появится фейковый «суточник».
                    val startedAt = _trackingStartMs.value
                    if (startedAt > 0 && System.currentTimeMillis() - startedAt > 14 * 3600_000L) {
                        stopTracking()
                    }
                }
            }
            else -> {
                releaseWakeLock()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTracking(alarmId: Long) {
        // Без RECORD_AUDIO сервис не сможет стать microphone-FGS, AudioRecord упадёт,
        // а ночь запишется «мёртвой» константой 25.0. Лучше не начинать вовсе.
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e(
                "SleepTrackingService",
                "RECORD_AUDIO not granted — sleep tracking is not started"
            )
            try {
                android.widget.Toast.makeText(
                    this,
                    "Нет доступа к микрофону: разрешите его в настройках, иначе трекинг сна не работает",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (ignored: Exception) {
            }
            stopSelf()
            return
        }

        serviceScope.launch {
            if (_isTracking.value) {
                // Если трекинг уже был формально активен, закрываем предыдущую сессию
                val lastEpoch = if (currentSessionId > 0) {
                    sleepEpochDao.getLatestEpochForSession(currentSessionId)
                } else null
                finishStaleSession(lastEpoch?.timestampMs)
            } else {
                // Проверяем, не осталась ли незавершённая «повисшая» сессия в базе
                val latestSession = sleepSessionDao.getLatestSession()
                if (latestSession != null && latestSession.wakeUpMs == null) {
                    val lastEpoch = sleepEpochDao.getLatestEpochForSession(latestSession.id)
                    val finalizeMs = lastEpoch?.timestampMs ?: latestSession.trackingStartMs
                    sleepSessionDao.updateSession(latestSession.copy(wakeUpMs = finalizeMs))
                }
            }
            doStartTracking(alarmId)
        }
    }

    /** Закрывает «повисшую» сессию по времени последней записанной эпохи. */
    private suspend fun finishStaleSession(lastEpochMs: Long?) {
        epochCollectorJob?.cancel()
        epochCollectorJob = null
        sensorFusion.stop()
        audioAnalyzer.stop()
        silentAudioPlayer.stop()
        releaseWakeLock()
        if (currentSessionId > 0) {
            val existing = sleepSessionDao.getSessionById(currentSessionId)
            if (existing != null && existing.wakeUpMs == null) {
                sleepSessionDao.updateSession(
                    existing.copy(wakeUpMs = lastEpochMs ?: System.currentTimeMillis())
                )
            }
        }
        currentSessionId = 0
        _isTracking.value = false
        _trackingStartMs.value = 0L
        _armedAlarmId.value = null
        _armedDurationMinutes.value = null
        _scheduledWakeTimeMs.value = null
        cancelHeartbeat()
    }

    private fun doStartTracking(alarmId: Long) {
        targetAlarmId = alarmId
        sleepOnsetMs = null
        disturbanceCount = 0
        snoreMinutesCount = 0
        isDurationMode = false
        isSmartWakeEnabled = false
        scheduledAlarmTimeMs = 0L
        targetDurationMinutes = 0
        alarmTriggered = false

        startForegroundServiceWithNotification()
        _isTracking.value = true
        _trackingStartMs.value = System.currentTimeMillis()

        // Непрерывный PARTIAL_WAKE_LOCK на всё время трекинга (длинный таймаут безопасности 12 часов):
        acquireWakeLock(12 * 3600_000L)

        // Запуск системного AlarmManager Heartbeat каждые 8 минут.
        // AlarmManager.setExactAndAllowWhileIdle выводит устройство из Doze Mode,
        // гарантируя, что ни один планировщик Android не заснет на часы!
        lastRecordedPhase = null
        scheduleHeartbeat()

        serviceScope.launch {
            val alarm = if (alarmId > 0) {
                alarmDao.getAlarmById(alarmId)
            } else {
                alarmDao.getActiveAlarms().firstOrNull()?.firstOrNull { it.mode == AlarmMode.DURATION.name }
            }
            if (alarm != null) {
                targetAlarmId = alarm.id
                isDurationMode = alarm.mode == AlarmMode.DURATION.name
                targetDurationMinutes = alarm.durationMinutes ?: (7 * 60 + 30)
                isSmartWakeEnabled = alarm.smartWakeEnabled
                smartWakeWindowMinutes = alarm.smartWakeWindowMinutes
                scheduledAlarmTimeMs = alarm.targetTimeMs ?: 0L

                if (isDurationMode) {
                    _armedAlarmId.value = alarm.id
                    _armedDurationMinutes.value = targetDurationMinutes
                    _scheduledWakeTimeMs.value = System.currentTimeMillis() + targetDurationMinutes * 60_000L
                } else {
                    _armedAlarmId.value = null
                    _armedDurationMinutes.value = null
                    _scheduledWakeTimeMs.value = null
                }
            } else {
                _armedAlarmId.value = null
                _armedDurationMinutes.value = null
                _scheduledWakeTimeMs.value = null
            }

            // Create new SleepSession in DB (alarmId is null for manual tracking)
            val session = SleepSessionEntity(
                alarmId = alarmId.takeIf { it > 0 },
                trackingStartMs = System.currentTimeMillis()
            )
            currentSessionId = sleepSessionDao.insertSession(session)

            // Start sensors
            silentAudioPlayer.start()
            sensorFusion.start(currentSessionId)

            // Listen for sensor events
            epochCollectorJob?.cancel()
            epochCollectorJob = serviceScope.launch {
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
        // Никогда не дописываем эпохи в сессию, чья последняя эпоха старше 30 минут:
        // финализируем её временем последней эпохи и открываем новую сессию.
        val lastEpoch = if (currentSessionId > 0) {
            sleepEpochDao.getLatestEpochForSession(currentSessionId)
        } else null
        if (lastEpoch != null && (epochState.timestampMs - lastEpoch.timestampMs > STALE_SESSION_GAP_MS)) {
            android.util.Log.w(
                "SleepTrackingService",
                "Stale gap > 30 min (gap=${(epochState.timestampMs - lastEpoch.timestampMs) / 60000}m). Finalizing session $currentSessionId and starting new one."
            )
            val existing = sleepSessionDao.getSessionById(currentSessionId)
            if (existing != null && existing.wakeUpMs == null) {
                sleepSessionDao.updateSession(existing.copy(wakeUpMs = lastEpoch.timestampMs))
            }
            // Создаем новую строку сессии и сбрасываем состояние SensorFusion
            val newSession = SleepSessionEntity(
                alarmId = targetAlarmId.takeIf { it > 0 },
                trackingStartMs = epochState.timestampMs
            )
            currentSessionId = sleepSessionDao.insertSession(newSession)
            sleepOnsetMs = null
            disturbanceCount = 0
            snoreMinutesCount = 0
            lastRecordedPhase = null
            sensorFusion.start(currentSessionId)
        }

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

        // Overnight debug logging to CSV if enabled in developer settings
        // Uses epochState.sleepOnsetMs so the onset timestamp is logged immediately on the epoch it was detected!
        sleepDebugLogger.logEpoch(
            sessionId = currentSessionId,
            epochState = epochState,
            sleepOnsetMs = epochState.sleepOnsetMs ?: sleepOnsetMs
        )

        if (epochState.dominantAudioClass == com.appssimple.alarmsimple.domain.model.AudioClass.SNORE) {
            snoreMinutesCount += 1 // ~30 sec
        }
        if (epochState.phase == SleepPhase.AWAKE && lastRecordedPhase != null && lastRecordedPhase != SleepPhase.AWAKE && sleepOnsetMs != null) {
            disturbanceCount++
        }
        lastRecordedPhase = epochState.phase

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
                _scheduledWakeTimeMs.value = wakeTimeMs
                alarmScheduler.scheduleAlarm(wakeTimeMs, targetAlarmId)
            }
        }

        // Smart Wake-Up evaluation
        if (isSmartWakeEnabled && scheduledAlarmTimeMs > 0 && !alarmTriggered) {
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
        if (alarmTriggered) return
        alarmTriggered = true

        if (targetAlarmId > 0) {
            alarmScheduler.cancelAlarm(targetAlarmId)
        }

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

    fun disarmDurationAlarm() {
        val alarmIdToDisarm = targetAlarmId
        if (alarmIdToDisarm > 0) {
            alarmScheduler.cancelAlarm(alarmIdToDisarm)
        }
        targetAlarmId = NO_ALARM_ID
        isDurationMode = false
        scheduledAlarmTimeMs = 0L
        _armedAlarmId.value = null
        _armedDurationMinutes.value = null
        _scheduledWakeTimeMs.value = null

        if (alarmIdToDisarm > 0) {
            serviceScope.launch {
                alarmDao.setAlarmActive(alarmIdToDisarm, false)
            }
        }
    }

    private fun stopTracking() {
        epochCollectorJob?.cancel()
        epochCollectorJob = null
        if (!_isTracking.value) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        _isTracking.value = false
        _trackingStartMs.value = 0L
        _armedAlarmId.value = null
        _armedDurationMinutes.value = null
        _scheduledWakeTimeMs.value = null

        serviceScope.launch {
            silentAudioPlayer.stop()
            sensorFusion.stop()

                if (currentSessionId > 0) {
                    val existing = sleepSessionDao.getSessionById(currentSessionId)
                    if (existing != null) {
                        val now = System.currentTimeMillis()
                        val onset = existing.sleepOnsetMs
                        val sleepDurationMs = if (onset != null) now - onset else 0L

                        // Реальная длительность сна — только эпохи LIGHT/REM/DEEP (30 сек каждая).
                        // AWAKE сном не считается: сессия из одних пробуждений — мусор.
                        val epochStats = sleepEpochDao.getEpochStatsForSession(currentSessionId)
                        val hasEpochs = (epochStats?.totalEpochs ?: 0) > 0
                        val realSleepMs = if (hasEpochs) {
                            (epochStats!!.totalEpochs - epochStats.awakeEpochs) * 30_000L
                        } else {
                            // Датчики ничего не записали — опираемся на границы сессии
                            sleepDurationMs
                        }

                        // Сна не было вовсе, реальный сон короче 10 минут или сессия дольше
                        // 14 часов (зависший сервис) — удаляем и не засоряем статистику.
                        if (onset == null || realSleepMs < 600_000L || sleepDurationMs > 14 * 3600_000L) {
                            sleepEpochDao.deleteEpochsForSession(currentSessionId)
                            audioSnippetDao.deleteSnippetsForSession(currentSessionId)
                            sleepSessionDao.deleteSession(existing)
                        } else if (existing.wakeUpMs == null) {
                        sleepSessionDao.updateSession(
                            existing.copy(
                                wakeUpMs = now,
                                disturbanceCount = disturbanceCount,
                                snoreMinutes = snoreMinutesCount / 2
                            )
                        )
                    }
                }
            }

            cancelHeartbeat()
            releaseWakeLock()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun scheduleHeartbeat() {
        com.appssimple.alarmsimple.receiver.SleepHeartbeatReceiver.scheduleNext(this)
    }

    private fun cancelHeartbeat() {
        com.appssimple.alarmsimple.receiver.SleepHeartbeatReceiver.cancel(this)
    }

    private fun startForegroundServiceWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SleepTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AlarmSimpleApp.CHANNEL_SLEEP_TRACKING)
            .setContentTitle(
                if (targetAlarmId > 0) getString(R.string.channel_tracking_name) else "Сон отслеживается"
            )
            .setContentText("Датчики активны. Нажмите, чтобы открыть приложение.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Завершить", stopPendingIntent)
            .build()

        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, serviceTypes)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android 14+ запрещает запускать FGS с типом microphone из фона (SecurityException),
            // даже при автостарте по exact-alarm. Не падаем, а деградируем до mediaPlayback:
            // трекинг движения по акселерометру, фазы сна и умное пробуждение продолжают работать,
            // недоступно только прослушивание микрофона (храп) до следующего запуска из foreground.
            e.printStackTrace()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Exception) {
                // Совсем не удалось подняться — корректно гасим сервис,
                // иначе через 5 секунд система убьёт процесс за неявленный startForeground.
                e2.printStackTrace()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        epochCollectorJob?.cancel()
        epochCollectorJob = null
        _isTracking.value = false
        _trackingStartMs.value = 0L
        _armedAlarmId.value = null
        _armedDurationMinutes.value = null
        _scheduledWakeTimeMs.value = null
        serviceScope.cancel()
        silentAudioPlayer.stop()
        releaseWakeLock()
        super.onDestroy()
    }
}
