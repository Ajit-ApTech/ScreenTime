package com.screentime.admin.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.screentime.admin.R
import com.screentime.admin.models.AppSession
import com.screentime.admin.models.CallRecord
import com.screentime.admin.models.MessageRecord
import com.screentime.admin.models.NotificationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUsageAdminAdapter(
    private val onEditUsage: (AppSession) -> Unit,
    private val onDeleteUsage: (AppSession) -> Unit,
    private val onAppClick: (AppSession) -> Unit = {}
) : RecyclerView.Adapter<AppUsageAdminAdapter.ViewHolder>() {

    private var items: List<AppSession> = emptyList()

    fun submitList(newItems: List<AppSession>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val tvUsageTime: TextView = view.findViewById(R.id.tvUsageTime)
        val tvAppInitial: TextView = view.findViewById(R.id.tvAppInitial)
        val btnEditUsage: ImageButton = view.findViewById(R.id.btnEditUsage)
        val btnDeleteUsage: ImageButton = view.findViewById(R.id.btnDeleteUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_usage_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Human-readable app name formatting
        val displayName = if (item.appName.contains(".")) {
            item.appName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        } else {
            item.appName
        }
        holder.tvAppName.text = displayName
        holder.tvPackageName.text = item.packageName

        val initial = displayName.trim().take(1).uppercase()
        holder.tvAppInitial.text = if (initial.isNotEmpty()) initial else "A"

        val hours = item.totalTimeSeconds / 3600
        val mins = (item.totalTimeSeconds % 3600) / 60
        val durationStr = when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${item.totalTimeSeconds}s"
        }
        holder.tvUsageTime.text = "⏱️ $durationStr (${item.totalTimeSeconds}s)"

        holder.itemView.setOnClickListener { onAppClick(item) }
        holder.btnEditUsage.setOnClickListener { onEditUsage(item) }
        holder.btnDeleteUsage.setOnClickListener { onDeleteUsage(item) }
    }

    override fun getItemCount(): Int = items.size
}

class CallLogAdminAdapter(
    private val onEditCall: (CallRecord) -> Unit,
    private val onDeleteCall: (CallRecord) -> Unit
) : RecyclerView.Adapter<CallLogAdminAdapter.ViewHolder>() {

    private var items: List<CallRecord> = emptyList()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun submitList(newItems: List<CallRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContactName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhoneNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvCallDetails: TextView = view.findViewById(R.id.tvCallDetails)
        val tvCallIcon: TextView = view.findViewById(R.id.tvCallIcon)
        val btnEditCall: ImageButton = view.findViewById(R.id.btnEditCall)
        val btnDeleteCall: ImageButton = view.findViewById(R.id.btnDeleteCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvContactName.text = item.contactName
        holder.tvPhoneNumber.text = item.phoneNumber

        val icon = when (item.type.lowercase()) {
            "incoming" -> "↙️"
            "outgoing" -> "↗️"
            "missed" -> "❌"
            else -> "📞"
        }
        holder.tvCallIcon.text = icon

        val timeStr = if (item.timestamp > 0) timeFormat.format(Date(item.timestamp)) else item.date
        holder.tvCallDetails.text = "${item.type.replaceFirstChar { it.uppercase() }} • ${item.durationSeconds}s • $timeStr"

        holder.btnEditCall.setOnClickListener { onEditCall(item) }
        holder.btnDeleteCall.setOnClickListener { onDeleteCall(item) }
    }

    override fun getItemCount(): Int = items.size
}

class MessageAdminAdapter(
    private val onDeleteMsg: (MessageRecord) -> Unit
) : RecyclerView.Adapter<MessageAdminAdapter.ViewHolder>() {

    private var items: List<MessageRecord> = emptyList()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun submitList(newItems: List<MessageRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContactName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhoneNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvMsgDetails: TextView = view.findViewById(R.id.tvMsgDetails)
        val btnDeleteMsg: ImageButton = view.findViewById(R.id.btnDeleteMsg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvContactName.text = item.contactName
        holder.tvPhoneNumber.text = item.phoneNumber

        val timeStr = if (item.timestamp > 0) timeFormat.format(Date(item.timestamp)) else item.date
        holder.tvMsgDetails.text = "${item.type.replaceFirstChar { it.uppercase() }} • ${item.messageLength} chars • $timeStr"

        holder.btnDeleteMsg.setOnClickListener { onDeleteMsg(item) }
    }

    override fun getItemCount(): Int = items.size
}

class NotificationAdminAdapter(
    private val onDeleteNotif: (NotificationRecord) -> Unit
) : RecyclerView.Adapter<NotificationAdminAdapter.ViewHolder>() {

    private var items: List<NotificationRecord> = emptyList()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun submitList(newItems: List<NotificationRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvNotifTitle: TextView = view.findViewById(R.id.tvNotifTitle)
        val tvNotifText: TextView = view.findViewById(R.id.tvNotifText)
        val tvNotifTime: TextView = view.findViewById(R.id.tvNotifTime)
        val btnDeleteNotif: ImageButton = view.findViewById(R.id.btnDeleteNotif)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        val displayName = if (item.appName.contains(".")) {
            item.appName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        } else {
            item.appName
        }
        holder.tvAppName.text = displayName

        holder.tvNotifTitle.text = item.title.ifEmpty { "Notification" }
        holder.tvNotifText.text = item.text
        holder.tvNotifTime.text = if (item.timestamp > 0) timeFormat.format(Date(item.timestamp)) else item.date

        holder.btnDeleteNotif.setOnClickListener { onDeleteNotif(item) }
    }

    override fun getItemCount(): Int = items.size
}
