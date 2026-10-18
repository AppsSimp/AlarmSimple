package com.appssimple.alarmsimple.ui.setup

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.domain.model.QuestType
import com.appssimple.alarmsimple.quest.MathDifficulty
import com.appssimple.alarmsimple.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val mathDifficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val sirenEnabled: Boolean = false,
    val volumeLockEnabled: Boolean = false,
    val snoozeMinutes: Int = 5,
    val repeatDays: Set<Int> = emptySet(), // 1=Пн, 2=Вт, ..., 7=Вс
    val repeatIntervalWeeks: Int = 1, // 1 = каждую неделю, 2 = через неделю, 3 = через 2 недели
    val ringtoneUri: String? = null,
    val ringtoneTitle: String = "По умолчанию (Системный)",
    val isActive: Boolean = true,
    val label: String = "",
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val durationHours: Int get() = durationMinutes / 60
    val durationMinutesPart: Int get() = durationMinutes % 60
    val isEditing: Boolean get() = alarmId != null
}

@HiltViewModel
class AlarmSetupViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmSetupUiState())
    val uiState: StateFlow<AlarmSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val snooze = userPreferences.defaultSnoozeMinutes.first()
            val window = userPreferences.defaultSmartWakeWindow.first()
            val siren = userPreferences.defaultSirenEnabled.first()
            val volumeLock = userPreferences.defaultVolumeLock.first()
            _uiState.update {
                if (it.alarmId == null) {
                    it.copy(
                        snoozeMinutes = snooze,
                        smartWakeWindowMinutes = window,
                        sirenEnabled = siren,
                        volumeLockEnabled = volumeLock
                    )
                } else {
                    it
                }
            }
        }
    }

    fun loadAlarm(id: Long?) {
        if (id == null || id <= 0) {
            _uiState.update { it.copy(alarmId = null, isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val alarm = alarmDao.getAlarmById(id)
            if (alarm == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val cal = Calendar.getInstance()
            if (alarm.targetTimeMs != null) {
                cal.timeInMillis = alarm.targetTimeMs
            }

            val repeatDays = runCatching {
                val arr = org.json.JSONArray(alarm.repeatDays)
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            }.getOrDefault(emptySet())

            val ringtoneTitle = if (alarm.ringtoneUri != null) {
                try {
                    val ringtone = RingtoneManager.getRingtone(context, Uri.parse(alarm.ringtoneUri))
                    ringtone?.getTitle(context) ?: "Выбранный сигнал"
                } catch (e: Exception) {
                    "Выбранный сигнал"
                }
            } else "По умолчанию (Системный)"

            _uiState.update {
                it.copy(
                    alarmId = alarm.id,
                    mode = runCatching { AlarmMode.valueOf(alarm.mode) }.getOrDefault(AlarmMode.CLASSIC),
                    targetHour = cal.get(Calendar.HOUR_OF_DAY),
                    targetMinute = cal.get(Calendar.MINUTE),
                    durationMinutes = alarm.durationMinutes ?: 450,
                    smartWakeEnabled = alarm.smartWakeEnabled,
                    smartWakeWindowMinutes = alarm.smartWakeWindowMinutes,
                    questType = runCatching { QuestType.valueOf(alarm.questType) }.getOrDefault(QuestType.NONE),
                    questData = alarm.questData,
                    sirenEnabled = alarm.sirenEnabled,
                    volumeLockEnabled = alarm.volumeLockEnabled,
                    snoozeMinutes = alarm.snoozeDurationMinutes,
                    repeatDays = repeatDays,
                    repeatIntervalWeeks = alarm.repeatIntervalWeeks,
                    ringtoneUri = alarm.ringtoneUri,
                    ringtoneTitle = ringtoneTitle,
                    isActive = alarm.isActive,
                    label = alarm.label,
                    isLoading = false
                )
            }
        }
    }

    fun toggleRepeatDay(day: Int) {
        _uiState.update { current ->
            val updated = if (current.repeatDays.contains(day)) {
                current.repeatDays - day
            } else {
                current.repeatDays + day
            }
            current.copy(repeatDays = updated)
        }
    }

    fun clearRepeatDays() {
        _uiState.update { it.copy(repeatDays = emptySet()) }
    }

    fun setRepeatDays(days: Set<Int>) {
        _uiState.update { it.copy(repeatDays = days) }
    }

    fun setRepeatIntervalWeeks(weeks: Int) {
        _uiState.update { it.copy(repeatIntervalWeeks = weeks) }
    }

    fun setMode(mode: AlarmMode) { _uiState.update { it.copy(mode = mode) } }
    fun setDuration(minutes: Int) { _uiState.update { it.copy(durationMinutes = minutes) } }
    fun setTime(hour: Int, minute: Int) { _uiState.update { it.copy(targetHour = hour, targetMinute = minute) } }
    fun setSmartWakeEnabled(enabled: Boolean) { _uiState.update { it.copy(smartWakeEnabled = enabled) } }
    fun setSmartWakeWindow(minutes: Int) { _uiState.update { it.copy(smartWakeWindowMinutes = minutes) } }
    fun setQuestType(type: QuestType) {
        _uiState.update {
            it.copy(
                questType = type,
                // Сбрасываем старые данные если меняется тип
                questData = if (type == it.questType) it.questData else null
            )
        }
    }
    fun setQuestData(data: String?) { _uiState.update { it.copy(questData = data) } }
    fun setMathDifficulty(diff: MathDifficulty) { _uiState.update { it.copy(mathDifficulty = diff) } }
    fun setSirenEnabled(enabled: Boolean) { _uiState.update { it.copy(sirenEnabled = enabled) } }
    fun setVolumeLockEnabled(enabled: Boolean) { _uiState.update { it.copy(volumeLockEnabled = enabled) } }
    fun setSnoozeMinutes(minutes: Int) { _uiState.update { it.copy(snoozeMinutes = minutes) } }
    fun setRingtone(uri: String?, title: String) { _uiState.update { it.copy(ringtoneUri = uri, ringtoneTitle = title) } }
    fun setLabel(label: String) { _uiState.update { it.copy(label = label) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun saveAlarm(onSaved: () -> Unit) {
        val snapshot = _uiState.value
        if (snapshot.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val state = _uiState.value
                val targetTimeMs = if (state.mode == AlarmMode.CLASSIC) {
                    alarmScheduler.calculateNextTriggerTime(
                        hour = state.targetHour,
                        minute = state.targetMinute,
                        repeatDays = state.repeatDays,
                        repeatIntervalWeeks = state.repeatIntervalWeeks
                    )
                } else null

                val repeatDaysJson = org.json.JSONArray(state.repeatDays.toList()).toString()

                val baseEntity = AlarmEntity(
                    id = state.alarmId ?: 0L,
                    mode = state.mode.name,
                    targetTimeMs = targetTimeMs,
                    durationMinutes = if (state.mode == AlarmMode.DURATION) state.durationMinutes else null,
                    smartWakeEnabled = state.smartWakeEnabled,
                    smartWakeWindowMinutes = state.smartWakeWindowMinutes,
                    questType = state.questType.name,
                    questData = state.questData,
                    sirenEnabled = false, // Режим сирены отключен по просьбе пользователя
                    volumeLockEnabled = state.volumeLockEnabled,
                    snoozeDurationMinutes = state.snoozeMinutes,
                    repeatDays = repeatDaysJson,
                    repeatIntervalWeeks = state.repeatIntervalWeeks,
                    ringtoneUri = state.ringtoneUri,
                    isActive = state.isActive,
                    label = state.label
                )

                val id = alarmDao.insertAlarm(baseEntity)
                val finalId = if (baseEntity.id != 0L) baseEntity.id else id

                if (state.isActive && targetTimeMs != null) {
                    alarmScheduler.scheduleAlarm(targetTimeMs, finalId)

                    // Если включен предохранитель в настройках — ставим системный будильник на +5 минут
                    val isBackupAlarmEnabled = userPreferences.backupSystemAlarmEnabled.first()
                    if (isBackupAlarmEnabled) {
                        alarmScheduler.setSystemBackupAlarm(targetTimeMs, "Страховка: AlarmSimple")
                    }
                }

                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            } catch (t: Throwable) {
                _uiState.update { it.copy(isSaving = false, errorMessage = t.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun deleteAlarm(onDeleted: () -> Unit) {
        val id = _uiState.value.alarmId ?: return
        viewModelScope.launch {
            try {
                alarmScheduler.cancelAlarm(id)
                val entity = alarmDao.getAlarmById(id)
                if (entity != null) {
                    alarmDao.deleteAlarm(entity)
                }
                onDeleted()
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = t.message ?: "Ошибка удаления") }
            }
        }
    }
}
