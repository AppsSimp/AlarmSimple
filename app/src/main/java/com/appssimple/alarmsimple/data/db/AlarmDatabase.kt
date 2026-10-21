package com.appssimple.alarmsimple.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SavedQuestDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import com.appssimple.alarmsimple.data.db.entity.AlarmEntity
import com.appssimple.alarmsimple.data.db.entity.AudioSnippetEntity
import com.appssimple.alarmsimple.data.db.entity.SavedQuestEntity
import com.appssimple.alarmsimple.data.db.entity.SleepEpochEntity
import com.appssimple.alarmsimple.data.db.entity.SleepSessionEntity

@Database(
    entities = [
        AlarmEntity::class,
        SleepSessionEntity::class,
        SleepEpochEntity::class,
        AudioSnippetEntity::class,
        SavedQuestEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepEpochDao(): SleepEpochDao
    abstract fun audioSnippetDao(): AudioSnippetDao
    abstract fun savedQuestDao(): SavedQuestDao
}
