package com.screentime.kids.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.screentime.kids.R
import com.screentime.kids.models.AppSession

class AppUsageAdapter(
    private val context: Context,
    appSessions: List<AppSession> = emptyList()
) : RecyclerView.Adapter<AppUsageAdapter.AppViewHolder>() {

    // Internal mutable copy — avoids UnsupportedOperationException on submitList()
    private val items: MutableList<AppSession> = appSessions.toMutableList()
    private var totalSecondsToday: Long = items.sumOf { it.totalTimeSeconds }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvAppTime: TextView = itemView.findViewById(R.id.tvAppTime)
        val tvLastUsed: TextView = itemView.findViewById(R.id.tvLastUsed)
        val viewUsageBar: View = itemView.findViewById(R.id.viewUsageBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage_parent, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = items[position]

        // App icon
        try {
            val icon = context.packageManager.getApplicationIcon(app.packageName)
            holder.ivAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.ivAppIcon.setImageDrawable(
                ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            )
        }

        holder.tvAppName.text = app.appName
        holder.tvAppTime.text = formatDuration(app.totalTimeSeconds)

        // Format last used time
        if (app.lastUsedTimestamp > 0) {
            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            holder.tvLastUsed.text = "Last used ${sdf.format(java.util.Date(app.lastUsedTimestamp))}"
            holder.tvLastUsed.visibility = View.VISIBLE
        } else {
            holder.tvLastUsed.visibility = View.GONE
        }

        // Usage progress bar (proportional width to total usage today)
        val proportion = if (totalSecondsToday > 0) {
            (app.totalTimeSeconds.toFloat() / totalSecondsToday.toFloat()).coerceIn(0f, 1f)
        } else 0f
        
        holder.viewUsageBar.post {
            val parentWidth = (holder.viewUsageBar.parent as View).width
            val barWidth = (parentWidth * proportion).toInt()
            holder.viewUsageBar.layoutParams = holder.viewUsageBar.layoutParams.apply {
                width = if (barWidth > 0) barWidth else 1 // At least 1px to be visible
            }
        }

        // Color pill badge based on usage
        val hours = app.totalTimeSeconds / 3600
        val (textColor, bgRes) = when {
            hours < 1 -> Pair(R.color.status_green, R.drawable.bg_pill_green)
            hours < 2 -> Pair(R.color.status_orange, R.drawable.bg_pill_orange)
            else      -> Pair(R.color.status_red, R.drawable.bg_pill_red)
        }
        
        holder.tvAppTime.setTextColor(ContextCompat.getColor(context, textColor))
        holder.tvAppTime.setBackgroundResource(bgRes)
        
        // Also tint the usage bar
        val bgDrawable = ContextCompat.getDrawable(context, R.drawable.bg_usage_bar)?.mutate()
        bgDrawable?.setTint(ContextCompat.getColor(context, textColor))
        holder.viewUsageBar.background = bgDrawable
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newApps: List<AppSession>) {
        items.clear()
        items.addAll(newApps)
        totalSecondsToday = newApps.sumOf { it.totalTimeSeconds }
        notifyDataSetChanged()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0  -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else        -> "< 1m"
        }
    }
}
