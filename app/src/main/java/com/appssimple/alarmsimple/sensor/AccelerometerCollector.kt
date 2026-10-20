package com.appssimple.alarmsimple.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class AccelSample(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

data class EpochMovementResult(
    val timestampMs: Long,
    val movementIntensity: Float, // Sum of dynamic acceleration delta
    val maxDelta: Float,
    val standardDeviation: Float,
    val movementCount: Int,
    val isUserMoving: Boolean
)

/**
 * Collects accelerometer samples, removes gravity vector via low-pass/high-pass filtering,
 * and packages them into 30-second epochs for sleep staging.
 */
@Singleton
class AccelerometerCollector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _epochFlow = MutableSharedFlow<EpochMovementResult>(extraBufferCapacity = 64)
    val epochFlow: SharedFlow<EpochMovementResult> = _epochFlow.asSharedFlow()

    private val samplesLock = Any()
    private val currentSamples = mutableListOf<AccelSample>()

    // Low-pass filter for gravity isolation
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private val alpha = 0.8f // Gravity low-pass filter factor

    private var isCollecting = false
    private var epochStartTimeMs = 0L

    companion object {
        const val SAMPLES_DELAY_US = 200_000 // 5 Hz (200ms)
        const val MAX_REPORT_LATENCY_US = 5_000_000 // 5-second FIFO batching for power efficiency
        const val EPOCH_DURATION_MS = 30_000L // 30 seconds standard actigraphy epoch
        const val MOVEMENT_NOISE_THRESHOLD = 0.025f // Filter out tiny sensor jitter (m/s^2)
        const val WAKE_MOVEMENT_THRESHOLD = 0.35f
    }

    fun start() {
        if (isCollecting || accelerometer == null) return
        isCollecting = true
        epochStartTimeMs = System.currentTimeMillis()
        synchronized(samplesLock) {
            currentSamples.clear()
        }
        sensorManager.registerListener(
            this,
            accelerometer,
            SAMPLES_DELAY_US,
            MAX_REPORT_LATENCY_US
        )
    }

    fun stop() {
        if (!isCollecting) return
        isCollecting = false
        sensorManager.unregisterListener(this)
        synchronized(samplesLock) {
            currentSamples.clear()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isCollecting) return

        // 1. Isolate gravity using low-pass filter
        gravityX = alpha * gravityX + (1 - alpha) * event.values[0]
        gravityY = alpha * gravityY + (1 - alpha) * event.values[1]
        gravityZ = alpha * gravityZ + (1 - alpha) * event.values[2]

        // 2. Linear acceleration without gravity
        val linearX = event.values[0] - gravityX
        val linearY = event.values[1] - gravityY
        val linearZ = event.values[2] - gravityZ

        val now = System.currentTimeMillis()
        var completedEpochSamples: List<AccelSample>? = null

        synchronized(samplesLock) {
            currentSamples.add(AccelSample(now, linearX, linearY, linearZ))
            if (now - epochStartTimeMs >= EPOCH_DURATION_MS) {
                completedEpochSamples = ArrayList(currentSamples)
                currentSamples.clear()
                epochStartTimeMs = now
            }
        }

        completedEpochSamples?.let { samples ->
            if (samples.isNotEmpty()) {
                val epochResult = processEpoch(samples, now)
                _epochFlow.tryEmit(epochResult)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processEpoch(samples: List<AccelSample>, epochTimestamp: Long): EpochMovementResult {
        var movementIntensity = 0f
        var maxDelta = 0f
        var movementCount = 0
        val magnitudes = mutableListOf<Float>()

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]

            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dz = curr.z - prev.z
            val delta = sqrt(dx * dx + dy * dy + dz * dz)

            if (delta > MOVEMENT_NOISE_THRESHOLD) {
                movementIntensity += delta
                movementCount++
            }
            maxDelta = max(maxDelta, delta)

            val mag = sqrt(curr.x * curr.x + curr.y * curr.y + curr.z * curr.z)
            magnitudes.add(mag)
        }

        val stdDev = calculateStdDev(magnitudes)
        // User movement threshold (calibrated for in-bed tossing & shifting)
        val isUserMoving = movementIntensity >= 0.10f || maxDelta >= 0.20f

        return EpochMovementResult(
            timestampMs = epochTimestamp,
            movementIntensity = movementIntensity,
            maxDelta = maxDelta,
            standardDeviation = stdDev,
            movementCount = movementCount,
            isUserMoving = isUserMoving
        )
    }

    private fun calculateStdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val sumSquared = values.sumOf { (it - mean).pow(2).toDouble() }
        return sqrt(sumSquared / (values.size - 1)).toFloat()
    }
}
