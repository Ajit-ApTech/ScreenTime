package com.screentime.kids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    // Tracks what date we last displayed — so we notice when midnight rolls over
    private var lastDisplayedDate = ""
    private val dateSdf = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
    private val dateSdfShort = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var appsAdapter: AppsUsageAdapter

    // Runnable that syncs data every 30 seconds
    private val syncRunnable = object : Runnable {
        override fun run() {
            updateData()
            handler.postDelayed(this, 30_000L)
        }
    }

    // Runnable that ticks the "last updated X seconds ago" counter every second
    // and also refreshes the date header if the calendar day rolls over.
    private val tickRunnable = object : Runnable {
        override fun run() {
            secondsSinceLastUpdate++
            binding.tvFooterUpdate.text = "Last updated: ${secondsSinceLastUpdate}s ago"
            // Check once per minute whether the date has changed (covers midnight rollover)
            if (secondsSinceLastUpdate % 60 == 0) {
                updateDateHeader()
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupRefreshButton()
    }

    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            triggerRefresh()
        }
    }

    private fun triggerRefresh() {
        // Disable button while refreshing to prevent double-taps
        binding.btnRefresh.isEnabled = false
        binding.tvRefreshingStatus.visibility = View.VISIBLE

        // Spin the refresh icon
        val spinAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
        binding.btnRefresh.startAnimation(spinAnim)

        // Run data fetch off the main thread, then update UI
        Thread {
            // Read usage data (already thread-safe)
            val childName = firebaseHelper.getChildName() ?: ""
            val todayAppSessions = appUsageHelper.getTodayAppSessions()
            val currentApp = appUsageHelper.getCurrentForegroundApp()
            val lastSyncTimestamp = System.currentTimeMillis() - 30_000L
            val callLogs = callLogHelper.getNewCallLogs(lastSyncTimestamp)
            val messages = messageHelper.getNewMessages(lastSyncTimestamp)

            // Push Firebase sync (fire-and-forget)
            Thread { firebaseHelper.syncData(childName, currentApp, todayAppSessions, callLogs, messages) }.start()

            // Update UI on main thread
            runOnUiThread {
                updateTodayScreenTime(todayAppSessions)
                appsAdapter.submitList(todayAppSessions)

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

        // Always refresh the date header when the app becomes visible
        updateDateHeader()

        // Check if the user has granted Usage Access permission
        if (!appUsageHelper.hasUsageStatsPermission()) {
            showPermissionWarning()
        } else {
            hidePermissionWarning()
            // Load data immediately on resume
            updateData()
        }

        // Start auto-refresh every 30 seconds
        handler.postDelayed(syncRunnable, 30_000L)
        // Start the tick counter
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop all runnables when not visible
        handler.removeCallbacks(syncRunnable)
        handler.removeCallbacks(tickRunnable)
    }

    private fun setupRecyclerView() {
        appsAdapter = AppsUsageAdapter(this)
        binding.rvApps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvApps.adapter = appsAdapter
    }

    /** Updates the date header. Safe to call repeatedly — only updates the view if the date changed. */
    private fun updateDateHeader() {
        val today = dateSdfShort.format(Date())
        if (today != lastDisplayedDate) {
            lastDisplayedDate = today
            binding.tvDate.text = dateSdf.format(Date())
        }
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

    private fun hidePermissionWarning() {
        // No persistent banner view, Snackbar auto-dismisses
    }

    private fun updateData() {
        val childName = firebaseHelper.getChildName() ?: ""
        val todayAppSessions = appUsageHelper.getTodayAppSessions()
        val currentApp = appUsageHelper.getCurrentForegroundApp()

        android.util.Log.d("HomeActivity", "updateData: ${todayAppSessions.size} apps found")

        // Update UI
        updateTodayScreenTime(todayAppSessions)
        appsAdapter.submitList(todayAppSessions)

        // Reset the seconds counter and update the "last updated" label
        secondsSinceLastUpdate = 0
        binding.tvLastUpdate.text = "Last updated: just now"
        binding.tvFooterUpdate.text = "Last updated: just now"

        // Get call logs and messages since last 30 seconds
        val lastSyncTimestamp = System.currentTimeMillis() - 30_000L
        val callLogs = callLogHelper.getNewCallLogs(lastSyncTimestamp)
        val messages = messageHelper.getNewMessages(lastSyncTimestamp)

        // Sync data to Firebase in background
        Thread {
            firebaseHelper.syncData(childName, currentApp, todayAppSessions, callLogs, messages)
        }.start()
    }

    private fun updateTodayScreenTime(appSessions: List<AppSession>) {
        val totalSeconds = appSessions.sumOf { it.totalTimeSeconds }
        binding.tvTotalScreenTime.text = formatDuration(totalSeconds)
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60

        return when {
            hours > 0 -> "$hours h $minutes m"
            minutes > 0 -> "$minutes m"
            else -> "< 1 m"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

class AppsUsageAdapter(private val context: android.content.Context) :
    androidx.recyclerview.widget.RecyclerView.Adapter<AppsUsageAdapter.AppViewHolder>() {

    private var apps: List<AppSession> = emptyList()

    class AppViewHolder(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val ivAppIcon = itemView.findViewById<android.widget.ImageView>(R.id.ivAppIcon)
        val tvAppName = itemView.findViewById<android.widget.TextView>(R.id.tvAppName)
        val tvAppTime = itemView.findViewById<android.widget.TextView>(R.id.tvAppTime)
        val tvLastUsed = itemView.findViewById<android.widget.TextView>(R.id.tvLastUsed)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AppViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        // Load app icon
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(app.packageName)
            holder.ivAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.ivAppIcon.setImageDrawable(
                androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            )
        }

        holder.tvAppName.text = app.appName
        holder.tvAppTime.text = formatDuration(app.totalTimeSeconds)
        holder.tvLastUsed.text = if (app.lastUsedTimestamp > 0) {
            "Last used: ${formatTimeAgo(app.lastUsedTimestamp)}"
        } else {
            ""
        }
    }

    override fun getItemCount(): Int = apps.size

    fun submitList(newApps: List<AppSession>) {
        apps = newApps
        notifyDataSetChanged()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "$hours h $minutes m"
            minutes > 0 -> "$minutes m"
            else -> "< 1 m"
        }
    }

    /** Returns a human-readable relative time string, e.g. "just now", "5 min ago", "2 h ago" */
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
