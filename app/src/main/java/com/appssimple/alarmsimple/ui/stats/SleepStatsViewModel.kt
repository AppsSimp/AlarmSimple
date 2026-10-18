package com.appssimple.alarmsimple.ui.stats

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.domain.model.SleepPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SleepStatsUiState(
    val sessionId: Long = 0,
    val isLoaded: Boolean = false,
    val totalSleepFormatted: String = "0ч 0м",
    val sleepOnsetFormatted: String = "--:--",
    val wakeUpFormatted: String = "--:--",
    val sleepEfficiency: Int = 0,
    val disturbanceCount: Int = 0,
    val snoreMinutes: Int = 0,
    val epochs: List<SleepEpochEntity> = emptyList(),
    val phaseMinutes: Map<String, Int> = emptyMap(),
    val snippets: List<AudioSnippetEntity> = emptyList(),
    val playingSnippetId: Long? = null
)

@HiltViewModel
class SleepStatsViewModel @Inject constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepEpochDao: SleepEpochDao,
    private val audioSnippetDao: AudioSnippetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepStatsUiState())
    val uiState: StateFlow<SleepStatsUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            val session = if (sessionId > 0) {
                sleepSessionDao.getSessionById(sessionId)
            } else {
                sleepSessionDao.getLatestValidSession()
            } ?: return@launch

            val epochs = sleepEpochDao.getEpochsForSessionOnce(session.id)
            val snippets = audioSnippetDao.getSnippetsForSessionOnce(session.id)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val onsetStr = session.sleepOnsetMs?.let { timeFormat.format(Date(it)) } ?: "--:--"
            val wakeStr = session.wakeUpMs?.let { timeFormat.format(Date(it)) } ?: "--:--"

            val totalSleepMinutes = if (session.sleepOnsetMs != null && session.wakeUpMs != null) {
                ((session.wakeUpMs - session.sleepOnsetMs) / 60_000L).toInt().coerceAtLeast(0)
            } else 0

            val timeInBedMinutes = if (session.wakeUpMs != null) {
                ((session.wakeUpMs - session.trackingStartMs) / 60_000L).toInt().coerceAtLeast(1)
            } else 1

            val efficiency = ((totalSleepMinutes.toFloat() / timeInBedMinutes) * 100)
                .toInt()
                .coerceIn(0, 100)

            val hours = totalSleepMinutes / 60
            val mins = totalSleepMinutes % 60

            val phaseMinutes = epochs
                .groupingBy { it.phase }
                .eachCount()
                .mapValues { (_, count) -> (count + 1) / 2 }

            _uiState.update {
                it.copy(
                    sessionId = session.id,
                    isLoaded = true,
                    totalSleepFormatted = "${hours}ч ${mins}м",
                    sleepOnsetFormatted = onsetStr,
                    wakeUpFormatted = wakeStr,
                    sleepEfficiency = efficiency,
                    disturbanceCount = session.disturbanceCount,
                    snoreMinutes = session.snoreMinutes,
                    epochs = epochs,
                    phaseMinutes = phaseMinutes,
                    snippets = snippets
                )
            }
        }
    }

    fun toggleAudioSnippet(snippet: AudioSnippetEntity) {
        if (_uiState.value.playingSnippetId == snippet.id) {
            stopAudioPlayback()
        } else {
            playAudioSnippet(snippet)
        }
    }

    private fun playAudioSnippet(snippet: AudioSnippetEntity) {
        stopAudioPlayback()
        val file = File(snippet.filePath)
        if (!file.exists()) return

        try {
            _uiState.update { it.copy(playingSnippetId = snippet.id) }
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(snippet.filePath)
                setOnPreparedListener { start() }
                setOnCompletionListener { stopAudioPlayback() }
                setOnErrorListener { _, _, _ ->
                    stopAudioPlayback()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudioPlayback()
        }
    }

    fun stopAudioPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _uiState.update { it.copy(playingSnippetId = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
    }
}
