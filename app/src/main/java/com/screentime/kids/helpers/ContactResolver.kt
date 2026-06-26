package com.screentime.kids.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup

class ContactResolver(private val context: Context) {

    fun resolveContactName(phoneNumber: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val projection = arrayOf(PhoneLookup.DISPLAY_NAME)

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    return cursor.getString(nameIdx)
                }
            }
        }

        return "Unknown"
    }
}
