package com.rytm.app.repository

import com.rytm.app.data.dao.*
import com.rytm.app.data.database.AppDatabase
import com.rytm.app.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val db: AppDatabase,
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao,
    private val waterReminderDao: WaterReminderDao,
    private val waterLogDao: WaterLogDao,
    private val completionLogDao: CompletionLogDao,
    private val appSettingsDao: AppSettingsDao
) {

    // --- Habits -----------------------------------------------------------------

    fun getAllActiveHabits(): Flow<List<Habit>> = habitDao.getAllActiveHabits()

    fun getHabitsWithReminders(): Flow<List<HabitWithReminders>> =
        habitDao.getHabitsWithReminders()

    suspend fun getHabitsWithRemindersOnce(): List<HabitWithReminders> =
        habitDao.getHabitsWithRemindersOnce()

    suspend fun getHabitWithReminders(habitId: Long): HabitWithReminders? =
        habitDao.getHabitWithReminders(habitId)

    suspend fun insertHabit(habit: Habit): Long = habitDao.insertHabit(habit)

    suspend fun updateHabit(habit: Habit) = habitDao.updateHabit(habit)

    suspend fun deleteHabit(habit: Habit) = habitDao.deleteHabit(habit)

    suspend fun setHabitActive(habitId: Long, isActive: Boolean) =
        habitDao.setHabitActive(habitId, isActive)

    suspend fun updateReminderLastScheduledAt(reminderId: Long, timestamp: Long) =
        reminderDao.updateLastScheduledAt(reminderId, timestamp)

    // --- Reminders --------------------------------------------------------------

    fun getRemindersForHabit(habitId: Long): Flow<List<Reminder>> =
        reminderDao.getRemindersForHabit(habitId)

    suspend fun getRemindersForHabitOnce(habitId: Long): List<Reminder> =
        reminderDao.getRemindersForHabitOnce(habitId)

    suspend fun getAllActiveReminders(): List<Reminder> =
        reminderDao.getAllActiveReminders()

    suspend fun insertReminder(reminder: Reminder): Long =
        reminderDao.insertReminder(reminder)

    suspend fun deleteReminder(reminder: Reminder) =
        reminderDao.deleteReminder(reminder)

    suspend fun deleteRemindersForHabit(habitId: Long) =
        reminderDao.deleteRemindersForHabit(habitId)

    suspend fun replaceRemindersForHabit(habitId: Long, reminders: List<Reminder>) {
        reminderDao.replaceRemindersForHabit(habitId, reminders)
    }

    // --- Water Reminders --------------------------------------------------------

    fun getAllWaterReminders(): Flow<List<WaterReminder>> = waterReminderDao.getAllReminders()

    suspend fun getAllWaterRemindersOnce(): List<WaterReminder> = waterReminderDao.getAllRemindersOnce()

    suspend fun insertWaterReminder(reminder: WaterReminder): Long = waterReminderDao.insertReminder(reminder)

    suspend fun updateWaterReminder(reminder: WaterReminder) = waterReminderDao.updateReminder(reminder)

    suspend fun deleteWaterReminder(reminder: WaterReminder) = waterReminderDao.deleteReminder(reminder)

    suspend fun getWaterReminderById(id: Long): WaterReminder? = waterReminderDao.getReminderById(id)

    suspend fun updateWaterReminderLastScheduledAt(reminderId: Long, timestamp: Long) =
        waterReminderDao.updateLastScheduledAt(reminderId, timestamp)

    // --- Water Logs -------------------------------------------------------------

    fun getWaterLogForDate(date: String): Flow<WaterLog?> = waterLogDao.getLogForDate(date)

    suspend fun updateWaterCount(date: String, count: Int) = waterLogDao.updateCount(date, count)

    suspend fun updateWaterGoal(date: String, goal: Int) = waterLogDao.updateGoal(date, goal)

    suspend fun incrementWaterCount(date: String) {
        waterLogDao.incrementWaterCount(date)
    }

    suspend fun ensureWaterLogExists(date: String) {
        val existing = waterLogDao.getLogForDateOnce(date)
        if (existing == null) {
            val lastGoal = waterLogDao.getLastGoal() ?: 8
            waterLogDao.insertIfMissing(WaterLog(date = date, goal = lastGoal, count = 0))
        }
    }

    // --- Completion Logs --------------------------------------------------------

    suspend fun logCompletion(log: CompletionLog) = completionLogDao.insertLog(log)

    fun getLogsForHabit(habitId: Long): Flow<List<CompletionLog>> =
        completionLogDao.getLogsForHabit(habitId)

    fun getAllLogs(): Flow<List<CompletionLog>> = completionLogDao.getAllLogs()

    fun getLogsInRange(startMs: Long, endMs: Long): Flow<List<CompletionLog>> =
        completionLogDao.getLogsInRange(startMs, endMs)

    suspend fun getLogsForHabitInRange(
        habitId: Long, startMs: Long, endMs: Long
    ): List<CompletionLog> =
        completionLogDao.getLogsForHabitInRange(habitId, startMs, endMs)

    suspend fun getTotalCompletedCount(habitId: Long): Int =
        completionLogDao.getTotalCompletedCount(habitId)

    suspend fun getAllLogsFrom(startMs: Long): List<CompletionLog> =
        completionLogDao.getAllLogsFrom(startMs)

    suspend fun getMissedHabits(): List<HabitWithReminders> {
        val now = System.currentTimeMillis()
        val habits = habitDao.getHabitsWithRemindersOnce()
        val missed = mutableListOf<HabitWithReminders>()

        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (hwr in habits) {
            if (!hwr.habit.isActive) continue
            
            // Check if any reminder for today was missed
            for (reminder in hwr.reminders) {
                if (!reminder.isActive) continue
                
                val scheduledTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
                    set(java.util.Calendar.MINUTE, reminder.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                // If scheduled time is in the past today
                if (scheduledTime < now) {
                    // Check if there is a log for this specific window
                    val logs = completionLogDao.getLogsForHabitInRange(hwr.habit.id, todayStart, now)
                    val wasHandled = logs.any { 
                        // Simple check: was there ANY log today for this habit?
                        // For simplicity, if they have multiple reminders, we might need more precision,
                        // but usually "missed your routine" is a general nudge.
                        it.reminderId == reminder.id || (it.reminderId == 0L && it.completedAt >= todayStart)
                    }
                    
                    if (!wasHandled) {
                        missed.add(hwr)
                        break // Found one missed reminder for this habit today
                    }
                }
            }
        }
        return missed
    }

    // --- App Settings -----------------------------------------------------------

    companion object {
        const val KEY_WATER_REMINDERS_ENABLED = "water_reminders_enabled"
    }

    suspend fun getSettingOnce(key: String): String? = appSettingsDao.getSettingOnce(key)?.value

    fun getSetting(key: String): Flow<AppSettings?> = appSettingsDao.getSetting(key)

    suspend fun saveSetting(key: String, value: String) {
        appSettingsDao.saveSetting(AppSettings(key, value))
    }

    suspend fun isWaterRemindersEnabledOnce(): Boolean =
        getSettingOnce(KEY_WATER_REMINDERS_ENABLED)?.toBoolean() ?: true

    // --- Backup & Restore -------------------------------------------------------

    suspend fun getEntireBackup(): AppBackup {
        return AppBackup(
            habits = habitDao.getAllHabitsOnce(),
            reminders = reminderDao.getAllRemindersOnce(),
            completionLogs = completionLogDao.getAllLogsOnce(),
            waterReminders = waterReminderDao.getAllRemindersOnce(),
            waterLogs = waterLogDao.getAllLogsOnce(),
            settings = appSettingsDao.getAllSettingsOnce()
        )
    }

    suspend fun restoreFromBackup(backup: AppBackup) {
        db.restoreFromBackup(backup)
    }
}
