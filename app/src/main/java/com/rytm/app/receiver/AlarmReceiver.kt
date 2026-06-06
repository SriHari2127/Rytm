package com.rytm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.rytm.app.service.AlarmService
import com.rytm.app.utils.AlarmScheduler

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        
        Log.d("RytmAlarm", "Receiver fired: type=$type, habitId=$habitId, reminderId=$reminderId")

        // For Water reminders, habitId will be -1L, but reminderId should be present
        if (type == AlarmScheduler.TYPE_HABIT && habitId == -1L) {
            Log.e("RytmAlarm", "Receiver: Missing habitId for habit alarm, ignoring")
            return
        }
        
        if (reminderId == -1L) {
            Log.e("RytmAlarm", "Receiver: Missing reminderId, ignoring")
            return
        }

        // Acquire a temporary wakelock to ensure the service starts reliably
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rytm:ReceiverWakeLock")
        wakeLock.acquire(10000) // 10 seconds is plenty to start the service

        // Start foreground alarm service — plays ringtone, shows full-screen activity
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtras(intent)
        }
        context.startForegroundService(serviceIntent)
    }
}

