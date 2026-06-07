package com.rytm.app.ui.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rytm.app.R
import com.rytm.app.data.entity.*
import com.rytm.app.repository.HabitRepository
import com.rytm.app.databinding.ActivityAlarmRingBinding
import com.rytm.app.service.AlarmService
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.*
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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable { stopAlarmAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupWindowFlags()
        setFinishOnTouchOutside(false)
        super.onCreate(savedInstanceState)
        
        extractIntentData()
        
        Log.d("RytmAlarm", "AlarmRingActivity: onCreate $reminderId")
        
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {
        }

        setupUI()
        loadStats()
        startAnimations()
        handler.postDelayed(timeoutRunnable, 30000L)
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun extractIntentData() {
        type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
    }

    private fun setupUI() {
        var habitName = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_NAME) ?: "Habit"
        val habitDescription = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_DESCRIPTION) ?: ""

        if (type == AlarmScheduler.TYPE_WATER) {
            val amount = intent.getIntExtra(AlarmScheduler.EXTRA_WATER_AMOUNT, 250)
            habitName = "Water Intake ($amount ml)"
            binding.tvMainTitle.text = "Drink Water"
        }

        binding.tvHabitNameSub.text = if (habitDescription.isNotEmpty()) {
            habitDescription
        } else {
            habitName
        }

        binding.btnCompleteCard.setOnClickListener {
            animatePress(it) {
                if (type == AlarmScheduler.TYPE_HABIT) logAndDismiss(CompletionStatus.COMPLETED)
                else logWaterAndFinish()
            }
        }
    }

    private fun startAnimations() {
        val breath = AnimationUtils.loadAnimation(this, R.anim.breath)
        binding.ivNeonRing.startAnimation(breath)
        binding.ivHabitIcon.startAnimation(breath)

        val rotate = AnimationUtils.loadAnimation(this, R.anim.premium_rotate)
        binding.ivNeonRingBg.startAnimation(rotate)

        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        binding.motivationContainer.startAnimation(slideIn)
        binding.streakPill.startAnimation(slideIn)
        binding.progressCard.startAnimation(slideIn)
        binding.actionsContainer.startAnimation(slideIn)
    }

    private fun animatePress(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val weekStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
                val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

                launch {
                    repository.getAllLogs().collect { allLogs ->
                        val todayDone = allLogs.count { it.status == CompletionStatus.COMPLETED && it.completedAt >= todayStart }
                        val totalHabits = repository.getAllActiveHabits().first().size

                        val weeklyCount = allLogs.count { it.status == CompletionStatus.COMPLETED && it.completedAt >= weekStart }
                        val monthlyCount = allLogs.count { it.status == CompletionStatus.COMPLETED && it.completedAt >= monthStart }
                        
                        val habits = repository.getHabitsWithRemindersOnce()
                        var bestStreak = 0
                        habits.forEach { hwr ->
                            val streak = calculateStreakForHabit(allLogs.filter { it.habitId == hwr.habit.id })
                            if (streak > bestStreak) bestStreak = streak
                        }

                        val currentHabitStreak = if (habitId != -1L) {
                            calculateStreakForHabit(allLogs.filter { it.habitId == habitId })
                        } else 0

                        withContext(Dispatchers.Main) {
                            binding.tvStatToday.text = "$todayDone/$totalHabits"
                            binding.tvStatWeekly.text = weeklyCount.toString()
                            binding.tvStatMonthly.text = monthlyCount.toString()
                            binding.tvStatBest.text = bestStreak.toString()
                            binding.tvStreakCount.text = "$currentHabitStreak day streak"
                            
                            if (type != AlarmScheduler.TYPE_WATER) {
                                val habitProgress = if (totalHabits > 0) (todayDone * 100) / totalHabits else 0
                                binding.progressRing.progress = habitProgress
                            }
                        }
                    }
                }

                launch {
                    val activeReminders = repository.getAllWaterRemindersOnce().filter { it.isActive }
                    val trueTargetMl = activeReminders.sumOf { it.amountMl }.coerceAtLeast(2000)

                    repository.getWaterLogForDate(WaterLog.getCurrentDate()).collect { waterLog ->
                        val waterProgress = if (waterLog != null && trueTargetMl > 0) {
                            (waterLog.totalMl * 100) / trueTargetMl
                        } else 0

                        withContext(Dispatchers.Main) {
                            if (type == AlarmScheduler.TYPE_WATER) {
                                binding.progressRing.progress = waterProgress
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateStreakForHabit(logs: List<CompletionLog>): Int {
        val completed = logs.filter { it.status == CompletionStatus.COMPLETED }.sortedByDescending { it.completedAt }
        if (completed.isEmpty()) return 0
        var streak = 0
        val expected = Calendar.getInstance().apply { 
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = completed.map { 
            val c = Calendar.getInstance().apply { timeInMillis = it.completedAt }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }.distinct().sortedDescending()
        for (day in days) {
            if (day >= expected.timeInMillis) { streak++; expected.add(Calendar.DAY_OF_YEAR, -1) }
            else break
        }
        return streak
    }

    private fun logAndDismiss(status: CompletionStatus) {
        if ((habitId == -1L) || (reminderId == -1L)) {
            stopAlarmAndFinish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.logCompletion(
                CompletionLog(
                    habitId = habitId,
                    reminderId = reminderId,
                    status = status,
                    scheduledAt = scheduledTime,
                )
            )
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

    private fun stopAlarmAndFinish() {
        handler.removeCallbacks(timeoutRunnable)
        val stopIntent = Intent(this, AlarmService::class.java)
        stopService(stopIntent)
        finish()
    }

    override fun onDestroy() {
        Log.d("RytmAlarm", "AlarmRingActivity: onDestroy")
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
    }
}
