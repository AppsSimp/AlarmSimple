package com.appssimple.alarmsimple.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val retentionDays by viewModel.historyRetentionDays.collectAsState(initial = 0)
    val defaultSnooze by viewModel.defaultSnoozeMinutes.collectAsState(initial = 5)
    val defaultSmartWake by viewModel.defaultSmartWakeWindow.collectAsState(initial = 30)
    val defaultVolumeLock by viewModel.defaultVolumeLock.collectAsState(initial = false)
    val backupAlarmEnabled by viewModel.backupSystemAlarmEnabled.collectAsState(initial = false)

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Хранение истории
                ModernCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = BrandPrimary)
                            Spacer(Modifier.width(10.dp))
                            Text("Хранение истории сна", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Срок автоматического сохранения ночей и аудиоклипов:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        val retentionOptions = listOf(0 to "Без ограничений", 30 to "30 дней", 90 to "90 дней")
                        retentionOptions.forEach { (days, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setRetentionDays(days) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = retentionDays == days,
                                    onClick = { viewModel.setRetentionDays(days) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Параметры по умолчанию
                ModernCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Snooze, contentDescription = null, tint = BrandSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text("Отложение по умолчанию", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Длительность снуза (когда квесты выключены):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 15).forEach { mins ->
                                FilterChip(
                                    selected = defaultSnooze == mins,
                                    onClick = { viewModel.setDefaultSnooze(mins) },
                                    label = { Text("$mins мин") }
                                )
                            }
                        }
                    }
                }

                // Надежность и предохранитель
                ModernCard {
                    Column {
                        Text("Надежность и защита", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Системный предохранитель", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Заводить резервный системный будильник на 5 минут позже (на случай перезагрузки/сбоя системы). При успешном срабатывании приложения отключается автоматически.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = backupAlarmEnabled, onCheckedChange = viewModel::setBackupSystemAlarmEnabled)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Блокировка громкости", fontWeight = FontWeight.SemiBold)
                                Text("Блокировать качельку звука во время звонка", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = defaultVolumeLock, onCheckedChange = viewModel::setDefaultVolumeLock)
                        }
                    }
                }

                // О приложении
                ModernCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("О продукте AlarmSimple", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Версия 1.0.0 (Open Source)\n" +
                            "• 100% Offline: без интернета, без рекламы и подписок\n" +
                            "• Приватность: аудио обрабатывается локально и никогда не передается в сеть.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {
    val historyRetentionDays = userPreferences.historyRetentionDays
    val defaultSnoozeMinutes = userPreferences.defaultSnoozeMinutes
    val defaultSmartWakeWindow = userPreferences.defaultSmartWakeWindow
    val defaultVolumeLock = userPreferences.defaultVolumeLock
    val backupSystemAlarmEnabled = userPreferences.backupSystemAlarmEnabled

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { userPreferences.setHistoryRetentionDays(days) }
    }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch { userPreferences.setDefaultSnoozeMinutes(minutes) }
    }

    fun setDefaultVolumeLock(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDefaultVolumeLock(enabled) }
    }

    fun setBackupSystemAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBackupSystemAlarmEnabled(enabled) }
    }
}
