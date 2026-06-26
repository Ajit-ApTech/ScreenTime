package com.screentime.kids

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.screentime.kids.adapters.ChildChipAdapter
import com.screentime.kids.databinding.ActivityParentDashboardBinding
import com.screentime.kids.fragments.AppUsageFragment
import com.screentime.kids.fragments.CallLogFragment
import com.screentime.kids.fragments.MessageFragment
import com.screentime.kids.models.AppSession
import com.screentime.kids.models.CallRecord
import com.screentime.kids.models.ChildChipItem
import com.screentime.kids.models.MessageRecord
import java.text.SimpleDateFormat
import java.util.*

class ParentDashboardActivity : AppCompatActivity() {

    private var _binding: ActivityParentDashboardBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var selectedChildId: String? = null

    // Keep direct references to fragments (fixes Bug 9 — ViewPager2 tag bridge)
    private val appUsageFragment = AppUsageFragment()
    private val callLogFragment   = CallLogFragment()
    private val messageFragment   = MessageFragment()

    // Last sync tracking for the live countdown
    private var lastSyncTime = System.currentTimeMillis()
    private val syncCounterRunnable = object : Runnable {
        override fun run() {
            val secs = (System.currentTimeMillis() - lastSyncTime) / 1000
            val label = when {
                secs < 60   -> "Last synced: ${secs}s ago"
                secs < 3600 -> "Last synced: ${secs / 60}m ago"
                else        -> "Last synced: ${secs / 3600}h ago"
            }
            if (_binding != null) {
                binding.tvLastSync.text = label
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    // Pulsing dot animation
    private var liveDotAlpha = 1.0f
    private val liveDotPulseRunnable = object : Runnable {
        override fun run() {
            liveDotAlpha = if (liveDotAlpha == 1.0f) 0.3f else 1.0f
            if (_binding != null) {
                binding.tvLiveStatus.alpha = liveDotAlpha
                binding.viewLiveDot.alpha = liveDotAlpha
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and screen recording
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        _binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back button
        binding.btnBack.setOnClickListener { finish() }

        // Child chip RecyclerView
        binding.rvChildSelector.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )

        // Setup tabs with direct fragment references (fixes Bug 9)
        setupTabs()

        // Start animations and counters
        handler.post(liveDotPulseRunnable)
        handler.post(syncCounterRunnable)

        // Load children from Firestore
        loadChildList()
    }

    private fun setupTabs() {
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = when (position) {
                0    -> appUsageFragment
                1    -> callLogFragment
                2    -> messageFragment
                else -> appUsageFragment
            }
        }
        binding.viewPager.adapter = pagerAdapter
        // Disable swiping so scroll inside RecyclerView works correctly
        binding.viewPager.isUserInputEnabled = true

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0    -> "📱 Apps"
                1    -> "📞 Calls"
                2    -> "💬 Messages"
                else -> ""
            }
        }.attach()
    }

    private fun loadChildList() {
        db.collection("children")
            .get()
            .addOnSuccessListener { documents ->
                val children = documents.mapNotNull { doc ->
                    val name = doc.getString("childName") ?: return@mapNotNull null
                    val lastSeen = doc.getLong("lastSeen") ?: 0L
                    val isOnline = System.currentTimeMillis() - lastSeen < 60_000L
                    ChildChipItem(id = doc.id, name = name, isOnline = isOnline, lastSeen = lastSeen)
                }

                if (children.isNotEmpty()) {
                    // Wire up the ChildChipAdapter (Bug 7 — was never done)
                    val chipAdapter = ChildChipAdapter(this, children) { selected ->
                        updateUIForChild(selected)
                    }
                    binding.rvChildSelector.adapter = chipAdapter

                    // Auto-select first child
                    updateUIForChild(children[0])
                    binding.tvNoData.visibility = View.GONE
                } else {
                    binding.tvNoData.visibility = View.VISIBLE
                    binding.tvNoData.text = "No child data found in Firebase"
                }
            }
            .addOnFailureListener {
                binding.tvNoData.visibility = View.VISIBLE
                binding.tvNoData.text = "Failed to load data. Check internet connection."
            }
    }

    private fun updateUIForChild(child: ChildChipItem) {
        selectedChildId = child.id

        // Update live card header — show child name in the status label
        binding.tvLiveStatus.text = if (child.isOnline) "🟢 ${child.name} · Live" else "⚫ ${child.name} · Offline"

        // Clear old data
        binding.tvCurrentApp.text = "Not in use"
        binding.tvSince.text = "—"
        binding.tvTotalScreenTime.text = "0h 0m"
        updateTabTitles(0, 0, 0)

        // Start real-time listener for current app (Firestore live updates)
        db.collection("children").document(child.id)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists() && _binding != null) {
                    val currentApp = snapshot.get("currentApp") as? Map<*, *>
                    val lastSeen = snapshot.getLong("lastSeen") ?: 0L

                    if (currentApp != null) {
                        val appName = currentApp["appName"] as? String ?: "—"
                        val startTime = (currentApp["startTime"] as? Long) ?: 0L
                        binding.tvCurrentApp.text = appName
                        val durationSecs = (System.currentTimeMillis() - startTime) / 1000
                        binding.tvSince.text = "for ${formatDuration(durationSecs)}"
                    } else {
                        binding.tvCurrentApp.text = "Not in use"
                        binding.tvSince.text = "—"
                    }

                    val isOnline = System.currentTimeMillis() - lastSeen < 60_000L
                    binding.tvLiveStatus.text = if (isOnline) "Live" else "Offline"
                }
            }

        // One-time read for full stats
        loadChildStats(child.id)
    }

    private fun loadChildStats(childId: String) {
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document == null || !document.exists() || _binding == null) return@addOnSuccessListener

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val rawSessions  = document.get("appSessions") as? List<Map<*, *>> ?: emptyList()
                val rawCallLogs  = document.get("callLogs")    as? List<Map<*, *>> ?: emptyList()
                val rawMessages  = document.get("messages")    as? List<Map<*, *>> ?: emptyList()

                // Filter to today only
                val todaySessions = rawSessions.filter { (it["date"] as? String) == today }
                val todayCalls    = rawCallLogs.filter { (it["date"] as? String) == today }
                val todayMessages = rawMessages.filter { (it["date"] as? String) == today }

                // KPI cards - Now just Total Screen Time
                val totalSecs = todaySessions.sumOf { (it["totalTimeSeconds"] as? Long) ?: 0L }
                binding.tvTotalScreenTime.text = formatDuration(totalSecs)

                // Update Tab titles with counts
                updateTabTitles(todaySessions.size, todayCalls.size, todayMessages.size)

                // Update last sync time
                lastSyncTime = System.currentTimeMillis()

                // Push data into fragments directly (fixes Bug 9 — no findFragmentByTag)
                val appSessionModels = todaySessions.mapNotNull { map ->
                    AppSession(
                        appName          = map["appName"] as? String ?: return@mapNotNull null,
                        packageName      = map["packageName"] as? String ?: "",
                        totalTimeSeconds = (map["totalTimeSeconds"] as? Long) ?: 0L,
                        date             = today,
                        lastUsedTimestamp = (map["lastUsedTimestamp"] as? Long) ?: 0L
                    )
                }.sortedByDescending { it.totalTimeSeconds }

                val callRecordModels = todayCalls.mapNotNull { map ->
                    CallRecord(
                        contactName     = map["contactName"] as? String ?: "Unknown",
                        phoneNumber     = map["phoneNumber"] as? String ?: "",
                        type            = map["type"] as? String ?: "",
                        durationSeconds = ((map["durationSeconds"] as? Long) ?: 0L).toInt(),
                        timestamp       = (map["timestamp"] as? Long) ?: 0L,
                        date            = today
                    )
                }.sortedByDescending { it.timestamp }

                val messageModels = todayMessages.mapNotNull { map ->
                    MessageRecord(
                        contactName   = map["contactName"] as? String ?: "Unknown",
                        phoneNumber   = map["phoneNumber"] as? String ?: "",
                        type          = map["type"] as? String ?: "",
                        messageLength = ((map["messageLength"] as? Long) ?: 0L).toInt(),
                        timestamp     = (map["timestamp"] as? Long) ?: 0L,
                        date          = today
                    )
                }.sortedByDescending { it.timestamp }

                // Deliver directly to fragment instances — no tag lookup needed
                appUsageFragment.updateAppSessions(appSessionModels)
                callLogFragment.updateCallLogs(callRecordModels)
                messageFragment.updateMessages(messageModels)
            }
    }

    private fun updateTabTitles(appCount: Int, callCount: Int, msgCount: Int) {
        val tabLayout = binding.tabLayout
        if (tabLayout.tabCount >= 3) {
            tabLayout.getTabAt(0)?.text = "📱 Apps ($appCount)"
            tabLayout.getTabAt(1)?.text = "📞 Calls ($callCount)"
            tabLayout.getTabAt(2)?.text = "💬 Messages ($msgCount)"
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0   -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else        -> "< 1m"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(liveDotPulseRunnable)
        handler.removeCallbacks(syncCounterRunnable)
        _binding = null
    }
}
