package com.screentime.kids.helpers

import android.content.Context
import android.provider.CallLog
import com.screentime.kids.models.CallRecord
import java.text.SimpleDateFormat
import java.util.*

class CallLogHelper(private val context: Context) {

    private val contactResolver = ContactResolver(context)

    /**
     * Fetches ALL calls that happened today (since midnight).
     * We always fetch from midnight rather than from the last sync timestamp,
     * so we never miss a call that happened between two sync cycles.
     * Deduplication against Firebase is handled server-side via a timestamp+number key.
     *
     * The [lastSyncTimestamp] parameter is kept for API compatibility but is no longer
     * used as the query filter — today's midnight is always used instead.
     */
    fun getNewCallLogs(lastSyncTimestamp: Long): List<CallRecord> {
        return getTodayCallLogs()
    }

    /**
     * Fetches ALL calls from today (since midnight local time).
     */
    fun getTodayCallLogs(): List<CallRecord> {
        val callRecords = mutableListOf<CallRecord>()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val startOfToday = startOfTodayMillis()

        android.util.Log.d("CallLogHelper", "Fetching ALL calls from $startOfToday (today's midnight)")

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
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(startOfToday.toString()),
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIdx   = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx     = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx     = it.getColumnIndex(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val phoneNumber = it.getString(numberIdx) ?: ""
                val contactName = contactResolver.resolveContactName(phoneNumber)
                val typeInt     = it.getInt(typeIdx)
                val duration    = it.getInt(durationIdx)
                val timestamp   = it.getLong(dateIdx)

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE   -> "missed"
                    CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                    else                        -> "unknown"
                }

                callRecords.add(
                    CallRecord(
                        contactName     = contactName,
                        phoneNumber     = phoneNumber,
                        type            = type,
                        durationSeconds = duration,
                        timestamp       = timestamp,
                        date            = todayDate
                    )
                )
            }
        }

        android.util.Log.d("CallLogHelper", "Found ${callRecords.size} calls today")
        return callRecords
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
