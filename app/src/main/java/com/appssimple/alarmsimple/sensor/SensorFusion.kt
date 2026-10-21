package com.appssimple.alarmsimple.sensor

import com.appssimple.alarmsimple.domain.model.AudioClass
import com.appssimple.alarmsimple.domain.model.SleepPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FusionEpochState(
    val timestampMs: Long,
    val phase: SleepPhase,
    val movementIntensity: Float,
    val maxDelta: Float,
    val isUserMoving: Boolean,
    val dominantAudioClass: AudioClass?,
    val noiseFloorRms: Float,
    val consecutiveStillEpochs: Int,
    val consecutiveQuietEpochs: Int,
    val isSleepOnsetDetected: Boolean,
    val sleepOnsetMs: Long?,
    val isLightPhaseForWakeUp: Boolean
)

/**
 * Fuses data streams from AccelerometerCollector and AudioAnalyzer.
 * Handles Sleep Onset Detection, Ultradian 90-minute sleep cycle modeling, and Smart Wake trigger detection.
 */
@Singleton
class SensorFusion @Inject constructor(
    private val accelerometerCollector: AccelerometerCollector,
    private val audioAnalyzer: AudioAnalyzer
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var fusionJob: Job? = null

    private val _fusionEpochFlow = MutableSharedFlow<FusionEpochState>(extraBufferCapacity = 64)
    val fusionEpochFlow: SharedFlow<FusionEpochState> = _fusionEpochFlow.asSharedFlow()

    @Volatile
    private var trackingStartTimeMs = 0L

    @Volatile
    var sleepOnsetTimeMs: Long? = null
        private set

    // Rolling window of recent audio classes (last 30 seconds)
    private val recentAudioClasses = mutableListOf<AudioClass>()
    private val audioLock = Any()

    // Sliding window of recent epochs (last 16 epochs ~ 8 min) for robust onset detection
    // Each entry is true if epoch was quiet and still
    private val recentQuietStillEpochs = ArrayDeque<Boolean>()

    // Consecutive quiet and still epochs counter (each epoch is 30 sec)
    private var consecutiveQuietEpochs = 0
    private var consecutiveStillEpochs = 0

    // Consecutive stillness epochs inside sleep (used for DEEP sleep entry)
    private var sleepConsecutiveStillEpochs = 0
    private var lastEpochTimestampMs = 0L

    // Adaptive thresholding: history of movement intensities during this session
    private val sessionMovementHistory = mutableListOf<Float>()

    fun start(sessionId: Long) {
        trackingStartTimeMs = System.currentTimeMillis()
        sleepOnsetTimeMs = null
        consecutiveQuietEpochs = 0
        consecutiveStillEpochs = 0
        sleepConsecutiveStillEpochs = 0
        recentQuietStillEpochs.clear()
        synchronized(audioLock) {
            recentAudioClasses.clear()
        }
        sessionMovementHistory.clear()

        accelerometerCollector.start()
        audioAnalyzer.start(sessionId)

        fusionJob = scope.launch {
            launch {
                audioAnalyzer.analysisFlow.collect { result ->
                    synchronized(audioLock) {
                        recentAudioClasses.add(result.audioClass)
                        if (recentAudioClasses.size > 15) {
                            recentAudioClasses.removeAt(0)
                        }
                    }
                }
            }

            launch {
                accelerometerCollector.epochFlow.collect { movementResult ->
                    processCombinedEpoch(movementResult)
                }
            }

            // Fallback watchdog: if accelerometer callbacks stall or sleep (e.g. phone completely stationary on table),
            // emit a motionless epoch every 30 seconds so night tracking never stalls
            launch {
                while (isActive) {
                    delay(30_000L)
                    val now = System.currentTimeMillis()
                    val lastSampleTime = accelerometerCollector.lastSampleTimeMs
                    // If no samples arrived for over 35 seconds, synthesize a quiet/still epoch
                    if (now - lastSampleTime >= 35_000L) {
                        processCombinedEpoch(
                            EpochMovementResult(
                                timestampMs = now,
                                movementIntensity = 0f,
                                maxDelta = 0f,
                                standardDeviation = 0f,
                                movementCount = 0,
                                isUserMoving = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        fusionJob?.cancel()
        fusionJob = null
        accelerometerCollector.stop()
        audioAnalyzer.stop()
    }

    private fun processCombinedEpoch(movement: EpochMovementResult) {
        val now = movement.timestampMs
        val timeDeltaMs = if (lastEpochTimestampMs > 0L) now - lastEpochTimestampMs else 30_000L
        lastEpochTimestampMs = now

        // Если операционная система замораживала поток процессора (разрыв больше 60 секунд),
        // не засчитываем время как 1 эпоху! При разрыве более 60 сек сбрасываем счетчик подряд идущих эпох
        val isSystemGap = timeDeltaMs > 65_000L
        if (isSystemGap) {
            // Фриз системы: плавно восстанавливаемся, не делая резких ложных переходов в REM
            sleepConsecutiveStillEpochs = kotlin.math.min(sleepConsecutiveStillEpochs, 4)
        }

        sessionMovementHistory.add(movement.movementIntensity)

        // 1. Determine dominant audio class during this 30s epoch
        val dominantAudio = synchronized(audioLock) {
            if (recentAudioClasses.isEmpty()) AudioClass.SILENCE
            else recentAudioClasses.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }

        // 2. Track Sleep Onset
        // Quiet audio: silence, ambient room hum, steady breathing or snoring
        val isQuiet = dominantAudio in listOf(AudioClass.SILENCE, AudioClass.AMBIENT, AudioClass.BREATHING, AudioClass.SNORE)
        if (isQuiet) {
            consecutiveQuietEpochs++
        } else if (dominantAudio == AudioClass.SPEECH) {
            // Speech (e.g. talking on phone or talking to someone) resets quiet count
            consecutiveQuietEpochs = 0
        } else {
            // Loud non-snore noise decreases quiet count
            consecutiveQuietEpochs = kotlin.math.max(0, consecutiveQuietEpochs - 2)
        }

        // Movement / Stillness evaluation:
        // isStill: user is motionless (intensity < 0.08 and maxDelta < 0.15)
        val isStill = !movement.isUserMoving && movement.movementIntensity < 0.08f && movement.maxDelta < 0.15f
        val isMajorMove = movement.movementIntensity >= 2.0f || movement.maxDelta >= 1.0f

        if (isStill) {
            consecutiveStillEpochs++
        } else if (isMajorMove) {
            // Big posture shift or getting out of bed resets still epochs completely
            consecutiveStillEpochs = 0
        } else {
            // Moderate micro-movement/twitches (e.g. rolling over or adjusting pillow):
            // Instead of wiping out 8 minutes of accumulated stillness to 0, penalize by 3 epochs (1.5 min)
            consecutiveStillEpochs = kotlin.math.max(0, consecutiveStillEpochs - 3)
        }

        // Sliding window: track whether the epoch was relaxed (still & quiet)
        val isRelaxedEpoch = isStill && (isQuiet || consecutiveQuietEpochs >= 10)
        recentQuietStillEpochs.addLast(isRelaxedEpoch)
        if (recentQuietStillEpochs.size > 16) { // 16 epochs = 8 minutes
            recentQuietStillEpochs.removeFirst()
        }

        var onsetDetectedThisEpoch = false
        if (sleepOnsetTimeMs == null) {
            // Sleep onset criteria:
            // 1) Sliding window: In an 8-minute window (16 epochs), at least 13 epochs are still & quiet, AND
            //    user has had at least 10 minutes (20 epochs) of overall quiet ambient conditions
            val relaxedInWindow = recentQuietStillEpochs.count { it }
            val windowSatisfied = recentQuietStillEpochs.size >= 14 && relaxedInWindow >= 12 && consecutiveQuietEpochs >= 16

            // 2) Or continuous stillness for 14 epochs (~7 min) with quiet conditions
            val continuousSatisfied = consecutiveQuietEpochs >= 16 && consecutiveStillEpochs >= 14

            // 3) Or prolonged stillness regardless of ambient noise (e.g. sleeping with TV/fan on) for 24 epochs (12 min)
            val deepStillSatisfied = consecutiveStillEpochs >= 24

            if (windowSatisfied || continuousSatisfied || deepStillSatisfied) {
                val backdateEpochs = kotlin.math.max(consecutiveStillEpochs, 14)
                sleepOnsetTimeMs = now - (backdateEpochs * 30_000L)
                onsetDetectedThisEpoch = true
            }
        }

        // 3. Classify Sleep Phase
        val phase = classifyPhase(movement, dominantAudio, now)

        // 4. Check if optimal for Smart Wake-Up:
        // Must be in LIGHT sleep phase or awakening spike, and NEVER wake up later than scheduled alarm
        val isLightForWake = phase == SleepPhase.LIGHT || (phase == SleepPhase.AWAKE && movement.movementIntensity > 0.20f)

        _fusionEpochFlow.tryEmit(
            FusionEpochState(
                timestampMs = now,
                phase = phase,
                movementIntensity = movement.movementIntensity,
                maxDelta = movement.maxDelta,
                isUserMoving = movement.isUserMoving,
                dominantAudioClass = dominantAudio,
                noiseFloorRms = audioAnalyzer.noiseFloorRms,
                consecutiveStillEpochs = consecutiveStillEpochs,
                consecutiveQuietEpochs = consecutiveQuietEpochs,
                isSleepOnsetDetected = onsetDetectedThisEpoch,
                sleepOnsetMs = sleepOnsetTimeMs,
                isLightPhaseForWakeUp = isLightForWake
            )
        )
    }

    private fun classifyPhase(
        movement: EpochMovementResult,
        audio: AudioClass?,
        now: Long
    ): SleepPhase {
        // If sleep onset not yet reached, user is Awake
        val onset = sleepOnsetTimeMs ?: return SleepPhase.AWAKE

        // Gross movement indicates awakening
        if (movement.isUserMoving || movement.movementIntensity >= 0.35f || movement.maxDelta >= 0.6f) {
            sleepConsecutiveStillEpochs = 0
            return SleepPhase.AWAKE
        }

        // Minor movements: shift in bed / twitching = LIGHT sleep
        if (movement.movementIntensity >= 0.08f || movement.maxDelta >= 0.15f) {
            sleepConsecutiveStillEpochs = 0
            return SleepPhase.LIGHT
        }

        // Motionless epoch in bed during sleep
        sleepConsecutiveStillEpochs++

        val minutesSinceOnset = ((now - onset) / 60_000L).toInt()
        val cyclePosition = ((minutesSinceOnset % 90) / 90f) // Position in 90-min ultradian cycle [0.0, 1.0]

        // Physiological rules:
        // 1. Начальные 15 минут после засыпания — строго LIGHT (N1/N2).
        if (minutesSinceOnset < 15) {
            return SleepPhase.LIGHT
        }

        // 2. Чтобы войти в DEEP (N3) или REM:
        // Обязателен барьер покоя: не менее 12-14 эпох (6-7 минут непрерывного штиля) после любого движения!
        // Никакого REM через 3 минуты после пробуждения утром.
        if (sleepConsecutiveStillEpochs < 12) {
            return SleepPhase.LIGHT
        }

        // 3. Вход в DEEP (Глубокий сон):
        // Доминирует в первые 4 часа ночи (циклы 1-3), в первой половине цикла (cyclePosition < 0.60).
        // Порог: от 12 эпох покоя в первой половине ночи или стабильный храп.
        val isEarlyInCycle = cyclePosition < 0.60f
        val isEarlyHours = minutesSinceOnset < 240 // Первые 4 часа ночи
        val isDeepCandidate = sleepConsecutiveStillEpochs >= 12 && isEarlyInCycle && isEarlyHours
        val isSnoringDeep = audio == AudioClass.SNORE && sleepConsecutiveStillEpochs >= 8 && isEarlyInCycle

        if (isDeepCandidate || isSnoringDeep) {
            return SleepPhase.DEEP
        }

        // 4. REM (Быстрый сон):
        // Возникает только во второй половине 90-минутного цикла (cyclePosition >= 0.65)
        // и только после минимум 180 минут (3 часов) от начала сна.
        // Требует минимум 16 эпох непрерывного покоя после AWAKE (не менее 8 минут).
        val isLateInCycle = cyclePosition >= 0.65f
        val isLateInNight = minutesSinceOnset >= 180
        if (isLateInCycle && isLateInNight && sleepConsecutiveStillEpochs >= 16) {
            return SleepPhase.REM
        }

        // 5. Базовая фаза сна — LIGHT (Основной сон, 55-60% времени)
        return SleepPhase.LIGHT
    }
}
