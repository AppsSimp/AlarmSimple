package com.appssimple.alarmsimple.sensor

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Воспроизводит бесшумный PCM-поток (абсолютная тишина) с минимальной частотой дискретизации (8 кГц mono).
 * В сочетании с FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK сообщает аудиоподсистеме Android (AudioFlinger),
 * что медиапоток активен, предотвращая переход процессора и аудиодрайверов в режим глубокой спячки (Doze Mode).
 * Энергопотребление практически нулевое (< 1% за всю ночь).
 */
@Singleton
class SilentAudioPlayer @Inject constructor() {

    @Volatile
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null

    @Synchronized
    fun start() {
        if (isPlaying) return
        isPlaying = true

        try {
            val sampleRate = 8000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track

            val silence = ByteArray(bufferSize) // Все нули — абсолютная тишина
            val thread = Thread({
                while (isPlaying && audioTrack == track) {
                    try {
                        track.write(silence, 0, silence.size)
                        Thread.sleep(800)
                    } catch (e: Exception) {
                        break
                    }
                }
            }, "AlarmSimple-SilentAudioTrack")
            thread.isDaemon = true
            thread.start()
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
