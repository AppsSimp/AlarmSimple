package com.appssimple.alarmsimple.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.navigation.AppNavHost
import com.appssimple.alarmsimple.ui.navigation.Screen
import com.appssimple.alarmsimple.ui.theme.AlarmSimpleTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var alarmScheduler: com.appssimple.alarmsimple.service.AlarmScheduler

    override fun onResume() {
        super.onResume()
        checkAndRedirectIfRinging()
    }

    private fun checkAndRedirectIfRinging() {
        if (com.appssimple.alarmsimple.service.AlarmFiringService.isRinging.value) {
            val alarmId = com.appssimple.alarmsimple.service.AlarmFiringService.ringingAlarmId.value
            val ringIntent = android.content.Intent(this, com.appssimple.alarmsimple.ui.alarm.AlarmRingingActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (alarmId > 0) {
                    putExtra(com.appssimple.alarmsimple.service.AlarmFiringService.EXTRA_ALARM_ID, alarmId)
                }
            }
            startActivity(ringIntent)
        }
    }

    /**
     * Запуск по тапу на уведомление-напоминание: приложение на экране, поэтому сервис
     * можно поднять с микрофоном (Android 14+ блокирует микрофон фоновым запускам).
     */
    private fun maybeStartTrackingFromReminder(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(
                com.appssimple.alarmsimple.service.SleepTrackingService.EXTRA_AUTO_START_TRACKING,
                false
            ) != true
        ) return
        if (com.appssimple.alarmsimple.service.SleepTrackingService.isTracking.value) return

        val alarmId = intent.getLongExtra(
            com.appssimple.alarmsimple.service.SleepTrackingService.EXTRA_ALARM_ID,
            com.appssimple.alarmsimple.service.SleepTrackingService.NO_ALARM_ID
        )
        val serviceIntent = android.content.Intent(
            this,
            com.appssimple.alarmsimple.service.SleepTrackingService::class.java
        ).apply {
            action = com.appssimple.alarmsimple.service.SleepTrackingService.ACTION_START_TRACKING
            putExtra(
                com.appssimple.alarmsimple.service.SleepTrackingService.EXTRA_ALARM_ID,
                alarmId
            )
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRedirectIfRinging()
        maybeStartTrackingFromReminder(intent)

        // Самолечение: Android стирает AlarmManager-алармы при обновлении APK и принудительной
        // остановке приложения. При каждом холодном старте восстанавливаем будильники и цепочку
        // напоминаний об анализе сна из БД.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                alarmScheduler.rescheduleAllAlarms()
                alarmScheduler.ensureSleepReminderScheduled()
            } catch (ignored: Exception) {
            }
        }

        enableEdgeToEdge()

        setContent {
            AlarmSimpleTheme {
                val isRinging by com.appssimple.alarmsimple.service.AlarmFiringService.isRinging.collectAsState()
                LaunchedEffect(isRinging) {
                    if (isRinging) {
                        checkAndRedirectIfRinging()
                    }
                }

                var ready by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var onboardingCompleted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!ready) {
                        onboardingCompleted = userPreferences.onboardingCompleted.first()
                        ready = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = ready,
                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                        label = "root"
                    ) { isReady ->
                        if (isReady) {
                            val navController = rememberNavController()
                            AppNavHost(
                                navController = navController,
                                startDestination = if (onboardingCompleted) {
                                    Screen.AlarmList
                                } else {
                                    Screen.Onboarding
                                }
                            )
                        } else {
                            SplashContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashContent() {
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "AlarmSimple",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Умный сон. Мягкое пробуждение.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
