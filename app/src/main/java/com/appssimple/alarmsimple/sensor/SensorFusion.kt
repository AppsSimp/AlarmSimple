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
    private var sleepOnsetTimeMs: Long? = null

    // Rolling window of recent audio classes (last 30 seconds)
    private val recentAudioClasses = mutableListOf<AudioClass>()
    private val audioLock = Any()

    // Consecutive quiet and still epochs counter (each epoch is 30 sec)
    private var consecutiveQuietEpochs = 0
    private var consecutiveStillEpochs = 0

    // Adaptive thresholding: history of movement intensities during this session
    private val sessionMovementHistory = mutableListOf<Float>()

    fun start(sessionId: Long) {
        trackingStartTimeMs = System.currentTimeMillis()
        sleepOnsetTimeMs = null
        consecutiveQuietEpochs = 0
        consecutiveStillEpochs = 0
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
            // Talking on phone or watching video in bed resets quiet
            consecutiveQuietEpochs = 0
        } else {
            // Loud non-snore noise decreses quiet count
            consecutiveQuietEpochs = kotlin.math.max(0, consecutiveQuietEpochs - 2)
        }

        // Movement / Tossing and Turning:
        // When tossing and turning in bed, movementIntensity is typically between 0.08 and 0.40.
        // If movement is detected, consecutiveStillEpochs MUST be reset to 0 (or heavily penalized),
        // preventing tossing/turning from falsely qualifying as sleep onset!
        val isStill = !movement.isUserMoving && movement.movementIntensity < 0.08f && movement.maxDelta < 0.15f
        val isTossingOrMoving = movement.isUserMoving || movement.movementIntensity >= 0.12f || movement.maxDelta >= 0.25f

        if (isStill) {
            consecutiveStillEpochs++
        } else if (isTossingOrMoving) {
            // Active movement or turning in bed resets stillness completely
            consecutiveStillEpochs = 0
        } else {
            // Very minor bed jitter / settling down (0.08 .. 0.12)
            consecutiveStillEpochs = kotlin.math.max(0, consecutiveStillEpochs - 1)
        }

        var onsetDetectedThisEpoch = false
        if (sleepOnsetTimeMs == null) {
            // User fell asleep:
            // 1) 10 min quiet (20 epochs) AND at least 8 min (16 epochs) continuous stillness
            // 2) OR 18 min (36 epochs) of continuous stillness regardless of audio (e.g. TV / music playing)
            if ((consecutiveQuietEpochs >= 20 && consecutiveStillEpochs >= 16) || consecutiveStillEpochs >= 36) {
                sleepOnsetTimeMs = now - (consecutiveStillEpochs * 30_000L)
                onsetDetectedThisEpoch = true
            }
        }

        // 3. Classify Sleep Phase
        val phase = classifyPhase(movement, dominantAudio, now)

        // 4. Check if optimal for Smart Wake-Up (Light phase + movement spike)
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
        val onset = sleepOnsetTimeMs
        if (onset == null) {
            return SleepPhase.AWAKE
        }

        val minutesSinceOnset = ((now - onset) / 60_000L).toInt()
        val cyclePosition = ((minutesSinceOnset % 90) / 90f) // Position in 90-min ultradian cycle [0.0, 1.0]

        // Gross movement indicates awakening or restless sleep
        if (movement.isUserMoving || movement.movementIntensity > 0.45f) {
            return SleepPhase.AWAKE
        }

        // Moderate movement = Light Sleep (N1 / N2)
        if (movement.movementIntensity > 0.12f) {
            return SleepPhase.LIGHT
        }

        // Low / near zero movement: Distinguish Deep Sleep (N3) vs REM
        // Deep sleep dominates the first 3 hours of the night and first half of each cycle.
        // REM dominates the latter half of the night and end of 90-min cycle.
        val isLateInCycle = cyclePosition > 0.60f
        val isLateInNight = minutesSinceOnset > 180

        return if (isLateInCycle && (isLateInNight || audio == AudioClass.BREATHING)) {
            SleepPhase.REM
        } else {
            // Snore or deep stillness indicates deep sleep (N3)
            SleepPhase.DEEP
        }
    }
}
