package com.rytm.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconEmoji: String = "⚡",
    val colorHex: String = "#6200EE",
    val alarmSoundUri: String = "",      // Empty = default ringtone
    val isActive: Boolean = true,
    val repeatDays: String = "1,2,3,4,5,6,7", // 1=Mon..7=Sun, comma-separated
    val createdAt: Long = System.currentTimeMillis()
)

