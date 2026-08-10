package com.screentime.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.admin.adapters.FamilyAdapter
import com.screentime.admin.databinding.ActivityAdminMainBinding
import com.screentime.admin.dialogs.AdminDialogs
import com.screentime.admin.helpers.AdminFirebaseHelper
import com.screentime.admin.models.ChildChipItem
import com.screentime.admin.models.FamilyItem

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private val firebaseHelper = AdminFirebaseHelper()
    private lateinit var adapter: FamilyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = FamilyAdapter(
            onChildClick = { child ->
                val intent = Intent(this, ChildDetailAdminActivity::class.java).apply {
                    putExtra("CHILD_ID", child.id)
                    putExtra("CHILD_NAME", child.name)
                    putExtra("FAMILY_ID", child.familyId)
                }
                startActivity(intent)
            },
            onEditChildName = { child ->
                AdminDialogs.showEditTextDialog(
                    this,
                    "Rename Child Device",
                    "New Child Name",
                    child.name
                ) { newName ->
                    firebaseHelper.updateChildName(child.familyId, child.id, newName) { success, err ->
                        if (success) {
                            Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDeleteChild = { child ->
                AdminDialogs.showConfirmDeleteDialog(
                    this,
                    "Delete Child Device",
                    "Are you sure you want to permanently delete device '${child.name}' (${child.id}) from Firestore?"
                ) {
                    firebaseHelper.deleteChildDevice(child.familyId, child.id) { success, err ->
                        if (success) {
                            Toast.makeText(this, "Child device deleted", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDeleteFamily = { family ->
                AdminDialogs.showConfirmDeleteDialog(
                    this,
                    "Delete Family",
                    "Are you sure you want to delete family '${family.familyName}' (${family.familyId})?"
                ) {
                    firebaseHelper.deleteFamily(family.familyId) { success, err ->
                        if (success) {
                            Toast.makeText(this, "Family deleted", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onToggleChildTracking = { child, isEnabled ->
                firebaseHelper.updateChildTrackingState(child.familyId, child.id, isEnabled) { success, err ->
                    if (success) {
                        val statusText = if (isEnabled) "Data sync enabled" else "Data sync paused"
                        Toast.makeText(this, "$statusText for ${child.name}", Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(this, "Failed to update tracking: $err", Toast.LENGTH_LONG).show()
                        loadData()
                    }
                }
            }
        )
        binding.rvFamilies.layoutManager = LinearLayoutManager(this)
        binding.rvFamilies.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnRefresh.setOnClickListener {
            loadData()
        }

        binding.btnLockSettings.setOnClickListener {
            val options = arrayOf("Lock App Now", "Change 4-Digit Passcode")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Admin Security")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> lockAppNow()
                        1 -> changePasscode()
                    }
                }
                .show()
        }
    }

    private fun lockAppNow() {
        getSharedPreferences(AdminLockActivity.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .remove(AdminLockActivity.KEY_LAST_UNLOCKED)
            .apply()

        val intent = Intent(this, AdminLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun changePasscode() {
        val intent = Intent(this, AdminLockActivity::class.java).apply {
            putExtra(AdminLockActivity.EXTRA_CHANGE_PIN, true)
            putExtra(AdminLockActivity.EXTRA_FROM_MAIN, true)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!AdminLockActivity.isUnlockedRecently(this)) {
            val intent = Intent(this, AdminLockActivity::class.java).apply {
                putExtra(AdminLockActivity.EXTRA_FROM_MAIN, false)
            }
            startActivity(intent)
            finish()
            return
        }
        loadData()
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE

        firebaseHelper.loadAllFamiliesAndChildren(
            onResult = { families, unlinkedChildren ->
                binding.progressBar.visibility = View.GONE

                val allFamilies = families.toMutableList()
                if (unlinkedChildren.isNotEmpty()) {
                    allFamilies.add(
                        FamilyItem(
                            familyId = "",
                            familyName = "Unlinked Devices",
                            inviteCode = "UNLINKED",
                            children = unlinkedChildren
                        )
                    )
                }

                val allChildren = allFamilies.flatMap { it.children }
                val onlineCount = allChildren.count { it.isOnline }

                binding.tvFamiliesCount.text = families.size.toString()
                binding.tvDevicesCount.text = allChildren.size.toString()
                binding.tvOnlineCount.text = onlineCount.toString()

                if (allFamilies.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvFamilies.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvFamilies.visibility = View.VISIBLE
                    adapter.submitList(allFamilies)
                }
            },
            onError = { err ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            }
        )
    }
}
