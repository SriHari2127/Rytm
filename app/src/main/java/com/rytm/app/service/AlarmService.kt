package com.rytm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.rytm.app.R
import com.rytm.app.repository.HabitRepository
import com.rytm.app.ui.alarm.AlarmRingActivity
import com.rytm.app.ui.alarm.WaterRingActivity
import com.rytm.app.utils.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var repository: HabitRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.d("RytmAlarm", "Service: Null intent, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // Clean up any existing alarm state before starting a new one
        cleanupServiceState()
        acquireWakeLock()

        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        val habitName = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_NAME) ?: "Habit"
        val habitEmoji = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_EMOJI) ?: "⚡"
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        val soundUri = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND_URI) ?: ""
        val scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        
        Log.d("RytmAlarm", "Service started: type=$type, reminderId=$reminderId")

        createNotificationChannels()
        val notification = buildNotification(type, habitName, habitEmoji, habitId, reminderId, scheduledTime)
        
        // Use a unique notification ID for each reminder to prevent collisions
        val notificationId = if (reminderId != -1L) (reminderId.toInt() % 10000) + 1000 else FOREGROUND_ID
        startForeground(notificationId, notification)

        if (type == AlarmScheduler.TYPE_HABIT) {
            playAlarmSound(soundUri)
            startVibration()
        } else if (type == AlarmScheduler.TYPE_WATER) {
            startVibration()
            scheduleTimeout(reminderId)
        }
        
        launchRingActivity(type, habitId, habitName, habitEmoji, reminderId, scheduledTime, intent)

        return START_NOT_STICKY
    }

    private fun launchRingActivity(
        type: String, habitId: Long, habitName: String, habitEmoji: String,
        reminderId: Long, scheduledTime: Long, incomingIntent: Intent?
    ) {
        val targetActivity = if (type == AlarmScheduler.TYPE_WATER) {
            WaterRingActivity::class.java
        } else {
            AlarmRingActivity::class.java
        }

        val ringIntent = Intent(this, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_TYPE, type)
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(AlarmScheduler.EXTRA_HABIT_NAME, habitName)
            putExtra(AlarmScheduler.EXTRA_HABIT_EMOJI, habitEmoji)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
            
            if (type == AlarmScheduler.TYPE_WATER) {
                val amount = incomingIntent?.getIntExtra(AlarmScheduler.EXTRA_WATER_AMOUNT, 250) ?: 250
                putExtra(AlarmScheduler.EXTRA_WATER_AMOUNT, amount)
            }
        }
        startActivity(ringIntent)
    }

    private fun playAlarmSound(soundUriString: String) {
        try {
            val uri: Uri = if (soundUriString.isNotEmpty()) {
                soundUriString.toUri()
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(
        type: String, habitName: String, habitEmoji: String,
        habitId: Long, reminderId: Long, scheduledTime: Long
    ): Notification {
        val ringIntent = Intent(this, if (type == AlarmScheduler.TYPE_WATER) WaterRingActivity::class.java else AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmScheduler.EXTRA_TYPE, type)
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(AlarmScheduler.EXTRA_HABIT_NAME, habitName)
            putExtra(AlarmScheduler.EXTRA_HABIT_EMOJI, habitEmoji)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val pi = PendingIntent.getActivity(
            this, habitId.toInt() + (if (type == AlarmScheduler.TYPE_WATER) AlarmScheduler.WATER_ID_OFFSET else 0), ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (type == AlarmScheduler.TYPE_WATER) {
            AlarmScheduler.WATER_NOTIFICATION_CHANNEL_ID
        } else {
            AlarmScheduler.NOTIFICATION_CHANNEL_ID
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$habitEmoji Time for $habitName")
            .setContentText(null)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .apply {
                if (type == AlarmScheduler.TYPE_HABIT) {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                } else {
                    setSound(null)
                }
            }
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Habit Channel (High importance, with sound)
        val habitChannel = NotificationChannel(
            AlarmScheduler.NOTIFICATION_CHANNEL_ID,
            "Rytm Habit Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Habit reminder alarms"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(habitChannel)

        // Water Channel (High importance, but SILENT)
        val waterChannel = NotificationChannel(
            AlarmScheduler.WATER_NOTIFICATION_CHANNEL_ID,
            "Rytm Water Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Silent water reminders"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setBypassDnd(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(waterChannel)
    }

    private fun scheduleTimeout(reminderId: Long) {
        timeoutRunnable = Runnable {
            CoroutineScope(Dispatchers.IO).launch {
                alarmScheduler.rescheduleWaterForNextDay(repository, reminderId)
            }
            stopAlarm()
        }
        handler.postDelayed(timeoutRunnable!!, 60000L) // 1 minute timeout
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Rytm:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    private fun cleanupServiceState() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    fun stopAlarm() {
        cleanupServiceState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("RytmAlarm", "Service destroyed")
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    companion object {
        const val FOREGROUND_ID = 1001
    }
}
