package com.screentime.kids.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.kids.R
import com.screentime.kids.adapters.AppUsageAdapter
import com.screentime.kids.databinding.FragmentAppUsageBinding
import com.screentime.kids.models.AppSession
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import androidx.core.content.ContextCompat

class AppUsageFragment : Fragment() {

    private var _binding: FragmentAppUsageBinding? = null
    private val binding get() = _binding!!

    // Buffer data in case activity delivers it before the view is created
    private var pendingSessions: List<AppSession> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppUsage.adapter = AppUsageAdapter(requireContext(), pendingSessions)
        
        setupChart()
        updateChartAndList(pendingSessions)
    }

    private fun setupChart() {
        binding.barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            legend.isEnabled = false
            setScaleEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }
            axisRight.isEnabled = false
        }
    }

    fun updateAppSessions(newSessions: List<AppSession>) {
        pendingSessions = newSessions
        if (_binding != null) {
            updateChartAndList(newSessions)
        }
    }

    private fun updateChartAndList(sessions: List<AppSession>) {
        val adapter = binding.rvAppUsage.adapter as? AppUsageAdapter
        adapter?.submitList(sessions)

        if (sessions.isEmpty()) {
            binding.chartContainer.visibility = View.GONE
            binding.tvNoData.visibility = View.VISIBLE
            binding.rvAppUsage.visibility = View.GONE
        } else {
            binding.chartContainer.visibility = View.VISIBLE
            binding.tvNoData.visibility = View.GONE
            binding.rvAppUsage.visibility = View.VISIBLE
            
            populateChart(sessions)
        }
    }

    private fun populateChart(sessions: List<AppSession>) {
        // Take top 5 apps for chart to avoid crowding
        val topSessions = sessions.sortedByDescending { it.totalTimeSeconds }.take(5)
        
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        
        for (i in topSessions.indices) {
            val session = topSessions[i]
            // Convert seconds to hours for display
            val hours = session.totalTimeSeconds / 3600f
            entries.add(BarEntry(i.toFloat(), hours))
            
            // Truncate name
            val name = if (session.appName.length > 8) session.appName.substring(0, 8) + ".." else session.appName
            labels.add(name)
        }
        
        val dataSet = BarDataSet(entries, "Usage (Hours)")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.accent_blue)
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        dataSet.valueTextSize = 10f
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.5f
        
        binding.barChart.data = barData
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
