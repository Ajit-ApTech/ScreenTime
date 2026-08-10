package com.screentime.admin.helpers

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.screentime.admin.models.AppSession
import com.screentime.admin.models.AppSessionEntry
import com.screentime.admin.models.CallRecord
import com.screentime.admin.models.ChildChipItem
import com.screentime.admin.models.FamilyItem
import com.screentime.admin.models.MessageRecord
import com.screentime.admin.models.NotificationRecord

class AdminFirebaseHelper {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun ensureAuth(onReady: () -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            Log.d("AdminFirebaseHelper", "Auth ready, UID=${user.uid}")
            onReady()
        } else {
            Log.d("AdminFirebaseHelper", "Signing in anonymously...")
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    Log.d("AdminFirebaseHelper", "Anonymous auth success, UID=${result.user?.uid}")
                    onReady()
                }
                .addOnFailureListener { e ->
                    Log.e("AdminFirebaseHelper", "Anonymous auth failed: ${e.message}")
                    onReady() // proceed anyway to try query
                }
        }
    }

    private fun getChildDocRef(familyId: String?, deviceId: String): DocumentReference {
        return if (!familyId.isNullOrEmpty()) {
            db.collection("families").document(familyId).collection("children").document(deviceId)
        } else {
            db.collection("children").document(deviceId)
        }
    }

    fun loadAllFamiliesAndChildren(onResult: (List<FamilyItem>, List<ChildChipItem>) -> Unit, onError: (String) -> Unit) {
        ensureAuth {
            Log.d("AdminFirebaseHelper", "Starting database load...")

            // Step 1: Get all family metadata documents
            db.collection("families").get().addOnSuccessListener { familyMetaDocs ->
                val familyMetaMap = mutableMapOf<String, Pair<String, String>>()
                for (doc in familyMetaDocs) {
                    val name = doc.getString("familyName") ?: "Family (${doc.id.take(6)})"
                    val code = doc.getString("inviteCode") ?: "------"
                    familyMetaMap[doc.id] = Pair(name, code)
                }
                Log.d("AdminFirebaseHelper", "Found ${familyMetaDocs.size()} family metadata docs")

                // Step 2: Try collectionGroup query first
                db.collectionGroup("children").get().addOnSuccessListener { groupChildDocs ->
                    Log.d("AdminFirebaseHelper", "Found ${groupChildDocs.size()} children via collectionGroup")

                    val childrenByFamily = mutableMapOf<String, MutableList<ChildChipItem>>()
                    for (doc in groupChildDocs) {
                        val familyId = doc.reference.parent.parent?.id ?: continue
                        val name = doc.getString("childName") ?: "Child (${doc.id.take(6)})"
                        val lastSeen = doc.getLong("lastSeen") ?: 0L
                        val isTrackingActive = doc.getBoolean("isTrackingActive") ?: true
                        val isOnline = (System.currentTimeMillis() - lastSeen < 60_000L) && isTrackingActive

                        val item = ChildChipItem(
                            id = doc.id,
                            name = name,
                            isOnline = isOnline,
                            lastSeen = lastSeen,
                            familyId = familyId,
                            isTrackingActive = isTrackingActive
                        )
                        childrenByFamily.getOrPut(familyId) { mutableListOf() }.add(item)
                    }

                    val familiesList = mutableListOf<FamilyItem>()
                    for ((fid, meta) in familyMetaMap) {
                        val children = childrenByFamily.remove(fid) ?: emptyList()
                        familiesList.add(FamilyItem(fid, meta.first, meta.second, children))
                    }
                    for ((fid, children) in childrenByFamily) {
                        familiesList.add(FamilyItem(fid, "Family (${fid.take(6)})", "------", children))
                    }

                    loadUnlinkedChildren { unlinkedList ->
                        onResult(familiesList, unlinkedList)
                    }

                }.addOnFailureListener { groupErr ->
                    Log.w("AdminFirebaseHelper", "collectionGroup query failed (${groupErr.message}), trying family-by-family fallback...")
                    
                    // Fallback: Query subcollections document by document
                    if (familyMetaMap.isEmpty()) {
                        loadUnlinkedChildren { unlinkedList ->
                            onResult(emptyList(), unlinkedList)
                        }
                    } else {
                        val familiesList = mutableListOf<FamilyItem>()
                        var processed = 0
                        val total = familyMetaMap.size

                        for ((fid, meta) in familyMetaMap) {
                            db.collection("families").document(fid).collection("children").get()
                                .addOnSuccessListener { childDocs ->
                                    val children = childDocs.map { doc ->
                                        val name = doc.getString("childName") ?: "Child (${doc.id.take(6)})"
                                        val lastSeen = doc.getLong("lastSeen") ?: 0L
                                        val isTrackingActive = doc.getBoolean("isTrackingActive") ?: true
                                        val isOnline = (System.currentTimeMillis() - lastSeen < 60_000L) && isTrackingActive
                                        ChildChipItem(id = doc.id, name = name, isOnline = isOnline, lastSeen = lastSeen, familyId = fid, isTrackingActive = isTrackingActive)
                                    }
                                    familiesList.add(FamilyItem(fid, meta.first, meta.second, children))
                                    processed++
                                    if (processed == total) {
                                        loadUnlinkedChildren { unlinkedList ->
                                            onResult(familiesList, unlinkedList)
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    processed++
                                    if (processed == total) {
                                        loadUnlinkedChildren { unlinkedList ->
                                            onResult(familiesList, unlinkedList)
                                        }
                                    }
                                }
                        }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("AdminFirebaseHelper", "Families query failed: ${e.message}")
                onError("Firebase Permission Denied or Network Error: ${e.message}")
            }
        }
    }

    private fun loadUnlinkedChildren(onResult: (List<ChildChipItem>) -> Unit) {
        db.collection("children").get().addOnSuccessListener { unlinkedDocs ->
            Log.d("AdminFirebaseHelper", "Found ${unlinkedDocs.size()} top-level unlinked children")
            val unlinkedList = unlinkedDocs.map { doc ->
                val name = doc.getString("childName") ?: "Unlinked (${doc.id.take(6)})"
                val lastSeen = doc.getLong("lastSeen") ?: 0L
                val isTrackingActive = doc.getBoolean("isTrackingActive") ?: true
                val isOnline = (System.currentTimeMillis() - lastSeen < 60_000L) && isTrackingActive
                ChildChipItem(id = doc.id, name = name, isOnline = isOnline, lastSeen = lastSeen, familyId = null, isTrackingActive = isTrackingActive)
            }
            onResult(unlinkedList)
        }.addOnFailureListener { e ->
            Log.w("AdminFirebaseHelper", "Unlinked query failed: ${e.message}")
            onResult(emptyList())
        }
    }

    fun listenToChildDocument(
        familyId: String?,
        deviceId: String,
        onData: (childName: String, appSessions: List<AppSession>, callLogs: List<CallRecord>, messages: List<MessageRecord>, notifs: List<NotificationRecord>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return getChildDocRef(familyId, deviceId).addSnapshotListener { doc, error ->
            if (error != null) {
                onError(error.message ?: "Error reading child document")
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                val name = doc.getString("childName") ?: "Unknown"

                @Suppress("UNCHECKED_CAST")
                val rawSessions = doc.get("appSessions") as? List<Map<*, *>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val rawCalls = doc.get("callLogs") as? List<Map<*, *>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val rawMsgs = doc.get("messages") as? List<Map<*, *>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val rawNotifs = doc.get("notifications") as? List<Map<*, *>> ?: emptyList()

                val appSessions = rawSessions.mapNotNull { m ->
                    val appName = m["appName"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val rawEntries = m["sessions"] as? List<Map<*, *>> ?: emptyList()
                    val sessionEntries = rawEntries.map { e ->
                        AppSessionEntry(
                            startTime = (e["startTime"] as? Long) ?: 0L,
                            endTime = (e["endTime"] as? Long) ?: 0L,
                            durationSeconds = (e["durationSeconds"] as? Long) ?: 0L
                        )
                    }
                    AppSession(
                        appName = appName,
                        packageName = m["packageName"] as? String ?: "",
                        totalTimeSeconds = (m["totalTimeSeconds"] as? Long) ?: 0L,
                        date = m["date"] as? String ?: "",
                        lastUsedTimestamp = (m["lastUsedTimestamp"] as? Long) ?: 0L,
                        sessions = sessionEntries.sortedBy { it.startTime }
                    )
                }

                val callLogs = rawCalls.mapNotNull { m ->
                    CallRecord(
                        contactName = m["contactName"] as? String ?: "Unknown",
                        phoneNumber = m["phoneNumber"] as? String ?: "",
                        type = m["type"] as? String ?: "",
                        durationSeconds = ((m["durationSeconds"] as? Long) ?: 0L).toInt(),
                        timestamp = (m["timestamp"] as? Long) ?: 0L,
                        date = m["date"] as? String ?: ""
                    )
                }

                val messages = rawMsgs.mapNotNull { m ->
                    MessageRecord(
                        contactName = m["contactName"] as? String ?: "Unknown",
                        phoneNumber = m["phoneNumber"] as? String ?: "",
                        type = m["type"] as? String ?: "",
                        messageLength = ((m["messageLength"] as? Long) ?: 0L).toInt(),
                        timestamp = (m["timestamp"] as? Long) ?: 0L,
                        date = m["date"] as? String ?: ""
                    )
                }

                val notifs = rawNotifs.mapNotNull { m ->
                    NotificationRecord(
                        appName = m["appName"] as? String ?: "Unknown",
                        title = m["title"] as? String ?: "",
                        text = m["text"] as? String ?: "",
                        timestamp = (m["timestamp"] as? Long) ?: 0L,
                        date = m["date"] as? String ?: ""
                    )
                }

                onData(name, appSessions, callLogs, messages, notifs)
            }
        }
    }

    fun updateChildName(familyId: String?, deviceId: String, newName: String, onComplete: (Boolean, String?) -> Unit) {
        getChildDocRef(familyId, deviceId).update("childName", newName)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun updateChildTrackingState(familyId: String?, deviceId: String, isTrackingActive: Boolean, onComplete: (Boolean, String?) -> Unit) {
        getChildDocRef(familyId, deviceId).update("isTrackingActive", isTrackingActive)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun updateAppSessions(familyId: String?, deviceId: String, sessions: List<AppSession>, onComplete: (Boolean, String?) -> Unit) {
        val list = sessions.map { s ->
            mapOf(
                "appName" to s.appName,
                "packageName" to s.packageName,
                "totalTimeSeconds" to s.totalTimeSeconds,
                "date" to s.date,
                "lastUsedTimestamp" to s.lastUsedTimestamp
            )
        }
        getChildDocRef(familyId, deviceId).update("appSessions", list)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun updateCallLogs(familyId: String?, deviceId: String, calls: List<CallRecord>, onComplete: (Boolean, String?) -> Unit) {
        val list = calls.map { c ->
            mapOf(
                "contactName" to c.contactName,
                "phoneNumber" to c.phoneNumber,
                "type" to c.type,
                "durationSeconds" to c.durationSeconds,
                "timestamp" to c.timestamp,
                "date" to c.date
            )
        }
        getChildDocRef(familyId, deviceId).update("callLogs", list)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun updateMessages(familyId: String?, deviceId: String, messages: List<MessageRecord>, onComplete: (Boolean, String?) -> Unit) {
        val list = messages.map { m ->
            mapOf(
                "contactName" to m.contactName,
                "phoneNumber" to m.phoneNumber,
                "type" to m.type,
                "messageLength" to m.messageLength,
                "timestamp" to m.timestamp,
                "date" to m.date
            )
        }
        getChildDocRef(familyId, deviceId).update("messages", list)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun updateNotifications(familyId: String?, deviceId: String, notifs: List<NotificationRecord>, onComplete: (Boolean, String?) -> Unit) {
        val list = notifs.map { n ->
            mapOf(
                "appName" to n.appName,
                "title" to n.title,
                "text" to n.text,
                "timestamp" to n.timestamp,
                "date" to n.date
            )
        }
        getChildDocRef(familyId, deviceId).update("notifications", list)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun deleteChildDevice(familyId: String?, deviceId: String, onComplete: (Boolean, String?) -> Unit) {
        getChildDocRef(familyId, deviceId).delete()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }

    fun deleteFamily(familyId: String, onComplete: (Boolean, String?) -> Unit) {
        db.collection("families").document(familyId).delete()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }
}
