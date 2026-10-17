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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(
    onOpenSession: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SleepHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("История сна", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Пока нет сохранённых ночей",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Здесь появятся ваши ночи сна продолжительностью более 10 минут.",
                            style = MaterialTheme.typography.bodySmall,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

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

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

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
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${hours}ч ${mins}м",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary,
                    letterSpacing = (-0.5).sp
                )
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
