package com.screentime.kids.helpers

import android.content.Context
import android.provider.Telephony
import com.screentime.kids.models.MessageRecord
import java.text.SimpleDateFormat
import java.util.*

class MessageHelper(private val context: Context) {

    private val contactResolver = ContactResolver(context)

    fun getNewMessages(lastSyncTimestamp: Long): List<MessageRecord> {
        val messageRecords = mutableListOf<MessageRecord>()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
            "${Telephony.Sms.DATE} > ?",
            arrayOf(lastSyncTimestamp.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val personIdx = it.getColumnIndex(Telephony.Sms.PERSON)
            val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val phoneNumber = it.getString(addressIdx) ?: ""
                val person = it.getString(personIdx)
                val contactName = if (person.isNullOrEmpty()) {
                    contactResolver.resolveContactName(phoneNumber)
                } else {
                    person
                }
                val typeInt = it.getInt(typeIdx)
                val body = it.getString(bodyIdx) ?: ""
                val timestamp = it.getLong(dateIdx)

                val type = when (typeInt) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
                    else -> "unknown"
                }

                messageRecords.add(
                    MessageRecord(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        type = type,
                        messageLength = body.length,
                        timestamp = timestamp,
                        date = todayDate
                    )
                )
            }
        }

        return messageRecords
    }
}
