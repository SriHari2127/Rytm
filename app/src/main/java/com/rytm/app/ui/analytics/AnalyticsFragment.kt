package com.rytm.app.ui.analytics

import android.graphics.Color
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.rytm.app.databinding.FragmentAnalyticsBinding
import com.rytm.app.viewmodel.AnalyticsState
import com.rytm.app.viewmodel.AnalyticsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyticsViewModel by viewModels()
    private lateinit var logsAdapter: RecentLogsAdapter

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { saveBackupToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadBackupFromFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logsAdapter = RecentLogsAdapter()
        binding.recyclerRecentLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentLogs.adapter = logsAdapter

        binding.btnExport.setOnClickListener {
            val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            exportLauncher.launch("rytm_backup_$date.json")
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        updateOverallStats(state)
                        updateWeeklyBarChart(state)
                        updateHabitStatsCards(state)
                        logsAdapter.submitList(state.recentLogs)
                    }
                }
                launch {
                    viewModel.events.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveBackupToFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = viewModel.getBackupJson()
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(requireContext(), "Backup saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBackupFromFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            viewModel.restoreFromJson(json)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateOverallStats(state: AnalyticsState) {
        val pct = (state.overallWeeklyRate * 100).toInt()
        binding.tvOverallRate.text = "$pct%"
        binding.tvOverallLabel.text = "Weekly completion rate"
        binding.progressOverall.progress = pct

        val best = state.habitStats.maxOfOrNull { it.currentStreak } ?: 0
        binding.tvBestStreak.text = "🔥 $best day streak"

        binding.tvThisMonthCount.text = state.completionsThisMonth.toString()
        binding.tvLastMonthCount.text = state.completionsLastMonth.toString()
    }

    private fun updateWeeklyBarChart(state: AnalyticsState) {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val calDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val entries = calDays.mapIndexed { i, calDay ->
            BarEntry(i.toFloat(), (state.weeklyCompletionByDay[calDay] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "Completions this week").apply {
            color = Color.parseColor("#6200EE")
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        binding.barChart.apply {
            data = BarData(dataSet)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(days)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.GRAY
            }
            axisLeft.apply {
                granularity = 1f
                textColor = Color.GRAY
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setFitBars(true)
            animateY(600)
            invalidate()
        }
    }

    private fun updateHabitStatsCards(state: AnalyticsState) {
        val adapter = HabitStatsAdapter()
        binding.recyclerHabitStats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHabitStats.adapter = adapter
        adapter.submitList(state.habitStats)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

