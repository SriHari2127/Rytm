package com.rytm.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rytm.app.data.entity.CompletionLog
import com.rytm.app.data.entity.CompletionStatus
import com.rytm.app.data.entity.Habit
import com.rytm.app.data.entity.HabitWithReminders
import com.rytm.app.data.entity.Reminder
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HabitListItem(
    val habitWithReminders: HabitWithReminders,
    val isCompletedToday: Boolean
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val habitsWithReminders: StateFlow<List<HabitListItem>> = combine(
        repository.getHabitsWithReminders(),
        repository.getAllLogs()
    ) { habits, logs ->
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        habits.map { hwr ->
            val isCompleted = logs.any { 
                it.habitId == hwr.habit.id && 
                it.status == CompletionStatus.COMPLETED &&
                it.completedAt >= todayStart
            }
            HabitListItem(hwr, isCompleted)
        }.sortedWith(
            compareBy<HabitListItem> { it.isCompletedToday }
                .thenBy { it.habitWithReminders.reminders.minOfOrNull { r -> r.toMinutesOfDay() } ?: Int.MAX_VALUE }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveHabitWithReminders(habit: Habit, reminders: List<Reminder>) {
        viewModelScope.launch {
            val habitId = if (habit.id == 0L) {
                repository.insertHabit(habit)
            } else {
                repository.updateHabit(habit)
                repository.getRemindersForHabitOnce(habit.id).forEach {
                    alarmScheduler.cancelReminder(it.id)
                }
                habit.id
            }
            repository.replaceRemindersForHabit(habitId, reminders)
            val savedHabit = repository.getHabitWithReminders(habitId) ?: return@launch
            savedHabit.reminders.forEach { reminder ->
                alarmScheduler.scheduleReminder(savedHabit.habit, reminder)
            }
        }
    }

    fun deleteHabit(habitWithReminders: HabitWithReminders) {
        viewModelScope.launch {
            habitWithReminders.reminders.forEach {
                alarmScheduler.cancelReminder(it.id)
            }
            repository.deleteHabit(habitWithReminders.habit)
        }
    }

    fun logManualCompletion(habitId: Long) {
        viewModelScope.launch {
            repository.logCompletion(
                CompletionLog(
                    habitId = habitId,
                    reminderId = 0L,
                    status = CompletionStatus.COMPLETED,
                    scheduledAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleHabitActive(habitWithReminders: HabitWithReminders) {
        viewModelScope.launch {
            val newState = !habitWithReminders.habit.isActive
            repository.setHabitActive(habitWithReminders.habit.id, newState)
            if (newState) {
                habitWithReminders.reminders.forEach {
                    alarmScheduler.scheduleReminder(habitWithReminders.habit.copy(isActive = true), it)
                }
            } else {
                habitWithReminders.reminders.forEach {
                    alarmScheduler.cancelReminder(it.id)
                }
            }
        }
    }
}
