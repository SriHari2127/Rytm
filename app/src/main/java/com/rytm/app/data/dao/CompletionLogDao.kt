package com.rytm.app.data.dao

import androidx.room.*
import com.rytm.app.data.entity.CompletionLog
import com.rytm.app.data.entity.CompletionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CompletionLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<CompletionLog>)

    @Query("SELECT * FROM completion_logs WHERE habitId = :habitId ORDER BY completedAt DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<CompletionLog>>

    @Query("SELECT * FROM completion_logs WHERE habitId = :habitId ORDER BY completedAt DESC")
    suspend fun getLogsForHabitOnce(habitId: Long): List<CompletionLog>

    @Query("""
        SELECT * FROM completion_logs 
        WHERE completedAt >= :startMs AND completedAt <= :endMs 
        ORDER BY completedAt DESC
    """)
    fun getLogsInRange(startMs: Long, endMs: Long): Flow<List<CompletionLog>>

    @Query("""
        SELECT * FROM completion_logs 
        WHERE habitId = :habitId AND completedAt >= :startMs AND completedAt <= :endMs 
        ORDER BY completedAt DESC
    """)
    suspend fun getLogsForHabitInRange(habitId: Long, startMs: Long, endMs: Long): List<CompletionLog>

    @Query("""
        SELECT COUNT(*) FROM completion_logs 
        WHERE habitId = :habitId AND status = 'COMPLETED'
    """)
    suspend fun getTotalCompletedCount(habitId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM completion_logs 
        WHERE habitId = :habitId 
        AND status = 'COMPLETED' 
        AND completedAt >= :startMs AND completedAt <= :endMs
    """)
    suspend fun getCompletedCountInRange(habitId: Long, startMs: Long, endMs: Long): Int

    @Query("SELECT * FROM completion_logs ORDER BY completedAt DESC")
    fun getAllLogs(): Flow<List<CompletionLog>>

    @Query("SELECT * FROM completion_logs ORDER BY completedAt DESC")
    suspend fun getAllLogsOnce(): List<CompletionLog>

    @Query("""
        SELECT * FROM completion_logs 
        WHERE completedAt >= :startMs 
        ORDER BY completedAt DESC
    """)
    suspend fun getAllLogsFrom(startMs: Long): List<CompletionLog>
}

