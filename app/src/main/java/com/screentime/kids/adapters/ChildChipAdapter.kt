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
import com.screentime.kids.models.ChildChipItem

class ChildChipAdapter(
    private val context: Context,
    children: List<ChildChipItem> = emptyList(),
    private val onChildSelect: (ChildChipItem) -> Unit
) : RecyclerView.Adapter<ChildChipAdapter.ChildChipViewHolder>() {

    // Internal mutable copy — avoids UnsupportedOperationException
    private val items: MutableList<ChildChipItem> = children.toMutableList()
    private var selectedChildId: String? = if (items.isNotEmpty()) items[0].id else null

    class ChildChipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvChildName)
        val ivOnlineStatus: ImageView = itemView.findViewById(R.id.ivOnlineStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildChipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child_chip, parent, false)
        return ChildChipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildChipViewHolder, position: Int) {
        val child = items[position]
        val isSelected = selectedChildId == child.id

        holder.tvName.text = child.name

        // Online dot color
        val statusColor = if (child.isOnline) R.color.status_green else R.color.text_tertiary
        holder.ivOnlineStatus.setColorFilter(ContextCompat.getColor(context, statusColor))

        // Chip background: selected = blue, unselected = dark card
        val bgColor = if (isSelected) R.color.accent_blue else R.color.bg_card
        holder.itemView.setBackgroundColor(ContextCompat.getColor(context, bgColor))

        val textColor = if (isSelected) R.color.white else R.color.text_primary
        holder.tvName.setTextColor(ContextCompat.getColor(context, textColor))

        holder.itemView.setOnClickListener {
            selectedChildId = child.id
            notifyDataSetChanged()
            onChildSelect(child)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setSelectedChild(id: String) {
        selectedChildId = id
        notifyDataSetChanged()
    }

    fun updateChildren(newChildren: List<ChildChipItem>) {
        items.clear()
        items.addAll(newChildren)
        if (items.isNotEmpty() && selectedChildId == null) {
            selectedChildId = items[0].id
        }
        notifyDataSetChanged()
    }
}
