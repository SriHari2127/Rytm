package com.rytm.app.ui.habits

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rytm.app.data.entity.HabitWithReminders
import com.rytm.app.databinding.ItemHabitBinding
import com.rytm.app.viewmodel.HabitListItem

class HabitsAdapter(
    private val onEditClick: (HabitWithReminders) -> Unit,
    private val onDeleteClick: (HabitWithReminders) -> Unit,
    private val onToggleActive: (HabitWithReminders) -> Unit,
    private val onMarkDoneClick: (HabitWithReminders) -> Unit
) : ListAdapter<HabitListItem, HabitsAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemHabitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HabitListItem) {
            val hwr = item.habitWithReminders
            val habit = hwr.habit
            binding.tvHabitEmoji.text = habit.iconEmoji
            binding.tvHabitName.text = habit.name
            
            // Strike-through if completed
            if (item.isCompletedToday) {
                binding.tvHabitName.paintFlags = binding.tvHabitName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.root.alpha = 0.6f
            } else {
                binding.tvHabitName.paintFlags = binding.tvHabitName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.root.alpha = 1.0f
            }

            // Fix flickering: clear listener before setting state, and only set if different
            binding.switchActive.setOnCheckedChangeListener(null)
            if (binding.switchActive.isChecked != habit.isActive) {
                binding.switchActive.isChecked = habit.isActive
            }

            val times = hwr.reminders.sortedBy { it.toMinutesOfDay() }
                .joinToString("  •  ") { it.toDisplayTime() }
            binding.tvReminderTimes.text = if (times.isEmpty()) "No reminders set" else times

            binding.btnMarkDone.visibility = if (item.isCompletedToday) android.view.View.GONE else android.view.View.VISIBLE
            binding.btnMarkDone.setOnClickListener { onMarkDoneClick(hwr) }

            binding.btnEdit.setOnClickListener { onEditClick(hwr) }
            binding.btnDelete.setOnClickListener { onDeleteClick(hwr) }
            
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != habit.isActive) {
                    onToggleActive(hwr)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<HabitListItem>() {
        override fun areItemsTheSame(a: HabitListItem, b: HabitListItem) =
            a.habitWithReminders.habit.id == b.habitWithReminders.habit.id
        override fun areContentsTheSame(a: HabitListItem, b: HabitListItem) = a == b
    }
}

