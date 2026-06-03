package com.rytm.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rytm.app.data.entity.WaterLog
import com.rytm.app.data.entity.WaterReminder
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val waterLog: StateFlow<WaterLog?> = repository.getWaterLogForDate(WaterLog.getCurrentDate())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val reminders: StateFlow<List<WaterReminder>> = repository.getAllWaterReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _waterRemindersEnabled = MutableStateFlow(true)
    val waterRemindersEnabled = _waterRemindersEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureWaterLogExists(WaterLog.getCurrentDate())
            _waterRemindersEnabled.value = repository.isWaterRemindersEnabledOnce()
        }
    }

    fun toggleWaterReminders(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting(HabitRepository.KEY_WATER_REMINDERS_ENABLED, enabled.toString())
            _waterRemindersEnabled.value = enabled
            
            if (enabled) {
                // Reschedule all active reminders
                repository.getAllWaterRemindersOnce().forEach { reminder ->
                    if (reminder.isActive) {
                        alarmScheduler.scheduleWaterReminder(reminder)
                    }
                }
            } else {
                // Cancel all water alarms
                repository.getAllWaterRemindersOnce().forEach { reminder ->
                    alarmScheduler.cancelWaterReminder(reminder.id)
                }
            }
        }
    }

    fun addWater() {
        viewModelScope.launch {
            repository.incrementWaterCount(WaterLog.getCurrentDate())
        }
    }

    fun setGoal(goal: Int) {
        viewModelScope.launch {
            repository.updateWaterGoal(WaterLog.getCurrentDate(), goal)
        }
    }

    fun addReminder(hour: Int, minute: Int, amountMl: Int) {
        viewModelScope.launch {
            val id = repository.insertWaterReminder(
                WaterReminder(hour = hour, minute = minute, amountMl = amountMl)
            )
            if (_waterRemindersEnabled.value) {
                alarmScheduler.scheduleWaterReminder(
                    WaterReminder(id = id, hour = hour, minute = minute, amountMl = amountMl)
                )
            }
        }
    }

    fun updateReminder(reminder: WaterReminder) {
        viewModelScope.launch {
            repository.updateWaterReminder(reminder)
            if (_waterRemindersEnabled.value) {
                alarmScheduler.scheduleWaterReminder(reminder)
            }
        }
    }

    fun deleteReminder(reminder: WaterReminder) {
        viewModelScope.launch {
            repository.deleteWaterReminder(reminder)
            alarmScheduler.cancelWaterReminder(reminder.id)
        }
    }
}
