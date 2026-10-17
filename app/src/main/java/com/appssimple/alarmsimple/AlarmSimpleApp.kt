package com.appssimple.alarmsimple

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlarmSimpleApp : Application() {

    companion object {
        const val CHANNEL_ALARM = "alarm_ringing"
        const val CHANNEL_SLEEP_TRACKING = "sleep_tracking"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Alarm ringing channel — high priority, alarm sound
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_alarm_description)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                null, // Sound is managed by MediaPlayer/ExoPlayer
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
        }

        // Sleep tracking channel — low priority, silent
        val trackingChannel = NotificationChannel(
            CHANNEL_SLEEP_TRACKING,
            getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_tracking_description)
            setShowBadge(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannels(
            listOf(alarmChannel, trackingChannel)
        )
    }
}
