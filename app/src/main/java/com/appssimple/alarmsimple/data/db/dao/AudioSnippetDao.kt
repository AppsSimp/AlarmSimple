package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSnippetDao {
    @Query("SELECT * FROM audio_snippets WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getSnippetsForSession(sessionId: Long): Flow<List<AudioSnippetEntity>>

    @Query("SELECT * FROM audio_snippets WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSnippetsForSessionOnce(sessionId: Long): List<AudioSnippetEntity>

    @Query("SELECT COUNT(*) FROM audio_snippets WHERE sessionId = :sessionId")
    suspend fun getSnippetCountForSession(sessionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: AudioSnippetEntity): Long

    @Delete
    suspend fun deleteSnippet(snippet: AudioSnippetEntity)

    @Query("DELETE FROM audio_snippets WHERE sessionId = :sessionId")
    suspend fun deleteSnippetsForSession(sessionId: Long)
}
