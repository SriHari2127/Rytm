package com.rytm.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_reminder_logs")
data class WaterReminderLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val status: CompletionStatus,
    val scheduledAt: Long,
    val timestamp: Long = System.currentTimeMillis()
)
