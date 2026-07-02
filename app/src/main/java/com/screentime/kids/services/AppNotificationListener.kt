package com.screentime.kids.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.screentime.kids.helpers.FirebaseHelper
import com.screentime.kids.models.NotificationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppNotificationListener : NotificationListenerService() {

    private lateinit var firebaseHelper: FirebaseHelper

    override fun onCreate() {
        super.onCreate()
        firebaseHelper = FirebaseHelper(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Extract title (usually sender name) and text (message body)
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Ignore empty notifications or ongoing notifications (like music players)
        if (title.isBlank() && text.isBlank()) return
        if (sbn.isOngoing) return
        
        // Get friendly app name
        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }

        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

        val record = NotificationRecord(
            appName = appName,
            title = title,
            text = text,
            timestamp = timestamp,
            date = dateStr
        )

        Log.d("AppNotificationListener", "Captured notification from $appName")
        firebaseHelper.saveNotification(record)
    }
}
