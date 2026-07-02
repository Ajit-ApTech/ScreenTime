package com.screentime.parent.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.screentime.parent.R
import com.screentime.parent.models.NotificationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val context: Context,
    notifications: List<NotificationRecord> = emptyList()
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val items: MutableList<NotificationRecord> = notifications.toMutableList()
    private val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvText: TextView = itemView.findViewById(R.id.tvText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = items[position]
        
        holder.tvAppName.text = notification.appName
        holder.tvTime.text = if (notification.timestamp > 0) timeSdf.format(Date(notification.timestamp)) else "--"
        holder.tvTitle.text = notification.title.ifBlank { "No Title" }
        holder.tvText.text = notification.text.ifBlank { "No Content" }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newNotifications: List<NotificationRecord>) {
        items.clear()
        items.addAll(newNotifications)
        notifyDataSetChanged()
    }
}
