package com.screentime.kids.models

data class CurrentAppInfo(
    val appName: String,
    val packageName: String,
    val startTime: Long,
    val durationSeconds: Long
)
