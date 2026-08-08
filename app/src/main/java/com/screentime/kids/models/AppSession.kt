package com.screentime.kids.models

/**
 * A single continuous foreground session for an app.
 * Captured from ACTIVITY_RESUMED → ACTIVITY_PAUSED events.
 */
data class AppSessionEntry(
    val startTime: Long = 0L,       // epoch ms — when user opened the app
    val endTime: Long = 0L,         // epoch ms — when user left the app
    val durationSeconds: Long = 0L  // (endTime - startTime) / 1000
)

/**
 * Aggregated daily usage for a single app.
 * Contains both the total and the individual session list.
 */
data class AppSession(
    val appName: String,
    val packageName: String,
    val totalTimeSeconds: Long,
    val date: String,                           // "yyyy-MM-dd"
    val lastUsedTimestamp: Long = 0L,           // epoch ms — for sorting "recently used"
    val sessions: List<AppSessionEntry> = emptyList() // individual RESUME→PAUSE pairs
)
