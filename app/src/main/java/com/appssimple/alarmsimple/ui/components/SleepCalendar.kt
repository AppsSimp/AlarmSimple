package com.appssimple.alarmsimple.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.domain.model.SleepPhase
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val EfficiencyHigh = Color(0xFF22C55E)
private val EfficiencyMid = Color(0xFFFBBF24)
private val EfficiencyLow = Color(0xFFF43F5E)

private data class MonthCell(val key: String, val day: Int)

private fun efficiencyColor(efficiency: Int): Color = when {
    efficiency >= 80 -> EfficiencyHigh
    efficiency >= 60 -> EfficiencyMid
    else -> EfficiencyLow
}

private fun monthStartOf(timeMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMs
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(monthStartMs: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthStartMs
    add(Calendar.MONTH, delta)
}.timeInMillis

/**
 * Календарь сна на месяц.
 *
 * [data] — карта «yyyy-MM-dd (локальная дата пробуждения) → эффективность сна в %».
 * Дни с записью закрашены по эффективности и кликабельны, остальные показаны нейтрально.
 */
@Composable
fun SleepCalendar(
    data: Map<String, Int>,
    modifier: Modifier = Modifier,
    onDateSelected: (String) -> Unit
) {
    val locale = Locale.getDefault()
    val keyFormat = remember(locale) { SimpleDateFormat("yyyy-MM-dd", locale) }
    val monthFormat = remember(locale) { SimpleDateFormat("LLLL yyyy", locale) }

    val currentMonthStart = remember { monthStartOf(System.currentTimeMillis()) }
    val earliestMonthStart = remember(currentMonthStart) { shiftMonth(currentMonthStart, -11) }
    val todayKey = remember { keyFormat.format(Date()) }

    var monthStart by rememberSaveable { mutableStateOf(currentMonthStart) }
    val canGoBack = monthStart > earliestMonthStart
    val canGoForward = monthStart < currentMonthStart

    val monthLabel = remember(monthStart) {
        monthFormat.format(Date(monthStart))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    val cells = remember(monthStart) {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leadingEmpty = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val result = MutableList<MonthCell?>(leadingEmpty) { null }
        repeat(daysInMonth) { index ->
            result.add(MonthCell(key = keyFormat.format(cal.time), day = index + 1))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        while (result.size % 7 != 0) result.add(null)
        result
    }

    val nightsInMonth = remember(cells, data) {
        cells.count { cell -> cell != null && data.containsKey(cell.key) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Календарь сна", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = if (nightsInMonth == 0) "нет записей" else "$nightsInMonth ноч.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { monthStart = shiftMonth(monthStart, -1) }, enabled = canGoBack) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Предыдущий месяц",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            }
            Text(
                text = monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { monthStart = shiftMonth(monthStart, 1) }, enabled = canGoForward) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Следующий месяц",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cell != null) {
                            val efficiency = data[cell.key]
                            val isToday = cell.key == todayKey
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (efficiency != null) Modifier.background(efficiencyColor(efficiency))
                                        else Modifier
                                    )
                                    .then(
                                        if (isToday) Modifier.border(1.5.dp, BrandPrimary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable(enabled = efficiency != null) { onDateSelected(cell.key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cell.day.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (efficiency != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (efficiency != null) Color.White
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (nightsInMonth == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "В этом месяце записей нет",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalendarLegend(EfficiencyHigh, "≥ 80%")
            CalendarLegend(EfficiencyMid, "60–79%")
            CalendarLegend(EfficiencyLow, "< 60%")
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Эффективность ночи в процентах — та же формула, что и на экране статистики сессии. */
fun sleepEfficiencyPercent(
    wakeUpMs: Long,
    sleepOnsetMs: Long,
    trackingStartMs: Long
): Int {
    val totalSleep = ((wakeUpMs - sleepOnsetMs) / 60_000L).toInt()
    val timeInBed = ((wakeUpMs - trackingStartMs) / 60_000L).toInt().coerceAtLeast(1)
    return sleepQualityPercent(totalSleep, timeInBed)
}

/** Целевая длительность сна, при которой «количество» перестаёт быть проблемой. */
private const val TARGET_SLEEP_MINUTES = 7 * 60

/** Длительность одной эпохи сна в секундах. */
const val SLEEP_EPOCH_SECONDS = 30

/**
 * Минуты реального сна по эпохам: AWAKE — это не сон, он в длительность не входит.
 */
fun sleepMinutesFromEpochs(epochs: List<SleepEpochEntity>): Int =
    epochs.count { it.phase != SleepPhase.AWAKE.name } * SLEEP_EPOCH_SECONDS / 60

/** Общая длительность записанных эпох в минутах — фактическое время под наблюдением. */
fun epochSpanMinutes(epochs: List<SleepEpochEntity>): Int =
    epochs.size * SLEEP_EPOCH_SECONDS / 60

/**
 * Качество сна в процентах (0..100).
 *
 * Это не «эффективность» в смысле sleep / time-in-bed: часовая ночь формально может иметь
 * 90% непрерывности, но качественным сном не является. Поэтому 60% оценки даёт набранная
 * длительность относительно 7 часов, и только 40% — доля сна во времени в постели.
 */
fun sleepQualityPercent(totalSleepMinutes: Int, timeInBedMinutes: Int): Int {
    val durationFactor = (totalSleepMinutes.toFloat() / TARGET_SLEEP_MINUTES).coerceIn(0f, 1f)
    val continuity = (totalSleepMinutes.toFloat() / timeInBedMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
    return ((durationFactor * 0.6f + continuity * 0.4f) * 100f).roundToInt().coerceIn(0, 100)
}
