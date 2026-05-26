package com.rytm.app.data.dao

import androidx.room.*
import com.rytm.app.data.entity.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<Reminder>)

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE habitId = :habitId ORDER BY hour, minute")
    fun getRemindersForHabit(habitId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE habitId = :habitId ORDER BY hour, minute")
    suspend fun getRemindersForHabitOnce(habitId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    suspend fun getAllActiveReminders(): List<Reminder>

    @Query("DELETE FROM reminders WHERE habitId = :habitId")
    suspend fun deleteRemindersForHabit(habitId: Long)

    @Query("UPDATE reminders SET isActive = :isActive WHERE id = :reminderId")
    suspend fun setReminderActive(reminderId: Long, isActive: Boolean)

    @Query("UPDATE reminders SET lastScheduledAt = :timestamp WHERE id = :reminderId")
    suspend fun updateLastScheduledAt(reminderId: Long, timestamp: Long)

    @Transaction
    suspend fun replaceRemindersForHabit(habitId: Long, reminders: List<Reminder>) {
        deleteRemindersForHabit(habitId)
        insertReminders(reminders.map { it.copy(habitId = habitId) })
    }
}

