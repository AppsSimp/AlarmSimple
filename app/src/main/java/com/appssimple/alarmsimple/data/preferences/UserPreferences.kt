package com.appssimple.alarmsimple.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Manages user preferences via DataStore.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val COACH_MARKS_SHOWN = booleanPreferencesKey("coach_marks_shown")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_SMART_WAKE_WINDOW = intPreferencesKey("default_smart_wake_window")
        val DEFAULT_VOLUME_LOCK = booleanPreferencesKey("default_volume_lock")
        val DEFAULT_SIREN_ENABLED = booleanPreferencesKey("default_siren_enabled")
        val BACKUP_SYSTEM_ALARM_ENABLED = booleanPreferencesKey("backup_system_alarm_enabled")
        val TEST_RING_BUTTONS_ENABLED = booleanPreferencesKey("test_ring_buttons_enabled")
        val DEBUG_SLEEP_LOGGING_ENABLED = booleanPreferencesKey("debug_sleep_logging_enabled")
    }
    
    val testRingButtonsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TEST_RING_BUTTONS_ENABLED] ?: false }

    val debugSleepLoggingEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[DEBUG_SLEEP_LOGGING_ENABLED] ?: false }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETED] ?: false }

    val coachMarksShown: Flow<Boolean> = context.dataStore.data
        .map { it[COACH_MARKS_SHOWN] ?: false }
    
    /** 0 means unlimited */
    val historyRetentionDays: Flow<Int> = context.dataStore.data
        .map { it[HISTORY_RETENTION_DAYS] ?: 0 }
    
    val defaultSnoozeMinutes: Flow<Int> = context.dataStore.data
        .map { it[DEFAULT_SNOOZE_MINUTES] ?: 5 }
    
    val defaultSmartWakeWindow: Flow<Int> = context.dataStore.data
        .map { it[DEFAULT_SMART_WAKE_WINDOW] ?: 30 }
    
    val defaultVolumeLock: Flow<Boolean> = context.dataStore.data
        .map { it[DEFAULT_VOLUME_LOCK] ?: false }
    
    val defaultSirenEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[DEFAULT_SIREN_ENABLED] ?: false }

    val backupSystemAlarmEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[BACKUP_SYSTEM_ALARM_ENABLED] ?: false }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setCoachMarksShown(shown: Boolean) {
        context.dataStore.edit { it[COACH_MARKS_SHOWN] = shown }
    }
    
    suspend fun setHistoryRetentionDays(days: Int) {
        context.dataStore.edit { it[HISTORY_RETENTION_DAYS] = days }
    }
    
    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_SNOOZE_MINUTES] = minutes }
    }
    
    suspend fun setDefaultSmartWakeWindow(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_SMART_WAKE_WINDOW] = minutes }
    }
    
    suspend fun setDefaultVolumeLock(enabled: Boolean) {
        context.dataStore.edit { it[DEFAULT_VOLUME_LOCK] = enabled }
    }
    
    suspend fun setDefaultSirenEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DEFAULT_SIREN_ENABLED] = enabled }
    }

    suspend fun setBackupSystemAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BACKUP_SYSTEM_ALARM_ENABLED] = enabled }
    }

    suspend fun setTestRingButtonsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TEST_RING_BUTTONS_ENABLED] = enabled }
    }

    suspend fun setDebugSleepLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_SLEEP_LOGGING_ENABLED] = enabled }
    }
}
