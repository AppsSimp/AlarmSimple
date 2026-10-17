package com.appssimple.alarmsimple.ui.stats

import android.media.MediaPlayer
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepStatsScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SleepStatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioPlayback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_stats), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
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

            // 1. Сводные метрики
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Длительность сна",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = state.totalSleepFormatted,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Уснул", style = MaterialTheme.typography.bodySmall)
                                Text(state.sleepOnsetFormatted, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Проснулся", style = MaterialTheme.typography.bodySmall)
                                Text(state.wakeUpFormatted, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Эффективность", style = MaterialTheme.typography.bodySmall)
                                Text("${state.sleepEfficiency}%", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. График фаз сна (Гипнограмма)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Фазы сна за ночь",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        HypnogramChart(
                            epochs = state.epochs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Легенда
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PhaseLegendItem("Глубокий", Color(0xFF1E88E5))
                            PhaseLegendItem("Легкий", Color(0xFF43A047))
                            PhaseLegendItem("REM", Color(0xFF8E24AA))
                            PhaseLegendItem("Бодрств.", Color(0xFFE53935))
                        }
                    }
                }
            }

            // 3. Секция "Интересные звуки ночью"
            item {
                Text(
                    text = "Записанные звуки (${state.snippets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.snippets.isEmpty()) {
                item {
                    Text(
                        text = "Ночью было тихо. Никаких необычных звуков не обнаружено.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.snippets, key = { it.id }) { snippet ->
                    AudioSnippetItem(
                        snippet = snippet,
                        isPlaying = state.playingSnippetId == snippet.id,
                        onPlayClick = { viewModel.toggleAudioSnippet(snippet) }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun PhaseLegendItem(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HypnogramChart(
    epochs: List<SleepEpochEntity>,
    modifier: Modifier = Modifier
) {
    val phaseColors = mapOf(
        SleepPhase.DEEP.name to Color(0xFF1E88E5),
        SleepPhase.LIGHT.name to Color(0xFF43A047),
        SleepPhase.REM.name to Color(0xFF8E24AA),
        SleepPhase.AWAKE.name to Color(0xFFE53935)
    )

    Canvas(modifier = modifier) {
        if (epochs.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val stepX = w / epochs.size

        val yMap = mapOf(
            SleepPhase.AWAKE.name to 0.15f * h,
            SleepPhase.REM.name to 0.40f * h,
            SleepPhase.LIGHT.name to 0.65f * h,
            SleepPhase.DEEP.name to 0.90f * h
        )

        val path = Path()
        epochs.forEachIndexed { index, epoch ->
            val x = index * stepX
            val y = yMap[epoch.phase] ?: (0.65f * h)
            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = Color(0xFF8B9CF7),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw colored bars at bottom
        epochs.forEachIndexed { index, epoch ->
            val color = phaseColors[epoch.phase] ?: Color.Gray
            drawRect(
                color = color.copy(alpha = 0.7f),
                topLeft = Offset(index * stepX, h - 8.dp.toPx()),
                size = Size(stepX + 1f, 8.dp.toPx())
            )
        }
    }
}

@Composable
fun AudioSnippetItem(
    snippet: AudioSnippetEntity,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snippet.timestampMs))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlayClick,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Слушать"
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = when (snippet.type) {
                            "SNORE" -> "😴 Храп"
                            "SLEEP_TALK" -> "💬 Разговор во сне"
                            "IMPACT_NOISE" -> "💥 Ночной шум / удар"
                            "FLATULENCE" -> "💨 Забавный звук"
                            else -> "⚡ Необычный звук"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$timeStr • ${snippet.durationSeconds} сек",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
