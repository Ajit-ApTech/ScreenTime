package com.screentime.admin.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.screentime.admin.R
import com.screentime.admin.models.AppSession
import com.screentime.admin.models.AppSessionEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppSessionsBottomSheet {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun show(context: Context, allSessions: List<AppSession>, packageName: String) {
        // Gather all sessions for this app across all dates
        val appSessions = allSessions.filter { it.packageName == packageName }
            .sortedByDescending { it.date }

        if (appSessions.isEmpty()) return

        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_app_sessions, null)
        dialog.setContentView(view)

        // Populate header
        val appName = appSessions.first().appName
        val displayName = if (appName.contains(".")) {
            appName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        } else {
            appName
        }
        val initial = displayName.trim().take(1).uppercase()
        view.findViewById<TextView>(R.id.tvSheetAppInitial).text = if (initial.isNotEmpty()) initial else "A"
        view.findViewById<TextView>(R.id.tvSheetAppName).text = displayName
        view.findViewById<TextView>(R.id.tvSheetPackageName).text = packageName

        // Compute total across all dates for this app
        val totalSecs = appSessions.sumOf { it.totalTimeSeconds }
        view.findViewById<TextView>(R.id.tvSheetTotalTime).text = formatDuration(totalSecs)

        // Build flat item list: interleave date headers + session entries
        val flatItems = mutableListOf<SessionListItem>()
        for (session in appSessions) {
            flatItems.add(SessionListItem.DateHeader(session.date, session.totalTimeSeconds))
            if (session.sessions.isNotEmpty()) {
                for (entry in session.sessions.sortedBy { it.startTime }) {
                    flatItems.add(SessionListItem.Entry(entry))
                }
            } else {
                // Legacy data — show only the daily total
                flatItems.add(SessionListItem.LegacyTotal(session.totalTimeSeconds, session.lastUsedTimestamp))
            }
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvSessions)
        val tvNoSessions = view.findViewById<TextView>(R.id.tvNoSessions)

        if (flatItems.isEmpty()) {
            rv.visibility = View.GONE
            tvNoSessions.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            tvNoSessions.visibility = View.GONE
            rv.layoutManager = LinearLayoutManager(context)
            rv.adapter = SessionFlatAdapter(flatItems)
        }

        dialog.show()
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${seconds}s"
        }
    }

    // ── Data types ─────────────────────────────────────────────────────────────
    sealed class SessionListItem {
        data class DateHeader(val date: String, val totalSeconds: Long) : SessionListItem()
        data class Entry(val entry: AppSessionEntry) : SessionListItem()
        data class LegacyTotal(val totalSeconds: Long, val lastUsedTs: Long) : SessionListItem()
    }

    // ── Adapter ────────────────────────────────────────────────────────────────
    private class SessionFlatAdapter(private val items: List<SessionListItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_DATE = 0
            const val TYPE_ENTRY = 1
            const val TYPE_LEGACY = 2
        }

        override fun getItemViewType(position: Int) = when (items[position]) {
            is SessionListItem.DateHeader -> TYPE_DATE
            is SessionListItem.Entry -> TYPE_ENTRY
            is SessionListItem.LegacyTotal -> TYPE_LEGACY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_DATE -> {
                    val v = inflater.inflate(R.layout.item_session_date_header, parent, false)
                    DateHeaderVH(v)
                }
                TYPE_ENTRY -> {
                    val v = inflater.inflate(R.layout.item_app_individual_session, parent, false)
                    EntryVH(v)
                }
                else -> {
                    val v = inflater.inflate(R.layout.item_app_individual_session, parent, false)
                    EntryVH(v) // reuse same layout for legacy
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is SessionListItem.DateHeader -> {
                    (holder as DateHeaderVH).bind(item)
                }
                is SessionListItem.Entry -> {
                    (holder as EntryVH).bind(item.entry)
                }
                is SessionListItem.LegacyTotal -> {
                    (holder as EntryVH).bindLegacy(item.totalSeconds, item.lastUsedTs)
                }
            }
        }

        override fun getItemCount() = items.size

        // ── ViewHolders ──
        class DateHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            private val layoutHeader: ViewGroup = view.findViewById(R.id.layoutDateHeader)
            private val tvDateHeader: TextView = view.findViewById(R.id.tvDateHeader)

            fun bind(item: SessionListItem.DateHeader) {
                layoutHeader.visibility = View.VISIBLE
                val dateStr = try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.date)
                    SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(parsed ?: Date())
                } catch (e: Exception) {
                    item.date
                }
                val hours = item.totalSeconds / 3600
                val mins = (item.totalSeconds % 3600) / 60
                val durStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                tvDateHeader.text = "📅  $dateStr  •  $durStr total"
            }
        }

        class EntryVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTimeRange: TextView = view.findViewById(R.id.tvSessionTimeRange)
            private val tvDuration: TextView = view.findViewById(R.id.tvSessionDuration)

            fun bind(entry: AppSessionEntry) {
                val startStr = timeFormat.format(Date(entry.startTime))
                val endStr = timeFormat.format(Date(entry.endTime))
                tvTimeRange.text = "$startStr – $endStr"
                tvDuration.text = formatDuration(entry.durationSeconds)
            }

            fun bindLegacy(totalSeconds: Long, lastUsedTs: Long) {
                val lastUsedStr = if (lastUsedTs > 0) timeFormat.format(Date(lastUsedTs)) else "Unknown time"
                tvTimeRange.text = "Last used at $lastUsedStr  (legacy data)"
                tvDuration.text = formatDuration(totalSeconds)
            }

            private fun formatDuration(seconds: Long): String {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                return when {
                    h > 0 -> "${h}h ${m}m"
                    m > 0 -> "${m}m"
                    else -> "${seconds}s"
                }
            }
        }
    }
}
