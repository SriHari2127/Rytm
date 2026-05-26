package com.rytm.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val count: Int = 0,
    val goal: Int = 8
) {
    companion object {
        fun getCurrentDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}

