package com.appssimple.alarmsimple.domain.model

import java.time.Instant

/**
 * A single 30-second epoch of classified sleep data.
 */
data class SleepEpoch(
    val timestamp: Instant,
    val phase: SleepPhase,
    val movementIntensity: Float,
    val audioEvent: AudioClass? = null
)
