package com.screentime.parent.models

import java.io.Serializable

/**
 * A single continuous foreground session (startTime → endTime) for an app.
 * Stored by the child app via ACTIVITY_RESUMED/PAUSED events.
 */
data class AppSessionEntry(
    val startTime: Long = 0L,       // epoch ms — when the user opened the app
    val endTime: Long = 0L,         // epoch ms — when the user left the app
    val durationSeconds: Long = 0L
) : Serializable

/**
 * Aggregated daily usage for a single app — contains both the total
 * and the list of individual sessions for drill-down display.
 */
data class AppSession(
    val appName: String,
    val packageName: String,
    val totalTimeSeconds: Long,
    val date: String,                               // "yyyy-MM-dd"
    val lastUsedTimestamp: Long = 0L,
    val sessions: List<AppSessionEntry> = emptyList()  // individual RESUME→PAUSE pairs
) : Serializable
