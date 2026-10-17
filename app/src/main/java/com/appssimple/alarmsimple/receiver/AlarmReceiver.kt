package com.appssimple.alarmsimple.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appssimple.alarmsimple.service.AlarmFiringService
import com.appssimple.alarmsimple.service.SleepTrackingService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "com.appssimple.alarmsimple.ACTION_FIRE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(SleepTrackingService.EXTRA_ALARM_ID, -1L)

        val serviceIntent = Intent(context, AlarmFiringService::class.java).apply {
            putExtra(AlarmFiringService.EXTRA_ALARM_ID, alarmId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
