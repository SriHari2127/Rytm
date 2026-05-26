package com.rytm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rytm.app.service.AlarmService
import com.rytm.app.utils.AlarmScheduler

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        
        if (habitId == -1L && reminderId == -1L) return

        // Start foreground alarm service — plays ringtone, shows full-screen activity
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtras(intent)
        }
        context.startForegroundService(serviceIntent)
    }
}

