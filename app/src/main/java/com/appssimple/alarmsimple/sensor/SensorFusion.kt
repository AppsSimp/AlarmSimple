package com.appssimple.alarmsimple.sensor

import com.appssimple.alarmsimple.domain.model.AudioClass
import com.appssimple.alarmsimple.domain.model.SleepPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Пауза, после которой считаем, что трекер просто не работал, и не выдумываем сон. */
private const val MAX_BACKFILL_GAP_MS = 30 * 60_000L

/** Сколько эпох максимум можно восстановить (2 часа). */
private const val MAX_BACKFILL_EPOCHS = 240

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

    // Последние 20 эпох: был ли человек неподвижен. Окно терпит редкие подёргивания,
    // иначе один микродвижмент раз в пару минут не даёт вернуться из AWAKE в сон.
    private val recentStillFlags = ArrayDeque<Boolean>()
    private var consecutiveSpeechEpochs = 0

    // Сторож микрофона: если noise floor не меняется 5 эпох подряд или равен дефолту 25.0
    private var lastNoiseFloorValue = -1f
    private var unchangedNoiseEpochs = 0
    private var defaultNoiseEpochs = 0

    // Rolling window спокойных эпох (последние 6 эпох ~ 3 мин) для плавного выхода из AWAKE в LIGHT
    // Терпит редкие изолированные микродвижения.
    private val recentCalmWindow = ArrayDeque<Boolean>()

    // Consecutive stillness epochs inside sleep (used for DEEP sleep entry)
    private var sleepConsecutiveStillEpochs = 0
    private var consecutiveMovingEpochs = 0
    private var isFinalWakeupDetected = false
    private val recentMovementWindow = ArrayDeque<Float>()
    private var lastEpochTimestampMs = 0L

    // Adaptive thresholding: history of movement intensities during this session
    private val sessionMovementHistory = mutableListOf<Float>()

    // REM bout tracking and phase continuity
    private var currentSleepPhase: SleepPhase = SleepPhase.AWAKE
    private var remEpochsInBout = 0
    private var currentCycleIndex = -1
    private var deepEpochsInCycle = 0

    fun start(sessionId: Long) {
        trackingStartTimeMs = System.currentTimeMillis()
        sleepOnsetTimeMs = null
        consecutiveQuietEpochs = 0
        consecutiveStillEpochs = 0
        sleepConsecutiveStillEpochs = 0
        consecutiveMovingEpochs = 0
        isFinalWakeupDetected = false
        currentSleepPhase = SleepPhase.AWAKE
        remEpochsInBout = 0
        currentCycleIndex = -1
        deepEpochsInCycle = 0
        recentMovementWindow.clear()
        recentQuietStillEpochs.clear()
        recentStillFlags.clear()
        recentCalmWindow.clear()
        consecutiveSpeechEpochs = 0
        lastNoiseFloorValue = -1f
        unchangedNoiseEpochs = 0
        defaultNoiseEpochs = 0
        // КРИТИЧНО: сбрасываем метку последней эпохи, иначе новая сессия через несколько
        // часов «бэкфиллит» пропуск и приписывает пользователю до 10 часов сна.
        lastEpochTimestampMs = 0L
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
        // математически восстанавливаем пропущенные 30-секундные эпохи покоя по реальному времени.
        // Ограничения: не больше 2 часов подряд и только для пауз до 3 часов — иначе это уже
        // не «сон в Doze», а выключенный трекер, и придумывать часы сна нельзя.
        if (lastEpochTimestampMs > 0L && timeDeltaMs in 45_000L..MAX_BACKFILL_GAP_MS) {
            val missingCount = ((timeDeltaMs - 15_000L) / 30_000L).toInt()
            val safeCount = kotlin.math.min(missingCount, MAX_BACKFILL_EPOCHS)
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

        // Сторож микрофона: если noise_floor_rms равен дефолтному 25.0 в течение 5 эпох подряд
        // или не меняется вовсе 5 эпох подряд — AudioRecord завис или не запустился.
        val noiseNow = audioAnalyzer.noiseFloorRms
        val isDefaultNoise = kotlin.math.abs(noiseNow - 25.0f) < 0.001f
        if (isDefaultNoise) {
            defaultNoiseEpochs++
        } else {
            defaultNoiseEpochs = 0
        }

        if (lastNoiseFloorValue >= 0f && kotlin.math.abs(noiseNow - lastNoiseFloorValue) < 0.001f) {
            unchangedNoiseEpochs++
        } else {
            unchangedNoiseEpochs = 1
            lastNoiseFloorValue = noiseNow
        }

        if (defaultNoiseEpochs >= 5 || unchangedNoiseEpochs >= 5) {
            android.util.Log.w(
                "SensorFusion",
                "Microphone watchdog triggered (default25Epochs=$defaultNoiseEpochs, unchangedEpochs=$unchangedNoiseEpochs, noiseFloor=$noiseNow) — restarting AudioRecord"
            )
            audioAnalyzer.forceRestartMic()
            defaultNoiseEpochs = 0
            unchangedNoiseEpochs = 0
            lastNoiseFloorValue = -1f
        }

        // 2. Track Sleep Onset
        // Quiet audio: тишина, ровный фон, дыхание и звуки, которые случаются ВО СНЕ:
        // храп, газы, скрежет зубами, судорожный вдох после апноэ
        val isQuiet = dominantAudio in listOf(
            AudioClass.SILENCE, AudioClass.AMBIENT, AudioClass.BREATHING,
            AudioClass.SNORE, AudioClass.FLATULENCE, AudioClass.BRUXISM, AudioClass.GASP
        )

        // Речь считается бодрствованием только если ей сопутствует движение или она длится
        // несколько эпох подряд. Телевизор/разговор рядом не должен «будить» спящего.
        if (dominantAudio == AudioClass.SPEECH) consecutiveSpeechEpochs++ else consecutiveSpeechEpochs = 0
        val speechIsWake = dominantAudio == AudioClass.SPEECH &&
                (movement.isUserMoving || movement.movementIntensity >= 0.35f || consecutiveSpeechEpochs >= 3)

        if (isQuiet) {
            consecutiveQuietEpochs++
        } else if (speechIsWake) {
            // Настоящая речь с движением сбрасывает счётчик тишины
            consecutiveQuietEpochs = 0
        } else {
            // Фоновый шум (телевизор, речь без движения) лишь слегка уменьшает счётчик, не сбрасывая в ноль
            consecutiveQuietEpochs = kotlin.math.max(0, consecutiveQuietEpochs - 1)
        }

        // Спокойная эпоха для выхода из AWAKE: человек спокоен (нет явного движения, intensity < 0.35, maxDelta < 0.40)
        // либо храпит/дышит во сне. Терпит изолированные микродвижения (подёргивания).
        val isCalm = ((!movement.isUserMoving && movement.movementIntensity < 0.35f && movement.maxDelta < 0.40f && !speechIsWake) ||
                (dominantAudio == AudioClass.SNORE && movement.movementIntensity < 0.50f))
        recentCalmWindow.addLast(isCalm)
        if (recentCalmWindow.size > 6) {
            recentCalmWindow.removeFirst()
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

        recentStillFlags.addLast(isStill)
        if (recentStillFlags.size > 20) recentStillFlags.removeFirst()

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
                // Бэктайминг максимум на 16 эпох (8 минут): consecutiveStillEpochs после
                // гэп-бэкфилла может быть огромным — без ограничителя засыпание «уедет» в прошлое
                val backdateEpochs = kotlin.math.min(kotlin.math.max(consecutiveStillEpochs, 14), 16)
                sleepOnsetTimeMs = now - (backdateEpochs * 30_000L)
                onsetDetectedThisEpoch = true
            }
        }

        // 3. Classify Sleep Phase
        val phase = classifyPhase(movement, dominantAudio, speechIsWake, now)

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
        speechIsWake: Boolean,
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

        val calmCount = recentCalmWindow.count { it }
        val isCalmEnough = recentCalmWindow.size >= 4 && calmCount >= 4 && (recentCalmWindow.lastOrNull() == true)

        if (isFinalWakeupDetected) {
            // После утреннего подъёма человек бодрствует, но если он снова лёг и спокойно спит
            // 4-6 спокойных эпох (2-3 минуты), возвращаемся в сон, не заставляя лежать абсолютно неподвижно час.
            if (!isCalmEnough) {
                sleepConsecutiveStillEpochs = 0
                remEpochsInBout = 0
                currentSleepPhase = SleepPhase.AWAKE
                return SleepPhase.AWAKE
            } else {
                // Пользователь действительно решил поспать еще (утренний сон)
                isFinalWakeupDetected = false
            }
        }

        // --- 2. Ночное истинное пробуждение (WASO) ---
        // Требует:
        // А) Мощный всплеск (подъем с кровати, поворот всем телом, телефон в руке: intensity >= 8.0 или maxDelta >= 2.5)
        // Б) Непрерывное ворочание 2+ эпохи подряд (>= 60-90 сек непрерывных движений)
        // В) Голос (SPEECH)
        val isMajorSpike = movement.movementIntensity >= 8.0f || movement.maxDelta >= 2.5f
        val isSustainedMotion = consecutiveMovingEpochs >= 2 && movement.movementIntensity >= 1.5f
        val isSpeech = speechIsWake

        if (isMajorSpike || isSustainedMotion || isSpeech) {
            sleepConsecutiveStillEpochs = 0
            remEpochsInBout = 0
            currentSleepPhase = SleepPhase.AWAKE
            return SleepPhase.AWAKE
        }

        // Если находились в фазе AWAKE (пробуждение, поворот), возвращаемся в LIGHT
        // только после 4-6 спокойных эпох (2-3 минуты), терпя редкие микродвижения.
        if (currentSleepPhase == SleepPhase.AWAKE) {
            if (!isCalmEnough) {
                sleepConsecutiveStillEpochs = 0
                remEpochsInBout = 0
                return SleepPhase.AWAKE
            }
            currentSleepPhase = SleepPhase.LIGHT
        }

        // --- 3. Шевеления, микродвижения и гистерезис фазы REM ---
        val isStill = !movement.isUserMoving && movement.movementIntensity < 0.08f && movement.maxDelta < 0.15f
        // Физиологические микродвижения (миоклонические подергивания) во время REM
        val isTwitch = (movement.movementIntensity in 0.08f..0.59f) && (movement.maxDelta < 0.40f)

        if (currentSleepPhase == SleepPhase.REM && remEpochsInBout < 30 && (isStill || isTwitch)) {
            // Удерживаем фазу REM при микродвижениях
            remEpochsInBout++
            sleepConsecutiveStillEpochs++
            return SleepPhase.REM
        } else {
            remEpochsInBout = 0
        }

        // Если не полный покой и не REM — выходим в LIGHT
        if (!isStill) {
            sleepConsecutiveStillEpochs = 0
            currentSleepPhase = SleepPhase.LIGHT
            return SleepPhase.LIGHT
        }

        // --- 4. Полный покой во сне ---
        sleepConsecutiveStillEpochs++

        val cyclePosition = ((minutesSinceOnset % 90) / 90f) // Позиция в 90-мин ультрадианном цикле [0.0, 1.0]
        val cycleIndex = minutesSinceOnset / 90
        if (cycleIndex != currentCycleIndex) {
            currentCycleIndex = cycleIndex
            deepEpochsInCycle = 0
        }

        // Начальные 5 минут после засыпания — строго LIGHT (N1/N2 переход)
        if (minutesSinceOnset < 5) {
            currentSleepPhase = SleepPhase.LIGHT
            return SleepPhase.LIGHT
        }

        // --- 5. DEEP (Глубокий медленноволновой сон, N3) ---
        // Бюджет глубокого сна уменьшается к утру (биология сна):
        // Цикл 0: до 25 мин (50 эпох), Цикл 1: до 20 мин (40 эпох), Цикл 2: до 12 мин (24 эпохи), Цикл 3+: 0 мин
        val maxDeepInCycle = when (cycleIndex) {
            0 -> 50
            1 -> 40
            2 -> 24
            else -> 0
        }
        val deepStillThresh = if (cycleIndex == 0) 8 else 10
        val isDeepEligible = deepEpochsInCycle < maxDeepInCycle &&
                sleepConsecutiveStillEpochs >= deepStillThresh &&
                cyclePosition < 0.60f &&
                minutesSinceOnset < 240
        val isSnoringDeep = audio == AudioClass.SNORE && deepEpochsInCycle < maxDeepInCycle &&
                sleepConsecutiveStillEpochs >= 8 && cyclePosition < 0.60f && minutesSinceOnset < 240

        if (isDeepEligible || isSnoringDeep) {
            deepEpochsInCycle++
            currentSleepPhase = SleepPhase.DEEP
            return SleepPhase.DEEP
        }

        // --- 6. REM (Быстрый сон, сновидения) ---
        // Доминирует во второй половине ночи (> 140 мин) во 2-й половине цикла, либо под утро (> 240 мин)
        val isRemTiming = (minutesSinceOnset >= 140 && cyclePosition >= 0.58f) ||
                (minutesSinceOnset >= 240 && cyclePosition >= 0.45f)

        if (isRemTiming && sleepConsecutiveStillEpochs >= 10) {
            remEpochsInBout = 1
            currentSleepPhase = SleepPhase.REM
            return SleepPhase.REM
        }

        // Базовый сон: LIGHT (Основной сон)
        currentSleepPhase = SleepPhase.LIGHT
        return SleepPhase.LIGHT
    }
}
