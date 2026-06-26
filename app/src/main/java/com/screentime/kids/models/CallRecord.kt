package com.screentime.kids.models

data class CallRecord(
    val contactName: String,
    val phoneNumber: String,
    val type: String, // "incoming" | "outgoing" | "missed"
    val durationSeconds: Int,
    val timestamp: Long,
    val date: String
)
