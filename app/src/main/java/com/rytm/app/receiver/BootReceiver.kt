package com.rytm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rytm.app.repository.HabitRepository
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: HabitRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if ((action != Intent.ACTION_BOOT_COMPLETED) &&
            (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) &&
            (action != "android.intent.action.QUICKBOOT_POWERON")) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                alarmScheduler.rescheduleAllAlarms(repository)
                
                // After rescheduling, check for missed routines
                val missed = repository.getMissedHabits()
                if (missed.isNotEmpty()) {
                    alarmScheduler.postMissedRoutineNotification(missed)
                }

                val missedWater = repository.getMissedWaterReminders()
                if (missedWater.isNotEmpty()) {
                    alarmScheduler.postMissedWaterSummaryNotification(missedWater)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
