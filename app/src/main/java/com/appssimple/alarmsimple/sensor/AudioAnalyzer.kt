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
import java.nio.ByteBuffer
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
 * Uses a memory rolling circular buffer to save pre-trigger audio (3-5 sec) plus post-trigger (8-10 sec).
 */
@Singleton
class AudioAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
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

    // Rolling circular buffer in memory (stores ~5 seconds of 16-bit PCM @ 8000Hz mono)
    // 8000 samples/sec * 2 bytes = 16,000 bytes per second -> 5s = 80,000 bytes
    private val preTriggerDurationSec = 4
    private val postTriggerDurationSec = 8
    private val sampleRate = 8000
    private val bytesPerSample = 2 // 16-bit mono
    private val preTriggerBufferSize = sampleRate * bytesPerSample * preTriggerDurationSec
    private val circularBuffer = ByteArray(preTriggerBufferSize)
    private var circularHead = 0

    // Anti-spam cooldown for audio snippets
    private var lastSnippetRecordedTimeMs = 0L
    private val snippetCooldownMs = 90_000L // Min 1.5 minutes between snippets
    private var totalSnippetsTonight = 0
    private val maxSnippetsPerNight = 20

    // Dynamic noise floor tracking (Leaky minimum follower)
    var noiseFloorRms = 25f // Start at realistic night bedroom silence level to avoid 12x calibration drops
        private set

    companion object {
        const val BUFFER_SIZE_SAMPLES = 2048 // ~256ms per analysis frame
    }

    @SuppressLint("MissingPermission")
    fun start(sessionId: Long) {
        if (isRunning) return
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

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

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

            // Check if music/video is actively playing on this device
            val isDeviceMediaPlaying = audioManager.isMusicActive

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

            val audioClass = classifyAudio(readBuffer, readCount, rms, isDeviceMediaPlaying)
            val snoreLikelihood = if (audioClass == AudioClass.SNORE) 0.9f else 0.1f

            val now = System.currentTimeMillis()
            _analysisFlow.tryEmit(
                AudioAnalysisResult(
                    timestampMs = now,
                    audioClass = audioClass,
                    rmsAmplitude = rms,
                    snoreLikelihood = snoreLikelihood,
                    isMediaPlayingOnDevice = isDeviceMediaPlaying
                )
            )

            // Trigger saving snippet if an interesting event occurs
            if (!isDeviceMediaPlaying && shouldTriggerSnippet(audioClass, rms, now)) {
                triggerSnippetRecording(sessionId, audioClass, rms, now)
            }

            // Power-efficiency sleep: 15-20ms
            delay(15)
        }
    }

    private fun writeToCircularBuffer(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            circularBuffer[circularHead] = data[i]
            circularHead = (circularHead + 1) % preTriggerBufferSize
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
        isMediaActive: Boolean
    ): AudioClass {
        if (isMediaActive) return AudioClass.AMBIENT

        val deltaOverNoise = rms - noiseFloorRms
        if (deltaOverNoise < 40f) return AudioClass.SILENCE

        // Compute zero-crossing rate (ZCR)
        var zcr = 0
        for (i in 1 until count) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                zcr++
            }
        }
        val normalizedZcr = zcr.toFloat() / count

        // Snoring: high energy in low frequencies (100 - 800 Hz) with low ZCR
        if (deltaOverNoise > 180f && normalizedZcr < 0.18f) {
            return AudioClass.SNORE
        }

        // Speech: mid-level ZCR, formants between 300Hz and 3000Hz
        if (deltaOverNoise > 220f && normalizedZcr in 0.20f..0.55f) {
            return AudioClass.SPEECH
        }

        // Sudden transient movement/rustle
        if (deltaOverNoise > 120f) {
            return AudioClass.NOISE
        }

        return AudioClass.AMBIENT
    }

    private fun shouldTriggerSnippet(audioClass: AudioClass, rms: Float, now: Long): Boolean {
        if (totalSnippetsTonight >= maxSnippetsPerNight) return false
        if (now - lastSnippetRecordedTimeMs < snippetCooldownMs) return false

        return when (audioClass) {
            AudioClass.SNORE -> rms > noiseFloorRms + 250f
            AudioClass.SPEECH -> rms > noiseFloorRms + 220f
            AudioClass.NOISE -> rms > noiseFloorRms + 400f // Sudden loud noise
            else -> false
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
            AudioClass.SPEECH -> AudioSnippetType.SLEEP_TALK
            AudioClass.NOISE -> AudioSnippetType.IMPACT_NOISE
            else -> AudioSnippetType.UNKNOWN
        }

        // Copy pre-trigger buffer contents snapshot
        val preTriggerData = ByteArray(preTriggerBufferSize)
        val tail = circularHead
        for (i in 0 until preTriggerBufferSize) {
            preTriggerData[i] = circularBuffer[(tail + i) % preTriggerBufferSize]
        }

        scope.launch(Dispatchers.IO) {
            saveSnippetToFile(sessionId, snippetType, peakRms, eventTimestamp, preTriggerData)
        }
    }

    private suspend fun saveSnippetToFile(
        sessionId: Long,
        type: AudioSnippetType,
        peakRms: Float,
        timestampMs: Long,
        preTriggerData: ByteArray
    ) {
        val snippetsDir = File(context.filesDir, "audio_snippets").apply { mkdirs() }
        val outputFile = File(snippetsDir, "snippet_${sessionId}_${timestampMs}.m4a")

        val totalDurationSec = preTriggerDurationSec + postTriggerDurationSec
        val postTriggerBytesNeeded = sampleRate * bytesPerSample * postTriggerDurationSec
        val postTriggerData = ByteArray(postTriggerBytesNeeded)
        var postBytesRead = 0

        val tempShort = ShortArray(BUFFER_SIZE_SAMPLES)
        while (postBytesRead < postTriggerBytesNeeded && isRunning) {
            val record = audioRecord ?: break
            val count = record.read(tempShort, 0, BUFFER_SIZE_SAMPLES)
            if (count > 0) {
                for (i in 0 until count) {
                    if (postBytesRead + 1 < postTriggerBytesNeeded) {
                        val s = tempShort[i].toInt()
                        postTriggerData[postBytesRead++] = (s and 0xFF).toByte()
                        postTriggerData[postBytesRead++] = ((s shr 8) and 0xFF).toByte()
                    }
                }
            }
            delay(10)
        }

        // Combine pre-trigger and post-trigger PCM
        val totalPcmData = preTriggerData + postTriggerData
        encodePcmToM4a(totalPcmData, outputFile, sampleRate)

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
        try {
            val mimeType = "audio/mp4a-latm"
            val bitRate = 24000 // 24 kbps - very light and clear for voice/snore

            val mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(mimeType)
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

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

            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
                muxer.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
