package com.screentime.kids.models

data class AppSession(
    val appName: String,
    val packageName: String,
    val totalTimeSeconds: Long,
    val date: String, // "2026-06-26"
    val lastUsedTimestamp: Long = 0L // epoch ms — used for "recently used" sorting
)
