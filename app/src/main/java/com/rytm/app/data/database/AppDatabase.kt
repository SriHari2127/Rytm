package com.rytm.app.data.database

import androidx.room.*
import com.rytm.app.data.dao.AppSettingsDao
import com.rytm.app.data.dao.CompletionLogDao
import com.rytm.app.data.dao.HabitDao
import com.rytm.app.data.dao.ReminderDao
import com.rytm.app.data.dao.WaterLogDao
import com.rytm.app.data.dao.WaterReminderDao
import com.rytm.app.data.entity.*

class Converters {
    @TypeConverter
    fun fromStatus(value: CompletionStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): CompletionStatus = CompletionStatus.valueOf(value)
}

@Database(
    entities = [Habit::class, Reminder::class, CompletionLog::class, WaterReminder::class, WaterLog::class, AppSettings::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun waterReminderDao(): WaterReminderDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun appSettingsDao(): AppSettingsDao

    @Transaction
    suspend fun restoreFromBackup(backup: AppBackup) {
        clearAllTables() // Native Room method to safely wipe everything
        
        habitDao().insertHabits(backup.habits)
        reminderDao().insertReminders(backup.reminders)
        completionLogDao().insertLogs(backup.completionLogs)
        waterReminderDao().insertReminders(backup.waterReminders)
        waterLogDao().insertLogs(backup.waterLogs)
        appSettingsDao().insertSettings(backup.settings)
    }
}

