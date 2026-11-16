package com.appssimple.alarmsimple.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import com.appssimple.alarmsimple.domain.model.AudioClass
import com.appssimple.alarmsimple.domain.model.AudioSnippetType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class AudioAnalysisResult(
    val timestampMs: Long,
    val audioClass: AudioClass,
    val rmsAmplitude: Float,
    val snoreLikelihood: Float,
    val isMediaPlayingOnDevice: Boolean
)

data class SnippetRecordRequest(
    val type: AudioSnippetType,
    val timestampMs: Long,
    val peakAmplitude: Float,
    val audioFile: File,
    val durationSeconds: Int
)

/**
 * Captures audio via AudioRecord, classifies acoustic events, and saves interesting snippets.
 * Uses a memory rolling circular buffer to save pre-trigger audio (4 sec) plus post-trigger (8 sec).
 */
@Singleton
class AudioAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val silentAudioPlayer: SilentAudioPlayer
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _analysisFlow = MutableSharedFlow<AudioAnalysisResult>(extraBufferCapacity = 64)
    val analysisFlow: SharedFlow<AudioAnalysisResult> = _analysisFlow.asSharedFlow()

    private val _snippetFlow = MutableSharedFlow<SnippetRecordRequest>(extraBufferCapacity = 16)
    val snippetFlow: SharedFlow<SnippetRecordRequest> = _snippetFlow.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isRunning = false

    @Volatile
    private var lastAnalysisAtMs = 0L
    private var micWatchdogJob: Job? = null

    /** id сессии, чтобы уметь поднять захват заново, если старт не удался. */
    private var sessionIdForRestart = 0L

    /** Последнее значение RMS и момент, с которого оно не меняется (детектор зависшего захвата). */
    @Volatile
    private var lastRmsValue = -1f

    @Volatile
    private var rmsUnchangedSinceMs = 0L

    // Rolling circular buffer in memory (stores ~12 seconds of 16-bit PCM @ 8000Hz mono):
    // 4 сек ДО события + 8 сек ПОСЛЕ. Буфер заполняет только главный цикл чтения
    // (единственный читатель AudioRecord) — сниппет позже снимает готовый срез.
    // 8000 samples/sec * 2 bytes = 16,000 bytes per second -> 12s = 192,000 bytes
    private val preTriggerDurationSec = 4
    private val postTriggerDurationSec = 8
    private val sampleRate = 8000
    private val bytesPerSample = 2 // 16-bit mono
    private val ringBufferSize = sampleRate * bytesPerSample * (preTriggerDurationSec + postTriggerDurationSec)
    private val circularBuffer = ByteArray(ringBufferSize)
    private var circularHead = 0
    private val bufferLock = Any()

    // Anti-spam cooldown for audio snippets
    private var lastSnippetRecordedTimeMs = 0L
    private val snippetCooldownMs = 90_000L // Min 1.5 minutes between snippets
    private var totalSnippetsTonight = 0
    private val maxSnippetsPerNight = 30

    // Dynamic noise floor tracking (Leaky minimum follower)
    var noiseFloorRms = 25f // Start at realistic night bedroom silence level to avoid calibration drops
        private set

    // --- Признаки для классификации (доступ только из главного цикла чтения) ---
    private val recentRmsWindow = ArrayDeque<Float>()   // последние ~2 сек RMS (взрывность старта)
    private var recentRmsAvg = 0f
    private val frameDeltaHistory = ArrayDeque<Float>() // последние ~32 сек дельт RMS (цикличность)
    private var isCyclicPattern = false
    private var lpFilterState = 0f                      // состояние однополюсного ФНЧ ~500 Гц
    private var bruxismRun = 0                          // подряд идущие высокочастотные кадры
    private var snoreRmsBaseline = 0f                   // типичная громкость храпа (для детекта апноэ-вдоха)

    companion object {
        const val BUFFER_SIZE_SAMPLES = 2048 // ~256ms per analysis frame
    }

    /**
     * Проверяет, играет ли на устройстве стороннее медиа (музыка, видео, подкаст).
     * Важно: SilentAudioPlayer играет постоянную цифровую тишину через AudioTrack,
     * поэтому стандартный audioManager.isMusicActive возвращает true ВСЕГДА!
     * Чтобы не блокировать анализ ночных звуков и запись храпа, фильтруем наш тихий трек.
     */
    fun isExternalMediaPlaying(): Boolean {
        if (!audioManager.isMusicActive) return false
        if (!silentAudioPlayer.isSilentPlaying) return true
        return try {
            // Если играет наш SilentAudioPlayer, проверяем количество активных потоков:
            // более 1 потока означает, что кроме нашей тишины играет другое приложение (Spotify, YouTube и т.д.)
            audioManager.activePlaybackConfigurations.size > 1
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun start(sessionId: Long) {
        if (isRunning) return
        sessionIdForRestart = sessionId
        isRunning = true
        totalSnippetsTonight = 0

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()

            lastAnalysisAtMs = System.currentTimeMillis()
            startMicWatchdog()

            recordingJob = scope.launch {
                runProcessingLoop(sessionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        micWatchdogJob?.cancel()
        micWatchdogJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    /** Принудительно пересоздаёт AudioRecord (вызывается сторожем из SensorFusion). */
    @SuppressLint("MissingPermission")
    fun forceRestartMic() {
        if (!isRunning) {
            // Захват вообще не поднялся на старте — пробуем инициализировать заново,
            // иначе микрофон молчит всю ночь (кейс session 78: 25.0 на всех эпохах).
            if (sessionIdForRestart > 0) start(sessionIdForRestart)
            return
        }
        restartAudioRecord()
        lastAnalysisAtMs = System.currentTimeMillis()
        synchronized(bufferLock) {
            rmsUnchangedSinceMs = 0L
            lastRmsValue = -1f
        }
    }

    /**
     * Сторож захвата: если анализ не отдаёт результаты дольше 90 секунд, значит
     * AudioRecord «залип» и молча возвращает пустоту — пересоздаём его.
     */
    private fun startMicWatchdog() {
        micWatchdogJob?.cancel()
        micWatchdogJob = scope.launch {
            while (isActive && isRunning) {
                delay(30_000L)
                if (!isRunning) break
                val sinceLast = System.currentTimeMillis() - lastAnalysisAtMs
                if (sinceLast > 90_000L) {
                    android.util.Log.w("AudioAnalyzer", "No audio analysis for ${sinceLast / 1000}s — restarting AudioRecord")
                    forceRestartMic()
                }
            }
        }
    }

    @Synchronized
    private fun restartAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufferSize, BUFFER_SIZE_SAMPLES * 2)
            val newRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (newRecord.state == AudioRecord.STATE_INITIALIZED) {
                newRecord.startRecording()
                audioRecord = newRecord
            } else {
                newRecord.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun runProcessingLoop(sessionId: Long) {
        val readBuffer = ShortArray(BUFFER_SIZE_SAMPLES)
        val byteBuffer = ByteArray(BUFFER_SIZE_SAMPLES * 2)
        var errorCount = 0

        while (isRunning && currentCoroutineContext().isActive) {
            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                restartAudioRecord()
                delay(300)
                continue
            }

            val readCount = try {
                record.read(readBuffer, 0, BUFFER_SIZE_SAMPLES)
            } catch (e: Exception) {
                -1
            }

            if (readCount <= 0) {
                errorCount++
                if (errorCount >= 5) {
                    restartAudioRecord()
                    errorCount = 0
                    delay(500)
                } else {
                    delay(100)
                }
                continue
            }
            errorCount = 0

            // Check if external music/video is actively playing on this device
            val isExternalMedia = isExternalMediaPlaying()

            // Convert short array to bytes and write to circular buffer
            for (i in 0 until readCount) {
                val s = readBuffer[i].toInt()
                byteBuffer[i * 2] = (s and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }

            writeToCircularBuffer(byteBuffer, readCount * 2)

            // Acoustic analysis
            val rms = calculateRms(readBuffer, readCount)
            updateNoiseFloor(rms)

            // Детектор «застывшего» микрофона: одинаковый RMS дольше 30 секунд при ненулевом
            // уровне невозможен, значит AudioRecord отдаёт кэш/константу — пересоздаём захват.
            val nowMs = System.currentTimeMillis()
            lastAnalysisAtMs = nowMs
            if (rms == lastRmsValue) {
                if (rmsUnchangedSinceMs == 0L) rmsUnchangedSinceMs = nowMs
                else if (nowMs - rmsUnchangedSinceMs > 30_000L && rms > 2f) {
                    android.util.Log.w("AudioAnalyzer", "RMS frozen at $rms for 30s — restarting AudioRecord")
                    forceRestartMic()
                }
            } else {
                lastRmsValue = rms
                rmsUnchangedSinceMs = nowMs
            }

            val deltaOverNoise = rms - noiseFloorRms
            val audioClass = classifyAudio(readBuffer, readCount, rms, deltaOverNoise, isExternalMedia)
            val snoreLikelihood = when (audioClass) {
                AudioClass.SNORE -> 0.95f
                AudioClass.GASP -> 0.70f
                else -> 0.05f
            }

            val now = System.currentTimeMillis()
            _analysisFlow.tryEmit(
                AudioAnalysisResult(
                    timestampMs = now,
                    audioClass = audioClass,
                    rmsAmplitude = rms,
                    snoreLikelihood = snoreLikelihood,
                    isMediaPlayingOnDevice = isExternalMedia
                )
            )

            // Trigger saving snippet if an interesting acoustic event occurs
            if (shouldTriggerSnippet(audioClass, deltaOverNoise, now, isExternalMedia)) {
                triggerSnippetRecording(sessionId, audioClass, rms, now)
            }

            // Power-efficiency sleep: 15-20ms
            delay(15)
        }
    }

    private fun writeToCircularBuffer(data: ByteArray, length: Int) {
        synchronized(bufferLock) {
            for (i in 0 until length) {
                circularBuffer[circularHead] = data[i]
                circularHead = (circularHead + 1) % ringBufferSize
            }
        }
    }

    private fun calculateRms(buffer: ShortArray, count: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until count) {
            val s = buffer[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / count).toFloat()
    }

    private fun updateNoiseFloor(rms: Float) {
        // Smoothly track background ambient noise floor
        if (rms < noiseFloorRms) {
            noiseFloorRms = noiseFloorRms * 0.90f + rms * 0.10f
        } else {
            noiseFloorRms = noiseFloorRms * 0.998f + rms * 0.002f
        }
        // Keep within realistic physical bounds
        if (noiseFloorRms < 10f) noiseFloorRms = 10f
    }

    private fun classifyAudio(
        buffer: ShortArray,
        count: Int,
        rms: Float,
        deltaOverNoise: Float,
        isExternalMedia: Boolean
    ): AudioClass {
        // Если звук на уровне тишины комнаты (< 12 RMS дельты от фона)
        if (deltaOverNoise < 12f) {
            bruxismRun = 0
            snoreRmsBaseline *= 0.95f
            updateFrameFeatures(rms, deltaOverNoise)
            return AudioClass.SILENCE
        }

        val rawClass = classifyLoudFrame(buffer, count, rms, deltaOverNoise)
        updateFrameFeatures(rms, deltaOverNoise)

        // Если играет внешняя музыка/подкаст, не классифицируем вокал музыки как разговоры во сне
        return if (isExternalMedia && (rawClass == AudioClass.SPEECH || rawClass == AudioClass.UNKNOWN)) {
            AudioClass.AMBIENT
        } else {
            rawClass
        }
    }

    /**
     * Классификация громкого кадра (~256 мс @ 8 кГц) по эвристикам:
     * ZCR (число пересечений нуля), доля низких частот, взрывность старта, цикличность.
     */
    private fun classifyLoudFrame(
        buffer: ShortArray,
        count: Int,
        rms: Float,
        deltaOverNoise: Float
    ): AudioClass {
        // Zero-crossing rate
        var zcr = 0
        for (i in 1 until count) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                zcr++
            }
        }
        val normalizedZcr = zcr.toFloat() / count

        // Доля энергии в полосе ниже ~500 Гц (храп/газы/вдох — низкочастотные)
        val lowBandRatio = computeLowBandRatio(buffer, count, rms)

        // Взрывной ли старт: скачок относительно среднего за последние ~2 сек
        val burstRatio = if (recentRmsAvg > 1f) rms / recentRmsAvg else 1f

        // 1. Храп: низкочастотный, ровный (низкий ZCR). Пороги умеренные, чтобы ловить
        //    и тихий храп: энергия в басах решает, а не абсолютная громкость
        if (deltaOverNoise >= 18f && normalizedZcr < 0.25f && lowBandRatio > 0.30f) {
            snoreRmsBaseline = if (snoreRmsBaseline <= 0f) rms
            else 0.7f * snoreRmsBaseline + 0.3f * rms
            bruxismRun = 0
            return AudioClass.SNORE
        }

        // 2. Резкий вдох после ровного храпа (эпизод апноэ): вдруг ГОРАЗДО громче обычного храпа
        if (snoreRmsBaseline > 0f && rms > 2.0f * snoreRmsBaseline &&
            deltaOverNoise > 35f && normalizedZcr < 0.35f
        ) {
            return AudioClass.GASP
        }

        // 3. Газы/бурчание: низкочастотное «бубнение» (как храп, но ZCR заметно выше)
        if (deltaOverNoise > 30f && normalizedZcr in 0.18f..0.30f && lowBandRatio > 0.25f) {
            return AudioClass.FLATULENCE
        }

        // 4. Кашель/чихание: взрывной старт, широкополосный, короткий
        if (burstRatio > 2.5f && deltaOverNoise > 35f && normalizedZcr in 0.10f..0.50f) {
            bruxismRun = 0
            return AudioClass.COUGH
        }

        // 5. Скрежет зубами: высокочастотный негромкий звук, устойчивый несколько кадров подряд
        if (normalizedZcr > 0.40f && lowBandRatio < 0.45f && deltaOverNoise in 15f..120f) {
            bruxismRun++
            return if (bruxismRun >= 2) AudioClass.BRUXISM else AudioClass.AMBIENT
        }
        bruxismRun = 0

        // 6. Шуршание простыней (ворочание): очень высокий ZCR, средняя громкость
        if (normalizedZcr > 0.45f && deltaOverNoise > 25f) {
            return AudioClass.RUSTLING
        }

        // 7. Речь: средний ZCR, форманты 300–3000 Гц
        if (deltaOverNoise > 50f && normalizedZcr in 0.18f..0.50f) {
            return AudioClass.SPEECH
        }

        // 8. Удар/хлопок: очень резкий мощный скачок любой частоты
        if (burstRatio > 3.5f && deltaOverNoise > 70f) {
            return AudioClass.IMPACT
        }

        // 9. Неопознанный шум: громкий, но без признаков известных звуков,
        //    либо повторяющийся циклично (что-то жужжит/стучит всю ночь)
        if (deltaOverNoise > 60f || (isCyclicPattern && deltaOverNoise > 30f)) {
            snoreRmsBaseline *= 0.97f
            return AudioClass.UNKNOWN
        }

        // 10. Прочее громкое
        snoreRmsBaseline *= 0.97f
        if (deltaOverNoise > 30f) return AudioClass.NOISE
        return AudioClass.AMBIENT
    }

    /** Однополюсный ФНЧ ~500 Гц: возвращает долю низкой полосы в общей RMS кадра. */
    private fun computeLowBandRatio(buffer: ShortArray, count: Int, rms: Float): Float {
        val a = 0.675f // exp(-2π·500/8000)
        var y = lpFilterState
        var sumSq = 0.0
        for (i in 0 until count) {
            y = a * y + (1 - a) * buffer[i]
            sumSq += y.toDouble() * y
        }
        lpFilterState = y
        val rmsLow = sqrt(sumSq / count).toFloat()
        return if (rms > 1f) rmsLow / rms else 0f
    }

    /** Обновляет скользящие признаки: среднюю громкость и детектор цикличности. */
    private fun updateFrameFeatures(rms: Float, delta: Float) {
        recentRmsWindow.addLast(rms)
        if (recentRmsWindow.size > 8) recentRmsWindow.removeFirst()
        recentRmsAvg = if (recentRmsWindow.isNotEmpty()) recentRmsWindow.average().toFloat() else 0f

        frameDeltaHistory.addLast(delta)
        if (frameDeltaHistory.size > 128) frameDeltaHistory.removeFirst()
        isCyclicPattern = detectCyclicPattern()
    }

    /**
     * Цикличность: ищем громкие пики за последние ~30 сек; если 4+ пика идут
     * с почти одинаковыми интервалами (разброс < 30%) — фон цикличный.
     */
    private fun detectCyclicPattern(): Boolean {
        if (frameDeltaHistory.size < 24) return false
        val deltas = frameDeltaHistory.toList()

        val peakIndices = mutableListOf<Int>()
        for (i in 2 until deltas.size - 2) {
            val v = deltas[i]
            if (v > 120f &&
                v >= deltas[i - 1] && v >= deltas[i - 2] &&
                v >= deltas[i + 1] && v >= deltas[i + 2]
            ) {
                if (peakIndices.isEmpty() || i - peakIndices.last() >= 3) {
                    peakIndices.add(i)
                }
            }
        }
        if (peakIndices.size < 4) return false

        val lastPeaks = peakIndices.takeLast(6)
        val intervals = (1 until lastPeaks.size).map { lastPeaks[it] - lastPeaks[it - 1] }
        val meanInterval = intervals.average()
        if (meanInterval < 3 || meanInterval > 120) return false // период ~0.75–30 сек
        val variance = intervals.map { (it - meanInterval) * (it - meanInterval) }.average()
        return sqrt(variance) < meanInterval * 0.30
    }

    private fun shouldTriggerSnippet(
        audioClass: AudioClass,
        deltaOverNoise: Float,
        now: Long,
        isExternalMedia: Boolean
    ): Boolean {
        if (totalSnippetsTonight >= maxSnippetsPerNight) return false
        if (now - lastSnippetRecordedTimeMs < snippetCooldownMs) return false

        // Если играет внешняя музыка/подкаст, не пишем речь и неизвестный шум
        if (isExternalMedia && (audioClass == AudioClass.SPEECH || audioClass == AudioClass.UNKNOWN)) {
            return false
        }

        return when (audioClass) {
            AudioClass.SNORE -> deltaOverNoise >= 18f
            AudioClass.GASP -> deltaOverNoise >= 28f
            AudioClass.FLATULENCE -> deltaOverNoise >= 25f
            AudioClass.COUGH -> deltaOverNoise >= 30f
            AudioClass.BRUXISM -> deltaOverNoise >= 15f
            AudioClass.RUSTLING -> deltaOverNoise >= 30f
            AudioClass.SPEECH -> deltaOverNoise >= 40f
            AudioClass.IMPACT -> deltaOverNoise >= 45f
            AudioClass.UNKNOWN -> deltaOverNoise >= 35f
            else -> false // NOISE/AMBIENT/SILENCE не записываем
        }
    }

    private fun triggerSnippetRecording(
        sessionId: Long,
        audioClass: AudioClass,
        peakRms: Float,
        eventTimestamp: Long
    ) {
        lastSnippetRecordedTimeMs = eventTimestamp
        totalSnippetsTonight++

        val snippetType = when (audioClass) {
            AudioClass.SNORE -> AudioSnippetType.SNORE
            AudioClass.GASP -> AudioSnippetType.APNEA_GASP
            AudioClass.FLATULENCE -> AudioSnippetType.FLATULENCE
            AudioClass.COUGH -> AudioSnippetType.COUGH_SNEEZE
            AudioClass.BRUXISM -> AudioSnippetType.BRUXISM
            AudioClass.RUSTLING -> AudioSnippetType.RUSTLING
            AudioClass.SPEECH -> AudioSnippetType.SLEEP_TALK
            AudioClass.IMPACT -> AudioSnippetType.IMPACT_NOISE
            AudioClass.UNKNOWN -> AudioSnippetType.UNKNOWN
            else -> AudioSnippetType.UNKNOWN
        }

        scope.launch(Dispatchers.IO) {
            // КРИТИЧНО: НЕ читаем AudioRecord из этой корутины — иначе два читателя
            // разрывают поток на части (дыры в анализе и в самом сниппете).
            // Главный цикл продолжает писать в кольцевой буфер; ждём post-trigger
            // и снимаем последние 12 секунд (4 сек до события + 8 сек после).
            delay(postTriggerDurationSec * 1000L)

            val snippetPcm = synchronized(bufferLock) {
                ByteArray(ringBufferSize).also { snapshot ->
                    val tail = circularHead
                    for (i in 0 until ringBufferSize) {
                        snapshot[i] = circularBuffer[(tail + i) % ringBufferSize]
                    }
                }
            }

            saveSnippetToFile(sessionId, snippetType, peakRms, eventTimestamp, snippetPcm)
        }
    }

    private suspend fun saveSnippetToFile(
        sessionId: Long,
        type: AudioSnippetType,
        peakRms: Float,
        timestampMs: Long,
        pcmData: ByteArray
    ) {
        val snippetsDir = File(context.filesDir, "audio_snippets").apply { mkdirs() }
        val outputFile = File(snippetsDir, "snippet_${sessionId}_${timestampMs}.m4a")

        val totalDurationSec = preTriggerDurationSec + postTriggerDurationSec
        encodePcmToM4a(pcmData, outputFile, sampleRate)

        _snippetFlow.tryEmit(
            SnippetRecordRequest(
                type = type,
                timestampMs = timestampMs,
                peakAmplitude = peakRms,
                audioFile = outputFile,
                durationSeconds = totalDurationSec
            )
        )
    }

    /**
     * Encodes raw PCM 16-bit mono bytes to lightweight AAC (.m4a).
     */
    private fun encodePcmToM4a(pcmData: ByteArray, outputFile: File, sampleRate: Int) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val mimeType = "audio/mp4a-latm"
            val bitRate = 24000 // 24 kbps - very light and clear for voice/snore

            val mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            codec = MediaCodec.createEncoderByType(mimeType)
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            var pcmOffset = 0
            var presentationTimeUs = 0L

            while (true) {
                val inputIndex = codec.dequeueInputBuffer(5000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    inputBuffer.clear()

                    val bytesToRead = min(inputBuffer.remaining(), pcmData.size - pcmOffset)
                    if (bytesToRead > 0) {
                        inputBuffer.put(pcmData, pcmOffset, bytesToRead)
                        codec.queueInputBuffer(inputIndex, 0, bytesToRead, presentationTimeUs, 0)
                        pcmOffset += bytesToRead
                        presentationTimeUs += (bytesToRead / (sampleRate * 2.0) * 1_000_000).toLong()
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        break
                    }
                }
            }

            // Drain output
            var isEos = false
            while (!isEos) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outputIndex >= 0) {
                    val encodedBuffer = codec.getOutputBuffer(outputIndex)
                    if (encodedBuffer != null && muxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        muxer.writeSampleData(audioTrackIndex, encodedBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                codec?.stop()
            } catch (ignored: Exception) {}
            try {
                codec?.release()
            } catch (ignored: Exception) {}
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (ignored: Exception) {}
            }
            try {
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }
}
