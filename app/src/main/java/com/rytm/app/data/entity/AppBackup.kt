package com.rytm.app.data.entity

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a full application backup.
 * Using nullable lists and @SerializedName to handle potential missing fields 
 * from older backup versions gracefully during JSON deserialization.
 */
data class AppBackup(
    @SerializedName("habits")
    val habits: List<Habit>? = emptyList(),
    
    @SerializedName("reminders")
    val reminders: List<Reminder>? = emptyList(),
    
    @SerializedName("completionLogs")
    val completionLogs: List<CompletionLog>? = emptyList(),
    
    @SerializedName("waterReminders")
    val waterReminders: List<WaterReminder>? = emptyList(),
    
    @SerializedName("waterLogs")
    val waterLogs: List<WaterLog>? = emptyList(),
    
    @SerializedName("waterReminderLogs")
    val waterReminderLogs: List<WaterReminderLog>? = emptyList(),
    
    @SerializedName("settings")
    val settings: List<AppSettings>? = emptyList()
)
