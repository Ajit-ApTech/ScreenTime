package com.screentime.kids.helpers

import android.content.Context
import android.provider.Telephony
import com.screentime.kids.models.MessageRecord
import java.text.SimpleDateFormat
import java.util.*

class MessageHelper(private val context: Context) {

    private val contactResolver = ContactResolver(context)

    /**
     * Fetches ALL messages that arrived today (since midnight).
     * We always fetch from midnight rather than from the last sync timestamp,
     * so we never miss a message that arrived between two sync cycles.
     * Deduplication against Firebase is handled server-side via a timestamp+address key.
     *
     * The [lastSyncTimestamp] parameter is kept for API compatibility but is no longer
     * used as the query filter — today's midnight is always used instead.
     */
    fun getNewMessages(lastSyncTimestamp: Long): List<MessageRecord> {
        return getTodayMessages()
    }

    /**
     * Fetches ALL SMS messages from today (since midnight local time).
     */
    fun getTodayMessages(): List<MessageRecord> {
        val messageRecords = mutableListOf<MessageRecord>()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val startOfToday = startOfTodayMillis()

        android.util.Log.d("MessageHelper", "Fetching ALL messages from $startOfToday (today's midnight)")

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.PERSON,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(startOfToday.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val personIdx  = it.getColumnIndex(Telephony.Sms.PERSON)
            val typeIdx    = it.getColumnIndex(Telephony.Sms.TYPE)
            val bodyIdx    = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx    = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val phoneNumber = it.getString(addressIdx) ?: ""
                val person      = it.getString(personIdx)
                val contactName = if (person.isNullOrEmpty()) {
                    contactResolver.resolveContactName(phoneNumber)
                } else {
                    person
                }
                val typeInt   = it.getInt(typeIdx)
                val body      = it.getString(bodyIdx) ?: ""
                val timestamp = it.getLong(dateIdx)

                val type = when (typeInt) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
                    Telephony.Sms.MESSAGE_TYPE_SENT  -> "sent"
                    else                             -> "unknown"
                }

                messageRecords.add(
                    MessageRecord(
                        contactName   = contactName,
                        phoneNumber   = phoneNumber,
                        type          = type,
                        messageLength = body.length,
                        timestamp     = timestamp,
                        date          = todayDate
                    )
                )
            }
        }

        android.util.Log.d("MessageHelper", "Found ${messageRecords.size} messages today")
        return messageRecords
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
