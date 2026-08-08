package com.screentime.admin

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.screentime.admin.databinding.ActivityAppUsageAnalyticsBinding
import com.screentime.admin.dialogs.AppSessionsBottomSheet
import com.screentime.admin.models.AppSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUsageAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppUsageAnalyticsBinding

    private var allSessions: List<AppSession> = emptyList()
    private var childName: String = ""
    private var selectedDate: String? = null // null means "All dates"
    private lateinit var appListAdapter: AppDateSessionAdapter

    // Date → total minutes map (sorted ascending)
    private var dateMinutesMap: LinkedHashMap<String, Long> = linkedMapOf()
    private var allDates: List<String> = emptyList()

    private val displayDateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val storedDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppUsageAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childName = intent.getStringExtra("CHILD_NAME") ?: "Child"
        binding.tvChildNameAnalytics.text = childName

        @Suppress("UNCHECKED_CAST")
        val rawSessions = intent.getSerializableExtra("APP_SESSIONS") as? ArrayList<AppSession>
        allSessions = rawSessions ?: emptyList()

        binding.btnBack.setOnClickListener { finish() }

        appListAdapter = AppDateSessionAdapter { session ->
            AppSessionsBottomSheet.show(this, allSessions, session.packageName)
        }
        binding.rvAppSessions.layoutManager = LinearLayoutManager(this)
        binding.rvAppSessions.adapter = appListAdapter

        buildDateMap()
        renderChart()
        renderAppList(null) // show all initially
    }

    private fun buildDateMap() {
        // Aggregate total usage per date across all app sessions
        val tempMap = mutableMapOf<String, Long>()
        for (session in allSessions) {
            tempMap[session.date] = (tempMap[session.date] ?: 0L) + session.totalTimeSeconds
        }
        // Sort by date ascending
        allDates = tempMap.keys.sorted()
        dateMinutesMap = LinkedHashMap()
        for (date in allDates) {
            dateMinutesMap[date] = tempMap[date]!! / 60L // convert to minutes for chart
        }

        // Update tracked days count
        val trackedDays = allDates.size
        binding.tvTotalTracked.text = "$trackedDays day${if (trackedDays != 1) "s" else ""} tracked"

        // Total all-time usage
        val totalSecs = allSessions.sumOf { it.totalTimeSeconds }
        val totalH = totalSecs / 3600
        val totalM = (totalSecs % 3600) / 60
        binding.tvWeeklyTotal.text = "${totalH}h ${totalM}m"

        // Daily average
        val avgSecs = if (trackedDays > 0) totalSecs / trackedDays else 0L
        val avgH = avgSecs / 3600
        val avgM = (avgSecs % 3600) / 60
        binding.tvDailyAvg.text = if (avgH > 0) "${avgH}h ${avgM}m" else "${avgM}m"
    }

    private fun renderChart() {
        val chart = binding.barChart
        val entries = allDates.mapIndexed { index, date ->
            BarEntry(index.toFloat(), dateMinutesMap[date]?.toFloat() ?: 0f)
        }

        if (entries.isEmpty()) {
            chart.visibility = View.GONE
            return
        }

        val dataSet = BarDataSet(entries, "Daily Usage (minutes)").apply {
            color = Color.parseColor("#6366F1") // Indigo accent
            highLightColor = Color.parseColor("#A5B4FC")
            valueTextColor = Color.parseColor("#CBD5E1")
            valueTextSize = 9f
            setDrawValues(true)
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        // Short date labels for X axis (e.g., "Aug 6")
        val shortFmt = SimpleDateFormat("MMM d", Locale.getDefault())
        val xLabels = allDates.map { date ->
            try {
                shortFmt.format(storedDateFmt.parse(date) ?: Date())
            } catch (e: Exception) {
                date
            }
        }

        chart.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setScaleEnabled(true)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            setExtraOffsets(0f, 8f, 0f, 0f)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(xLabels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#94A3B8")
                textSize = 9f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                labelCount = minOf(xLabels.size, 7)
                labelRotationAngle = -30f
            }
            axisLeft.apply {
                textColor = Color.parseColor("#64748B")
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1E293B")
                setDrawAxisLine(false)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false

            animateY(700)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {
                    val idx = e?.x?.toInt() ?: return
                    val date = allDates.getOrNull(idx) ?: return
                    selectedDate = date
                    renderAppList(date)
                }

                override fun onNothingSelected() {
                    selectedDate = null
                    renderAppList(null)
                }
            })

            invalidate()
        }
    }

    private fun renderAppList(date: String?) {
        val filteredSessions = if (date != null) {
            allSessions.filter { it.date == date }
                .sortedByDescending { it.totalTimeSeconds }
        } else {
            // All dates: aggregate by packageName, pick the latest date entry for display
            allSessions
                .groupBy { it.packageName }
                .map { (_, sessions) ->
                    // Sum total across all dates, keep latest date
                    val sorted = sessions.sortedByDescending { it.date }
                    sorted.first().copy(
                        totalTimeSeconds = sessions.sumOf { it.totalTimeSeconds }
                    )
                }
                .sortedByDescending { it.totalTimeSeconds }
        }

        // Update header label
        if (date != null) {
            val displayDate = try {
                val parsed = storedDateFmt.parse(date)
                displayDateFmt.format(parsed ?: Date())
            } catch (e: Exception) {
                date
            }
            val totalMins = dateMinutesMap[date] ?: 0L
            val h = totalMins / 60
            val m = totalMins % 60
            binding.tvSelectedDate.text = displayDate
            binding.tvDateTotal.text = if (h > 0) "${h}h ${m}m" else "${m}m"
        } else {
            binding.tvSelectedDate.text = "All Time"
            binding.tvDateTotal.text = ""
        }

        if (filteredSessions.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvAppSessions.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvAppSessions.visibility = View.VISIBLE
            appListAdapter.submitList(filteredSessions, date)
        }
    }

    // ── Adapter ─────────────────────────────────────────────────────────────────
    class AppDateSessionAdapter(
        private val onAppClick: (AppSession) -> Unit
    ) : RecyclerView.Adapter<AppDateSessionAdapter.VH>() {

        private var items: List<AppSession> = emptyList()
        private var currentDate: String? = null

        fun submitList(newItems: List<AppSession>, date: String?) {
            items = newItems
            currentDate = date
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvAppInitial: TextView = view.findViewById(R.id.tvAppInitial)
            val tvAppName: TextView = view.findViewById(R.id.tvAppName)
            val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
            val tvDateLabel: TextView = view.findViewById(R.id.tvDateLabel)
            val tvSessionCount: TextView = view.findViewById(R.id.tvSessionCount)
            val tvUsageDuration: TextView = view.findViewById(R.id.tvUsageDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_date_session, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val displayName = if (item.appName.contains(".")) {
                item.appName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            } else {
                item.appName
            }
            val initial = displayName.trim().take(1).uppercase()
            holder.tvAppInitial.text = if (initial.isNotEmpty()) initial else "A"
            holder.tvAppName.text = displayName
            holder.tvPackageName.text = item.packageName

            // Date label
            val shortFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val storedFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            holder.tvDateLabel.text = try {
                shortFmt.format(storedFmt.parse(item.date) ?: Date())
            } catch (e: Exception) {
                item.date
            }

            // Session count
            val sessionCount = item.sessions.size
            holder.tvSessionCount.text = if (sessionCount > 0) {
                "$sessionCount session${if (sessionCount != 1) "s" else ""}"
            } else {
                "tap to view history"
            }

            // Duration
            val h = item.totalTimeSeconds / 3600
            val m = (item.totalTimeSeconds % 3600) / 60
            holder.tvUsageDuration.text = when {
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m"
                else -> "${item.totalTimeSeconds}s"
            }

            holder.itemView.setOnClickListener { onAppClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
