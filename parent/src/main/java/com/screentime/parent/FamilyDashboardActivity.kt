package com.screentime.parent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.screentime.parent.databinding.ActivityFamilyDashboardBinding
import com.screentime.parent.models.ChildChipItem

class FamilyDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFamilyDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: ChildDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamilyDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadFamilyData()
    }

    private fun setupRecyclerView() {
        adapter = ChildDeviceAdapter { child ->
            val intent = Intent(this, ParentDashboardActivity::class.java)
            intent.putExtra("FAMILY_ID", auth.currentUser?.uid)
            intent.putExtra("SELECTED_CHILD_ID", child.id)
            intent.putExtra("SELECTED_CHILD_NAME", child.name)
            startActivity(intent)
        }
        binding.rvChildren.layoutManager = LinearLayoutManager(this)
        binding.rvChildren.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadFamilyData() {
        val parentUid = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE

        // 1. Load family details (Invite Code & Name)
        db.collection("families").document(parentUid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val familyName = document.getString("familyName") ?: "My Family"
                    val inviteCode = document.getString("inviteCode") ?: "------"
                    binding.tvFamilyName.text = familyName
                    binding.tvInviteCode.text = inviteCode
                }
            }

        // 2. Listen to child devices in real-time
        db.collection("families").document(parentUid).collection("children")
            .addSnapshotListener { snapshots, error ->
                binding.progressBar.visibility = View.GONE
                if (error != null) {
                    binding.tvNoChildren.visibility = View.VISIBLE
                    binding.tvNoChildren.text = "Error loading devices: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    binding.tvNoChildren.visibility = View.VISIBLE
                    binding.rvChildren.visibility = View.GONE
                } else {
                    binding.tvNoChildren.visibility = View.GONE
                    binding.rvChildren.visibility = View.VISIBLE

                    val children = snapshots.map { doc ->
                        val name = doc.getString("childName") ?: "Unknown"
                        val lastSeen = doc.getLong("lastSeen") ?: 0L
                        val isOnline = System.currentTimeMillis() - lastSeen < 60_000L
                        ChildChipItem(id = doc.id, name = name, isOnline = isOnline, lastSeen = lastSeen)
                    }
                    adapter.submitList(children)
                }
            }
    }
}

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class ChildDeviceAdapter(
    private val onChildClick: (ChildChipItem) -> Unit
) : RecyclerView.Adapter<ChildDeviceAdapter.ViewHolder>() {

    private var items: List<ChildChipItem> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvChildName)
        val tvLastSeen: TextView = view.findViewById(R.id.tvLastSeen)
        val viewStatus: View = view.findViewById(R.id.viewStatusDot)
        val ivIcon: ImageView = view.findViewById(R.id.ivDeviceIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val child = items[position]
        holder.tvName.text = child.name

        if (child.isOnline) {
            holder.tvLastSeen.text = "Active Now"
            holder.viewStatus.setBackgroundResource(R.drawable.bg_pill_green)
        } else {
            val minutesAgo = (System.currentTimeMillis() - child.lastSeen) / 60_000L
            val lastSeenText = when {
                child.lastSeen == 0L -> "Never seen"
                minutesAgo < 1 -> "Active just now"
                minutesAgo < 60 -> "Active ${minutesAgo}m ago"
                minutesAgo < 1440 -> "Active ${minutesAgo / 60}h ago"
                else -> "Active ${minutesAgo / 1440}d ago"
            }
            holder.tvLastSeen.text = lastSeenText
            holder.viewStatus.setBackgroundResource(R.drawable.bg_pill_neutral)
        }

        holder.itemView.setOnClickListener { onChildClick(child) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<ChildChipItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
