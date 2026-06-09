package com.rytm.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: HabitRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    private val _doNotShowOverlay = MutableStateFlow(value = false)
    val doNotShowOverlay: StateFlow<Boolean> = _doNotShowOverlay

    init {
        viewModelScope.launch {
            _doNotShowOverlay.value = repository.getSettingOnce(KEY_DO_NOT_SHOW_OVERLAY)?.toBoolean() ?: false
        }
    }

    fun setDoNotShowOverlay(value: Boolean) {
        viewModelScope.launch {
            _doNotShowOverlay.value = value
            repository.saveSetting(KEY_DO_NOT_SHOW_OVERLAY, value.toString())
        }
    }

    fun rescheduleAlarmsIfNeeded() {
        viewModelScope.launch {
            alarmScheduler.rescheduleAllAlarms(repository)
            
            // Audit on manual launch too
            val missed = repository.getMissedHabits()
            if (missed.isNotEmpty()) {
                alarmScheduler.postMissedRoutineNotification(missed)
            }
            
            val missedWater = repository.getMissedWaterReminders()
            if (missedWater.isNotEmpty()) {
                alarmScheduler.postMissedWaterSummaryNotification(missedWater)
            }
        }
    }

    companion object {
        private const val KEY_DO_NOT_SHOW_OVERLAY = "donot_show_overlay_dialog"
    }
}
