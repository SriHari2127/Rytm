package com.rytm.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CompletionStatus { COMPLETED, MISSED, SKIPPED, SNOOZED }

@Entity(
    tableName = "completion_logs",
    foreignKeys = [ForeignKey(
        entity = Habit::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId")]
)
data class CompletionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val reminderId: Long,
    val status: CompletionStatus,
    val completedAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long   // the original alarm time
)

