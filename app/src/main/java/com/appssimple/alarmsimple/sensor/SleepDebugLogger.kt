package com.appssimple.alarmsimple.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.appssimple.alarmsimple.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val userPreferences: UserPreferences,
    private val audioAnalyzer: AudioAnalyzer
) {

    private val mutex = Mutex()
    val logFile: File by lazy { File(context.filesDir, FILE_NAME) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var isLoggingEnabled = false
    @Volatile
    private var isInitialized = false

    init {
        scope.launch {
            userPreferences.debugSleepLoggingEnabled.collect { enabled ->
                isLoggingEnabled = enabled
                isInitialized = true
            }
        }
    }

    fun start() {
        if (!isInitialized) {
            scope.launch {
                isLoggingEnabled = userPreferences.debugSleepLoggingEnabled.first()
                isInitialized = true
            }
        }
    }

    companion object {
        const val FILE_NAME = "sleep_debug_log.csv"
        private const val CSV_HEADER = "timestamp,time_str,session_id,phase,movement_intensity,max_delta,is_moving,dominant_audio,noise_floor_rms,still_epochs,quiet_epochs,is_onset_detected,sleep_onset_ms,cpu_ticks,rss_kb,battery_pct,charging,screen_on,doze,mic_capturing,mic_restarts,snippets_tonight\n"
    }

    private fun ensureHeaderAndGetFile(): File {
        if (logFile.exists() && logFile.length() > 0L) {
            val firstLine = try {
                logFile.bufferedReader().use { it.readLine() }
            } catch (e: Exception) {
                null
            }
            if (firstLine != null && firstLine.trim() != CSV_HEADER.trim()) {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val oldFile = File(logFile.parentFile ?: context.filesDir, "sleep_debug_log_old_${timeStamp}.csv")
                val renamed = logFile.renameTo(oldFile)
                if (!renamed) {
                    try {
                        logFile.copyTo(oldFile, overwrite = true)
                        logFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return logFile
    }

    private fun readCpuTicks(): String {
        return try {
            val content = File("/proc/self/stat").readText()
            val lastParenIndex = content.lastIndexOf(')')
            if (lastParenIndex != -1 && lastParenIndex + 1 < content.length) {
                val afterParen = content.substring(lastParenIndex + 1).trim()
                val tokens = afterParen.split("\\s+".toRegex())
                if (tokens.size > 12) {
                    val utime = tokens[11].toLongOrNull()
                    val stime = tokens[12].toLongOrNull()
                    if (utime != null && stime != null) {
                        return (utime + stime).toString()
                    }
                }
            }
            ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readRssKb(): String {
        return try {
            File("/proc/self/status").useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("VmRSS:")) {
                        val valuePart = line.substringAfter("VmRSS:").trim()
                        val tokens = valuePart.split("\\s+".toRegex())
                        return@useLines tokens.firstOrNull()?.toLongOrNull()?.toString() ?: ""
                    }
                }
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readBatteryPct(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity != null && capacity in 0..100) {
                capacity.toString()
            } else {
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readCharging(): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                isCharging.toString()
            } else {
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readScreenOn(): String {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive?.toString() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readDoze(): String {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isDeviceIdleMode?.toString() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readMicCapturing(): String {
        return try {
            audioAnalyzer.micCapturing.toString()
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readMicRestarts(): String {
        return try {
            audioAnalyzer.micRestartCount.toString()
        } catch (e: Throwable) {
            ""
        }
    }

    private fun readSnippetsTonight(): String {
        return try {
            audioAnalyzer.snippetsRecorded.toString()
        } catch (e: Throwable) {
            ""
        }
    }

    suspend fun logEpoch(
        sessionId: Long,
        epochState: FusionEpochState,
        sleepOnsetMs: Long?
    ) {
        if (!isInitialized) {
            withContext(Dispatchers.IO) {
                isLoggingEnabled = userPreferences.debugSleepLoggingEnabled.first()
                isInitialized = true
            }
        }
        if (!isLoggingEnabled) return

        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val targetFile = ensureHeaderAndGetFile()
                    val exists = targetFile.exists() && targetFile.length() > 0
                    FileWriter(targetFile, true).use { writer ->
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
                                "${sleepOnsetMs ?: 0}," +
                                "${readCpuTicks()}," +
                                "${readRssKb()}," +
                                "${readBatteryPct()}," +
                                "${readCharging()}," +
                                "${readScreenOn()}," +
                                "${readDoze()}," +
                                "${readMicCapturing()}," +
                                "${readMicRestarts()}," +
                                "${readSnippetsTonight()}\n"
                        writer.write(line)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
