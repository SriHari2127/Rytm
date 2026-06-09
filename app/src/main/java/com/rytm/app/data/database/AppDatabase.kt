package com.rytm.app.data.database

import androidx.room.*
import com.rytm.app.data.dao.AppSettingsDao
import com.rytm.app.data.dao.CompletionLogDao
import com.rytm.app.data.dao.HabitDao
import com.rytm.app.data.dao.ReminderDao
import com.rytm.app.data.dao.WaterLogDao
import com.rytm.app.data.dao.WaterReminderDao
import com.rytm.app.data.dao.WaterReminderLogDao
import com.rytm.app.data.entity.*

class Converters {
    @TypeConverter
    fun fromStatus(value: CompletionStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): CompletionStatus = CompletionStatus.valueOf(value)
}

@Database(
    entities = [Habit::class, Reminder::class, CompletionLog::class, WaterReminder::class, WaterLog::class, AppSettings::class, WaterReminderLog::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun waterReminderDao(): WaterReminderDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun waterReminderLogDao(): WaterReminderLogDao

    @Transaction
    suspend fun restoreFromBackup(backup: AppBackup) {
        clearAllTables() // Native Room method to safely wipe everything
        
        habitDao().insertHabits(backup.habits ?: emptyList())
        reminderDao().insertReminders(backup.reminders ?: emptyList())
        completionLogDao().insertLogs(backup.completionLogs ?: emptyList())
        waterReminderDao().insertReminders(backup.waterReminders ?: emptyList())
        waterLogDao().insertLogs(backup.waterLogs ?: emptyList())
        waterReminderLogDao().insertLogs(backup.waterReminderLogs ?: emptyList())
        appSettingsDao().insertSettings(backup.settings ?: emptyList())
    }
}

