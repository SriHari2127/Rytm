package com.rytm.app.ui.habits

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rytm.app.R
import com.rytm.app.data.entity.Habit
import com.rytm.app.data.entity.Reminder
import com.rytm.app.databinding.FragmentAddEditHabitBinding
import com.rytm.app.viewmodel.AddEditHabitViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddEditHabitFragment : Fragment() {

    private var _binding: FragmentAddEditHabitBinding? = null
    private val binding get() = _binding!!

    private val args: AddEditHabitFragmentArgs by navArgs()

    private val viewModel: AddEditHabitViewModel by viewModels()

    private val reminders = mutableListOf<Reminder>()
    private var selectedSoundUri: String = ""
    private var selectedEmoji: String = "⚡"

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedSoundUri = uri?.toString() ?: ""
            binding.tvSelectedSound.text = if (selectedSoundUri.isEmpty()) "Default Alarm"
            else getRingtoneName(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = if (args.habitId == -1L) "New Habit" else "Edit Habit"
        binding.btnSave.text = if (args.habitId == -1L) "Create Habit" else "Save Changes"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupEmojiPicker()
        setupAddReminderButton()
        setupSoundPicker()
        setupSaveButton()

        if (viewModel.isEditMode) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.habitWithReminders.collect { hwr ->
                        hwr ?: return@collect
                        populateFields(hwr.habit, hwr.reminders)
                    }
                }
            }
        }
    }

    private fun populateFields(habit: Habit, existingReminders: List<Reminder>) {
        binding.etHabitName.setText(habit.name)
        binding.etDescription.setText(habit.description)
        selectedEmoji = habit.iconEmoji
        binding.btnEmoji.text = habit.iconEmoji
        selectedSoundUri = habit.alarmSoundUri
        binding.tvSelectedSound.text = if (selectedSoundUri.isEmpty()) "Default Alarm"
        else getRingtoneName(Uri.parse(selectedSoundUri))

        reminders.clear()
        reminders.addAll(existingReminders)
        refreshReminderChips()
    }

    private fun setupEmojiPicker() {
        val emojis = listOf("⚡","💪","📚","🏃","🧘","💊","💧","🥗","😴","✍️","🎯","🎸","🧠","❤️","🌅")
        binding.btnEmoji.text = selectedEmoji
        binding.btnEmoji.setOnClickListener {
            val idx = emojis.indexOf(selectedEmoji)
            selectedEmoji = emojis[(idx + 1) % emojis.size]
            binding.btnEmoji.text = selectedEmoji
        }
    }

    private fun setupAddReminderButton() {
        binding.btnAddReminder.setOnClickListener { showTimePicker() }
    }

    private fun showTimePicker(existing: Reminder? = null) {
        val hour = existing?.hour ?: 8
        val minute = existing?.minute ?: 0
        TimePickerDialog(requireContext(), { _, h, m ->
            if (existing != null) {
                val idx = reminders.indexOfFirst { it === existing }
                if (idx >= 0) reminders[idx] = existing.copy(hour = h, minute = m)
            } else {
                reminders.add(Reminder(habitId = 0, hour = h, minute = m))
            }
            refreshReminderChips()
        }, hour, minute, false).show()
    }

    private fun refreshReminderChips() {
        binding.chipGroupReminders.removeAllViews()
        reminders.sortedBy { it.hour * 60 + it.minute }.forEach { reminder ->
            val chip = Chip(requireContext()).apply {
                text = reminder.toDisplayTime()
                isCloseIconVisible = true
                setOnClickListener { showTimePicker(reminder) }
                setOnCloseIconClickListener {
                    reminders.remove(reminder)
                    refreshReminderChips()
                }
            }
            binding.chipGroupReminders.addView(chip)
        }
    }

    private fun setupSoundPicker() {
        binding.btnPickSound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                if (selectedSoundUri.isNotEmpty()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedSoundUri))
                }
            }
            ringtonePickerLauncher.launch(intent)
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val name = binding.etHabitName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etHabitName.error = "Habit name is required"
                return@setOnClickListener
            }
            if (reminders.isEmpty()) {
                Toast.makeText(requireContext(), "Add at least one reminder time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val habitId = if (viewModel.isEditMode) args.habitId else 0L
            val habit = Habit(
                id = habitId,
                name = name,
                description = binding.etDescription.text.toString().trim(),
                iconEmoji = selectedEmoji,
                alarmSoundUri = selectedSoundUri
            )
            
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnSave.isEnabled = false
                viewModel.saveHabitWithReminders(habit, reminders.toList())
                Toast.makeText(requireContext(), "Habit saved!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun getRingtoneName(uri: Uri?): String {
        if (uri == null) return "Default Alarm"
        return try {
            val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
            ringtone?.getTitle(requireContext()) ?: "Custom Sound"
        } catch (e: Exception) {
            "Custom Sound"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

