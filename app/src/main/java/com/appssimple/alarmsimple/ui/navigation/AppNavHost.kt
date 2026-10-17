package com.appssimple.alarmsimple.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.appssimple.alarmsimple.ui.alarmlist.AlarmListScreen
import com.appssimple.alarmsimple.ui.onboarding.OnboardingScreen
import com.appssimple.alarmsimple.ui.settings.SettingsScreen
import com.appssimple.alarmsimple.ui.setup.AlarmSetupScreen
import com.appssimple.alarmsimple.ui.stats.SleepStatsScreen
import com.appssimple.alarmsimple.ui.history.SleepHistoryScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Screen,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.Onboarding> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.AlarmList) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Screen.AlarmList> {
            AlarmListScreen(
                onAddAlarm = { navController.navigate(Screen.AlarmSetup()) },
                onEditAlarm = { id -> navController.navigate(Screen.AlarmSetup(alarmId = id)) },
                onOpenSettings = { navController.navigate(Screen.Settings) },
                onOpenHistory = { navController.navigate(Screen.SleepHistory) }
            )
        }
        
        composable<Screen.AlarmSetup> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.AlarmSetup>()
            AlarmSetupScreen(
                alarmId = route.alarmId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable<Screen.SleepStats> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.SleepStats>()
            SleepStatsScreen(
                sessionId = route.sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable<Screen.SleepHistory> {
            SleepHistoryScreen(
                onOpenSession = { id -> navController.navigate(Screen.SleepStats(sessionId = id)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
