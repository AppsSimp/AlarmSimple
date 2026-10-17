package com.appssimple.alarmsimple.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(
    onOpenSession: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История сна", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Пока нет сохраненных ночей",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(sessions, key = { it.id }) { session ->
                    SleepHistoryCard(
                        session = session,
                        onClick = { onOpenSession(session.id) }
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SleepHistoryCard(
    session: SleepSessionEntity,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMMM, EEEE", Locale("ru"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val dateStr = dateFormat.format(Date(session.trackingStartMs))
    val onsetStr = session.sleepOnsetMs?.let { timeFormat.format(Date(it)) } ?: "--:--"
    val wakeStr = session.wakeUpMs?.let { timeFormat.format(Date(it)) } ?: "--:--"

    val totalSleepMinutes = if (session.sleepOnsetMs != null && session.wakeUpMs != null) {
        ((session.wakeUpMs - session.sleepOnsetMs) / 60_000L).toInt()
    } else 0
    val hours = totalSleepMinutes / 60
    val mins = totalSleepMinutes % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${hours}ч ${mins}м",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$onsetStr → $wakeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (session.snoreMinutes > 0) {
                SuggestionChip(
                    onClick = onClick,
                    label = { Text("😴 ${session.snoreMinutes}м") }
                )
            }
        }
    }
}

@HiltViewModel
class SleepHistoryViewModel @Inject constructor(
    sleepSessionDao: SleepSessionDao
) : ViewModel() {
    val sessions: Flow<List<SleepSessionEntity>> = sleepSessionDao.getAllSessions()
}
