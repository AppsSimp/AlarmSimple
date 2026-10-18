package com.appssimple.alarmsimple.ui.setup

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.domain.model.QuestType
import com.appssimple.alarmsimple.quest.MathDifficulty
import com.appssimple.alarmsimple.quest.MathQuestEngine
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernBadge
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandPrimaryLight
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import com.appssimple.alarmsimple.ui.theme.BrandRose
import com.appssimple.alarmsimple.ui.theme.PrimaryGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmSetupScreen(
    alarmId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AlarmSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimePicker by remember { mutableStateOf(false) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        viewModel.loadAlarm(alarmId)
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Лаунчер для системного выбора рингтона
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "Выбранный сигнал"
                viewModel.setRingtone(uri.toString(), title)
            } else {
                viewModel.setRingtone(null, "Без звука")
            }
        }
    }

    if (showNfcDialog) {
        NfcSetupDialog(
            onDismiss = { showNfcDialog = false },
            onTagScanned = { tagId ->
                viewModel.setQuestData(tagId)
                showNfcDialog = false
            }
        )
    }

    if (showBarcodeDialog) {
        BarcodeSetupDialog(
            onDismiss = { showBarcodeDialog = false },
            onBarcodeScanned = { barcode ->
                viewModel.setQuestData(barcode)
                showBarcodeDialog = false
            }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.targetHour,
            initialMinute = state.targetMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить будильник?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAlarm { onNavigateBack() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
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
                        Text(
                            text = if (state.isEditing) "Изменить будильник" else "Новый будильник",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.saveAlarm { onNavigateBack() } },
                            enabled = !state.isSaving
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Сохранить", fontWeight = FontWeight.Bold)
                            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Название
                ModernCard {
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = viewModel::setLabel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название будильника") },
                        placeholder = { Text("Например: На работу, Тренировка") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                // Режим и время
                ModernCard {
                    Column {
                        Text("Режим установки", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.mode == AlarmMode.CLASSIC,
                                onClick = { viewModel.setMode(AlarmMode.CLASSIC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Классический")
                            }
                            SegmentedButton(
                                selected = state.mode == AlarmMode.DURATION,
                                onClick = { viewModel.setMode(AlarmMode.DURATION) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("По длительности")
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        if (state.mode == AlarmMode.CLASSIC) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTimePicker = true }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", state.targetHour, state.targetMinute),
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPrimary,
                                    letterSpacing = (-1).sp
                                )
                                Text(
                                    text = "Нажмите, чтобы изменить время",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(14.dp))

                            // Выбор повторения
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Повтор будильника",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (state.repeatDays.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearRepeatDays() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Сбросить", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))

                            // Быстрые пресеты повтора
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isOnce = state.repeatDays.isEmpty()
                                val isDaily = state.repeatDays.size == 7
                                val isWorkdays = state.repeatDays == setOf(1, 2, 3, 4, 5)
                                val isWeekend = state.repeatDays == setOf(6, 7)

                                FilterChip(
                                    selected = isOnce,
                                    onClick = { viewModel.clearRepeatDays() },
                                    label = { Text("Один раз", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = isWorkdays,
                                    onClick = { viewModel.setRepeatDays(setOf(1, 2, 3, 4, 5)) },
                                    label = { Text("По будням", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = isWeekend,
                                    onClick = { viewModel.setRepeatDays(setOf(6, 7)) },
                                    label = { Text("Выходные", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = isDaily,
                                    onClick = { viewModel.setRepeatDays((1..7).toSet()) },
                                    label = { Text("Каждый день", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Или выберите конкретные дни:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))

                            val days = listOf(
                                1 to "Пн",
                                2 to "Вт",
                                3 to "Ср",
                                4 to "Чт",
                                5 to "Пт",
                                6 to "Сб",
                                7 to "Вс"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                days.forEach { (dayNum, dayLabel) ->
                                    val isSelected = state.repeatDays.contains(dayNum)
                                    val isWeekend = dayNum >= 6
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> BrandPrimary
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                }
                                            )
                                            .clickable { viewModel.toggleRepeatDay(dayNum) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = when {
                                                isSelected -> Color.White
                                                isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Интервал повторения: каждую неделю / через неделю / через две недели
                            val isRepeatEnabled = state.repeatDays.isNotEmpty()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    1 to "Каждую нед.",
                                    2 to "Через неделю",
                                    3 to "Через 2 нед."
                                ).forEach { (interval, label) ->
                                    val isSelected = isRepeatEnabled && state.repeatIntervalWeeks == interval
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    !isRepeatEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                                    isSelected -> BrandPrimary
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                }
                                            )
                                            .clickable(enabled = isRepeatEnabled) { viewModel.setRepeatIntervalWeeks(interval) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                !isRepeatEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                isSelected -> Color.White
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            val baseSummary = when {
                                state.repeatDays.isEmpty() -> "Один раз (без повтора)"
                                state.repeatDays.size == 7 -> "Каждый день"
                                state.repeatDays == setOf(1, 2, 3, 4, 5) -> "Только по будням (Пн-Пт)"
                                state.repeatDays == setOf(6, 7) -> "Только по выходным (Сб-Вс)"
                                else -> "Выбрано дней: ${state.repeatDays.size}"
                            }
                            val intervalSummary = when (state.repeatIntervalWeeks) {
                                2 -> " • повтор через неделю"
                                3 -> " • повтор через 2 недели"
                                else -> ""
                            }
                            Text(
                                text = baseSummary + intervalSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandPrimaryLight
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val hours = state.durationHours
                                val mins = state.durationMinutesPart
                                val durationText = when {
                                    hours > 0 && mins > 0 -> "$hours ч $mins мин"
                                    hours > 0 -> "$hours ч"
                                    else -> "$mins мин"
                                }

                                Text(
                                    text = durationText,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandSecondary
                                )
                                Text(
                                    text = "Таймер сна запустится только после реального засыпания",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(10.dp))

                                // Быстрые пресеты
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        30 to "30м",
                                        45 to "45м",
                                        90 to "1.5ч",
                                        180 to "3ч",
                                        450 to "7.5ч"
                                    ).forEach { (minsPreset, label) ->
                                        val isSel = state.durationMinutes == minsPreset
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSel) BrandSecondary
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .clickable { viewModel.setDuration(minsPreset) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Slider(
                                    value = state.durationMinutes.toFloat(),
                                    onValueChange = { raw ->
                                        // Округляем до 5 минут для аккуратности
                                        val rounded = ((raw / 5).toInt() * 5).coerceIn(30, 600)
                                        viewModel.setDuration(rounded)
                                    },
                                    valueRange = 30f..600f,
                                    steps = 113, // шаг 5 минут: (600 - 30) / 5 - 1 = 113
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("30 мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("1.5 ч (1 цикл)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("7.5 ч (5 циклов)", style = MaterialTheme.typography.labelSmall, color = BrandSecondary, fontWeight = FontWeight.Bold)
                                    Text("10 ч", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }

                                if (state.durationMinutes < 90) {
                                    Spacer(Modifier.height(10.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Длительность меньше одного полного цикла сна (~90 мин). Умное пробуждение в фазе легкого сна может быть недоступно — будильник сработает точно по истечении времени.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFFCD34D),
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Звук будильника
                ModernCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BrandPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Мелодия сигнала", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = state.ringtoneTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите мелодию")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.ringtoneUri?.let { Uri.parse(it) })
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    }
                                    ringtonePickerLauncher.launch(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Выбрать", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // Умное пробуждение (Компактный аккуратный блок)
                ModernCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BrandPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Умное пробуждение", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "В фазе легкого сна в окне ${state.smartWakeWindowMinutes} мин",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = state.smartWakeEnabled,
                                onCheckedChange = viewModel::setSmartWakeEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandPrimary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF2A344E)
                                )
                            )
                        }

                        if (state.smartWakeEnabled) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 20, 30, 45).forEach { window ->
                                    val isSelected = state.smartWakeWindowMinutes == window
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) BrandPrimary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { viewModel.setSmartWakeWindow(window) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "±$window м",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Проверка для классического режима: если до будильника осталось меньше 90 минут
                            val calNow = Calendar.getInstance()
                            val nowMin = calNow.get(Calendar.HOUR_OF_DAY) * 60 + calNow.get(Calendar.MINUTE)
                            val targetMin = state.targetHour * 60 + state.targetMinute
                            val diffMin = if (targetMin >= nowMin) targetMin - nowMin else (1440 - nowMin) + targetMin

                            val isShortSleep = if (state.mode == AlarmMode.DURATION) {
                                state.durationMinutes < 90
                            } else {
                                state.repeatDays.isEmpty() && diffMin in 1..89
                            }

                            if (isShortSleep) {
                                Spacer(Modifier.height(10.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "До пробуждения менее 90 минут (меньше цикла сна). Определение лёгкой фазы сна может не сработать вовремя — будильник прозвонит в установленное время.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFCD34D),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Подтверждение бодрствования
                ModernCard {
                    Column {
                        Text("Подтверждение бодрствования", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Условие, которое необходимо выполнить для отключения звонка",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        val quests = listOf(
                            QuestType.NONE to "Обычное отключение (кнопкой)",
                            QuestType.MATH to "Математические примеры",
                            QuestType.BARCODE to "Сканирование штрихкода",
                            QuestType.NFC to "Прикладывание карты или NFC-метки"
                        )

                        quests.forEach { (type, title) ->
                            val selected = state.questType == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setQuestType(type) }
                                    .background(if (selected) BrandPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.setQuestType(type) },
                                    colors = RadioButtonDefaults.colors(selectedColor = BrandPrimary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        // Специфические настройки выбранного квеста
                        Spacer(Modifier.height(8.dp))

                        // NFC настройки
                        if (state.questType == QuestType.NFC) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    if (state.questData.isNullOrEmpty()) {
                                        Text(
                                            text = "⚠️ NFC-метка еще не привязана!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { showNfcDialog = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Nfc, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Привязать NFC-метку сейчас")
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                ModernBadge(text = "Метка привязана ✓", color = BrandSecondary)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "ID: ${state.questData}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = { showNfcDialog = true }) {
                                                Text("Сменить")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Штрихкод настройки
                        if (state.questType == QuestType.BARCODE) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    if (state.questData.isNullOrEmpty()) {
                                        Text(
                                            text = "⚠️ Предмет еще не выбран!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { showBarcodeDialog = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Отсканировать предмет камерой")
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                ModernBadge(text = "Штрихкод сохранен ✓", color = BrandSecondary)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "Код: ${state.questData}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = { showBarcodeDialog = true }) {
                                                Text("Пересканировать")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Математика настройки
                        if (state.questType == QuestType.MATH) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Text("Сложность вычислений", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MathDifficulty.entries.forEach { diff ->
                                        FilterChip(
                                            selected = state.mathDifficulty == diff,
                                            onClick = { viewModel.setMathDifficulty(diff) },
                                            label = { Text(diff.title, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Пример
                                val sample1 = remember(state.mathDifficulty) { MathQuestEngine.generateSample(state.mathDifficulty).expression }
                                val sample2 = remember(state.mathDifficulty) { MathQuestEngine.generateSample(state.mathDifficulty).expression }

                                Text(
                                    text = "Примеры: $sample1 = ? ,  $sample2 = ?",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "На каждый пример дается 15 секунд. Ошибка сбрасывает счетчик на 0.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Жесткость пробуждения
                ModernCard {
                    Column {
                        Text("Защита от отключения", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Блокировка громкости", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Запрещает убавлять звук кнопками на корпусе телефона",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.volumeLockEnabled,
                                onCheckedChange = viewModel::setVolumeLockEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BrandPrimary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF2A344E)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Кнопка сохранения
                Button(
                    onClick = { viewModel.saveAlarm { onNavigateBack() } },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить будильник", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (state.isEditing) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Удалить будильник")
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
