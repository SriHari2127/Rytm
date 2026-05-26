package com.rytm.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rytm.app.data.entity.Habit
import com.rytm.app.data.entity.HabitWithReminders
import com.rytm.app.data.entity.Reminder
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val alarmScheduler: AlarmScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val habitId: Long = savedStateHandle["habitId"] ?: -1L
    val isEditMode = habitId != -1L

    private val _habitWithReminders = MutableStateFlow<HabitWithReminders?>(null)
    val habitWithReminders: StateFlow<HabitWithReminders?> = _habitWithReminders

    init {
        if (isEditMode) {
            viewModelScope.launch {
                _habitWithReminders.value = repository.getHabitWithReminders(habitId)
            }
        }
    }

    suspend fun saveHabitWithReminders(habit: Habit, reminders: List<Reminder>) {
        val id = if (isEditMode) {
            repository.updateHabit(habit)
            // Cancel old alarms
            repository.getRemindersForHabitOnce(habit.id).forEach {
                alarmScheduler.cancelReminder(it.id)
            }
            habit.id
        } else {
            repository.insertHabit(habit)
        }

        repository.replaceRemindersForHabit(id, reminders)

        // Schedule new alarms
        val savedHabit = repository.getHabitWithReminders(id) ?: return
        savedHabit.reminders.forEach { reminder ->
            alarmScheduler.scheduleReminder(savedHabit.habit, reminder)
        }
    }
}
