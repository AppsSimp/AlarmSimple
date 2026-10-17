package com.appssimple.alarmsimple.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.domain.model.AlarmMode
import com.appssimple.alarmsimple.domain.model.QuestType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetupScreen(
    alarmId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AlarmSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(alarmId) {
        viewModel.loadAlarm(alarmId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId != null && alarmId != -1L) "Редактировать будильник" else "Новый будильник") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveAlarm {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Выбор режима установки
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Режим будильника",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.mode == AlarmMode.CLASSIC,
                            onClick = { viewModel.setMode(AlarmMode.CLASSIC) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(stringResource(R.string.mode_classic))
                        }
                        SegmentedButton(
                            selected = state.mode == AlarmMode.DURATION,
                            onClick = { viewModel.setMode(AlarmMode.DURATION) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(stringResource(R.string.mode_duration))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (state.mode == AlarmMode.CLASSIC) {
                        // Выбор времени
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d:%02d", state.targetHour, state.targetMinute),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    // Открытие выбора времени
                                }
                            )
                        }
                    } else {
                        // Выбор длительности сна
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${state.durationHours} ч ${state.durationMinutesPart} мин сна",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Таймер запустится автоматически, когда вы реально уснете",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = state.durationMinutes.toFloat(),
                                onValueChange = { viewModel.setDuration(it.toInt()) },
                                valueRange = 240f..600f, // от 4 до 10 часов
                                steps = 23, // шаг по 15 минут
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 2. Умное окно пробуждения (Smart Wake-Up)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.smart_wake),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.smart_wake_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.smartWakeEnabled,
                            onCheckedChange = { viewModel.setSmartWakeEnabled(it) }
                        )
                    }

                    if (state.smartWakeEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Окно пробуждения: за ${state.smartWakeWindowMinutes} минут",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 20, 30, 45).forEach { window ->
                                FilterChip(
                                    selected = state.smartWakeWindowMinutes == window,
                                    onClick = { viewModel.setSmartWakeWindow(window) },
                                    label = { Text("$window мин") }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Квесты на отключение
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Квест на отключение",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    val quests = listOf(
                        QuestType.NONE to stringResource(R.string.quest_none),
                        QuestType.MATH to stringResource(R.string.quest_math),
                        QuestType.BARCODE to stringResource(R.string.quest_barcode),
                        QuestType.NFC to stringResource(R.string.quest_nfc)
                    )

                    quests.forEach { (type, title) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuestType(type) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.questType == type,
                                onClick = { viewModel.setQuestType(type) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // 4. Жесткость пробуждения (Сирена и Блокировка звука)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Жесткость пробуждения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.siren_enabled), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.siren_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.sirenEnabled,
                            onCheckedChange = { viewModel.setSirenEnabled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.volume_lock), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.volume_lock_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.volumeLockEnabled,
                            onCheckedChange = { viewModel.setVolumeLockEnabled(it) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
