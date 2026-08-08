package com.screentime.parent

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.screentime.parent.adapters.ChildChipAdapter
import com.screentime.parent.databinding.ActivityParentDashboardBinding
import com.screentime.parent.fragments.AppUsageFragment
import com.screentime.parent.fragments.CallLogFragment
import com.screentime.parent.fragments.MessageFragment
import com.screentime.parent.fragments.NotificationFragment
import com.screentime.parent.models.AppSession
import com.screentime.parent.models.AppSessionEntry
import com.screentime.parent.models.CallRecord
import com.screentime.parent.models.ChildChipItem
import com.screentime.parent.models.MessageRecord
import com.screentime.parent.models.NotificationRecord
import java.text.SimpleDateFormat
import java.util.*

class ParentDashboardActivity : AppCompatActivity() {

    private var _binding: ActivityParentDashboardBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var selectedChildId: String? = null
    private var familyId: String? = null
    private var allAppSessionsForChild: List<AppSession> = emptyList() // full history for session bottom sheet

    // Keep direct references to fragments (fixes Bug 9 — ViewPager2 tag bridge)
    private val appUsageFragment = AppUsageFragment()
    private val callLogFragment   = CallLogFragment()
    private val messageFragment   = MessageFragment()
    private val notificationFragment = NotificationFragment()
    private val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())

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

        // Refresh button
        binding.btnRefresh.setOnClickListener { triggerRefresh() }

        // Child chip RecyclerView
        binding.rvChildSelector.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )

        // Setup tabs with direct fragment references (fixes Bug 9)
        setupTabs()

        familyId = intent.getStringExtra("FAMILY_ID")
        val initialChildId = intent.getStringExtra("SELECTED_CHILD_ID")

        if (familyId.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "Family ID missing", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Start animations and counters
        handler.post(liveDotPulseRunnable)
        handler.post(syncCounterRunnable)

        // Load children from Firestore
        loadChildList(initialChildId)
    }

    private fun setupTabs() {
        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int) = when (position) {
                0    -> appUsageFragment.apply {
                    onAppClicked = { app -> showAppDetailBottomSheet(app) }
                }
                1    -> callLogFragment
                2    -> messageFragment
                3    -> notificationFragment
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
                3    -> "🔔 Alerts"
                else -> ""
            }
        }.attach()
    }

    private fun loadChildList(initialChildId: String?) {
        val fid = familyId ?: return
        db.collection("families").document(fid).collection("children")
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

                    // Auto-select initial child or first child
                    val selectedChild = children.find { it.id == initialChildId } ?: children[0]
                    updateUIForChild(selectedChild)
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
        updateTabTitles(0, 0, 0, 0)

        // Start real-time listener for current app (Firestore live updates)
        val fid = familyId ?: return
        db.collection("families").document(fid).collection("children").document(child.id)
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
        val fid = familyId ?: return
        db.collection("families").document(fid).collection("children").document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document == null || !document.exists() || _binding == null) return@addOnSuccessListener

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                @Suppress("UNCHECKED_CAST")
                val rawSessions  = document.get("appSessions")   as? List<Map<*, *>> ?: emptyList()
                val rawCallLogs  = document.get("callLogs")      as? List<Map<*, *>> ?: emptyList()
                val rawMessages  = document.get("messages")      as? List<Map<*, *>> ?: emptyList()
                val rawNotifs    = document.get("notifications")  as? List<Map<*, *>> ?: emptyList()

                // Filter calls/messages/notifs to today only
                val todayCalls    = rawCallLogs.filter { (it["date"] as? String) == today }
                val todayMessages = rawMessages.filter { (it["date"] as? String) == today }
                val todayNotifs   = rawNotifs.filter { (it["date"] as? String) == today }

                // Parse ALL app sessions across ALL dates — the chart will show full history
                val allAppSessions = rawSessions.mapNotNull { map ->
                    @Suppress("UNCHECKED_CAST")
                    val rawEntries = map["sessions"] as? List<Map<*, *>> ?: emptyList()
                    val sessionEntries = rawEntries.map { e ->
                        AppSessionEntry(
                            startTime = (e["startTime"] as? Long) ?: 0L,
                            endTime = (e["endTime"] as? Long) ?: 0L,
                            durationSeconds = (e["durationSeconds"] as? Long) ?: 0L
                        )
                    }.sortedBy { it.startTime }

                    AppSession(
                        appName           = map["appName"] as? String ?: return@mapNotNull null,
                        packageName       = map["packageName"] as? String ?: "",
                        totalTimeSeconds  = (map["totalTimeSeconds"] as? Long) ?: 0L,
                        date              = map["date"] as? String ?: today,
                        lastUsedTimestamp = (map["lastUsedTimestamp"] as? Long) ?: 0L,
                        sessions          = sessionEntries
                    )
                }

                // KPI — today's total screen time
                val todaySessions = allAppSessions.filter { it.date == today }
                val totalSecs = todaySessions.sumOf { it.totalTimeSeconds }
                binding.tvTotalScreenTime.text = formatDuration(totalSecs)

                updateTabTitles(todaySessions.size, todayCalls.size, todayMessages.size, todayNotifs.size)
                lastSyncTime = System.currentTimeMillis()

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

                val notifModels = todayNotifs.mapNotNull { map ->
                    NotificationRecord(
                        appName   = map["appName"] as? String ?: "Unknown",
                        title     = map["title"] as? String ?: "",
                        text      = map["text"] as? String ?: "",
                        timestamp = (map["timestamp"] as? Long) ?: 0L,
                        date      = today
                    )
                }.sortedByDescending { it.timestamp }

                // Pass ALL sessions to fragment (chart uses full history, list shows today)
                allAppSessionsForChild = allAppSessions
                appUsageFragment.updateAppSessions(allAppSessions)
                callLogFragment.updateCallLogs(callRecordModels)
                messageFragment.updateMessages(messageModels)
                notificationFragment.updateNotifications(notifModels)

                binding.btnRefresh.isEnabled = true
            }
    }

    private fun triggerRefresh() {
        val childId = selectedChildId ?: return
        binding.btnRefresh.isEnabled = false
        val spinAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
        binding.btnRefresh.startAnimation(spinAnim)
        loadChildStats(childId)
    }

    private fun updateTabTitles(appCount: Int, callCount: Int, msgCount: Int, notifCount: Int) {
        val tabLayout = binding.tabLayout
        if (tabLayout.tabCount >= 4) {
            tabLayout.getTabAt(0)?.text = "📱 Apps ($appCount)"
            tabLayout.getTabAt(1)?.text = "📞 Calls ($callCount)"
            tabLayout.getTabAt(2)?.text = "💬 Messages ($msgCount)"
            tabLayout.getTabAt(3)?.text = "🔔 Alerts ($notifCount)"
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

    private fun showAppDetailBottomSheet(app: AppSession) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_app_detail, null)
        dialog.setContentView(view)

        // App icon with safe fallback
        val ivIcon = view.findViewById<ImageView>(R.id.bsIvAppIcon)
        try {
            ivIcon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
        } catch (e: Exception) {
            ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        view.findViewById<TextView>(R.id.bsTvAppName).text = app.appName
        view.findViewById<TextView>(R.id.bsTvPackageName).text = app.packageName

        // Gather ALL sessions for this app across all dates
        val allDatesForApp = allAppSessionsForChild
            .filter { it.packageName == app.packageName }
            .sortedByDescending { it.date }

        val allTimeSecs = allDatesForApp.sumOf { it.totalTimeSeconds }
        view.findViewById<TextView>(R.id.bsTvTotalTime).text = formatDuration(allTimeSecs)

        // Last used (exact time)
        val lastUsedText = if (app.lastUsedTimestamp > 0) {
            timeSdf.format(Date(app.lastUsedTimestamp))
        } else "--"
        view.findViewById<TextView>(R.id.bsTvLastUsed).text = lastUsedText

        // Real session count across all dates
        val totalRealSessions = allDatesForApp.sumOf { it.sessions.size }
        val sessionCountText = if (totalRealSessions > 0) {
            "$totalRealSessions session${if (totalRealSessions != 1) "s" else ""} (all time)"
        } else {
            val estimated = if (app.totalTimeSeconds > 0) maxOf(1, (app.totalTimeSeconds / 300).toInt()) else 0
            "~$estimated session${if (estimated != 1) "s" else ""} (est.)"
        }
        view.findViewById<TextView>(R.id.bsTvSessionCount).text = sessionCountText

        // First opened: either first real session start or estimate
        val firstRealEntry = allDatesForApp.lastOrNull()?.sessions?.firstOrNull()
        val firstOpenedText = if (firstRealEntry != null && firstRealEntry.startTime > 0) {
            timeSdf.format(Date(firstRealEntry.startTime)) + " · ${allDatesForApp.lastOrNull()?.date ?: ""}" 
        } else if (app.lastUsedTimestamp > 0 && app.totalTimeSeconds > 0) {
            val est = app.lastUsedTimestamp - (app.totalTimeSeconds * 1000)
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            timeSdf.format(Date(maxOf(est, startOfDay))) + " (est.)"
        } else "--"
        view.findViewById<TextView>(R.id.bsTvFirstOpened).text = firstOpenedText

        // Status pill
        val timePill = view.findViewById<TextView>(R.id.bsTvTimePill)
        val hours = allTimeSecs / 3600
        when {
            hours < 1 -> {
                timePill.text = "Light use"
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                timePill.setBackgroundResource(R.drawable.bg_pill_green)
            }
            hours < 2 -> {
                timePill.text = "Moderate use"
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_orange))
                timePill.setBackgroundResource(R.drawable.bg_pill_orange)
            }
            else -> {
                timePill.text = "Heavy use"
                timePill.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                timePill.setBackgroundResource(R.drawable.bg_pill_red)
            }
        }

        // Session history RecyclerView (real sessions grouped by date)
        val rvSessions = view.findViewById<RecyclerView>(R.id.bsRvSessions)
        val tvNoSessions = view.findViewById<TextView>(R.id.bsTvNoSessions)

        if (rvSessions != null) {
            val flatItems = buildSessionList(allDatesForApp)
            if (flatItems.isEmpty()) {
                rvSessions.visibility = View.GONE
                tvNoSessions?.visibility = View.VISIBLE
            } else {
                rvSessions.visibility = View.VISIBLE
                tvNoSessions?.visibility = View.GONE
                rvSessions.layoutManager = LinearLayoutManager(this)
                rvSessions.adapter = SessionHistoryAdapter(flatItems)
            }
        }

        dialog.show()
    }

    private fun buildSessionList(dateEntries: List<AppSession>): List<SessionItem> {
        val list = mutableListOf<SessionItem>()
        for (session in dateEntries) {
            val dateLabel = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(session.date)
                SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(parsed ?: Date())
            } catch (e: Exception) { session.date }
            val h = session.totalTimeSeconds / 3600
            val m = (session.totalTimeSeconds % 3600) / 60
            val dur = if (h > 0) "${h}h ${m}m" else "${m}m"
            list.add(SessionItem.DateHeader("📅  $dateLabel  •  $dur total"))
            if (session.sessions.isNotEmpty()) {
                for (entry in session.sessions.sortedBy { it.startTime }) {
                    list.add(SessionItem.Entry(entry))
                }
            } else {
                val lastStr = if (session.lastUsedTimestamp > 0) timeSdf.format(Date(session.lastUsedTimestamp)) else "Unknown"
                list.add(SessionItem.Legacy(session.totalTimeSeconds, lastStr))
            }
        }
        return list
    }

    sealed class SessionItem {
        data class DateHeader(val label: String) : SessionItem()
        data class Entry(val entry: AppSessionEntry) : SessionItem()
        data class Legacy(val totalSecs: Long, val lastUsedStr: String) : SessionItem()
    }

    inner class SessionHistoryAdapter(private val items: List<SessionItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        inner class EntryVH(view: View) : RecyclerView.ViewHolder(view) {
            val dot: View = view.findViewById(R.id.bsSessionDot)
            val tvRange: TextView = view.findViewById(R.id.bsTvSessionRange)
            val tvDur: TextView = view.findViewById(R.id.bsTvSessionDur)
        }

        override fun getItemViewType(pos: Int) = when (items[pos]) {
            is SessionItem.DateHeader -> 0
            else -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val tv = TextView(parent.context).apply {
                    setPadding(0, 28, 0, 8)
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(parent.context, R.color.text_secondary))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                HeaderVH(tv)
            } else {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_session_entry_parent, parent, false)
                EntryVH(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
            when (val item = items[pos]) {
                is SessionItem.DateHeader -> (holder as HeaderVH).tv.text = item.label
                is SessionItem.Entry -> {
                    (holder as EntryVH).apply {
                        tvRange.text = "${timeFmt.format(Date(item.entry.startTime))} – ${timeFmt.format(Date(item.entry.endTime))}"
                        val s = item.entry.durationSeconds
                        tvDur.text = if (s >= 3600) "${s/3600}h ${(s%3600)/60}m" else if (s >= 60) "${s/60}m" else "${s}s"
                    }
                }
                is SessionItem.Legacy -> {
                    (holder as EntryVH).apply {
                        tvRange.text = "Last used at ${item.lastUsedStr}  (legacy data)"
                        val s = item.totalSecs
                        tvDur.text = if (s >= 3600) "${s/3600}h ${(s%3600)/60}m" else if (s >= 60) "${s/60}m" else "${s}s"
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(liveDotPulseRunnable)
        handler.removeCallbacks(syncCounterRunnable)
        _binding = null
    }
}
