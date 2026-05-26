package com.rytm.app.data.dao

import androidx.room.*
import com.rytm.app.data.entity.WaterLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterLogDao {

    @Query("SELECT * FROM water_logs WHERE date = :date")
    fun getLogForDate(date: String): Flow<WaterLog?>

    @Query("SELECT * FROM water_logs WHERE date = :date")
    suspend fun getLogForDateOnce(date: String): WaterLog?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(log: WaterLog): Long

    @Query("UPDATE water_logs SET count = :count WHERE date = :date")
    suspend fun updateCount(date: String, count: Int)

    @Query("UPDATE water_logs SET goal = :goal WHERE date = :date")
    suspend fun updateGoal(date: String, goal: Int)

    @Query("SELECT goal FROM water_logs ORDER BY date DESC LIMIT 1")
    suspend fun getLastGoal(): Int?

    @Transaction
    suspend fun incrementWaterCount(date: String) {
        val log = getLogForDateOnce(date) ?: return
        if (log.count < log.goal) {
            updateCount(date, log.count + 1)
        }
    }
}

