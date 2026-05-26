package com.rytm.app.ui.habits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.rytm.app.R
import com.rytm.app.databinding.FragmentHabitsBinding
import com.rytm.app.viewmodel.HabitListItem
import com.rytm.app.viewmodel.HabitsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HabitsFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HabitsViewModel by viewModels()
    private lateinit var adapter: HabitsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HabitsAdapter(
            onEditClick = { hwr ->
                val action = HabitsFragmentDirections.actionHabitsToAddEditHabit(hwr.habit.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { hwr ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Habit")
                    .setMessage("Delete \"${hwr.habit.name}\"? All history will be lost.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteHabit(hwr) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onToggleActive = { hwr -> viewModel.toggleHabitActive(hwr) },
            onMarkDoneClick = { hwr -> viewModel.logManualCompletion(hwr.habit.id) }
        )

        binding.recyclerHabits.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabits.adapter = adapter
        
        // Disable change animation to prevent flickering when toggling switches
        (binding.recyclerHabits.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.fabAddHabit.setOnClickListener {
            val action = HabitsFragmentDirections.actionHabitsToAddEditHabit(-1L)
            findNavController().navigate(action)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.habitsWithReminders.collect { list ->
                    adapter.submitList(list)
                    binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerHabits.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

