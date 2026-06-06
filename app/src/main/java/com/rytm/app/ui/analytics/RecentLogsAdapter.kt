package com.rytm.app.ui.analytics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rytm.app.data.entity.CompletionStatus
import com.rytm.app.databinding.ItemLogBinding
import com.rytm.app.viewmodel.RecentActivityEntry
import java.text.SimpleDateFormat
import java.util.*

class RecentLogsAdapter : ListAdapter<RecentActivityEntry, RecentLogsAdapter.ViewHolder>(DiffCallback()) {

    private val sdf = SimpleDateFormat("EEE, MMM d  hh:mm a", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: RecentActivityEntry) {
            binding.tvLogTime.text = sdf.format(Date(entry.timestamp))
            val habitText = "${entry.habitEmoji} ${entry.habitName}"
            
            when (entry.status) {
                CompletionStatus.COMPLETED -> {
                    binding.tvLogStatus.text = "✅ Completed: $habitText"
                    binding.tvLogStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                CompletionStatus.MISSED -> {
                    binding.tvLogStatus.text = "⚠️ Missed: $habitText"
                    binding.tvLogStatus.setTextColor(Color.parseColor("#EF4444"))
                }
                else -> {
                    binding.tvLogStatus.text = habitText
                    binding.tvLogStatus.setTextColor(Color.GRAY)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<RecentActivityEntry>() {
        override fun areItemsTheSame(a: RecentActivityEntry, b: RecentActivityEntry) = 
            a.timestamp == b.timestamp && a.habitName == b.habitName
        override fun areContentsTheSame(a: RecentActivityEntry, b: RecentActivityEntry) = a == b
    }
}

