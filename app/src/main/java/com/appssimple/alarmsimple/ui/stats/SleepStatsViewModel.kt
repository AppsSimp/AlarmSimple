package com.appssimple.alarmsimple.ui.stats

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SleepStatsUiState(
    val sessionId: Long = 0,
    val totalSleepFormatted: String = "0ч 0м",
    val sleepOnsetFormatted: String = "--:--",
    val wakeUpFormatted: String = "--:--",
    val sleepEfficiency: Int = 0,
    val epochs: List<SleepEpochEntity> = emptyList(),
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
            val session = if (sessionId != -1L) {
                sleepSessionDao.getSessionById(sessionId)
            } else {
                sleepSessionDao.getLatestSession()
            } ?: return@launch

            val epochs = sleepEpochDao.getEpochsForSessionOnce(session.id)
            val snippets = audioSnippetDao.getSnippetsForSessionOnce(session.id)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val onsetStr = session.sleepOnsetMs?.let { timeFormat.format(Date(it)) } ?: "--:--"
            val wakeStr = session.wakeUpMs?.let { timeFormat.format(Date(it)) } ?: "--:--"

            val totalSleepMinutes = if (session.sleepOnsetMs != null && session.wakeUpMs != null) {
                ((session.wakeUpMs - session.sleepOnsetMs) / 60_000L).toInt()
            } else 0

            val timeInBedMinutes = if (session.wakeUpMs != null) {
                ((session.wakeUpMs - session.trackingStartMs) / 60_000L).toInt()
            } else 1

            val efficiency = if (timeInBedMinutes > 0) {
                ((totalSleepMinutes.toFloat() / timeInBedMinutes) * 100).toInt().coerceIn(0, 100)
            } else 0

            val hours = totalSleepMinutes / 60
            val mins = totalSleepMinutes % 60

            _uiState.update {
                it.copy(
                    sessionId = session.id,
                    totalSleepFormatted = "${hours}ч ${mins}м",
                    sleepOnsetFormatted = onsetStr,
                    wakeUpFormatted = wakeStr,
                    sleepEfficiency = efficiency,
                    epochs = epochs,
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
            mediaPlayer = MediaPlayer().apply {
                setDataSource(snippet.filePath)
                prepare()
                setOnCompletionListener {
                    stopAudioPlayback()
                }
                start()
            }
            _uiState.update { it.copy(playingSnippetId = snippet.id) }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudioPlayback()
        }
    }

    fun stopAudioPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _uiState.update { it.copy(playingSnippetId = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
    }
}
