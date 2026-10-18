package com.appssimple.alarmsimple.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 */
sealed interface Screen {
    @Serializable
    data object Onboarding : Screen
    
    @Serializable
    data object AlarmList : Screen
    
    @Serializable
    data class AlarmSetup(val alarmId: Long? = null) : Screen
    
    @Serializable
    data object Settings : Screen
    
    @Serializable
    data class SleepStats(val sessionId: Long) : Screen
    
    @Serializable
    data object SleepHistory : Screen
    
    @Serializable
    data object SleepTracking : Screen

    @Serializable
    data object AdminPanel : Screen
}
