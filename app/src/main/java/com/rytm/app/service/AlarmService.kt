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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.rytm.app.R
import com.rytm.app.ui.alarm.AlarmRingActivity
import com.rytm.app.utils.AlarmScheduler

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: AlarmScheduler.TYPE_HABIT
        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        val habitName = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_NAME) ?: "Habit"
        val habitEmoji = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_EMOJI) ?: "⚡"
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        val soundUri = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND_URI) ?: ""
        val scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())

        createNotificationChannel()
        val notification = buildNotification(type, habitName, habitEmoji, habitId, reminderId, scheduledTime)
        startForeground(FOREGROUND_ID, notification)

        if (type == AlarmScheduler.TYPE_HABIT) {
            playAlarmSound(soundUri)
            startVibration()
        }
        
        launchRingActivity(type, habitId, habitName, habitEmoji, reminderId, scheduledTime)

        return START_NOT_STICKY
    }

    private fun launchRingActivity(
        type: String, habitId: Long, habitName: String, habitEmoji: String,
        reminderId: Long, scheduledTime: Long
    ) {
        val ringIntent = Intent(this, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmScheduler.EXTRA_TYPE, type)
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(AlarmScheduler.EXTRA_HABIT_NAME, habitName)
            putExtra(AlarmScheduler.EXTRA_HABIT_EMOJI, habitEmoji)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        startActivity(ringIntent)
    }

    private fun playAlarmSound(soundUriString: String) {
        try {
            val uri: Uri = if (soundUriString.isNotEmpty()) {
                Uri.parse(soundUriString)
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
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
        val ringIntent = Intent(this, AlarmRingActivity::class.java).apply {
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

        return NotificationCompat.Builder(this, AlarmScheduler.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$habitEmoji Time for: $habitName")
            .setContentText("Tap to open reminder")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AlarmScheduler.NOTIFICATION_CHANNEL_ID,
            "Rytm Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Habit reminder alarms"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setBypassDnd(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    companion object {
        const val FOREGROUND_ID = 1001
        const val ACTION_STOP = "com.rytm.app.STOP_ALARM"
    }
}

