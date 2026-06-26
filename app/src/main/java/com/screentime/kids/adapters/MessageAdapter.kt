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
import com.screentime.kids.models.MessageRecord
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val context: Context,
    messages: List<MessageRecord> = emptyList()
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    // Internal mutable copy — avoids UnsupportedOperationException
    private val items: MutableList<MessageRecord> = messages.toMutableList()
    private val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivMessageIcon: ImageView = itemView.findViewById(R.id.ivMessageIcon)
        val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        val tvMessageType: TextView = itemView.findViewById(R.id.tvMessageType)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvMessageLength: TextView = itemView.findViewById(R.id.tvMessageLength)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = items[position]

        // Set icon based on message type
        val (iconRes, typeText) = when (message.type.lowercase()) {
            "received" -> {
                holder.ivMessageIcon.setColorFilter(ContextCompat.getColor(context, R.color.blue_500))
                Pair(R.drawable.ic_message_incoming, "Received")
            }
            "sent" -> {
                holder.ivMessageIcon.setColorFilter(ContextCompat.getColor(context, R.color.green_500))
                Pair(R.drawable.ic_message_outgoing, "Sent")
            }
            else -> {
                holder.ivMessageIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                Pair(R.drawable.ic_message_unknown, "Unknown")
            }
        }

        holder.ivMessageIcon.setImageResource(iconRes)
        holder.tvContactName.text = if (message.contactName.isNotBlank() && message.contactName != "Unknown") {
            message.contactName
        } else {
            message.phoneNumber
        }
        holder.tvMessageType.text = typeText
        holder.tvTime.text = if (message.timestamp > 0) timeSdf.format(Date(message.timestamp)) else "--"
        holder.tvMessageLength.text = "${message.messageLength} chars"
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newMessages: List<MessageRecord>) {
        items.clear()
        items.addAll(newMessages)
        notifyDataSetChanged()
    }
}
