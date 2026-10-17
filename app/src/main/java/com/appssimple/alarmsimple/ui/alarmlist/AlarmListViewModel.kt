package com.appssimple.alarmsimple.ui.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val alarms: Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()

    private val _selectedAlarmIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAlarmIds: StateFlow<Set<Long>> = _selectedAlarmIds.asStateFlow()

    fun toggleSelection(alarmId: Long) {
        _selectedAlarmIds.update { current ->
            if (current.contains(alarmId)) current - alarmId else current + alarmId
        }
    }

    fun selectAll(allIds: List<Long>) {
        _selectedAlarmIds.value = allIds.toSet()
    }

    fun clearSelection() {
        _selectedAlarmIds.value = emptySet()
    }

    fun toggleAlarmActive(alarmId: Long, isActive: Boolean) {
        viewModelScope.launch {
            alarmDao.setAlarmActive(alarmId, isActive)
            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm != null) {
                val target = alarm.targetTimeMs
                if (isActive && target != null) {
                    val repeatDays = runCatching {
                        val arr = org.json.JSONArray(alarm.repeatDays)
                        (0 until arr.length()).map { arr.getInt(it) }.toSet()
                    }.getOrDefault(emptySet())

                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = target }
                    val nextTrigger = alarmScheduler.calculateNextTriggerTime(
                        hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                        minute = cal.get(java.util.Calendar.MINUTE),
                        repeatDays = repeatDays
                    )
                    alarmDao.updateAlarm(alarm.copy(targetTimeMs = nextTrigger, isActive = true))
                    alarmScheduler.scheduleAlarm(nextTrigger, alarm.id)
                } else {
                    alarmScheduler.cancelAlarm(alarm.id)
                }
            }
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            alarmDao.deleteAlarm(alarm)
        }
    }

    fun deleteSelectedAlarms(allAlarms: List<AlarmEntity>) {
        val selected = _selectedAlarmIds.value
        if (selected.isEmpty()) return

        viewModelScope.launch {
            allAlarms.filter { selected.contains(it.id) }.forEach { alarm ->
                alarmScheduler.cancelAlarm(alarm.id)
                alarmDao.deleteAlarm(alarm)
            }
            clearSelection()
        }
    }

    fun restoreAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            val id = alarmDao.insertAlarm(alarm)
            val finalId = if (alarm.id != 0L) alarm.id else id
            if (alarm.isActive && alarm.targetTimeMs != null) {
                alarmScheduler.scheduleAlarm(alarm.targetTimeMs, finalId)
            }
        }
    }
}
