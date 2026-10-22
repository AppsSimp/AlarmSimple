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
    private var consecutiveMovingEpochs = 0
    private var isFinalWakeupDetected = false
    private val recentMovementWindow = ArrayDeque<Float>()
    private var lastEpochTimestampMs = 0L

    // Adaptive thresholding: history of movement intensities during this session
    private val sessionMovementHistory = mutableListOf<Float>()

    fun start(sessionId: Long) {
        trackingStartTimeMs = System.currentTimeMillis()
        sleepOnsetTimeMs = null
        consecutiveQuietEpochs = 0
        consecutiveStillEpochs = 0
        sleepConsecutiveStillEpochs = 0
        consecutiveMovingEpochs = 0
        isFinalWakeupDetected = false
        recentMovementWindow.clear()
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

        // ЖЕЛЕЗОБЕТОННЫЙ ЗАЩИТНИК ОТ DOZE MODE (Wall-Clock Gap Backfill):
        // Если операционная система спала или произошла пауза (> 45 сек),
        // математически восстанавливаем ВСЕ пропущенные 30-секундные эпохи покоя по реальному времени часов!
        // В это время телефон лежал неподвижно в кровати — данные сна и фазы не теряются!
        if (lastEpochTimestampMs > 0L && timeDeltaMs >= 45_000L) {
            val missingCount = ((timeDeltaMs - 15_000L) / 30_000L).toInt()
            val safeCount = kotlin.math.min(missingCount, 1200) // До 10 часов провала
            for (i in 1..safeCount) {
                val synthTime = lastEpochTimestampMs + (i * 30_000L)
                processSingleEpoch(
                    EpochMovementResult(
                        timestampMs = synthTime,
                        movementIntensity = 0f,
                        maxDelta = 0f,
                        standardDeviation = 0f,
                        movementCount = 0,
                        isUserMoving = false
                    ),
                    forcedAudio = AudioClass.SILENCE
                )
            }
        }

        processSingleEpoch(movement, forcedAudio = null)
        lastEpochTimestampMs = now
    }

    private fun processSingleEpoch(movement: EpochMovementResult, forcedAudio: AudioClass?) {
        val now = movement.timestampMs
        sessionMovementHistory.add(movement.movementIntensity)

        // 1. Determine dominant audio class during this 30s epoch
        val dominantAudio = forcedAudio ?: synchronized(audioLock) {
            if (recentAudioClasses.isEmpty()) AudioClass.SILENCE
            else recentAudioClasses.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }

        // 2. Track Sleep Onset
        // Quiet audio: silence, ambient room hum, steady breathing or snoring
        val isQuiet = dominantAudio in listOf(AudioClass.SILENCE, AudioClass.AMBIENT, AudioClass.BREATHING, AudioClass.SNORE)
        if (isQuiet) {
            consecutiveQuietEpochs++
        } else if (dominantAudio == AudioClass.SPEECH) {
            // Speech resets quiet count
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
            consecutiveStillEpochs = 0
        } else {
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
            val relaxedInWindow = recentQuietStillEpochs.count { it }
            val windowSatisfied = recentQuietStillEpochs.size >= 14 && relaxedInWindow >= 12 && consecutiveQuietEpochs >= 16
            val continuousSatisfied = consecutiveQuietEpochs >= 16 && consecutiveStillEpochs >= 14
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

        val minutesSinceOnset = ((now - onset) / 60_000L).toInt()

        // Track rolling 10-minute movement window (20 epochs)
        recentMovementWindow.addLast(movement.movementIntensity)
        if (recentMovementWindow.size > 20) {
            recentMovementWindow.removeFirst()
        }

        // Continuous movement tracking
        val isMovingEpoch = movement.movementIntensity >= 1.5f || movement.maxDelta >= 0.5f
        if (isMovingEpoch) {
            consecutiveMovingEpochs++
        } else {
            consecutiveMovingEpochs = kotlin.math.max(0, consecutiveMovingEpochs - 1)
        }

        // --- 1. Финальное утреннее пробуждение (Anti-Bounce Back) ---
        // Если человек проспал больше 3 часов и пошла активная фаза пробуждения
        // (серия сильных движений: подъем, ходьба, сборы суммарно > 40 за 10 мин):
        if (minutesSinceOnset >= 180 && !isFinalWakeupDetected) {
            val windowSum = recentMovementWindow.sum()
            val hasHighSpike = recentMovementWindow.any { it >= 20.0f }
            val frequentMoves = recentMovementWindow.count { it >= 1.5f } >= 5
            if (windowSum >= 40.0f || (hasHighSpike && windowSum >= 20.0f) || frequentMoves) {
                isFinalWakeupDetected = true
            }
        }

        if (isFinalWakeupDetected) {
            // После утреннего подъема человек бодрствует.
            // Положить телефон на стол на пару минут НЕ возвращает сон.
            // Для повторного засыпания требуется честный 10-минутный штиль (>= 20 эпох).
            if (consecutiveStillEpochs < 20) {
                sleepConsecutiveStillEpochs = 0
                return SleepPhase.AWAKE
            } else {
                // Пользователь действительно решил поспать еще (утренний сон)
                isFinalWakeupDetected = false
            }
        }

        // --- 2. Ночное истинное пробуждение (WASO) ---
        // Требует:
        // А) Мощный всплеск (подъем с кровати, поворот всем телом, телефон в руке: intensity >= 7.0 или maxDelta >= 2.0)
        // Б) Непрерывное ворочание 2+ эпохи подряд (>= 60-90 сек непрерывных движений)
        // В) Голос (SPEECH)
        val isMajorSpike = movement.movementIntensity >= 7.0f || movement.maxDelta >= 2.0f
        val isSustainedMotion = consecutiveMovingEpochs >= 2 && movement.movementIntensity >= 1.5f
        val isSpeech = audio == AudioClass.SPEECH

        if (isMajorSpike || isSustainedMotion || isSpeech) {
            sleepConsecutiveStillEpochs = 0
            return SleepPhase.AWAKE
        }

        // --- 3. Шевеления и микродвижения во сне -> LIGHT (а не AWAKE!) ---
        // Смена позы, движение ногой, дыхание (0.08 <= intensity < 7.0) — это нормальный
        // поверхностный сон (N1/N2), а не пробуждение! Сбрасывает счетчик глубокого сна.
        if (movement.movementIntensity >= 0.08f || movement.maxDelta >= 0.15f) {
            sleepConsecutiveStillEpochs = 0
            return SleepPhase.LIGHT
        }

        // --- 4. Полный покой во сне ---
        sleepConsecutiveStillEpochs++

        val cyclePosition = ((minutesSinceOnset % 90) / 90f) // Позиция в 90-мин ультрадианном цикле [0.0, 1.0]

        // Начальные 15 минут после засыпания — строго LIGHT (N1/N2)
        if (minutesSinceOnset < 15) {
            return SleepPhase.LIGHT
        }

        // Барьер покоя: не менее 12 эпох (6 минут тишины) после любого движения перед DEEP/REM
        if (sleepConsecutiveStillEpochs < 12) {
            return SleepPhase.LIGHT
        }

        // --- 5. DEEP (Глубокий сон, N3) ---
        // Доминирует в первые 4 часа ночи и в первой половине 90-мин цикла (< 0.60)
        val isEarlyInCycle = cyclePosition < 0.60f
        val isEarlyHours = minutesSinceOnset < 240
        val isDeepCandidate = sleepConsecutiveStillEpochs >= 12 && isEarlyInCycle && isEarlyHours
        val isSnoringDeep = audio == AudioClass.SNORE && sleepConsecutiveStillEpochs >= 8 && isEarlyInCycle

        if (isDeepCandidate || isSnoringDeep) {
            return SleepPhase.DEEP
        }

        // --- 6. REM (Быстрый сон, сновидения) ---
        // Доминирует под утро (> 180 мин) во второй половине 90-мин цикла (>= 0.65)
        val isLateInCycle = cyclePosition >= 0.65f
        val isLateInNight = minutesSinceOnset >= 180
        if (isLateInCycle && isLateInNight && sleepConsecutiveStillEpochs >= 16) {
            return SleepPhase.REM
        }

        // Базовый сон: LIGHT (Основной сон)
        return SleepPhase.LIGHT
    }
}
