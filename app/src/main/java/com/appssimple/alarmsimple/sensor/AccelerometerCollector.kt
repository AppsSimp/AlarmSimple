package com.appssimple.alarmsimple.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
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
 *
 * Driven by an independent internal ticker loop so epochs are ALWAYS emitted on schedule
 * even if the device is completely still or Android pauses sensor dispatching.
 */
@Singleton
class AccelerometerCollector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Prefer wake-up accelerometer if hardware supports it to keep reporting when screen is off
    private val accelerometer: Sensor? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    } else {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private val _epochFlow = MutableSharedFlow<EpochMovementResult>(extraBufferCapacity = 64)
    val epochFlow: SharedFlow<EpochMovementResult> = _epochFlow.asSharedFlow()

    private val samplesLock = Any()
    private val currentSamples = mutableListOf<AccelSample>()

    // Low-pass filter for gravity isolation
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private val alpha = 0.8f // Gravity low-pass filter factor

    @Volatile
    private var isCollecting = false
    @Volatile var lastSampleTimeMs = 0L
        private set

    companion object {
        const val SAMPLES_DELAY_US = 200_000 // 5 Hz (200ms)
        const val EPOCH_DURATION_MS = 30_000L // 30 seconds standard actigraphy epoch
        const val MOVEMENT_NOISE_THRESHOLD = 0.025f // Filter out tiny sensor jitter (m/s^2)
        const val MAX_SAMPLES_BUFFER = 600
    }

    fun start() {
        if (isCollecting || accelerometer == null) return
        isCollecting = true
        synchronized(samplesLock) {
            currentSamples.clear()
        }
        sensorManager.registerListener(
            this,
            accelerometer,
            SAMPLES_DELAY_US
        )

        // Master clock ticker: гарантирует выработку эпохи ровно каждые 30 секунд независимо от активности датчиков
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isCollecting && isActive) {
                delay(EPOCH_DURATION_MS)
                val now = System.currentTimeMillis()
                val samples = synchronized(samplesLock) {
                    val list = ArrayList(currentSamples)
                    currentSamples.clear()
                    list
                }
                val epochResult = processEpoch(samples, now)
                _epochFlow.tryEmit(epochResult)
            }
        }
    }

    fun stop() {
        if (!isCollecting) return
        isCollecting = false
        tickerJob?.cancel()
        tickerJob = null
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
        val now = System.currentTimeMillis()
        lastSampleTimeMs = now

        // 2. Linear acceleration without gravity
        val linearX = event.values[0] - gravityX
        val linearY = event.values[1] - gravityY
        val linearZ = event.values[2] - gravityZ

        synchronized(samplesLock) {
            if (currentSamples.size < MAX_SAMPLES_BUFFER) {
                currentSamples.add(AccelSample(now, linearX, linearY, linearZ))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processEpoch(samples: List<AccelSample>, epochTimestamp: Long): EpochMovementResult {
        if (samples.isEmpty()) {
            // Если за 30 секунд телефон лежал неподвижно и события датчика не поступали
            return EpochMovementResult(
                timestampMs = epochTimestamp,
                movementIntensity = 0f,
                maxDelta = 0f,
                standardDeviation = 0f,
                movementCount = 0,
                isUserMoving = false
            )
        }

        var movementIntensity = 0f
        var maxDelta = 0f
        var movementCount = 0
        val magnitudes = ArrayList<Float>(samples.size)

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
        // User movement threshold: значительное шевеление в кровати
        val isUserMoving = movementIntensity >= 0.50f || maxDelta >= 0.35f

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
