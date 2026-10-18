package com.appssimple.alarmsimple.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernBadge
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class SleepAnalysisPeriod(val label: String) {
    DAY("День"),
    WEEK("Неделя"),
    MONTH("Месяц"),
    YEAR("Год")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(
    onOpenSession: (Long) -> Unit,
    onNavigateBack: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: SleepHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    var selectedPeriod by remember { mutableStateOf(SleepAnalysisPeriod.WEEK) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Insights, contentDescription = null, tint = BrandPrimary)
                            Spacer(Modifier.width(10.dp))
                            Text("Сон и аналитика", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Вкладки переключения периода анализа: День / Неделя / Месяц / Год
                item {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SleepAnalysisPeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = SleepAnalysisPeriod.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = BrandPrimary,
                                    activeContentColor = Color.White
                                )
                            ) {
                                Text(period.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Карточка детального анализа за выбранный период
                item {
                    SleepPeriodAnalysisCard(
                        period = selectedPeriod,
                        sessions = sessions
                    )
                }

                item {
                    Text(
                        text = "Журнал ночей (${sessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (sessions.isEmpty()) {
                    item {
                        ModernCard(contentPadding = PaddingValues(28.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Пока нет записей сна",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Здесь появятся ваши ночи сна с фазами, ворочаниями и анализом.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissVal ->
                                if (dismissVal == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteSession(session)
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar("Запись удалена", actionLabel = "Отменить")
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreSession(session)
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
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFE11D48))
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                                }
                            }
                        ) {
                            SleepHistoryCardItem(
                                session = session,
                                onClick = { onOpenSession(session.id) }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
fun SleepPeriodAnalysisCard(
    period: SleepAnalysisPeriod,
    sessions: List<SleepSessionEntity>
) {
    val now = System.currentTimeMillis()
    val periodCutoff = remember(period, now) {
        val cal = Calendar.getInstance()
        when (period) {
            SleepAnalysisPeriod.DAY -> cal.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            SleepAnalysisPeriod.WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            SleepAnalysisPeriod.MONTH -> cal.apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
            SleepAnalysisPeriod.YEAR -> cal.apply { add(Calendar.DAY_OF_YEAR, -365) }.timeInMillis
        }
    }

    val periodSessions = remember(sessions, periodCutoff) {
        sessions.filter { it.trackingStartMs >= periodCutoff }
    }

    val (avgHours, avgEfficiency, totalDebtMinutes, analysisText, statusColor) = remember(periodSessions, period) {
        if (periodSessions.isEmpty()) {
            Triple(0f, 0, 0) to ("Нет данных за этот период. Спите с приложением для точной статистики." to Color.Gray)
        } else {
            val totalMinutes = periodSessions.mapNotNull {
                if (it.sleepOnsetMs != null && it.wakeUpMs != null) {
                    ((it.wakeUpMs - it.sleepOnsetMs) / 60_000L).toInt()
                } else null
            }

            val avgMins = if (totalMinutes.isNotEmpty()) totalMinutes.average().toFloat() else 0f
            val avgH = avgMins / 60f

            val efficiencies = periodSessions.mapNotNull {
                if (it.sleepOnsetMs != null && it.wakeUpMs != null) {
                    val sleep = it.wakeUpMs - it.sleepOnsetMs
                    val bed = it.wakeUpMs - it.trackingStartMs
                    if (bed > 0) ((sleep.toFloat() / bed) * 100).toInt() else null
                } else null
            }
            val avgEff = if (efficiencies.isNotEmpty()) efficiencies.average().toInt() else 0

            // Норма сна взрослого: 8 часов в сутки (480 минут)
            val expectedTotalMinutes = periodSessions.size * 480
            val actualTotalMinutes = totalMinutes.sum()
            val balance = actualTotalMinutes - expectedTotalMinutes

            val text: String
            val col: Color
            when {
                balance < -90 -> {
                    val debtHours = (-balance) / 60
                    val debtMins = (-balance) % 60
                    text = "⚠️ Зафиксирован недосып: дефицит $debtHours ч $debtMins мин. Глубокие фазы сна сокращаются, повышается утомляемость. Рекомендуем лечь спать на 40 минут раньше."
                    col = Color(0xFFF43F5E)
                }
                balance > 90 -> {
                    val overHours = balance / 60
                    text = "💤 Пересып: превышение нормы на $overHours ч. Длительный сон может вызывать дневную сонливость и смещение циркадных ритмов."
                    col = Color(0xFFFBBF24)
                }
                else -> {
                    text = "✅ Отличный баланс сна: вы находитесь в оптимальной норме (~8 часов). Восстановление организма и баланс глубоких/REM фаз на максимуме."
                    col = Color(0xFF10B981)
                }
            }

            Triple(avgH, avgEff, balance) to (text to col)
        }.let { (stats, textCol) ->
            Tuple5(stats.first, stats.second, stats.third, textCol.first, textCol.second)
        }
    }

    ModernCard(
        backgroundColor = Color(0xFF141E38),
        borderColor = BrandPrimary.copy(alpha = 0.35f),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Анализ сна (${period.label.lowercase()})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                ModernBadge(
                    text = if (totalDebtMinutes < -30) "Недосып" else if (totalDebtMinutes > 30) "Пересып" else "В норме",
                    color = statusColor
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("В среднем сна", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "%.1f ч".format(Locale.US, avgHours),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column {
                    Text("Эффективность", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$avgEfficiency%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (avgEfficiency >= 85) Color(0xFF10B981) else Color(0xFFFBBF24)
                    )
                }
                Column {
                    Text("Ночей в базе", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${periodSessions.size}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            Text(
                text = analysisText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

@Composable
fun SleepHistoryCardItem(
    session: SleepSessionEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMMM, EEEE", Locale("ru")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val dateStr = dateFormat.format(Date(session.trackingStartMs)).replaceFirstChar { it.titlecase(Locale("ru")) }
    val onsetStr = session.sleepOnsetMs?.let { timeFormat.format(Date(it)) } ?: "--:--"
    val wakeStr = session.wakeUpMs?.let { timeFormat.format(Date(it)) } ?: "--:--"

    val totalSleepMinutes = if (session.sleepOnsetMs != null && session.wakeUpMs != null) {
        ((session.wakeUpMs - session.sleepOnsetMs) / 60_000L).toInt()
    } else 0
    val hours = totalSleepMinutes / 60
    val mins = totalSleepMinutes % 60

    ModernCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${hours}ч ${mins}м сна",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Сон с $onsetStr до $wakeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (session.snoreMinutes > 0) {
                    ModernBadge(text = "😴 ${session.snoreMinutes}м храпа", color = Color(0xFF38BDF8))
                }
                if (session.disturbanceCount > 0) {
                    ModernBadge(text = "⚡ ${session.disturbanceCount} ворочаний", color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

@HiltViewModel
class SleepHistoryViewModel @Inject constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val userPreferences: UserPreferences
) : ViewModel() {
    val sessions: Flow<List<SleepSessionEntity>> = sleepSessionDao.getAllValidSessions()

    init {
        viewModelScope.launch {
            val retentionDays = userPreferences.historyRetentionDays.first()
            if (retentionDays > 0) {
                val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
                sleepSessionDao.deleteSessionsBefore(cutoff)
            }
        }
    }

    fun deleteSession(session: SleepSessionEntity) {
        viewModelScope.launch {
            sleepSessionDao.deleteSession(session)
        }
    }

    fun restoreSession(session: SleepSessionEntity) {
        viewModelScope.launch {
            sleepSessionDao.insertSession(session)
        }
    }
}
