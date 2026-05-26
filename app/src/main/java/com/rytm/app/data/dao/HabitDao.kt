package com.rytm.app.data.dao

import androidx.room.*
import com.rytm.app.data.entity.Habit
import com.rytm.app.data.entity.HabitWithReminders
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getAllHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: Long): Habit?

    @Transaction
    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun getHabitsWithReminders(): Flow<List<HabitWithReminders>>

    @Transaction
    @Query("SELECT * FROM habits")
    suspend fun getHabitsWithRemindersOnce(): List<HabitWithReminders>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitWithReminders(habitId: Long): HabitWithReminders?

    @Query("UPDATE habits SET isActive = :isActive WHERE id = :habitId")
    suspend fun setHabitActive(habitId: Long, isActive: Boolean)
}

