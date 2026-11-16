package com.appssimple.alarmsimple.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appssimple.alarmsimple.R
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.domain.model.SleepPhase
import com.appssimple.alarmsimple.ui.components.AppBackground
import com.appssimple.alarmsimple.ui.components.ModernBadge
import com.appssimple.alarmsimple.ui.components.ModernCard
import com.appssimple.alarmsimple.ui.theme.BrandPrimary
import com.appssimple.alarmsimple.ui.theme.BrandSecondary
import java.text.SimpleDateFormat
import java.util.*

private fun dayTitle(dateKey: String): String {
    val locale = Locale("ru")
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val output = SimpleDateFormat("d MMMM", locale)
    val parsed = runCatching { parser.parse(dateKey) }.getOrNull() ?: return dateKey
    return output.format(parsed)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepStatsScreen(
    sessionId: Long,
    date: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: SleepStatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId, date) {
        viewModel.loadData(sessionId, date)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioPlayback()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = date?.let { dayTitle(it) } ?: stringResource(R.string.sleep_stats),
                            fontWeight = FontWeight.Bold
                        )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Сводка сна
                item {
                    ModernCard(
                        backgroundColor = Color(0xFF141E38),
                        borderColor = BrandPrimary.copy(alpha = 0.35f),
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column {
                            Text(
                                text = "Длительность реального сна",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.totalSleepFormatted,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-1).sp
                            )

                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricColumn("Уснул", state.sleepOnsetFormatted)
                                MetricColumn("Проснулся", state.wakeUpFormatted)
                                MetricColumn("Качество", "${state.sleepQuality}%")
                            }
                        }
                    }
                }

                // Гипнограмма
                item {
                    ModernCard(contentPadding = PaddingValues(18.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Фазы сна (Гипнограмма)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (state.epochs.isNotEmpty()) {
                                    ModernBadge(
                                        text = "${state.epochs.count { it.phase != SleepPhase.AWAKE.name } * 30 / 60} мин",
                                        color = BrandSecondary
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            NightHypnogram(
                                segments = state.segments,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Звуки ночи
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Звуки ночи (${state.snippets.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.snippets.isNotEmpty()) {
                            Text(
                                "Нажмите ▶ чтобы слушать",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.snippets.isEmpty()) {
                    item {
                        ModernCard(contentPadding = PaddingValues(20.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Ночью было спокойно и тихо",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Никаких резких шумов или храпа не зафиксировано.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.snippets, key = { it.id }) { snippet ->
                        AudioSnippetItemCard(
                            snippet = snippet,
                            isPlaying = state.playingSnippetId == snippet.id,
                            onTogglePlay = { viewModel.toggleAudioSnippet(snippet) }
                        )
                    }
                }

                item { Spacer(Modifier.height(36.dp)) }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun PhaseLegendBadge(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NightHypnogram(
    segments: List<NightSegment>,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(modifier = modifier) {
        segments.forEachIndexed { index, segment ->
            val minutes = ((segment.endMs - segment.startMs) / 60_000L).toInt().coerceAtLeast(0)

            if (segments.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сессия ${index + 1}: ${timeFormat.format(Date(segment.startMs))}–${timeFormat.format(Date(segment.endMs))}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${minutes / 60}ч ${minutes % 60}м",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            if (segment.epochs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Фазы этой сессии не записаны",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                HypnogramChart(
                    epochs = segment.epochs,
                    windowStartMs = segment.startMs,
                    windowEndMs = segment.endMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }

            if (index != segments.lastIndex) {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun HypnogramChart(
    epochs: List<SleepEpochEntity>,
    windowStartMs: Long = 0L,
    windowEndMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val phaseColors = mapOf(
        SleepPhase.AWAKE.name to Color(0xFFF43F5E),
        SleepPhase.REM.name to Color(0xFF8B5CF6),
        SleepPhase.LIGHT.name to Color(0xFF10B981),
        SleepPhase.DEEP.name to Color(0xFF2563EB)
    )

    val phasesList = listOf(
        "Пробужд." to SleepPhase.AWAKE.name,
        "REM" to SleepPhase.REM.name,
        "Легкий" to SleepPhase.LIGHT.name,
        "Глубокий" to SleepPhase.DEEP.name
    )

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val hasWindow = windowEndMs > windowStartMs && windowStartMs > 0L
    val chartStartMs = if (hasWindow) windowStartMs else (epochs.firstOrNull()?.timestampMs ?: 0L)
    val chartEndMs = if (hasWindow) windowEndMs
    else ((epochs.lastOrNull()?.timestampMs ?: 0L) + 30_000L)
    val startStr = if (epochs.isNotEmpty()) timeFormat.format(Date(chartStartMs)) else "--:--"
    val endStr = if (epochs.isNotEmpty()) timeFormat.format(Date(chartEndMs)) else "--:--"

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Вертикальные названия фаз слева
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                phasesList.forEach { (label, phaseKey) ->
                    val color = phaseColors[phaseKey] ?: Color.White
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Полотно графика с прямоугольниками фаз
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                if (epochs.isEmpty()) return@Canvas

                val w = size.width
                val h = size.height
                val rowHeight = h / phasesList.size
                // Общая шкала времени ночи: если сессий несколько, между ними виден разрыв,
                // а не склеенные подряд эпохи.
                val windowMs = (chartEndMs - chartStartMs).coerceAtLeast(1L).toFloat()

                // Горизонтальные направляющие линии для каждой строки фазы
                phasesList.indices.forEach { i ->
                    val y = i * rowHeight
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Рисуем прямоугольники для каждой эпохи в соответствующей строке
                epochs.forEach { epoch ->
                    val rowIdx = when (epoch.phase) {
                        SleepPhase.AWAKE.name -> 0
                        SleepPhase.REM.name -> 1
                        SleepPhase.LIGHT.name -> 2
                        SleepPhase.DEEP.name -> 3
                        else -> 2
                    }
                    val color = phaseColors[epoch.phase] ?: Color(0xFF10B981)
                    val topY = rowIdx * rowHeight + 2.dp.toPx()
                    val blockHeight = (rowHeight - 4.dp.toPx()).coerceAtLeast(2.dp.toPx())

                    val left = ((epoch.timestampMs - chartStartMs) / windowMs) * w
                    val right = ((epoch.timestampMs + 30_000L - chartStartMs) / windowMs) * w

                    drawRect(
                        color = color,
                        topLeft = Offset(left, topY),
                        size = Size((right - left).coerceAtLeast(0.8f), blockHeight)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Горизонтальные подписи времени снизу: слева начало, справа конец сна
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 80.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Начало $startStr",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Конец $endStr",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AudioSnippetItemCard(
    snippet: AudioSnippetEntity,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snippet.timestampMs))

    val (title, color) = when (snippet.type) {
        "SNORE" -> "😴 Храп" to Color(0xFF38BDF8)
        "SLEEP_TALK" -> "💬 Разговор во сне" to Color(0xFFA78BFA)
        "COUGH_SNEEZE" -> "🤧 Кашель / чихание" to Color(0xFFFBBF24)
        "BRUXISM" -> "😬 Скрежет зубами" to Color(0xFFE879F9)
        "APNEA_GASP" -> "😮 Резкий вдох (пауза дыхания)" to Color(0xFFFB7185)
        "RUSTLING" -> "🛏️ Ворочание / шуршание" to Color(0xFF94A3B8)
        "IMPACT_NOISE" -> "💥 Удар / хлопок" to Color(0xFFF87171)
        "FLATULENCE" -> "💨 Забавный звук" to Color(0xFF34D399)
        "UNKNOWN" -> "❓ Неопознанный шум" to Color(0xFF818CF8)
        else -> "⚡ Необычный звук" to Color(0xFF818CF8)
    }

    ModernCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) Color(0xFFF43F5E) else BrandPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Воспроизвести",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$timeStr • длительность ${snippet.durationSeconds} сек",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ModernBadge(text = "${snippet.durationSeconds}с", color = color)
        }
    }
}
