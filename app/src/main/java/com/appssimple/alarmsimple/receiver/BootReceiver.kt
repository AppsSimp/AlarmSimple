package com.appssimple.alarmsimple.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.domain.model.AlarmMode
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val activeAlarms = alarmDao.getActiveAlarms().firstOrNull() ?: emptyList()
                    val now = System.currentTimeMillis()

                    for (alarm in activeAlarms) {
                        if (alarm.mode == AlarmMode.CLASSIC.name && alarm.targetTimeMs != null) {
                            var target = alarm.targetTimeMs
                            if (target > now) {
                                alarmScheduler.scheduleAlarm(target, alarm.id)
                            }
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
