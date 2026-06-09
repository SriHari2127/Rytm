package com.rytm.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rytm.app.data.entity.WaterLog
import com.rytm.app.data.entity.WaterReminder
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val waterLog: StateFlow<WaterLog?> = repository.getWaterLogForDate(WaterLog.getCurrentDate())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val reminders: StateFlow<List<WaterReminder>> = repository.getAllWaterReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _waterRemindersEnabled = MutableStateFlow(value = true)
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
            val success = repository.addWaterWithLimit(WaterLog.getCurrentDate(), 250) // Default 250ml for quick add
            if (!success) {
                _events.emit("Hydration target already reached!")
            }
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
