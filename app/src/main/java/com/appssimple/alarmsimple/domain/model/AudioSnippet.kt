package com.appssimple.alarmsimple.domain.model

import java.time.Instant

/**
 * Domain representation of an interesting audio snippet recorded during sleep.
 */
data class AudioSnippet(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Instant,
    val type: AudioSnippetType,
    val durationSeconds: Int,
    val filePath: String,
    val peakAmplitude: Float,
    val label: String? = null
)
