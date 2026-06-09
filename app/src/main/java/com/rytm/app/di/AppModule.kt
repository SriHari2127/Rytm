package com.rytm.app.di

import android.content.Context
import androidx.room.Room
import com.rytm.app.data.dao.*
import com.rytm.app.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rytm_database",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideCompletionLogDao(db: AppDatabase): CompletionLogDao = db.completionLogDao()

    @Provides
    fun provideWaterReminderDao(db: AppDatabase): WaterReminderDao = db.waterReminderDao()

    @Provides
    fun provideWaterLogDao(db: AppDatabase): WaterLogDao = db.waterLogDao()

    @Provides
    fun provideAppSettingsDao(db: AppDatabase): AppSettingsDao = db.appSettingsDao()

    @Provides
    fun provideWaterReminderLogDao(db: AppDatabase): WaterReminderLogDao = db.waterReminderLogDao()
}

