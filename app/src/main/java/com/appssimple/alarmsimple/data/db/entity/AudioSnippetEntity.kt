package com.appssimple.alarmsimple.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted audio snippet recorded when interesting nocturnal sounds occur.
 */
@Entity(
    tableName = "audio_snippets",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class AudioSnippetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val type: String, // String representation of AudioSnippetType
    val durationSeconds: Int,
    val filePath: String,
    val peakAmplitude: Float,
    val label: String? = null
)
