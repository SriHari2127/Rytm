package com.rytm.app.data.entity

data class AppBackup(
    val habits: List<Habit>,
    val reminders: List<Reminder>,
    val completionLogs: List<CompletionLog>,
    val waterReminders: List<WaterReminder>,
    val waterLogs: List<WaterLog>,
    val waterReminderLogs: List<WaterReminderLog>,
    val settings: List<AppSettings>
)
