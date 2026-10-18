package com.appssimple.alarmsimple.ui.admin

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import com.appssimple.alarmsimple.domain.model.SleepPhase
import com.appssimple.alarmsimple.service.AlarmFiringService
import com.appssimple.alarmsimple.service.AlarmScheduler
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(Modifier.width(8.dp))
                            Text("Панель разработчика", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Мгновенный будильник
                ModernCard {
                    Column {
                        Text("Тестирование будильника", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Запускает будильник прямо в эту секунду через создание тестового будильника и вызов AlarmFiringService.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.triggerInstantAlarm(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ЗАЗВОНИТЬ В ТУ ЖЕ СЕКУНДУ", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Тестовые данные истории сна
                ModernCard {
                    Column {
                        Text("Тестовая история сна", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Сгенерирует 14 ночей сна (за последние 2 недели) с реальными фазами (глубокий, легкий, REM, пробуждения), храпом и звуками, чтобы проверить графики и анализ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.generateTestSleepHistory() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Сгенерировать 14 ночей сна", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearAllSleepHistory() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Очистить всю историю сна")
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val alarmDao: AlarmDao,
    private val sleepSessionDao: SleepSessionDao,
    private val sleepEpochDao: SleepEpochDao,
    private val audioSnippetDao: AudioSnippetDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent = _messageEvent.asSharedFlow()

    fun triggerInstantAlarm(context: Context) {
        viewModelScope.launch {
            try {
                // Создаем мгновенный тестовый будильник в базе
                val testAlarm = AlarmEntity(
                    mode = "CLASSIC",
                    targetTimeMs = System.currentTimeMillis(),
                    label = "Тестовый будильник (Админка)",
                    isActive = true,
                    repeatDays = "[]",
                    questType = "NONE"
                )
                val alarmId = alarmDao.insertAlarm(testAlarm)

                // Сразу запускаем AlarmFiringService
                val intent = Intent(context, AlarmFiringService::class.java).apply {
                    putExtra(AlarmFiringService.EXTRA_ALARM_ID, alarmId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                _messageEvent.emit("Будильник запущен!")
            } catch (e: Exception) {
                _messageEvent.emit("Ошибка: ${e.message}")
            }
        }
    }

    fun generateTestSleepHistory() {
        viewModelScope.launch {
            try {
                val now = Calendar.getInstance()
                // Генерируем 14 ночей назад
                for (daysAgo in 1..14) {
                    val nightCal = (now.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, -daysAgo)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, Random.nextInt(0, 45))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val trackingStartMs = nightCal.timeInMillis
                    val onsetDelayMs = Random.nextLong(10 * 60_000L, 35 * 60_000L)
                    val sleepOnsetMs = trackingStartMs + onsetDelayMs

                    // Длительность от 6 до 8.5 часов
                    val sleepDurationHours = if (daysAgo % 4 == 0) 5.8f else (6.5f + Random.nextFloat() * 2.0f)
                    val sleepDurationMs = (sleepDurationHours * 3600 * 1000L).toLong()
                    val wakeUpMs = sleepOnsetMs + sleepDurationMs

                    val disturbanceCount = Random.nextInt(1, 6)
                    val snoreMinutes = if (Random.nextBoolean()) Random.nextInt(5, 40) else 0

                    val session = SleepSessionEntity(
                        trackingStartMs = trackingStartMs,
                        sleepOnsetMs = sleepOnsetMs,
                        wakeUpMs = wakeUpMs,
                        disturbanceCount = disturbanceCount,
                        snoreMinutes = snoreMinutes
                    )
                    val sessionId = sleepSessionDao.insertSession(session)

                    // Генерируем эпохи сна по 30 сек
                    val totalEpochs = (sleepDurationMs / 30_000L).toInt().coerceAtMost(900)
                    val epochs = ArrayList<SleepEpochEntity>(totalEpochs)

                    var currentPhase = SleepPhase.LIGHT
                    var phaseRemainingEpochs = Random.nextInt(10, 30)

                    for (epochIdx in 0 until totalEpochs) {
                        val epochTimeMs = sleepOnsetMs + (epochIdx * 30_000L)
                        if (phaseRemainingEpochs <= 0) {
                            val r = Random.nextFloat()
                            currentPhase = when {
                                r < 0.10f -> SleepPhase.AWAKE
                                r < 0.35f -> SleepPhase.REM
                                r < 0.65f -> SleepPhase.DEEP
                                else -> SleepPhase.LIGHT
                            }
                            phaseRemainingEpochs = Random.nextInt(10, 40)
                        } else {
                            phaseRemainingEpochs--
                        }

                        val movement = when (currentPhase) {
                            SleepPhase.AWAKE -> Random.nextFloat() * 0.8f + 0.2f
                            SleepPhase.LIGHT -> Random.nextFloat() * 0.3f
                            SleepPhase.REM -> Random.nextFloat() * 0.08f
                            SleepPhase.DEEP -> Random.nextFloat() * 0.02f
                        }

                        epochs.add(
                            SleepEpochEntity(
                                sessionId = sessionId,
                                timestampMs = epochTimeMs,
                                phase = currentPhase.name,
                                movementIntensity = movement
                            )
                        )
                    }
                    sleepEpochDao.insertEpochs(epochs)

                    // Добавляем пару аудио-записей если был храп или шум
                    if (snoreMinutes > 0) {
                        audioSnippetDao.insertSnippet(
                            AudioSnippetEntity(
                                sessionId = sessionId,
                                timestampMs = sleepOnsetMs + (Random.nextLong(60, 240) * 60_000L),
                                durationSeconds = 10,
                                filePath = "",
                                peakAmplitude = 0.6f,
                                type = "SNORE"
                            )
                        )
                    }
                    if (Random.nextFloat() > 0.4f) {
                        audioSnippetDao.insertSnippet(
                            AudioSnippetEntity(
                                sessionId = sessionId,
                                timestampMs = sleepOnsetMs + (Random.nextLong(120, 300) * 60_000L),
                                durationSeconds = 7,
                                filePath = "",
                                peakAmplitude = 0.45f,
                                type = if (Random.nextBoolean()) "SLEEP_TALK" else "COUGH_SNEEZE"
                            )
                        )
                    }
                }

                _messageEvent.emit("14 ночей тестового сна успешно созданы!")
            } catch (e: Exception) {
                _messageEvent.emit("Ошибка генерации: ${e.message}")
            }
        }
    }

    fun clearAllSleepHistory() {
        viewModelScope.launch {
            try {
                sleepSessionDao.cleanDiscardedSessions()
                val all = sleepSessionDao.getAllSessions()
                // удаляем за прошедшие дни
                sleepSessionDao.deleteSessionsBefore(System.currentTimeMillis() + 86400000L)
                _messageEvent.emit("История сна очищена!")
            } catch (e: Exception) {
                _messageEvent.emit("Ошибка очистки: ${e.message}")
            }
        }
    }
}
