package com.screentime.kids.models

data class NotificationRecord(
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val date: String
)
