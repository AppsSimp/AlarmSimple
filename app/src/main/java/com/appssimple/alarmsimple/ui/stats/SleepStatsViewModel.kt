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

            val allEpochs = sleepEpochDao.getEpochsForSessionOnce(session.id)
            val snippets = audioSnippetDao.getSnippetsForSessionOnce(session.id)

            // Эпохи ДО засыпания — это не «пробуждения», человек ещё просто не уснул.
            // Гипнограмма и статистика фаз начинаются с момента засыпания (sleepOnsetMs).
            val sinceOnset = session.sleepOnsetMs?.let { onset ->
                allEpochs.filter { it.timestampMs >= onset }
            } ?: allEpochs

            // Микро-«пробуждения» короче 3 эпох (90 сек) — это шевеления, а не просыпания.
            // На графике и в статистике фаз склеиваем их с окружающим сном (LIGHT).
            val epochs = smoothShortAwakenings(sinceOnset)

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

    /**
     * Серии AWAKE короче 3 эпох (90 сек) заменяем на LIGHT — это микрошевеления во сне,
     * а не реальные пробуждения. Реальные подъёмы (3+ минуты) остаются как есть.
     */
    private fun smoothShortAwakenings(epochs: List<SleepEpochEntity>): List<SleepEpochEntity> {
        if (epochs.size < 2) return epochs
        val result = mutableListOf<SleepEpochEntity>()
        var i = 0
        while (i < epochs.size) {
            val epoch = epochs[i]
            if (epoch.phase != SleepPhase.AWAKE.name) {
                result.add(epoch)
                i++
                continue
            }
            // Длина серии AWAKE, начиная с i
            var runEnd = i
            while (runEnd < epochs.size && epochs[runEnd].phase == SleepPhase.AWAKE.name) runEnd++
            val runLength = runEnd - i
            if (runLength >= 3) {
                result.addAll(epochs.subList(i, runEnd))
            } else {
                // Короткая серия -> LIGHT, но с исходной интенсивностью движения
                for (j in i until runEnd) {
                    result.add(epochs[j].copy(phase = SleepPhase.LIGHT.name))
                }
            }
            i = runEnd
        }
        return result
    }

    fun toggleAudioSnippet(snippet: AudioSnippetEntity) {        if (_uiState.value.playingSnippetId == snippet.id) {
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
