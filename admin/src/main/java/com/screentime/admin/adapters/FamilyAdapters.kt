package com.screentime.admin.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.screentime.admin.R
import com.screentime.admin.models.ChildChipItem
import com.screentime.admin.models.FamilyItem

class FamilyAdapter(
    private val onChildClick: (ChildChipItem) -> Unit,
    private val onEditChildName: (ChildChipItem) -> Unit,
    private val onDeleteChild: (ChildChipItem) -> Unit,
    private val onDeleteFamily: (FamilyItem) -> Unit,
    private val onToggleChildTracking: (ChildChipItem, Boolean) -> Unit
) : RecyclerView.Adapter<FamilyAdapter.ViewHolder>() {

    private var items: List<FamilyItem> = emptyList()

    fun submitList(newItems: List<FamilyItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFamilyName: TextView = view.findViewById(R.id.tvFamilyName)
        val tvInviteCode: TextView = view.findViewById(R.id.tvInviteCode)
        val tvChildrenCount: TextView = view.findViewById(R.id.tvChildrenCount)
        val btnDeleteFamily: ImageButton = view.findViewById(R.id.btnDeleteFamily)
        val rvFamilyChildren: RecyclerView = view.findViewById(R.id.rvFamilyChildren)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_family_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvFamilyName.text = item.familyName
        holder.tvInviteCode.text = item.inviteCode
        holder.tvChildrenCount.text = "${item.children.size} Device${if (item.children.size != 1) "s" else ""} Connected"

        val childAdapter = ChildAdminAdapter(
            onChildClick = onChildClick,
            onEditName = onEditChildName,
            onDelete = onDeleteChild,
            onToggleTracking = onToggleChildTracking
        )
        holder.rvFamilyChildren.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.rvFamilyChildren.adapter = childAdapter
        childAdapter.submitList(item.children)

        holder.btnDeleteFamily.setOnClickListener {
            onDeleteFamily(item)
        }
    }

    override fun getItemCount(): Int = items.size
}

class ChildAdminAdapter(
    private val onChildClick: (ChildChipItem) -> Unit,
    private val onEditName: (ChildChipItem) -> Unit,
    private val onDelete: (ChildChipItem) -> Unit,
    private val onToggleTracking: (ChildChipItem, Boolean) -> Unit
) : RecyclerView.Adapter<ChildAdminAdapter.ViewHolder>() {

    private var items: List<ChildChipItem> = emptyList()

    fun submitList(newItems: List<ChildChipItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvChildName: TextView = view.findViewById(R.id.tvChildName)
        val tvDeviceId: TextView = view.findViewById(R.id.tvDeviceId)
        val tvAvatarInitial: TextView = view.findViewById(R.id.tvAvatarInitial)
        val viewStatusDot: View = view.findViewById(R.id.viewStatusDot)
        val btnCopyDeviceId: LinearLayout = view.findViewById(R.id.btnCopyDeviceId)
        val switchTracking: SwitchMaterial = view.findViewById(R.id.switchTracking)
        val tvTrackingStatus: TextView = view.findViewById(R.id.tvTrackingStatus)
        val btnEditName: ImageButton = view.findViewById(R.id.btnEditName)
        val btnDeleteChild: ImageButton = view.findViewById(R.id.btnDeleteChild)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child_device_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.tvChildName.text = item.name

        // Initial for avatar circle
        val initial = item.name.trim().take(1).uppercase()
        holder.tvAvatarInitial.text = if (initial.isNotEmpty()) initial else "C"

        // Truncate Device ID for clean chip UI
        val truncatedId = if (item.id.length > 12) {
            "${item.id.take(6)}...${item.id.takeLast(4)}"
        } else {
            item.id
        }
        holder.tvDeviceId.text = "ID: $truncatedId"

        // Copy Device ID to clipboard on tap
        holder.btnCopyDeviceId.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Device ID", item.id)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied Device ID: ${item.id}", Toast.LENGTH_SHORT).show()
        }

        // Setup Tracking Switch state safely
        holder.switchTracking.setOnCheckedChangeListener(null)
        holder.switchTracking.isChecked = item.isTrackingActive

        if (item.isTrackingActive) {
            holder.tvTrackingStatus.text = "Sync ON"
            holder.tvTrackingStatus.setTextColor(ContextCompat.getColor(context, R.color.status_green))
            holder.viewStatusDot.setBackgroundResource(
                if (item.isOnline) R.drawable.bg_pill_green else R.drawable.bg_pill_neutral
            )
        } else {
            holder.tvTrackingStatus.text = "Sync OFF"
            holder.tvTrackingStatus.setTextColor(ContextCompat.getColor(context, R.color.status_red))
            holder.viewStatusDot.setBackgroundResource(R.drawable.bg_pill_red)
        }

        holder.switchTracking.setOnCheckedChangeListener { _, isChecked ->
            onToggleTracking(item, isChecked)
        }

        holder.itemView.setOnClickListener { onChildClick(item) }
        holder.btnEditName.setOnClickListener { onEditName(item) }
        holder.btnDeleteChild.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
