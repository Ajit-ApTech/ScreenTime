package com.screentime.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.ListenerRegistration
import com.screentime.admin.databinding.ActivityChildDetailAdminBinding
import com.screentime.admin.dialogs.AdminDialogs
import com.screentime.admin.dialogs.AppSessionsBottomSheet
import com.screentime.admin.fragments.AppUsageAdminFragment
import com.screentime.admin.fragments.CallLogAdminFragment
import com.screentime.admin.fragments.MessageAdminFragment
import com.screentime.admin.fragments.NotificationAdminFragment
import com.screentime.admin.helpers.AdminFirebaseHelper
import com.screentime.admin.models.AppSession
import com.screentime.admin.models.CallRecord
import com.screentime.admin.models.MessageRecord
import com.screentime.admin.models.NotificationRecord

class ChildDetailAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildDetailAdminBinding
    private val firebaseHelper = AdminFirebaseHelper()
    private var listenerRegistration: ListenerRegistration? = null

    private var childId: String = ""
    private var childName: String = ""
    private var familyId: String? = null

    private val appUsageFragment = AppUsageAdminFragment()
    private val callLogFragment = CallLogAdminFragment()
    private val messageFragment = MessageAdminFragment()
    private val notificationFragment = NotificationAdminFragment()

    private var currentSessions = mutableListOf<AppSession>()
    private var currentCalls = mutableListOf<CallRecord>()
    private var currentMsgs = mutableListOf<MessageRecord>()
    private var currentNotifs = mutableListOf<NotificationRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDetailAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childId = intent.getStringExtra("CHILD_ID") ?: ""
        childName = intent.getStringExtra("CHILD_NAME") ?: "Child"
        familyId = intent.getStringExtra("FAMILY_ID")

        if (childId.isEmpty()) {
            Toast.makeText(this, "Missing child ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvChildNameHeader.text = childName
        binding.tvDeviceIdHeader.text = "ID: $childId"

        setupViewPagerAndTabs()
        setupFragmentCallbacks()
        setupListeners()
        startLiveListener()
    }

    private fun setupViewPagerAndTabs() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int) = when (position) {
                0 -> appUsageFragment
                1 -> callLogFragment
                2 -> messageFragment
                3 -> notificationFragment
                else -> appUsageFragment
            }
        }
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "📱 Apps"
                1 -> "📞 Calls"
                2 -> "💬 Messages"
                3 -> "🔔 Alerts"
                else -> ""
            }
        }.attach()
    }

    private fun setupFragmentCallbacks() {
        // App usage callbacks
        appUsageFragment.onEditSession = { session ->
            AdminDialogs.showEditUsageDialog(this, session.totalTimeSeconds) { newSecs ->
                val updated = currentSessions.map { s ->
                    if (s.packageName == session.packageName && s.date == session.date) {
                        s.copy(totalTimeSeconds = newSecs)
                    } else s
                }
                firebaseHelper.updateAppSessions(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Usage updated" else "Error: $err")
                }
            }
        }

        appUsageFragment.onDeleteSession = { session ->
            AdminDialogs.showConfirmDeleteDialog(
                this,
                "Delete App Record",
                "Remove session record for '${session.appName}'?"
            ) {
                val updated = currentSessions.filterNot { it.packageName == session.packageName && it.date == session.date }
                firebaseHelper.updateAppSessions(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Session deleted" else "Error: $err")
                }
            }
        }

        // Tap on app row → show full session history bottom sheet
        appUsageFragment.onSessionClick = { session ->
            AppSessionsBottomSheet.show(this, currentSessions, session.packageName)
        }

        // Call log callbacks
        callLogFragment.onEditCall = { call ->
            AdminDialogs.showEditCallDialog(this, call) { newName, newDur ->
                val updated = currentCalls.map { c ->
                    if (c.timestamp == call.timestamp) {
                        c.copy(contactName = newName, durationSeconds = newDur)
                    } else c
                }
                firebaseHelper.updateCallLogs(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Call log updated" else "Error: $err")
                }
            }
        }

        callLogFragment.onDeleteCall = { call ->
            AdminDialogs.showConfirmDeleteDialog(
                this,
                "Delete Call Log",
                "Delete call record with '${call.contactName}'?"
            ) {
                val updated = currentCalls.filterNot { it.timestamp == call.timestamp }
                firebaseHelper.updateCallLogs(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Call log deleted" else "Error: $err")
                }
            }
        }

        // Message callbacks
        messageFragment.onDeleteMsg = { msg ->
            AdminDialogs.showConfirmDeleteDialog(
                this,
                "Delete Message Record",
                "Delete SMS record from '${msg.contactName}'?"
            ) {
                val updated = currentMsgs.filterNot { it.timestamp == msg.timestamp }
                firebaseHelper.updateMessages(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Message deleted" else "Error: $err")
                }
            }
        }

        // Notification callbacks
        notificationFragment.onDeleteNotif = { notif ->
            AdminDialogs.showConfirmDeleteDialog(
                this,
                "Delete Notification",
                "Delete notification from '${notif.appName}'?"
            ) {
                val updated = currentNotifs.filterNot { it.timestamp == notif.timestamp }
                firebaseHelper.updateNotifications(familyId, childId, updated) { success, err ->
                    showToast(if (success) "Notification deleted" else "Error: $err")
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnViewAnalytics.setOnClickListener {
            val intent = Intent(this, AppUsageAnalyticsActivity::class.java).apply {
                putExtra("CHILD_NAME", childName)
                putExtra("APP_SESSIONS", ArrayList(currentSessions))
            }
            startActivity(intent)
        }

        binding.btnEditChildName.setOnClickListener {
            AdminDialogs.showEditTextDialog(this, "Rename Child", "Child name", childName) { newName ->
                firebaseHelper.updateChildName(familyId, childId, newName) { success, err ->
                    if (success) {
                        childName = newName
                        binding.tvChildNameHeader.text = newName
                        showToast("Renamed child to $newName")
                    } else {
                        showToast("Error: $err")
                    }
                }
            }
        }

        binding.btnDeleteDevice.setOnClickListener {
            AdminDialogs.showConfirmDeleteDialog(
                this,
                "Delete Child Device",
                "Are you sure you want to permanently delete device '$childName' ($childId) from Firestore?"
            ) {
                firebaseHelper.deleteChildDevice(familyId, childId) { success, err ->
                    if (success) {
                        showToast("Child device deleted")
                        finish()
                    } else {
                        showToast("Error: $err")
                    }
                }
            }
        }

        // Batch wipe buttons
        binding.btnClearCalls.setOnClickListener {
            AdminDialogs.showConfirmDeleteDialog(this, "Wipe Call Logs", "Wipe ALL call logs for this child?") {
                firebaseHelper.updateCallLogs(familyId, childId, emptyList()) { success, err ->
                    showToast(if (success) "Call logs wiped" else "Error: $err")
                }
            }
        }

        binding.btnClearMsgs.setOnClickListener {
            AdminDialogs.showConfirmDeleteDialog(this, "Wipe Messages", "Wipe ALL message records for this child?") {
                firebaseHelper.updateMessages(familyId, childId, emptyList()) { success, err ->
                    showToast(if (success) "Messages wiped" else "Error: $err")
                }
            }
        }

        binding.btnClearNotifs.setOnClickListener {
            AdminDialogs.showConfirmDeleteDialog(this, "Wipe Notifications", "Wipe ALL notifications for this child?") {
                firebaseHelper.updateNotifications(familyId, childId, emptyList()) { success, err ->
                    showToast(if (success) "Notifications wiped" else "Error: $err")
                }
            }
        }
    }

    private fun startLiveListener() {
        listenerRegistration = firebaseHelper.listenToChildDocument(
            familyId = familyId,
            deviceId = childId,
            onData = { name, sessions, calls, msgs, notifs ->
                childName = name
                binding.tvChildNameHeader.text = name

                currentSessions = sessions.toMutableList()
                currentCalls = calls.toMutableList()
                currentMsgs = msgs.toMutableList()
                currentNotifs = notifs.toMutableList()

                // Update tab titles with real-time counts
                binding.tabLayout.getTabAt(0)?.text = "📱 Apps (${sessions.size})"
                binding.tabLayout.getTabAt(1)?.text = "📞 Calls (${calls.size})"
                binding.tabLayout.getTabAt(2)?.text = "💬 Msgs (${msgs.size})"
                binding.tabLayout.getTabAt(3)?.text = "🔔 Alerts (${notifs.size})"

                appUsageFragment.submitData(sessions)
                callLogFragment.submitData(calls)
                messageFragment.submitData(msgs)
                notificationFragment.submitData(notifs)
            },
            onError = { err ->
                showToast(err)
            }
        )
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }
}
