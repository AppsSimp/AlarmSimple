package com.appssimple.alarmsimple.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.appssimple.alarmsimple.data.db.AlarmDatabase
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
import com.appssimple.alarmsimple.data.db.dao.AudioSnippetDao
import com.appssimple.alarmsimple.data.db.dao.SleepEpochDao
import com.appssimple.alarmsimple.data.db.dao.SleepSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Allows sleep sessions without an alarm (manual tracking). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sleep_sessions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `alarmId` INTEGER,
                    `trackingStartMs` INTEGER NOT NULL,
                    `sleepOnsetMs` INTEGER,
                    `wakeUpMs` INTEGER,
                    `disturbanceCount` INTEGER NOT NULL,
                    `snoreMinutes` INTEGER NOT NULL,
                    FOREIGN KEY(`alarmId`) REFERENCES `alarms`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `sleep_sessions_new`
                    (`id`, `alarmId`, `trackingStartMs`, `sleepOnsetMs`, `wakeUpMs`, `disturbanceCount`, `snoreMinutes`)
                SELECT `id`, `alarmId`, `trackingStartMs`, `sleepOnsetMs`, `wakeUpMs`, `disturbanceCount`, `snoreMinutes`
                FROM `sleep_sessions`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `sleep_sessions`")
            db.execSQL("ALTER TABLE `sleep_sessions_new` RENAME TO `sleep_sessions`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_alarmId` ON `sleep_sessions` (`alarmId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AlarmDatabase {
        return Room.databaseBuilder(
            context,
            AlarmDatabase::class.java,
            "alarm_simple.db"
        ).addMigrations(MIGRATION_1_2)
        .build()
    }
    
    @Provides
    fun provideAlarmDao(db: AlarmDatabase): AlarmDao = db.alarmDao()
    
    @Provides
    fun provideSleepSessionDao(db: AlarmDatabase): SleepSessionDao = db.sleepSessionDao()
    
    @Provides
    fun provideSleepEpochDao(db: AlarmDatabase): SleepEpochDao = db.sleepEpochDao()

    @Provides
    fun provideAudioSnippetDao(db: AlarmDatabase): AudioSnippetDao = db.audioSnippetDao()
}
