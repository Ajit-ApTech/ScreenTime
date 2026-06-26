package com.screentime.kids.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.screentime.kids.R
import com.screentime.kids.models.CallRecord
import java.text.SimpleDateFormat
import java.util.*

class CallLogAdapter(
    private val context: Context,
    callLogs: List<CallRecord> = emptyList()
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    // Internal mutable copy — avoids UnsupportedOperationException
    private val items: MutableList<CallRecord> = callLogs.toMutableList()
    private val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())

    class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCallType: ImageView = itemView.findViewById(R.id.ivCallType)
        val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        val tvCallType: TextView = itemView.findViewById(R.id.tvCallType)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val call = items[position]

        // Reuse ic_call drawable, just tint it based on call type
        holder.ivCallType.setImageResource(R.drawable.ic_call)

        val (iconColor, typeText, durationText, badgeBg) = when (call.type.lowercase()) {
            "incoming" -> listOf(R.color.blue_500,   "Incoming", formatDuration(call.durationSeconds), R.drawable.bg_pill_neutral)
            "outgoing" -> listOf(R.color.green_500,  "Outgoing", formatDuration(call.durationSeconds), R.drawable.bg_pill_green)
            "missed"   -> listOf(R.color.status_red, "Missed",   "0s", R.drawable.bg_pill_red)
            else       -> listOf(R.color.text_secondary, call.type, "0s", R.drawable.bg_pill_neutral)
        }

        holder.ivCallType.setColorFilter(ContextCompat.getColor(context, iconColor as Int))
        holder.tvContactName.text = if (call.contactName.isNotBlank() && call.contactName != "Unknown") {
            call.contactName
        } else {
            call.phoneNumber
        }
        
        holder.tvCallType.text = typeText as String
        holder.tvCallType.setBackgroundResource(badgeBg as Int)
        
        holder.tvTime.text = if (call.timestamp > 0) timeSdf.format(Date(call.timestamp)) else "--"
        holder.tvDuration.text = durationText as String
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newCalls: List<CallRecord>) {
        items.clear()
        items.addAll(newCalls)
        notifyDataSetChanged()
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "—"
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
}
