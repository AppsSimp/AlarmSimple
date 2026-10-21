package com.appssimple.alarmsimple.data.db.dao

import androidx.room.*
import com.appssimple.alarmsimple.data.db.entity.SavedQuestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQuestDao {
    @Query("SELECT * FROM saved_quests ORDER BY createdAt DESC")
    fun getAllSavedQuests(): Flow<List<SavedQuestEntity>>

    @Query("SELECT * FROM saved_quests WHERE type = :type ORDER BY createdAt DESC")
    fun getSavedQuestsByType(type: String): Flow<List<SavedQuestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuest(quest: SavedQuestEntity): Long

    @Delete
    suspend fun deleteQuest(quest: SavedQuestEntity)
}
