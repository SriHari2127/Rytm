package com.rytm.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.rytm.app.data.entity.AppBackup
import com.rytm.app.data.entity.CompletionLog
import com.rytm.app.data.entity.CompletionStatus
import com.rytm.app.data.entity.Habit
import com.rytm.app.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class HabitStats(
    val habit: Habit,
    val totalCompleted: Int,
    val weeklyRate: Float,   // 0.0 - 1.0
    val currentStreak: Int,
    val longestStreak: Int
)

data class RecentActivityEntry(
    val habitName: String,
    val habitEmoji: String,
    val status: CompletionStatus,
    val timestamp: Long
)

data class AnalyticsState(
    val habitStats: List<HabitStats> = emptyList(),
    val recentLogs: List<RecentActivityEntry> = emptyList(),
    val weeklyCompletionByDay: Map<Int, Int> = emptyMap(), // dayOfWeek -> count
    val overallWeeklyRate: Float = 0f,
    val completionsThisMonth: Int = 0,
    val completionsLastMonth: Int = 0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val state: StateFlow<AnalyticsState> = combine(
        repository.getAllHabits(),
        repository.getAllLogs()
    ) { habits, allHistory ->
        calculateAnalytics(habits, allHistory)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    private fun calculateAnalytics(habits: List<Habit>, allHistory: List<CompletionLog>): AnalyticsState {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000

        val recentLogs = allHistory
            .filter { it.status == CompletionStatus.COMPLETED || it.status == CompletionStatus.MISSED }
            .sortedByDescending { it.completedAt }
            .take(50)
            .map { log ->
                val habit = habits.find { it.id == log.habitId }
                RecentActivityEntry(
                    habitName = habit?.name ?: "Unknown Habit",
                    habitEmoji = habit?.iconEmoji ?: "❓",
                    status = log.status,
                    timestamp = log.completedAt
                )
            }

        // Monthly Summary
        val thisMonthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastMonthCount = allHistory.count {
            it.status == CompletionStatus.COMPLETED &&
                    it.completedAt >= lastMonthStart &&
                    it.completedAt < thisMonthStart
        }
        val thisMonthCountFull = allHistory.count { it.status == CompletionStatus.COMPLETED && it.completedAt >= thisMonthStart }

        // Weekly by day
        val weeklyMap = mutableMapOf<Int, Int>()
        allHistory.filter {
            it.status == CompletionStatus.COMPLETED && it.completedAt >= sevenDaysAgo
        }.forEach { log ->
            val cal = Calendar.getInstance().apply { timeInMillis = log.completedAt }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            weeklyMap[day] = (weeklyMap[day] ?: 0) + 1
        }

        // Per-habit stats - ONLY ACTIVE HABITS
        val habitStats = habits.filter { it.isActive }.map { habit ->
            val logsForHabit = allHistory.filter { it.habitId == habit.id && it.completedAt >= thirtyDaysAgo }
            val completed = logsForHabit.count { it.status == CompletionStatus.COMPLETED }
            val weeklyLogs = logsForHabit.filter { it.completedAt >= sevenDaysAgo }
            val weeklyRate = if (weeklyLogs.isEmpty()) 0f
            else weeklyLogs.count { it.status == CompletionStatus.COMPLETED }.toFloat() / 7f

            val streak = calculateStreak(allHistory.filter { it.habitId == habit.id })
            val longest = calculateLongestStreak(allHistory.filter { it.habitId == habit.id })

            HabitStats(habit, completed, weeklyRate.coerceIn(0f, 1f), streak, longest)
        }

        val overallRate = if (habitStats.isEmpty()) 0f
        else habitStats.map { it.weeklyRate }.average().toFloat()

        return AnalyticsState(
            habitStats = habitStats,
            recentLogs = recentLogs,
            weeklyCompletionByDay = weeklyMap,
            overallWeeklyRate = overallRate,
            completionsThisMonth = thisMonthCountFull,
            completionsLastMonth = lastMonthCount
        )
    }

    suspend fun getBackupJson(): String {
        val backup = repository.getEntireBackup()
        return Gson().toJson(backup)
    }

    fun restoreFromJson(json: String) {
        viewModelScope.launch {
            try {
                val backup = Gson().fromJson(json, AppBackup::class.java)
                if (backup != null) {
                    withContext(Dispatchers.IO) {
                        repository.restoreFromBackup(backup)
                    }
                    _events.emit("Data restored successfully!")
                } else {
                    _events.emit("Error: Invalid backup file.")
                }
            } catch (e: Exception) {
                _events.emit("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun calculateStreak(logs: List<CompletionLog>): Int {
        if (logs.isEmpty()) return 0
        val completed = logs.filter { it.status == CompletionStatus.COMPLETED }
            .sortedByDescending { it.completedAt }
        if (completed.isEmpty()) return 0

        var streak = 0
        var expectedDay = Calendar.getInstance()
        expectedDay.set(Calendar.HOUR_OF_DAY, 0)
        expectedDay.set(Calendar.MINUTE, 0)
        expectedDay.set(Calendar.SECOND, 0)
        expectedDay.set(Calendar.MILLISECOND, 0)

        val days = completed.map {
            val c = Calendar.getInstance().apply { timeInMillis = it.completedAt }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }.distinct().sortedDescending()

        for (day in days) {
            if (day >= expectedDay.timeInMillis) {
                streak++
                expectedDay.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }

    private fun calculateLongestStreak(logs: List<CompletionLog>): Int {
        val days = logs.filter { it.status == CompletionStatus.COMPLETED }.map {
            val c = Calendar.getInstance().apply { timeInMillis = it.completedAt }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }.distinct().sorted()

        if (days.isEmpty()) return 0
        var longest = 1; var current = 1
        val dayMs = 24 * 60 * 60 * 1000L
        for (i in 1 until days.size) {
            current = if (days[i] - days[i - 1] == dayMs) current + 1 else 1
            if (current > longest) longest = current
        }
        return longest
    }
}
