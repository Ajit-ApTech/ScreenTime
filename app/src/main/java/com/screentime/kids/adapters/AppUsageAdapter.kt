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
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
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

        // Usage progress bar (proportional width to total usage today)
        val proportion = if (totalSecondsToday > 0) {
            (app.totalTimeSeconds.toFloat() / totalSecondsToday.toFloat()).coerceIn(0f, 1f)
        } else 0f
        holder.progressBar.progress = (proportion * 100).toInt()

        // Color bar based on usage — use correct color names
        val hours = app.totalTimeSeconds / 3600
        holder.progressBar.progressTintList = when {
            hours < 1 -> ContextCompat.getColorStateList(context, R.color.status_green)
            hours < 2 -> ContextCompat.getColorStateList(context, R.color.status_orange)
            else      -> ContextCompat.getColorStateList(context, R.color.status_red)
        }
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
