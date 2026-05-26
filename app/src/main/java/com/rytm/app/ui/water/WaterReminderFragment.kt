package com.rytm.app.ui.water

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rytm.app.R
import com.rytm.app.data.entity.WaterReminder
import com.rytm.app.databinding.FragmentWaterReminderBinding
import com.rytm.app.viewmodel.WaterViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class WaterReminderFragment : Fragment() {

    private var _binding: FragmentWaterReminderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WaterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnChangeGoal.setOnClickListener { showSetGoalDialog() }
        binding.btnAddWater.setOnClickListener { viewModel.addWater() }
        binding.btnAddReminder.setOnClickListener { showTimePicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.waterLog.collect { log ->
                        log?.let {
                            updateUi(it.count, it.goal)
                        }
                    }
                }
                launch {
                    viewModel.reminders.collect { list ->
                        refreshReminderChips(list)
                    }
                }
            }
        }
    }

    private fun showTimePicker(existing: WaterReminder? = null) {
        val hour = existing?.hour ?: 8
        val minute = existing?.minute ?: 0
        TimePickerDialog(requireContext(), { _, h, m ->
            showAmountDialog(existing, h, m)
        }, hour, minute, false).show()
    }

    private fun showAmountDialog(existing: WaterReminder?, hour: Int, minute: Int) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.amountMl?.toString() ?: "250")
            setSelection(text.length)
        }

        val container = FrameLayout(requireContext()).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 48
                marginEnd = 48
                topMargin = 16
                bottomMargin = 16
            }
            addView(input, params)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Water Amount")
            .setMessage("How many ml should you drink at this time?")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val amount = input.text.toString().toIntOrNull() ?: 250
                if (existing != null) {
                    viewModel.updateReminder(existing.copy(hour = hour, minute = minute, amountMl = amount))
                } else {
                    viewModel.addReminder(hour, minute, amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshReminderChips(reminders: List<WaterReminder>) {
        binding.chipGroupReminders.removeAllViews()
        reminders.sortedBy { it.hour * 60 + it.minute }.forEach { reminder ->
            val chip = Chip(requireContext()).apply {
                text = "${reminder.toDisplayTime()} • ${reminder.amountMl}ml"
                isCloseIconVisible = true
                setOnClickListener { showTimePicker(reminder) }
                setOnCloseIconClickListener {
                    viewModel.deleteReminder(reminder)
                }
            }
            binding.chipGroupReminders.addView(chip)
        }
    }

    private fun updateUi(count: Int, goal: Int) {
        // Trigger celebration ONLY if we just reached the goal
        val previousCountString = binding.tvWaterCount.text.toString()
        val previousCount = previousCountString.toIntOrNull() ?: 0
        
        binding.tvWaterCount.text = count.toString()
        binding.tvWaterGoal.text = "$goal glasses"
        binding.progressWater.max = goal
        binding.progressWater.progress = count

        if (count == goal && previousCount < goal) {
            triggerCelebration()
            Toast.makeText(requireContext(), "Goal reached! Amazing job!", Toast.LENGTH_SHORT).show()
        } else if (count == goal && previousCount == goal) {
            // Already reached, handled in ViewModel addWater to prevent over-counting
        }
    }

    private fun triggerCelebration() {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xb48def, 0xf4306d),
            position = Position.Relative(0.5, 0.3),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100)
        )
        binding.konfettiView.start(party)
    }

    private fun showSetGoalDialog() {
        val currentGoal = viewModel.waterLog.value?.goal ?: 8
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentGoal.toString())
            setSelection(text.length)
        }
        
        val container = FrameLayout(requireContext()).apply {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 48
                marginEnd = 48
                topMargin = 16
                bottomMargin = 16
            }
            addView(input, params)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Daily Goal")
            .setMessage("How many glasses of water do you want to drink today?")
            .setView(container)
            .setPositiveButton("Set") { _, _ ->
                val newGoal = input.text.toString().toIntOrNull() ?: currentGoal
                viewModel.setGoal(newGoal)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

