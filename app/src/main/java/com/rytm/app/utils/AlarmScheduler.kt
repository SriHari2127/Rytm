package com.rytm.app.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rytm.app.R
import com.rytm.app.data.entity.Habit
import com.rytm.app.data.entity.HabitWithReminders
import com.rytm.app.data.entity.Reminder
import com.rytm.app.data.entity.WaterReminder
import com.rytm.app.receiver.AlarmReceiver
import com.rytm.app.ui.MainActivity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun scheduleReminder(habit: Habit, reminder: Reminder) {
        if (!habit.isActive || !reminder.isActive) return

        val triggerTime = nextAlarmTime(reminder)
        
        val intent = buildAlarmIntent(habit, reminder, triggerTime)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d("RytmAlarm", "Scheduled habit alarm (inexact fallback): ${habit.name} at $triggerTime")
        } else {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("RytmAlarm", "Scheduled habit alarm (exact): ${habit.name} at $triggerTime")
        }
    }

    fun cancelReminder(reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("RytmAlarm", "Cancelled habit alarm: $reminderId")
    }

    fun scheduleWaterReminder(reminder: WaterReminder) {
        if (!reminder.isActive) return

        val triggerTime = nextWaterAlarmTime(reminder)
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_WATER)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_HABIT_NAME, "Water Intake")
            putExtra(EXTRA_HABIT_EMOJI, "💧")
            putExtra(EXTRA_WATER_AMOUNT, reminder.amountMl)
            putExtra(EXTRA_SCHEDULED_TIME, triggerTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() + WATER_ID_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d("RytmAlarm", "Scheduled water alarm (inexact fallback): ${reminder.id} at $triggerTime")
        } else {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("RytmAlarm", "Scheduled water alarm (exact): ${reminder.id} at $triggerTime")
        }
    }

    fun cancelWaterReminder(reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt() + WATER_ID_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleForNextDay(habit: Habit, reminder: Reminder) {
        scheduleReminder(habit, reminder)
    }

    suspend fun rescheduleWaterForNextDay(repository: com.rytm.app.repository.HabitRepository, reminderId: Long) {
        repository.getWaterReminderById(reminderId)?.let { reminder ->
            val triggerTime = nextWaterAlarmTime(reminder)
            scheduleWaterReminder(reminder)
            repository.updateWaterReminderLastScheduledAt(reminderId, triggerTime)
        }
    }

    suspend fun rescheduleAllAlarms(repository: com.rytm.app.repository.HabitRepository) {
        repository.getHabitsWithRemindersOnce().forEach { hwr ->
            hwr.reminders.forEach { reminder ->
                val triggerTime = nextAlarmTime(reminder)
                if (reminder.lastScheduledAt != triggerTime) {
                    scheduleReminder(hwr.habit, reminder)
                    repository.updateReminderLastScheduledAt(reminder.id, triggerTime)
                }
            }
        }
        if (repository.isWaterRemindersEnabledOnce()) {
            repository.getAllWaterRemindersOnce().forEach { waterReminder ->
                val triggerTime = nextWaterAlarmTime(waterReminder)
                if (waterReminder.lastScheduledAt != triggerTime) {
                    scheduleWaterReminder(waterReminder)
                    repository.updateWaterReminderLastScheduledAt(waterReminder.id, triggerTime)
                }
            }
        }
    }

    fun postMissedRoutineNotification(missedHabits: List<HabitWithReminders>) {
        if (missedHabits.isEmpty()) return

        val names = missedHabits.joinToString(", ") { it.habit.name }
        val title = "📅 You missed your routine"
        val message = if (missedHabits.size == 1) {
            "You missed: ${missedHabits[0].habit.name}. Keep your streak alive by completing it now!"
        } else {
            "You missed $names. Stay on track and complete them now!"
        }

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, MISSED_NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Rytm Alarms",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notificationManager.notify(MISSED_NOTIF_ID, notification)
    }

    fun postMissedWaterNotification(reminder: WaterReminder) {
        val title = "💧 Missed Water Reminder"
        val message = "You missed your ${reminder.toDisplayTime()} reminder to drink ${reminder.amountMl}ml of water."

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, reminder.id.toInt() + WATER_ID_OFFSET + 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WATER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notificationManager.notify(reminder.id.toInt() + WATER_ID_OFFSET + 100, notification)
    }

    fun postMissedHabitNotification(habitName: String, reminderId: Long) {
        val title = "📅 Missed Routine"
        val message = "You missed your reminder for $habitName. Keep your streak alive by completing it now!"

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, reminderId.toInt() + 500, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        notificationManager.notify(reminderId.toInt() + 500, notification)
    }

    private fun buildAlarmIntent(habit: Habit, reminder: Reminder, triggerTime: Long): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_HABIT)
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_HABIT_NAME, habit.name)
            putExtra(EXTRA_HABIT_EMOJI, habit.iconEmoji)
            putExtra(EXTRA_HABIT_DESCRIPTION, habit.description)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_ALARM_SOUND_URI, habit.alarmSoundUri)
            putExtra(EXTRA_SCHEDULED_TIME, triggerTime)
        }
    }

    private fun nextAlarmTime(reminder: Reminder): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun nextWaterAlarmTime(reminder: WaterReminder): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val TYPE_HABIT = "habit"
        const val TYPE_WATER = "water"
        const val WATER_ID_OFFSET = 100000

        const val EXTRA_WATER_AMOUNT = "water_amount"

        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_HABIT_NAME = "habit_name"
        const val EXTRA_HABIT_EMOJI = "habit_emoji"
        const val EXTRA_HABIT_DESCRIPTION = "habit_description"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ALARM_SOUND_URI = "alarm_sound_uri"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
        const val NOTIFICATION_CHANNEL_ID = "rytm_habit_alarms"
        const val WATER_NOTIFICATION_CHANNEL_ID = "rytm_water_reminders"
        private const val MISSED_NOTIF_ID = 9999
    }
}
