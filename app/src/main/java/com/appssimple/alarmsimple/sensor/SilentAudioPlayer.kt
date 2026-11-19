package com.appssimple.alarmsimple.sensor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ранее воспроизводил бесшумный PCM-поток (абсолютная тишина) через AudioTrack для удержания
 * процесса и аудиоподсистемы в активном состоянии.
 * Непрерывное воспроизведение AudioTrack удалено для устранения ночного расхода батареи.
 * Методы [start] и [stop] сохранены как no-op с флагом состояния [isSilentPlaying]
 * для полной обратной совместимости с существующим кодом.
 */
@Singleton
class SilentAudioPlayer @Inject constructor() {

    @Volatile
    private var isPlaying = false

    val isSilentPlaying: Boolean
        get() = isPlaying

    @Synchronized
    fun start() {
        if (isPlaying) return
        isPlaying = true
    }

    @Synchronized
    fun stop() {
        isPlaying = false
    }
}
