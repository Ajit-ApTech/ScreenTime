package com.screentime.admin.models

data class MessageRecord(
    val contactName: String,
    val phoneNumber: String,
    val type: String,
    val messageLength: Int,
    val timestamp: Long,
    val date: String
)
