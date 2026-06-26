package com.screentime.kids.helpers

import android.content.Context
import android.provider.CallLog
import com.screentime.kids.models.CallRecord
import java.text.SimpleDateFormat
import java.util.*

class CallLogHelper(private val context: Context) {

    private val contactResolver = ContactResolver(context)

    fun getNewCallLogs(lastSyncTimestamp: Long): List<CallRecord> {
        val callRecords = mutableListOf<CallRecord>()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.DATE} > ?",
            arrayOf(lastSyncTimestamp.toString()),
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val phoneNumber = it.getString(numberIdx) ?: ""
                val cachedName = it.getString(nameIdx)
                val contactName = contactResolver.resolveContactName(phoneNumber)
                val typeInt = it.getInt(typeIdx)
                val duration = it.getInt(durationIdx)
                val timestamp = it.getLong(dateIdx)

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                    else -> "unknown"
                }

                callRecords.add(
                    CallRecord(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        type = type,
                        durationSeconds = duration,
                        timestamp = timestamp,
                        date = todayDate
                    )
                )
            }
        }

        return callRecords
    }
}
