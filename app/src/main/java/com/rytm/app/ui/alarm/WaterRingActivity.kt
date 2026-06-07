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
import com.rytm.app.data.entity.WaterLog
import com.rytm.app.databinding.ActivityWaterRingBinding
import com.rytm.app.repository.HabitRepository
import com.rytm.app.service.AlarmService
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class WaterRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterRingBinding

    @Inject lateinit var repository: HabitRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private var reminderId: Long = -1L
    private var amountMl: Int = 250
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable { stopAlarmAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupWindowFlags()
        setFinishOnTouchOutside(false)
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        
        Log.d("RytmAlarm", "WaterRingActivity: onCreate $reminderId")
        
        binding = ActivityWaterRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {
        }

        setupUI()
        loadWaterStats()
        startAnimations()
        handler.postDelayed(timeoutRunnable, 30000L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("RytmAlarm", "WaterRingActivity: onNewIntent")
        handleIntent(intent)
        
        setupUI()
        loadWaterStats()
        
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 30000L)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            reminderId = it.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
            amountMl = it.getIntExtra(AlarmScheduler.EXTRA_WATER_AMOUNT, 250)
        }
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
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
    }

    private fun extractIntentData() {
        handleIntent(intent)
    }

    private fun setupUI() {
        binding.tvWaterAmountSub.text = "Goal: Drink $amountMl ml"

        binding.btnCompleteCard.setOnClickListener {
            animatePress(it) { logWaterAndFinish() }
        }
    }

    private fun startAnimations() {
        val fluidBreath = AnimationUtils.loadAnimation(this, R.anim.water_fluid_breath)
        binding.ivWaterRing.startAnimation(fluidBreath)
        binding.ivWaterIcon.startAnimation(fluidBreath)

        val rotate = AnimationUtils.loadAnimation(this, R.anim.premium_rotate)
        binding.ivWaterRingBg.startAnimation(rotate)

        val rotateSlow = AnimationUtils.loadAnimation(this, R.anim.premium_rotate_slow)
        binding.ivWaterRingOuter.startAnimation(rotateSlow)

        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        binding.motivationContainer.startAnimation(slideIn)
        binding.waterStatsCard.startAnimation(slideIn)
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

    private fun loadWaterStats() {
        lifecycleScope.launch {
            try {
                val today = WaterLog.getCurrentDate()
                repository.ensureWaterLogExists(today)

                val activeReminders = repository.getAllWaterRemindersOnce().filter { it.isActive }
                val trueTargetMl = activeReminders.sumOf { it.amountMl }.coerceAtLeast(2000)

                repository.getWaterLogForDate(today).collect { log ->
                    val actualLog = log ?: WaterLog(today, 0, 8)
                    
                    val currentMl = actualLog.totalMl
                    val remainingMl = (trueTargetMl - currentMl).coerceAtLeast(0)
                    val percent = if (trueTargetMl > 0) ((currentMl * 100) / trueTargetMl) else 0

                    withContext(Dispatchers.Main) {
                        binding.tvWaterStatRemaining.text = "${remainingMl}ml"
                        binding.tvWaterStatGlasses.text = "${actualLog.count} done"
                        binding.tvWaterStatPercent.text = "$percent%"
                        binding.progressRing.progress = percent
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun logWaterAndFinish() {
        val today = WaterLog.getCurrentDate()
        lifecycleScope.launch(Dispatchers.IO) {
            repository.ensureWaterLogExists(today)
            repository.addWater(today, amountMl)
            if (reminderId != -1L) {
                alarmScheduler.rescheduleWaterForNextDay(repository, reminderId)
            }
            
            delay(800)
            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }

    private fun stopAlarmAndFinish() {
        handler.removeCallbacks(timeoutRunnable)
        if (reminderId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                alarmScheduler.rescheduleWaterForNextDay(repository, reminderId)
            }
        }
        val stopIntent = Intent(this, AlarmService::class.java)
        stopService(stopIntent)
        finish()
    }

    override fun onDestroy() {
        Log.d("RytmAlarm", "WaterRingActivity: onDestroy")
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
    }
}
