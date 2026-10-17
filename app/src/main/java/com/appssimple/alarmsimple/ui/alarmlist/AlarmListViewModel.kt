package com.appssimple.alarmsimple.ui.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val alarms: Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()

    fun toggleAlarmActive(alarmId: Long, isActive: Boolean) {
        viewModelScope.launch {
            alarmDao.setAlarmActive(alarmId, isActive)
            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm != null) {
                if (isActive && alarm.targetTimeMs != null) {
                    alarmScheduler.scheduleAlarm(alarm.targetTimeMs, alarm.id)
                } else {
                    alarmScheduler.cancelAlarm(alarm.id)
                }
            }
        }
    }
}
