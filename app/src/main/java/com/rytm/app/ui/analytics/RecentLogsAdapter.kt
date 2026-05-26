package com.rytm.app.ui.analytics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rytm.app.data.entity.CompletionLog
import com.rytm.app.data.entity.CompletionStatus
import com.rytm.app.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.*

class RecentLogsAdapter : ListAdapter<CompletionLog, RecentLogsAdapter.ViewHolder>(DiffCallback()) {

    private val sdf = SimpleDateFormat("EEE, MMM d  hh:mm a", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(log: CompletionLog) {
            binding.tvLogTime.text = sdf.format(Date(log.completedAt))
            when (log.status) {
                CompletionStatus.COMPLETED -> {
                    binding.tvLogStatus.text = "✅ Completed"
                    binding.tvLogStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                CompletionStatus.SKIPPED -> {
                    binding.tvLogStatus.text = "❌ Skipped"
                    binding.tvLogStatus.setTextColor(Color.parseColor("#EF4444"))
                }
                CompletionStatus.SNOOZED -> {
                    binding.tvLogStatus.text = "⏰ Snoozed"
                    binding.tvLogStatus.setTextColor(Color.parseColor("#FF9800"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<CompletionLog>() {
        override fun areItemsTheSame(a: CompletionLog, b: CompletionLog) = a.id == b.id
        override fun areContentsTheSame(a: CompletionLog, b: CompletionLog) = a == b
    }
}

