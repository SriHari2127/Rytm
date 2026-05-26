package com.rytm.app.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rytm.app.data.entity.CompletionLog
import com.rytm.app.data.entity.CompletionStatus
import com.rytm.app.data.entity.WaterLog
import com.rytm.app.repository.HabitRepository
import com.rytm.app.databinding.ActivityAlarmRingBinding
import com.rytm.app.service.AlarmService
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding

    @Inject lateinit var repository: HabitRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private var type: String = AlarmScheduler.TYPE_HABIT
    private var habitId: Long = -1L
    private var reminderId: Long = -1L
    private var scheduledTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure it shows over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        var habitName = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_NAME) ?: "Habit"
        val habitEmoji = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_EMOJI) ?: "⚡"

        if (type == AlarmScheduler.TYPE_WATER) {
            val amount = intent.getIntExtra(AlarmScheduler.EXTRA_WATER_AMOUNT, 250)
            habitName = "$habitName ($amount ml)"
            binding.btnSnooze.visibility = View.GONE
            binding.btnSkip.text = "Close"
        }

        binding.tvAlarmEmoji.text = habitEmoji
        binding.tvAlarmHabitName.text = habitName

        binding.btnComplete.setOnClickListener {
            if (type == AlarmScheduler.TYPE_HABIT) {
                logAndDismiss(CompletionStatus.COMPLETED)
            } else {
                logWaterAndFinish()
            }
        }

        binding.btnSnooze.setOnClickListener {
            logAndDismiss(CompletionStatus.SNOOZED)
            scheduleSnooze()
        }

        binding.btnSkip.setOnClickListener {
            logAndDismiss(CompletionStatus.SKIPPED)
        }
    }

    private fun logAndDismiss(status: CompletionStatus) {
        if (habitId == -1L || reminderId == -1L) {
            stopAlarmAndFinish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.logCompletion(
                CompletionLog(
                    habitId = habitId,
                    reminderId = reminderId,
                    status = status,
                    scheduledAt = scheduledTime
                )
            )
            // Reschedule for tomorrow
            if (status != CompletionStatus.SNOOZED) {
                val hwr = repository.getHabitWithReminders(habitId)
                hwr?.reminders?.find { it.id == reminderId }?.let { reminder ->
                    hwr.habit.let { habit ->
                        alarmScheduler.rescheduleForNextDay(habit, reminder)
                    }
                }
            }
        }
        stopAlarmAndFinish()
    }

    private fun logWaterAndFinish() {
        val today = WaterLog.getCurrentDate()
        CoroutineScope(Dispatchers.IO).launch {
            repository.ensureWaterLogExists(today)
            repository.incrementWaterCount(today)
            stopAlarmAndFinish()
        }
    }

    private fun scheduleSnooze() {
        CoroutineScope(Dispatchers.IO).launch {
            val hwr = repository.getHabitWithReminders(habitId) ?: return@launch
            val reminder = hwr.reminders.find { it.id == reminderId } ?: return@launch
            // Snooze = reschedule 10 minutes from now
            val snoozeReminder = reminder.copy(
                hour = ((System.currentTimeMillis() + 10 * 60 * 1000) / 60000 % 1440 / 60).toInt(),
                minute = ((System.currentTimeMillis() + 10 * 60 * 1000) / 60000 % 60).toInt()
            )
            alarmScheduler.scheduleReminder(hwr.habit, snoozeReminder)
        }
    }

    private fun stopAlarmAndFinish() {
        val stopIntent = Intent(this, AlarmService::class.java)
        stopService(stopIntent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent back press from dismissing without action
    }
}

