package com.screentime.kids.models

data class MessageRecord(
    val contactName: String,
    val phoneNumber: String,
    val type: String, // "received" | "sent"
    val messageLength: Int, // character count only — NOT the actual body
    val timestamp: Long,
    val date: String
)
