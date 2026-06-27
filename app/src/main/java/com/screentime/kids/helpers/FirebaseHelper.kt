package com.screentime.kids.helpers

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.screentime.kids.models.AppSession
import com.screentime.kids.models.CallRecord
import com.screentime.kids.models.MessageRecord
import com.screentime.kids.models.CurrentAppInfo

class FirebaseHelper(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val prefs = context.getSharedPreferences("screentime_prefs", Context.MODE_PRIVATE)

    private val collectionName = "children"
    private val deviceIdKey = "device_id"

    /**
     * Returns a stable device ID backed by Firebase Anonymous Auth.
     *
     * - If the user is already signed in anonymously, returns their UID immediately.
     * - If not, kicks off anonymous sign-in (async) and returns null.
     *   The next sync cycle (30 s later) will succeed once auth has completed.
     *
     * No more "pending_XXX" document is ever created.
     */
    fun getDeviceId(): String? {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            // Always keep SharedPrefs in sync with the real UID
            prefs.edit().putString(deviceIdKey, uid).apply()
            return uid
        }

        // Auth not ready yet — trigger sign-in and return null so the caller can skip this cycle
        Log.d("FirebaseHelper", "Anonymous auth not ready — triggering sign-in, will retry on next sync")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid != null) {
                    prefs.edit().putString(deviceIdKey, uid).apply()
                    Log.d("FirebaseHelper", "Anonymous sign-in successful, deviceId=$uid")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseHelper", "Anonymous sign-in failed: ${e.message}")
            }

        return null
    }

    fun saveChildName(name: String) {
        prefs.edit().putString("child_name", name).apply()
    }

    fun getChildName(): String? {
        return prefs.getString("child_name", null)
    }

    fun isSetupDone(): Boolean {
        return prefs.getBoolean("is_setup_done", false)
    }

    fun markSetupDone() {
        prefs.edit().putBoolean("is_setup_done", true).apply()
    }

    fun syncData(
        childName: String,
        currentApp: CurrentAppInfo?,
        appSessions: List<AppSession>,
        callLogs: List<CallRecord>,
        messages: List<MessageRecord>
    ) {
        val deviceId = getDeviceId()
        if (deviceId == null) {
            Log.w("FirebaseHelper", "Skipping sync — device ID not ready yet (auth pending)")
            return
        }

        Log.d("FirebaseHelper", "Syncing to Firestore: deviceId=$deviceId, apps=${appSessions.size}, calls=${callLogs.size}, msgs=${messages.size}")

        val document = db.collection(collectionName).document(deviceId)

        // ── Step 1: Overwrite app usage + metadata (this is correct — always want the latest full list) ──
        val baseData = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "childName" to childName,
            "lastSeen" to System.currentTimeMillis(),
            "appSessions" to appSessions.map { session ->
                mapOf(
                    "appName" to session.appName,
                    "packageName" to session.packageName,
                    "totalTimeSeconds" to session.totalTimeSeconds,
                    "lastUsedTimestamp" to session.lastUsedTimestamp,
                    "date" to session.date
                )
            }
        )

        if (currentApp != null) {
            baseData["currentApp"] = mapOf(
                "appName" to currentApp.appName,
                "packageName" to currentApp.packageName,
                "startTime" to currentApp.startTime,
                "durationSeconds" to currentApp.durationSeconds
            )
        }

        document.set(baseData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FirebaseHelper", "Base sync successful ✓")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseHelper", "Base sync failed: ${e.message}")
            }

        // ── Step 2: Overwrite today's call logs (full list from midnight — no duplicates possible) ──
        if (callLogs.isNotEmpty()) {
            val callMaps = callLogs.map { call ->
                mapOf(
                    "contactName"     to call.contactName,
                    "phoneNumber"     to call.phoneNumber,
                    "type"            to call.type,
                    "durationSeconds" to call.durationSeconds,
                    "timestamp"       to call.timestamp,
                    "date"            to call.date
                )
            }
            document.update("callLogs", callMaps)
                .addOnSuccessListener {
                    Log.d("FirebaseHelper", "Call logs overwritten: ${callLogs.size} records ✓")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseHelper", "Call logs overwrite failed: ${e.message}")
                }
        }

        // ── Step 3: Overwrite today's messages (full list from midnight — no duplicates possible) ──
        if (messages.isNotEmpty()) {
            val messageMaps = messages.map { message ->
                mapOf(
                    "contactName"   to message.contactName,
                    "phoneNumber"   to message.phoneNumber,
                    "type"          to message.type,
                    "messageLength" to message.messageLength,
                    "timestamp"     to message.timestamp,
                    "date"          to message.date
                )
            }
            document.update("messages", messageMaps)
                .addOnSuccessListener {
                    Log.d("FirebaseHelper", "Messages overwritten: ${messages.size} records ✓")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseHelper", "Messages overwrite failed: ${e.message}")
                }
        }
    }
}
