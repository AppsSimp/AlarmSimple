package com.appssimple.alarmsimple.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.appssimple.alarmsimple.AlarmSimpleApp
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.domain.model.QuestType
import com.appssimple.alarmsimple.ui.alarm.AlarmRingingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service that plays the alarm sound, maintains volume lock,
 * runs the 5-minute quest grace period, and transitions into Siren Mode if needed.
 */
@AndroidEntryPoint
class AlarmFiringService : Service() {

    @Inject lateinit var alarmDao: AlarmDao

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var currentAlarmId: Long = 0
    private var isSirenActive = false
    private var isVolumeLockEnabled = false
    private var volumeObserver: VolumeObserver? = null

    private var questTimerJob: Job? = null
    private var volumeRampJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 3001
        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
        const val EXTRA_EARLY_WAKE = "EXTRA_EARLY_WAKE"
        const val ACTION_GIVE_5_MINUTES = "com.appssimple.alarmsimple.ACTION_GIVE_5_MINUTES"
        const val ACTION_DISMISS = "com.appssimple.alarmsimple.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.appssimple.alarmsimple.ACTION_SNOOZE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId != -1L) currentAlarmId = alarmId

        when (action) {
            ACTION_GIVE_5_MINUTES -> handleGive5Minutes()
            ACTION_DISMISS -> dismissAlarm()
            ACTION_SNOOZE -> handleSnooze()
            else -> startAlarmPlayback(currentAlarmId)
        }

        return START_STICKY
    }

    private fun startAlarmPlayback(alarmId: Long) {
        startForegroundWithFullScreenIntent(alarmId)

        serviceScope.launch {
            val alarm = alarmDao.getAlarmById(alarmId)
            isVolumeLockEnabled = alarm?.volumeLockEnabled ?: false

            if (isVolumeLockEnabled) {
                enforceMaxVolume()
                startVolumeObserver()
            }

            playAlarmSound(alarm?.ringtoneUri)
        }
    }

    private fun startForegroundWithFullScreenIntent(alarmId: Long) {
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AlarmSimpleApp.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.channel_alarm_name))
            .setContentText(getString(R.string.complete_quest))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun playAlarmSound(ringtoneUriString: String?) {
        try {
            requestAlarmAudioFocus()

            val soundUri = if (ringtoneUriString != null) {
                Uri.parse(ringtoneUriString)
            } else {
                Settings.System.DEFAULT_ALARM_ALERT_URI
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmFiringService, soundUri)
                isLooping = true
                setVolume(0.05f, 0.05f) // Start gentle for progressive wake
                prepare()
                start()
            }

            startGentleVolumeRamp()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startGentleVolumeRamp() {
        volumeRampJob?.cancel()
        volumeRampJob = serviceScope.launch {
            var currentVol = 0.05f
            while (currentVol < 1.0f && isActive) {
                delay(1000)
                currentVol = (currentVol + 0.035f).coerceAtMost(1.0f)
                mediaPlayer?.setVolume(currentVol, currentVol)
            }
        }
    }

    private fun handleGive5Minutes() {
        // Silence sound and give 5 minutes for quest
        volumeRampJob?.cancel()
        mediaPlayer?.setVolume(0f, 0f)

        questTimerJob?.cancel()
        questTimerJob = serviceScope.launch {
            delay(5 * 60 * 1000L) // 5 minutes grace period
            // If quest wasn't completed in 5 minutes -> SIREN MODE!
            triggerSirenMode()
        }
    }

    private fun triggerSirenMode() {
        isSirenActive = true
        enforceMaxVolume()
        mediaPlayer?.setVolume(1.0f, 1.0f)
        if (!isVolumeLockEnabled) {
            // Siren automatically enforces volume lock
            isVolumeLockEnabled = true
            startVolumeObserver()
        }

        // Broadcast to Activity to update UI
        val updateIntent = Intent(AlarmRingingActivity.ACTION_SIREN_TRIGGERED)
        sendBroadcast(updateIntent)
    }

    private fun handleSnooze() {
        serviceScope.launch {
            val alarm = alarmDao.getAlarmById(currentAlarmId)
            val snoozeMinutes = alarm?.snoozeDurationMinutes ?: 5
            val snoozeTimeMs = System.currentTimeMillis() + (snoozeMinutes * 60_000L)

            // Reschedule via scheduler and dismiss current
            val scheduler = AlarmScheduler(this@AlarmFiringService)
            scheduler.scheduleAlarm(snoozeTimeMs, currentAlarmId)

            dismissAlarm()
        }
    }

    private fun dismissAlarm() {
        questTimerJob?.cancel()
        volumeRampJob?.cancel()
        stopVolumeObserver()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        abandonAudioFocus()

        // Tell SleepTrackingService to wrap up and compute final session stats
        val stopTrackingIntent = Intent(this, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP_TRACKING
        }
        startService(stopTrackingIntent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enforceMaxVolume() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
    }

    private fun startVolumeObserver() {
        if (volumeObserver != null) return
        volumeObserver = VolumeObserver(this, audioManager, Handler(Looper.getMainLooper())).also {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, it)
        }
    }

    private fun stopVolumeObserver() {
        volumeObserver?.let {
            contentResolver.unregisterContentObserver(it)
            volumeObserver = null
        }
    }

    private fun requestAlarmAudioFocus() {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attr)
                .build()
            focusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    override fun onDestroy() {
        dismissAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    private class VolumeObserver(
        private val context: Context,
        private val audioManager: AudioManager,
        handler: Handler
    ) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol < maxVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }
        }
    }
}
