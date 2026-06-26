package com.screentime.kids

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.screentime.kids.databinding.ActivityHomeBinding
import com.screentime.kids.helpers.AppUsageHelper
import com.screentime.kids.helpers.CallLogHelper
import com.screentime.kids.helpers.FirebaseHelper
import com.screentime.kids.helpers.MessageHelper
import com.screentime.kids.models.AppSession
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private var _binding: ActivityHomeBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper by lazy { FirebaseHelper(this) }
    private val appUsageHelper by lazy { AppUsageHelper(this) }
    private val callLogHelper by lazy { CallLogHelper(this) }
    private val messageHelper by lazy { MessageHelper(this) }

    private val handler = Handler(Looper.getMainLooper())
    private var secondsSinceLastUpdate = 0
    private var lastDisplayedDate = ""
    private val dateSdf = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
    private val dateSdfShort = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())

    private lateinit var appsAdapter: AppsUsageAdapter

    // Parent access - 5 tap detection
    private var tapCount = 0
    private var lastTapTime = 0L
    private val TAP_THRESHOLD_MS = 500L
    private val PARENT_PASSWORD = "Parent@7879"

    // Runnable that syncs data every 30 seconds
    private val syncRunnable = object : Runnable {
        override fun run() {
            updateData()
            handler.postDelayed(this, 30_000L)
        }
    }

    // Tick counter for "last updated Xs ago"
    private val tickRunnable = object : Runnable {
        override fun run() {
            secondsSinceLastUpdate++
            val label = when {
                secondsSinceLastUpdate < 60 -> "Last updated: ${secondsSinceLastUpdate}s ago"
                secondsSinceLastUpdate < 3600 -> "Last updated: ${secondsSinceLastUpdate / 60}m ago"
                else -> "Last updated: ${secondsSinceLastUpdate / 3600}h ago"
            }
            binding.tvLastUpdate.text = label
            binding.tvFooterUpdate.text = label
            if (secondsSinceLastUpdate % 60 == 0) updateDateHeader()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupRefreshButton()

        // Setup parent access hidden feature
        setupParentAccess()

        // Start the background monitoring service so it runs even when this screen is closed
        startMonitoringService()
    }

    private fun setupParentAccess() {
        // Add click listener to detect rapid taps on app title
        binding.tvAppTitle.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < TAP_THRESHOLD_MS) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime

            if (tapCount >= 5) {
                tapCount = 0
                showParentLoginDialog()
            }
        }
    }

    private fun showParentLoginDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_parent_login, null)
        dialogBuilder.setView(dialogView)

        val dialog = dialogBuilder.create()
        dialog.setCancelable(false)
        dialog.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etPassword)
        val btnUnlock = dialogView.findViewById<Button>(R.id.btnUnlock)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        btnUnlock.setOnClickListener {
            val enteredPassword = etPassword.text?.toString()?.trim() ?: ""
            if (enteredPassword == PARENT_PASSWORD) {
                dialog.dismiss()
                openParentDashboard()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Incorrect password",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                tapCount = 0  // Reset on wrong password
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            tapCount = 0  // Reset on cancel
        }

        dialog.show()
    }

    private fun openParentDashboard() {
        val intent = Intent(this, ParentDashboardActivity::class.java)
        startActivity(intent)
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, MonitorForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            triggerRefresh()
        }
    }

    private fun triggerRefresh() {
        binding.btnRefresh.isEnabled = false
        binding.tvRefreshingStatus.visibility = View.VISIBLE

        val spinAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
        binding.btnRefresh.startAnimation(spinAnim)

        Thread {
            val childName = firebaseHelper.getChildName() ?: ""
            val todayAppSessions = appUsageHelper.getTodayAppSessions()
            val currentApp = appUsageHelper.getCurrentForegroundApp()
            val lastSyncTimestamp = System.currentTimeMillis() - 30_000L
            val callLogs = callLogHelper.getNewCallLogs(lastSyncTimestamp)
            val messages = messageHelper.getNewMessages(lastSyncTimestamp)

            Thread { firebaseHelper.syncData(childName, currentApp, todayAppSessions, callLogs, messages) }.start()

            runOnUiThread {
                updateTodayScreenTime(todayAppSessions)
                appsAdapter.submitList(todayAppSessions)
                updateAppCountBadge(todayAppSessions.size)

                secondsSinceLastUpdate = 0
                binding.tvLastUpdate.text = "Last updated: just now"
                binding.tvFooterUpdate.text = "Last updated: just now"

                binding.tvRefreshingStatus.visibility = View.GONE
                binding.btnRefresh.isEnabled = true
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        updateDateHeader()
        updateChildGreeting()

        if (!appUsageHelper.hasUsageStatsPermission()) {
            showPermissionWarning()
        } else {
            updateData()
        }

        handler.postDelayed(syncRunnable, 30_000L)
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(syncRunnable)
        handler.removeCallbacks(tickRunnable)
    }

    private fun setupRecyclerView() {
        appsAdapter = AppsUsageAdapter(this) { appSession ->
            showAppDetailBottomSheet(appSession)
        }
        binding.rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvApps.adapter = appsAdapter
    }

    private fun updateDateHeader() {
        val today = dateSdfShort.format(Date())
        if (today != lastDisplayedDate) {
            lastDisplayedDate = today
            binding.tvDate.text = dateSdf.format(Date())
        }
    }

    private fun updateChildGreeting() {
        val name = firebaseHelper.getChildName()
        if (!name.isNullOrBlank()) {
            binding.tvChildGreeting.text = "Monitoring · $name"
        } else {
            binding.tvChildGreeting.text = "Monitoring your child"
        }
    }

    private fun updateAppCountBadge(count: Int) {
        binding.tvAppCount.text = "$count apps"
    }

    private fun showPermissionWarning() {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "⚠️ Usage Access not granted. Tap GRANT to fix.",
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        ).setAction("Grant") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.show()
    }

    private fun updateData() {
        val childName = firebaseHelper.getChildName() ?: ""
        val todayAppSessions = appUsageHelper.getTodayAppSessions()
        val currentApp = appUsageHelper.getCurrentForegroundApp()

        android.util.Log.d("HomeActivity", "updateData: ${todayAppSessions.size} apps found")

        updateTodayScreenTime(todayAppSessions)
        appsAdapter.submitList(todayAppSessions)
        updateAppCountBadge(todayAppSessions.size)

        secondsSinceLastUpdate = 0
        binding.tvLastUpdate.text = "Last updated: just now"
        binding.tvFooterUpdate.text = "Last updated: just now"

        val lastSyncTimestamp = System.currentTimeMillis() - 30_000L
        val callLogs = callLogHelper.getNewCallLogs(lastSyncTimestamp)
        val messages = messageHelper.getNewMessages(lastSyncTimestamp)

        Thread {
            firebaseHelper.syncData(childName, currentApp, todayAppSessions, callLogs, messages)
        }.start()
    }

    private fun updateTodayScreenTime(appSessions: List<AppSession>) {
        val totalSeconds = appSessions.sumOf { it.totalTimeSeconds }
        binding.tvTotalScreenTime.text = formatDuration(totalSeconds)
    }

    /** Shows the bottom sheet with detailed info for a tapped app */
    private fun showAppDetailBottomSheet(app: AppSession) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_app_detail, null)
        dialog.setContentView(view)

        // App icon
        val ivIcon = view.findViewById<ImageView>(R.id.bsIvAppIcon)
        try {
            ivIcon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
        } catch (e: Exception) {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Name and package
        view.findViewById<TextView>(R.id.bsTvAppName).text = app.appName
        view.findViewById<TextView>(R.id.bsTvPackageName).text = app.packageName

        // Total time
        val totalTimeText = formatDuration(app.totalTimeSeconds)
        view.findViewById<TextView>(R.id.bsTvTotalTime).text = totalTimeText

        // Last used (exact time)
        val lastUsedText = if (app.lastUsedTimestamp > 0) {
            timeSdf.format(Date(app.lastUsedTimestamp))
        } else "--"
        view.findViewById<TextView>(R.id.bsTvLastUsed).text = lastUsedText

        // First opened: estimate = lastUsedTimestamp - totalTimeSeconds
        val firstOpenedText = if (app.lastUsedTimestamp > 0 && app.totalTimeSeconds > 0) {
            val estimatedStart = app.lastUsedTimestamp - (app.totalTimeSeconds * 1000)
            // Only show if it's within today
            val startOfDay = getStartOfDay()
            if (estimatedStart >= startOfDay) {
                timeSdf.format(Date(estimatedStart))
            } else {
                timeSdf.format(Date(startOfDay))
            }
        } else "--"
        view.findViewById<TextView>(R.id.bsTvFirstOpened).text = firstOpenedText

        // Estimated session count (rough estimate: avg 5 min per session)
        val estimatedSessions = if (app.totalTimeSeconds > 0) {
            maxOf(1, (app.totalTimeSeconds / 300).toInt()) // every ~5 min = 1 session
        } else 0
        val sessionText = "$estimatedSessions time${if (estimatedSessions != 1) "s" else ""}"
        view.findViewById<TextView>(R.id.bsTvSessionCount).text = sessionText

        // Time pill in the bottom stat box (colored by usage)
        val timePill = view.findViewById<TextView>(R.id.bsTvTimePill)
        timePill.text = totalTimeText
        val hours = app.totalTimeSeconds / 3600
        when {
            hours < 1 -> {
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                timePill.setBackgroundResource(R.drawable.bg_pill_green)
            }
            hours < 2 -> {
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_orange))
                timePill.setBackgroundResource(R.drawable.bg_pill_orange)
            }
            else -> {
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                timePill.setBackgroundResource(R.drawable.bg_pill_red)
            }
        }

        dialog.show()
    }

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class AppsUsageAdapter(
    private val context: android.content.Context,
    private val onItemClick: (AppSession) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<AppsUsageAdapter.AppViewHolder>() {

    private var apps: List<AppSession> = emptyList()
    private var totalSecondsToday: Long = 0L

    class AppViewHolder(itemView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvAppTime: TextView = itemView.findViewById(R.id.tvAppTime)
        val tvLastUsed: TextView = itemView.findViewById(R.id.tvLastUsed)
        val viewUsageBar: View = itemView.findViewById(R.id.viewUsageBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        // App icon
        try {
            val icon = context.packageManager.getApplicationIcon(app.packageName)
            holder.ivAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.ivAppIcon.setImageDrawable(
                androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            )
        }

        holder.tvAppName.text = app.appName
        holder.tvLastUsed.text = if (app.lastUsedTimestamp > 0) {
            "Last used: ${formatTimeAgo(app.lastUsedTimestamp)}"
        } else ""

        // Time text + colored badge
        val timeText = formatDuration(app.totalTimeSeconds)
        holder.tvAppTime.text = timeText
        val hours = app.totalTimeSeconds / 3600
        when {
            hours < 1 -> {
                holder.tvAppTime.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.status_green))
                holder.tvAppTime.setBackgroundResource(R.drawable.bg_pill_green)
            }
            hours < 2 -> {
                holder.tvAppTime.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.status_orange))
                holder.tvAppTime.setBackgroundResource(R.drawable.bg_pill_orange)
            }
            else -> {
                holder.tvAppTime.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.status_red))
                holder.tvAppTime.setBackgroundResource(R.drawable.bg_pill_red)
            }
        }

        // Usage progress bar (proportional width to total usage today)
        val proportion = if (totalSecondsToday > 0) {
            (app.totalTimeSeconds.toFloat() / totalSecondsToday.toFloat()).coerceIn(0f, 1f)
        } else 0f
        holder.viewUsageBar.post {
            val parentWidth = (holder.viewUsageBar.parent as View).width
            val barWidth = (parentWidth * proportion).toInt().coerceAtLeast(8)
            val params = holder.viewUsageBar.layoutParams
            params.width = barWidth
            holder.viewUsageBar.layoutParams = params
        }

        // Tap to open bottom sheet
        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    override fun getItemCount(): Int = apps.size

    fun submitList(newApps: List<AppSession>) {
        apps = newApps
        totalSecondsToday = newApps.sumOf { it.totalTimeSeconds }
        notifyDataSetChanged()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    private fun formatTimeAgo(timestampMs: Long): String {
        val diffMs = System.currentTimeMillis() - timestampMs
        val diffSec = diffMs / 1000
        return when {
            diffSec < 60 -> "just now"
            diffSec < 3600 -> "${diffSec / 60} min ago"
            diffSec < 86400 -> "${diffSec / 3600} h ago"
            else -> "earlier today"
        }
    }
}
