package com.appssimple.alarmsimple.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.domain.model.QuestType
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AlarmSetupUiState(
    val alarmId: Long? = null,
    val mode: AlarmMode = AlarmMode.CLASSIC,
    val targetHour: Int = 7,
    val targetMinute: Int = 0,
    val durationMinutes: Int = 450, // 7.5 hours
    val smartWakeEnabled: Boolean = true,
    val smartWakeWindowMinutes: Int = 30,
    val questType: QuestType = QuestType.NONE,
    val questData: String? = null,
    val sirenEnabled: Boolean = false,
    val volumeLockEnabled: Boolean = false,
    val label: String = ""
) {
    val durationHours: Int get() = durationMinutes / 60
    val durationMinutesPart: Int get() = durationMinutes % 60
}

@HiltViewModel
class AlarmSetupViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmSetupUiState())
    val uiState: StateFlow<AlarmSetupUiState> = _uiState.asStateFlow()

    fun loadAlarm(id: Long?) {
        if (id == null || id == -1L) return
        viewModelScope.launch {
            val alarm = alarmDao.getAlarmById(id) ?: return@launch
            val cal = Calendar.getInstance()
            if (alarm.targetTimeMs != null) {
                cal.timeInMillis = alarm.targetTimeMs
            }

            _uiState.update {
                it.copy(
                    alarmId = alarm.id,
                    mode = AlarmMode.valueOf(alarm.mode),
                    targetHour = cal.get(Calendar.HOUR_OF_DAY),
                    targetMinute = cal.get(Calendar.MINUTE),
                    durationMinutes = alarm.durationMinutes ?: 450,
                    smartWakeEnabled = alarm.smartWakeEnabled,
                    smartWakeWindowMinutes = alarm.smartWakeWindowMinutes,
                    questType = QuestType.valueOf(alarm.questType),
                    questData = alarm.questData,
                    sirenEnabled = alarm.sirenEnabled,
                    volumeLockEnabled = alarm.volumeLockEnabled,
                    label = alarm.label
                )
            }
        }
    }

    fun setMode(mode: AlarmMode) { _uiState.update { it.copy(mode = mode) } }
    fun setDuration(minutes: Int) { _uiState.update { it.copy(durationMinutes = minutes) } }
    fun setTime(hour: Int, minute: Int) { _uiState.update { it.copy(targetHour = hour, targetMinute = minute) } }
    fun setSmartWakeEnabled(enabled: Boolean) { _uiState.update { it.copy(smartWakeEnabled = enabled) } }
    fun setSmartWakeWindow(minutes: Int) { _uiState.update { it.copy(smartWakeWindowMinutes = minutes) } }
    fun setQuestType(type: QuestType) { _uiState.update { it.copy(questType = type) } }
    fun setSirenEnabled(enabled: Boolean) { _uiState.update { it.copy(sirenEnabled = enabled) } }
    fun setVolumeLockEnabled(enabled: Boolean) { _uiState.update { it.copy(volumeLockEnabled = enabled) } }

    fun saveAlarm(onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value

            val targetTimeMs = if (state.mode == AlarmMode.CLASSIC) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, state.targetHour)
                    set(Calendar.MINUTE, state.targetMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                cal.timeInMillis
            } else null

            val entity = AlarmEntity(
                id = state.alarmId ?: 0L,
                mode = state.mode.name,
                targetTimeMs = targetTimeMs,
                durationMinutes = if (state.mode == AlarmMode.DURATION) state.durationMinutes else null,
                smartWakeEnabled = state.smartWakeEnabled,
                smartWakeWindowMinutes = state.smartWakeWindowMinutes,
                questType = state.questType.name,
                questData = state.questData,
                sirenEnabled = state.sirenEnabled,
                volumeLockEnabled = state.volumeLockEnabled,
                isActive = true,
                label = state.label
            )

            val insertedId = alarmDao.insertAlarm(entity)
            val finalId = if (entity.id != 0L) entity.id else insertedId

            // If Classic mode, schedule exact alarm
            if (targetTimeMs != null) {
                alarmScheduler.scheduleAlarm(targetTimeMs, finalId)
            }

            onSaved()
        }
    }
}
