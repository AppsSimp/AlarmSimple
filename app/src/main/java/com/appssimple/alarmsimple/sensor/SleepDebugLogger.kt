package com.appssimple.alarmsimple.sensor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs epoch-level sensor data (accelerometer, audio RMS, classification, sleep onset status)
 * to a CSV file in app's internal storage when Developer Sleep Logging is active.
 *
 * This allows matching phone sleep staging against smartwatch logs to calibrate thresholds.
 */
@Singleton
class SleepDebugLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val mutex = Mutex()
    val logFile: File by lazy { File(context.filesDir, FILE_NAME) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    companion object {
        const val FILE_NAME = "sleep_debug_log.csv"
        private const val CSV_HEADER = "timestamp,time_str,session_id,phase,movement_intensity,max_delta,is_moving,dominant_audio,noise_floor_rms,still_epochs,quiet_epochs,is_onset_detected,sleep_onset_ms\n"
    }

    suspend fun logEpoch(
        sessionId: Long,
        epochState: FusionEpochState,
        sleepOnsetMs: Long?
    ) = withContext(Dispatchers.IO) {
        if (!userPreferences.debugSleepLoggingEnabled.first()) return@withContext

        mutex.withLock {
            try {
                val exists = logFile.exists() && logFile.length() > 0
                FileWriter(logFile, true).use { writer ->
                    if (!exists) {
                        writer.write(CSV_HEADER)
                    }
                    val timeStr = dateFormat.format(Date(epochState.timestampMs))
                    val line = "${epochState.timestampMs}," +
                            "$timeStr," +
                            "$sessionId," +
                            "${epochState.phase.name}," +
                            String.format(Locale.US, "%.4f", epochState.movementIntensity) + "," +
                            String.format(Locale.US, "%.4f", epochState.maxDelta) + "," +
                            "${epochState.isUserMoving}," +
                            "${epochState.dominantAudioClass?.name ?: "NONE"}," +
                            String.format(Locale.US, "%.1f", epochState.noiseFloorRms) + "," +
                            "${epochState.consecutiveStillEpochs}," +
                            "${epochState.consecutiveQuietEpochs}," +
                            "${epochState.isSleepOnsetDetected}," +
                            "${sleepOnsetMs ?: 0}\n"
                    writer.write(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLogFileSizeFormatted(): String {
        if (!logFile.exists()) return "0 KB"
        val bytes = logFile.length()
        return if (bytes < 1024 * 1024) {
            "${bytes / 1024} KB"
        } else {
            String.format(Locale.US, "%.2f MB", bytes.toFloat() / (1024 * 1024))
        }
    }

    suspend fun clearLog() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (logFile.exists()) {
                logFile.delete()
            }
        }
    }

    fun getShareUri(): Uri? {
        if (!logFile.exists() || logFile.length() == 0L) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
    }
}
