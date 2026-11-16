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
import com.appssimple.alarmsimple.ui.components.epochSpanMinutes
import com.appssimple.alarmsimple.ui.components.sleepMinutesFromEpochs
import com.appssimple.alarmsimple.ui.components.sleepQualityPercent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SleepStatsUiState(
    val sessionId: Long = 0,
    val isLoaded: Boolean = false,
    val totalSleepFormatted: String = "0ч 0м",
    val sleepOnsetFormatted: String = "--:--",
    val wakeUpFormatted: String = "--:--",
    val sleepQuality: Int = 0,
    val disturbanceCount: Int = 0,
    val snoreMinutes: Int = 0,
    val epochs: List<SleepEpochEntity> = emptyList(),
    val phaseMinutes: Map<String, Int> = emptyMap(),
    val snippets: List<AudioSnippetEntity> = emptyList(),
    val playingSnippetId: Long? = null,
    val dailySummary: String = "",
    /** Границы ночи (от первого засыпания до последнего пробуждения) — общая шкала гипнограммы. */
    val nightStartMs: Long = 0L,
    val nightEndMs: Long = 0L,
    /** Интервалы каждой сессии внутри объединённой ночи. */
    val sessionRanges: List<Pair<Long, Long>> = emptyList()
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

    fun loadData(sessionId: Long, date: String? = null) {
        viewModelScope.launch {
            val nights = loadNights(sessionId, date)
            if (nights.isEmpty()) return@launch

            val totalSleepMinutes = nights.sumOf { it.sleepMinutes }
            val timeInBedMinutes = nights.sumOf { it.timeInBedMinutes }.coerceAtLeast(1)
            val quality = sleepQualityPercent(totalSleepMinutes, timeInBedMinutes)

            val epochs = nights
                .flatMap { it.epochs }
                .sortedBy { it.timestampMs }
                .let { smoothShortAwakenings(it) }

            val snippets = nights
                .flatMap { audioSnippetDao.getSnippetsForSessionOnce(it.id) }
                .sortedBy { it.timestampMs }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val onsetStr = timeFormat.format(Date(nights.minOf { it.onsetMs }))
            val wakeStr = timeFormat.format(Date(nights.maxOf { it.wakeMs }))

            val hours = totalSleepMinutes / 60
            val mins = totalSleepMinutes % 60

            val phaseMinutes = epochs
                .groupingBy { it.phase }
                .eachCount()
                .mapValues { (_, count) -> (count + 1) / 2 }

            val disturbanceCount = nights.sumOf { it.disturbanceCount }
            val snoreMinutes = nights.sumOf { it.snoreMinutes }
            val summary = generateSummary(epochs, quality, snoreMinutes)

            _uiState.update {
                it.copy(
                    sessionId = nights.first().id,
                    isLoaded = true,
                    totalSleepFormatted = "${hours}ч ${mins}м",
                    sleepOnsetFormatted = onsetStr,
                    wakeUpFormatted = wakeStr,
                    sleepQuality = quality,
                    disturbanceCount = disturbanceCount,
                    snoreMinutes = snoreMinutes,
                    epochs = epochs,
                    phaseMinutes = phaseMinutes,
                    snippets = snippets,
                    dailySummary = summary,
                    nightStartMs = nights.minOf { it.onsetMs },
                    nightEndMs = nights.maxOf { it.wakeMs },
                    sessionRanges = nights.sortedBy { it.onsetMs }.map { it.onsetMs to it.wakeMs }
                )
            }
        }
    }

    private data class Night(
        val id: Long,
        val onsetMs: Long,
        val wakeMs: Long,
        val epochs: List<SleepEpochEntity>,
        val sleepMinutes: Int,
        val timeInBedMinutes: Int,
        val disturbanceCount: Int,
        val snoreMinutes: Int
    )

    /** Все сессии за выбранную ночь: одна сессия по id, все сессии дня по дате, либо последняя. */
    private suspend fun loadNights(sessionId: Long, date: String?): List<Night> {
        val sessions = when {
            !date.isNullOrEmpty() -> {
                val bounds = dayBounds(date) ?: return emptyList()
                sleepSessionDao.getSessionsByWakeUpRange(bounds.first, bounds.second)
            }
            sessionId > 0 -> listOfNotNull(sleepSessionDao.getSessionById(sessionId))
            else -> listOfNotNull(sleepSessionDao.getLatestValidSession())
        }
        return sessions.mapNotNull { session ->
            val wakeUpMs = session.wakeUpMs ?: return@mapNotNull null
            val sleepOnsetMs = session.sleepOnsetMs ?: return@mapNotNull null
            val epochs = sleepEpochDao.getEpochsForSessionOnce(session.id)
            val hasEpochs = epochs.isNotEmpty()
            Night(
                id = session.id,
                onsetMs = sleepOnsetMs,
                wakeMs = wakeUpMs,
                epochs = epochs,
                // Реальная длительность сна — только эпохи LIGHT/REM/DEEP, AWAKE сном не считается.
                sleepMinutes = if (hasEpochs) {
                    sleepMinutesFromEpochs(epochs)
                } else {
                    ((wakeUpMs - sleepOnsetMs) / 60_000L).toInt().coerceAtLeast(0)
                },
                timeInBedMinutes = if (hasEpochs) {
                    epochSpanMinutes(epochs).coerceAtLeast(1)
                } else {
                    ((wakeUpMs - session.trackingStartMs) / 60_000L).toInt().coerceAtLeast(1)
                },
                disturbanceCount = session.disturbanceCount,
                snoreMinutes = session.snoreMinutes
            )
        }
    }

    /** Границы локальных суток для даты yyyy-MM-dd. */
    private fun dayBounds(date: String): Pair<Long, Long>? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
        val parsed = runCatching { format.parse(date) }.getOrNull() ?: return null
        val cal = Calendar.getInstance().apply { time = parsed }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }

    private fun generateSummary(epochs: List<SleepEpochEntity>, quality: Int, snoreMins: Int): String {
        val awakeEpochs = epochs.count { it.phase == SleepPhase.AWAKE.name }

        var summary = "Результаты: "
        if (quality > 80) summary += "Отличное качество сна. "
        else if (quality > 60) summary += "Неплохое качество сна. "
        else summary += "Качество сна требует внимания. "

        if (awakeEpochs > 10) summary += "Было много пробуждений. "
        if (snoreMins > 30) summary += "Много храпа. "
        if (quality > 80 && awakeEpochs <= 10 && snoreMins <= 30) summary += "Вы отлично выспались!"
        else summary += "Постарайтесь лечь раньше."

        return summary
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
