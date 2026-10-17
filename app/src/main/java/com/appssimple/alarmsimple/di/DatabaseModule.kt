package com.appssimple.alarmsimple.di

import android.content.Context
import androidx.room.Room
import com.appssimple.alarmsimple.data.db.AlarmDatabase
import com.appssimple.alarmsimple.data.db.dao.AlarmDao
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
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AlarmDatabase {
        return Room.databaseBuilder(
            context,
            AlarmDatabase::class.java,
            "alarm_simple.db"
        ).build()
    }
    
    @Provides
    fun provideAlarmDao(db: AlarmDatabase): AlarmDao = db.alarmDao()
    
    @Provides
    fun provideSleepSessionDao(db: AlarmDatabase): SleepSessionDao = db.sleepSessionDao()
    
    @Provides
    fun provideSleepEpochDao(db: AlarmDatabase): SleepEpochDao = db.sleepEpochDao()
}
