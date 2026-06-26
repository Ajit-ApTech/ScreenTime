package com.screentime.kids

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.screentime.kids.helpers.*
import com.screentime.kids.models.*
import java.util.Calendar

class MonitorForegroundService : android.app.Service() {

    // Initialize handler at field level so it's always available
    private val handler = Handler(Looper.getMainLooper())
    private val syncIntervalMs = 30_000L // 30 seconds
    private val prefs by lazy { getSharedPreferences("screentime_prefs", Context.MODE_PRIVATE) }

    private val syncRunnable = object : Runnable {
        override fun run() {
            syncData()
            handler.postDelayed(this, syncIntervalMs)
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Start syncing immediately on create
        handler.post(syncRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If service is restarted, the runnable is already scheduled from onCreate.
        // Just ensure it's running (avoid double-posting).
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screentime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen time tracking"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_body))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun syncData() {
        val firebaseHelper = FirebaseHelper(this)
        val appUsageHelper = AppUsageHelper(this)
        val callLogHelper = CallLogHelper(this)
        val messageHelper = MessageHelper(this)

        val childName = firebaseHelper.getChildName() ?: ""
        val todayAppSessions = appUsageHelper.getTodayAppSessions()
        val currentApp = appUsageHelper.getCurrentForegroundApp()

        android.util.Log.d("MonitorService", "Syncing: ${todayAppSessions.size} apps, currentApp=${currentApp?.appName}")

        // Use persisted timestamp so we don't miss calls/messages between syncs.
        // On the very first run, default to start-of-today (midnight) so all of today's data is captured.
        val lastSyncTimestamp = prefs.getLong(PREF_LAST_CALL_MSG_SYNC, startOfTodayMillis())
        val callLogs = callLogHelper.getNewCallLogs(lastSyncTimestamp)
        val messages = messageHelper.getNewMessages(lastSyncTimestamp)

        android.util.Log.d("MonitorService", "Calls since last sync: ${callLogs.size}, Messages: ${messages.size}")

        // Persist the sync timestamp BEFORE the async Firebase write so we don't re-upload on the next tick
        prefs.edit().putLong(PREF_LAST_CALL_MSG_SYNC, System.currentTimeMillis()).apply()

        // Sync to Firebase on a background thread
        Thread {
            firebaseHelper.syncData(childName, currentApp, todayAppSessions, callLogs, messages)
        }.start()
    }

    /** Returns Unix-millis for midnight at the start of today (local time). */
    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "screentime_foreground"
        private const val PREF_LAST_CALL_MSG_SYNC = "last_call_msg_sync_ts"
    }
}
