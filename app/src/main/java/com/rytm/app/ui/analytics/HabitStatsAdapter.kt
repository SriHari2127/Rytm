package com.rytm.app.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rytm.app.databinding.ItemHabitStatsBinding
import com.rytm.app.viewmodel.HabitStats

class HabitStatsAdapter : ListAdapter<HabitStats, HabitStatsAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemHabitStatsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HabitStats) {
            binding.tvStatEmoji.text = item.habit.iconEmoji
            binding.tvStatName.text = item.habit.name
            binding.tvStatCompleted.text = "${item.totalCompleted} total done"
            binding.tvStatStreak.text = "🔥 ${item.currentStreak} day streak"
            binding.tvStatLongest.text = "Best: ${item.longestStreak} days"
            val pct = (item.weeklyRate * 100).toInt()
            binding.progressWeekly.progress = pct
            binding.tvWeeklyPct.text = "$pct% this week"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHabitStatsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<HabitStats>() {
        override fun areItemsTheSame(a: HabitStats, b: HabitStats) = a.habit.id == b.habit.id
        override fun areContentsTheSame(a: HabitStats, b: HabitStats) = a == b
    }
}

