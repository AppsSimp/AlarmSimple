package com.appssimple.alarmsimple.ui.alarmlist

import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.service.SleepTrackingService
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernBadge
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import com.appssimple.alarmsimple.ui.theme.BrandSecondaryLight
import com.appssimple.alarmsimple.ui.theme.PrimaryGradient
import com.appssimple.alarmsimple.ui.theme.SleepGradient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSleepTracking: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState(initial = emptyList())
    val selectedIds by viewModel.selectedAlarmIds.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()
    val testRingButtonsEnabled by viewModel.testRingButtonsEnabled.collectAsState(initial = false)

    val isTracking by SleepTrackingService.isTracking.collectAsState()
    val armedAlarmId by SleepTrackingService.armedAlarmId.collectAsState()
    val armedDurationMinutes by SleepTrackingService.armedDurationMinutes.collectAsState()
    val scheduledWakeTimeMs by SleepTrackingService.scheduledWakeTimeMs.collectAsState()

    val armedDurationText = remember(isTracking, armedDurationMinutes, scheduledWakeTimeMs) {
        if (isTracking && armedDurationMinutes != null && scheduledWakeTimeMs != null) {
            val hours = armedDurationMinutes!! / 60
            val mins = armedDurationMinutes!! % 60
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledWakeTimeMs!!))
            val durStr = buildString {
                append("$hours ч")
                if (mins > 0) append(" $mins м")
            }
            "Проснуться через $durStr · до $timeFormat"
        } else null
    }

    val coachMarksShown by viewModel.coachMarksShown.collectAsState(initial = true)
    var currentCoachStep by remember { mutableIntStateOf(0) } // 0: off, 1: Sleep card, 2: Help icon, 3: Add button

    LaunchedEffect(coachMarksShown) {
        if (!coachMarksShown) {
            currentCoachStep = 1
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Удалить выбранные будильники?") },
            text = { Text("Будет удалено будильников: ${selectedIds.size}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelectedAlarms(alarms)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Отмена") }
            }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("Выбрано: ${selectedIds.size}", fontWeight = FontWeight.Bold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Отмена выбора")
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                if (selectedIds.size == alarms.size) viewModel.clearSelection()
                                else viewModel.selectAll(alarms.map { it.id })
                            }) {
                                Icon(
                                    if (selectedIds.size == alarms.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = "Выбрать все"
                                )
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить выбранные", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = onOpenHelp) {
                                Icon(Icons.Default.HelpOutline, contentDescription = "Инструкция и возможности")
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = onAddAlarm,
                        containerColor = BrandPrimary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Новый будильник", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            if (alarms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Карточка быстрого запуска сна ВСЕГДА вверху экрана
                    QuickSleepCard(
                        onStartSleep = {
                            val intent = Intent(context, SleepTrackingService::class.java).apply {
                                action = SleepTrackingService.ACTION_START_TRACKING
                                putExtra(SleepTrackingService.EXTRA_ALARM_ID, -1L)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            onOpenSleepTracking()
                        },
                        armedDurationText = armedDurationText,
                        onDisableArmedAlarm = if (isTracking && armedAlarmId != null) {
                            { armedAlarmId?.let { viewModel.disarmDurationAlarm(it) } }
                        } else null
                    )

                    Spacer(Modifier.weight(0.5f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Нет будильников",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Вы можете запустить трекинг сна прямо сейчас или нажать «+» внизу для настройки умного будильника.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Баннер ближайшего будильника (обновляется в реальном времени каждую секунду)
                        var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1000L)
                                currentTimeMillis = System.currentTimeMillis()
                            }
                        }

                        val nextAlarmInfo = remember(alarms, currentTimeMillis) {
                            val now = currentTimeMillis
                            val activeAlarms = alarms.filter { it.isActive }
                            val upcomingTimes = activeAlarms.mapNotNull { alarm ->
                                if (alarm.mode == AlarmMode.DURATION.name) {
                                    null
                                } else if (alarm.targetTimeMs != null) {
                                    val repeatDays = runCatching {
                                        val arr = org.json.JSONArray(alarm.repeatDays)
                                        (0 until arr.length()).map { arr.getInt(it) }.toSet()
                                    }.getOrDefault(emptySet())

                                    if (alarm.targetTimeMs > now) {
                                        Pair(alarm, alarm.targetTimeMs)
                                    } else if (repeatDays.isNotEmpty()) {
                                        // Повторяющийся будильник: ищем следующий день
                                        val cal = Calendar.getInstance().apply { timeInMillis = alarm.targetTimeMs }
                                        val alarmCal = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                                            set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        if (alarmCal.timeInMillis <= now) {
                                            alarmCal.add(Calendar.DAY_OF_YEAR, 1)
                                        }
                                        Pair(alarm, alarmCal.timeInMillis)
                                    } else {
                                        // Одноразовый будильник с прошедшим временем не должен показывать срабатывание на следующий день
                                        null
                                    }
                                } else null
                            }

                            upcomingTimes.minByOrNull { it.second }
                        }

                        if (nextAlarmInfo != null) {
                            val diffMs = nextAlarmInfo.second - System.currentTimeMillis()
                            if (diffMs > 0) {
                                val diffHours = diffMs / (1000 * 60 * 60)
                                val diffMins = (diffMs / (1000 * 60)) % 60
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextAlarmInfo.second))
                                
                                val calNow = Calendar.getInstance()
                                val calAlarm = Calendar.getInstance().apply { timeInMillis = nextAlarmInfo.second }
                                val dayText = if (calAlarm.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)) {
                                    "сегодня"
                                } else if (calAlarm.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR) + 1) {
                                    "завтра"
                                } else {
                                    SimpleDateFormat("d MMM", Locale("ru")).format(Date(nextAlarmInfo.second))
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = BrandPrimary.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(BrandPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Ближайший: $dayText в $timeFormat",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val remainingText = buildString {
                                                append("через ")
                                                if (diffHours > 0) append("$diffHours ч ")
                                                append("$diffMins мин")
                                                if (nextAlarmInfo.first.label.isNotEmpty()) {
                                                    append(" • ${nextAlarmInfo.first.label}")
                                                }
                                            }
                                            Text(
                                                text = remainingText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = BrandPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Быстрый запуск ночного мониторинга
                        QuickSleepCard(
                            onStartSleep = {
                                val intent = Intent(context, SleepTrackingService::class.java).apply {
                                    action = SleepTrackingService.ACTION_START_TRACKING
                                    putExtra(SleepTrackingService.EXTRA_ALARM_ID, -1L)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                onOpenSleepTracking()
                            },
                            armedDurationText = armedDurationText,
                            onDisableArmedAlarm = if (isTracking && armedAlarmId != null) {
                                { armedAlarmId?.let { viewModel.disarmDurationAlarm(it) } }
                            } else null
                        )
                    }

                    items(alarms, key = { it.id }) { alarm ->
                        val isSelected = selectedIds.contains(alarm.id)

                        // Swipe-to-Dismiss для мгновенного удаления свайпом
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteAlarm(alarm)
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Будильник удален",
                                            actionLabel = "Отменить"
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreAlarm(alarm)
                                        }
                                    }
                                    true
                                } else false
                            }
                        )

                        val isSwipingAlarm = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                if (isSwipingAlarm) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFE11D48))
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
                        ) {
                            val isThisAlarmArmed = isTracking && armedAlarmId == alarm.id
                            AlarmItemCard(
                                alarm = alarm,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onSelect = { viewModel.toggleSelection(alarm.id) },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelection(alarm.id)
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(alarm.id)
                                    } else {
                                        onEditAlarm(alarm.id)
                                    }
                                },
                                onToggleActive = { isActive ->
                                    viewModel.toggleAlarmActive(alarm.id, isActive)
                                },
                                onStartSleepForAlarm = {
                                    val intent = Intent(context, SleepTrackingService::class.java).apply {
                                        action = SleepTrackingService.ACTION_START_TRACKING
                                        putExtra(SleepTrackingService.EXTRA_ALARM_ID, alarm.id)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    onOpenSleepTracking()
                                },
                                showTestRingButton = testRingButtonsEnabled,
                                onTriggerNow = {
                                    val intent = Intent(context, com.appssimple.alarmsimple.service.AlarmFiringService::class.java).apply {
                                        putExtra(com.appssimple.alarmsimple.service.AlarmFiringService.EXTRA_ALARM_ID, alarm.id)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                },
                                isArmedDuration = isThisAlarmArmed,
                                armedDurationText = if (isThisAlarmArmed) armedDurationText else null,
                                onDisableArmedAlarm = {
                                    viewModel.disarmDurationAlarm(alarm.id)
                                }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
        
        if (currentCoachStep > 0) {
            CoachMarksOverlay(
                step = currentCoachStep,
                onNext = {
                    if (currentCoachStep < 3) {
                        currentCoachStep++
                    } else {
                        currentCoachStep = 0
                        viewModel.dismissCoachMarks()
                    }
                },
                onDismiss = {
                    currentCoachStep = 0
                    viewModel.dismissCoachMarks()
                }
            )
        }
    }
}

@Composable
fun CoachMarksOverlay(
    step: Int,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = { /* block clicks underneath */ })
            .padding(24.dp)
    ) {
        val title: String
        val description: String
        val alignment: Alignment

        when (step) {
            1 -> {
                title = "🌙 Быстрый сон"
                description = "Хотите отслеживать фазы сна без звукового будильника? Нажмите эту карточку прямо перед сном — умный трекинг запишет фазы сна и пробуждения."
                alignment = Alignment.TopCenter
            }
            2 -> {
                title = "❓ Справочник и подсказки"
                description = "Здесь собрана интерактивная инструкция со всеми возможностями: как привязать NFC-метку, сканировать штрихкод или настроить умное окно пробуждения."
                alignment = Alignment.TopEnd
            }
            3 -> {
                title = "➕ Создание умного будильника"
                description = "Нажмите здесь, чтобы создать будильник с мягким пробуждением в лёгкой фазе сна, задачами подтверждения бодрствования и защитой от выключения."
                alignment = Alignment.BottomCenter
            }
            else -> {
                title = ""
                description = ""
                alignment = Alignment.Center
            }
        }

        Card(
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth()
                .padding(top = if (step == 1 || step == 2) 72.dp else 0.dp, bottom = if (step == 3) 88.dp else 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$step из 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (step < 3) "Далее" else "Понятно, начать!")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickSleepCard(
    onStartSleep: () -> Unit,
    armedDurationText: String? = null,
    onDisableArmedAlarm: (() -> Unit)? = null
) {
    val isTracking by SleepTrackingService.isTracking.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isTracking) BrandPrimary.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                width = 1.dp,
                brush = if (isTracking) PrimaryGradient else SleepGradient,
                shape = RoundedCornerShape(16.dp)
            )
            .background(if (isTracking) Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))) else SleepGradient)
            .clickable { onStartSleep() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTracking) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandSecondary)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isTracking) "Сон отслеживается сейчас" else "Идёте спать прямо сейчас?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isTracking) {
                        armedDurationText ?: "Нажмите, чтобы открыть таймер и управление сном"
                    } else {
                        "Нажмите «Спать», чтобы начать трекинг фаз сна прямо сейчас"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = if (armedDurationText != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isTracking && onDisableArmedAlarm != null) {
                    OutlinedButton(
                        onClick = onDisableArmedAlarm,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFB7185)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFB7185).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Откл. аларм", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onStartSleep,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) BrandSecondary else Color.White,
                        contentColor = if (isTracking) Color.White else Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        if (isTracking) Icons.Default.GraphicEq else Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isTracking) "Экран" else "Спать", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlarmItemCard(
    alarm: AlarmEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onStartSleepForAlarm: () -> Unit = {},
    showTestRingButton: Boolean = false,
    onTriggerNow: () -> Unit = {},
    isArmedDuration: Boolean = false,
    armedDurationText: String? = null,
    onDisableArmedAlarm: () -> Unit = {}
) {
    val isDuration = alarm.mode == AlarmMode.DURATION.name
    val isArmed = if (isDuration) (alarm.isActive || isArmedDuration) else alarm.isActive

    // 4) Armed duration alarm must look armed (white), not gray
    val borderColor = when {
        isSelected -> BrandPrimary
        isDuration && isArmed -> Color.White.copy(alpha = 0.85f)
        alarm.isActive -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    }

    val backgroundColor = when {
        isSelected -> BrandPrimary.copy(alpha = 0.12f)
        isDuration && isArmed -> Color(0xFF141C34)
        else -> MaterialTheme.colorScheme.surface
    }

    val primaryTextColor = when {
        isDuration && isArmed -> Color.White
        alarm.isActive -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline
    }

    // 5) Compact card: reduced vertical paddings
    ModernCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Верхняя строка: Время / Длительность слева, Кнопки / Переключатель справа
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = BrandPrimary)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (alarm.label.isNotEmpty()) {
                        Text(
                            text = alarm.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (isDuration) {
                        val hours = (alarm.durationMinutes ?: 450) / 60
                        val mins = (alarm.durationMinutes ?: 450) % 60
                        Text(
                            text = "$hours ч ${if (mins > 0) "$mins мин" else ""}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    } else {
                        val timeStr = if (alarm.targetTimeMs != null) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.targetTimeMs))
                        } else "07:00"

                        Text(
                            text = timeStr,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }

                // Элементы управления справа: на одной строке с временем
                if (!isSelectionMode) {
                    if (!isDuration) {
                        Switch(
                            checked = alarm.isActive,
                            onCheckedChange = onToggleActive,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandPrimary
                            )
                        )
                    } else {
                        if (isArmedDuration) {
                            // 2) Компактная кнопка отключения активного будильника БЕЗ остановки анализа сна
                            OutlinedButton(
                                onClick = onDisableArmedAlarm,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFB7185)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFB7185).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Отключить", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = onStartSleepForAlarm,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isArmed) BrandSecondary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isArmed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Спать", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            // 5) Вторая строка: режим / статус взведения / повтор / бейджи — НА ОДНОЙ СТРОКЕ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDuration) {
                    if (isArmedDuration && armedDurationText != null) {
                        // 2) Показываем какой будильник по длительности взведен (например: "Проснуться через 7 ч 30 м · до 05:30")
                        Text(
                            text = armedDurationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Отсчет от засыпания",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isArmed) BrandSecondaryLight else MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    val repeatDays = remember(alarm.repeatDays) {
                        runCatching {
                            val arr = org.json.JSONArray(alarm.repeatDays)
                            (0 until arr.length()).map { arr.getInt(it) }.toSet()
                        }.getOrDefault(emptySet())
                    }

                    if (repeatDays.isNotEmpty()) {
                        val daysText = if (repeatDays.size == 7) {
                            "Каждый день"
                        } else if (repeatDays == setOf(1, 2, 3, 4, 5)) {
                            "По будням"
                        } else if (repeatDays == setOf(6, 7)) {
                            "По выходным"
                        } else {
                            val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                            repeatDays.sorted().joinToString(", ") { names[it - 1] }
                        }
                        Text(
                            text = daysText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (alarm.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "Один раз",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (alarm.smartWakeEnabled) {
                    ModernBadge(
                        text = "±${alarm.smartWakeWindowMinutes}м",
                        color = if (isArmed) BrandPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }
                if (alarm.questType != "NONE") {
                    val questLabel = when (alarm.questType) {
                        "NFC" -> "NFC"
                        "BARCODE" -> "Штрихкод"
                        "MATH" -> "Математика"
                        else -> alarm.questType
                    }
                    ModernBadge(
                        text = questLabel,
                        color = if (isArmed) BrandSecondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                }
            }

            // Кнопка разработчика / теста "Зазвонить сейчас" (одна компактная строка, если включена)
            if (showTestRingButton && !isSelectionMode) {
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onTriggerNow,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Тест: зазвонить", fontSize = 11.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
