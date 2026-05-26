package com.rytm.app.data.dao

import androidx.room.*
import com.rytm.app.data.entity.WaterReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: WaterReminder): Long

    @Update
    suspend fun updateReminder(reminder: WaterReminder)

    @Delete
    suspend fun deleteReminder(reminder: WaterReminder)

    @Query("SELECT * FROM water_reminders ORDER BY hour, minute")
    fun getAllReminders(): Flow<List<WaterReminder>>

    @Query("SELECT * FROM water_reminders")
    suspend fun getAllRemindersOnce(): List<WaterReminder>

    @Query("SELECT * FROM water_reminders WHERE isActive = 1")
    suspend fun getAllActiveReminders(): List<WaterReminder>

    @Query("SELECT * FROM water_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): WaterReminder?

    @Query("UPDATE water_reminders SET lastScheduledAt = :timestamp WHERE id = :reminderId")
    suspend fun updateLastScheduledAt(reminderId: Long, timestamp: Long)
}

