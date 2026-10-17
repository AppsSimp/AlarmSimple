package com.appssimple.alarmsimple.ui.alarmlist

import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
    onOpenHistory: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState(initial = emptyList())
    val selectedIds by viewModel.selectedAlarmIds.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()

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
                            IconButton(onClick = onOpenHistory) {
                                Icon(Icons.Default.History, contentDescription = "История сна")
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(48.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Нет активных будильников",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Нажмите кнопку ниже, чтобы настроить умный подъем по фазам сна или обычный будильник.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
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
                                scope.launch {
                                    snackbarHostState.showSnackbar("Трекинг сна запущен. Спокойной ночи!")
                                }
                            }
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

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
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
                            }
                        ) {
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
                                }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
fun QuickSleepCard(onStartSleep: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SleepGradient)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Идёте спать прямо сейчас?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Приложение начнет анализ дыхания и фаз сна",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onStartSleep,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Баиньки", fontWeight = FontWeight.Bold)
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
    onToggleActive: (Boolean) -> Unit
) {
    val isDuration = alarm.mode == AlarmMode.DURATION.name

    val borderColor = when {
        isSelected -> BrandPrimary
        alarm.isActive -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    val backgroundColor = when {
        isSelected -> BrandPrimary.copy(alpha = 0.12f)
        alarm.isActive -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }

    ModernCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(16.dp)
    ) {
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
                // Label if present
                if (alarm.label.isNotEmpty()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (isDuration) {
                    val hours = (alarm.durationMinutes ?: 450) / 60
                    val mins = (alarm.durationMinutes ?: 450) % 60
                    Text(
                        text = "$hours ч ${if (mins > 0) "$mins мин" else ""}",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Отсчет только после засыпания",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandSecondary
                    )
                } else {
                    val timeStr = if (alarm.targetTimeMs != null) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.targetTimeMs))
                    } else "07:00"

                    Text(
                        text = timeStr,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        letterSpacing = (-1).sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alarm.smartWakeEnabled) {
                        ModernBadge(text = "Умное окно: ±${alarm.smartWakeWindowMinutes}м", color = BrandPrimary)
                    }
                    if (alarm.questType != "NONE") {
                        val questLabel = when (alarm.questType) {
                            "NFC" -> "NFC"
                            "BARCODE" -> "Штрихкод"
                            "MATH" -> "Математика"
                            else -> alarm.questType
                        }
                        ModernBadge(text = "Квест: $questLabel", color = BrandSecondary)
                    }
                    if (alarm.sirenEnabled) {
                        ModernBadge(text = "Сирена 🚨", color = Color(0xFFF43F5E))
                    }
                }
            }

            if (!isSelectionMode) {
                Switch(
                    checked = alarm.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandPrimary)
                )
            }
        }
    }
}
