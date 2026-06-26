package com.screentime.kids.helpers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.screentime.kids.models.AppSession
import java.text.SimpleDateFormat
import java.util.*

class AppUsageHelper(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Returns true if the "Usage Access" special permission has been granted by the user.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayAppSessions(): List<AppSession> {
        if (!hasUsageStatsPermission()) {
            android.util.Log.w("AppUsageHelper", "Usage stats permission not granted")
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)

        android.util.Log.d("AppUsageHelper", "Querying exact events from $todayStart to $now")

        val events = usageStatsManager.queryEvents(todayStart, now)

        val usageMap = mutableMapOf<String, Long>() // PackageName -> Total foreground time in ms
        val lastUsedMap = mutableMapOf<String, Long>() // PackageName -> Last time used
        val nameMap = mutableMapOf<String, String>() // PackageName -> App Name
        val startTimes = mutableMapOf<String, Long>() // PackageName -> Start time of current session
        val hasSeenEvent = mutableSetOf<String>() // To handle sessions that started before midnight

        var eventCount = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            val packageName = event.packageName

            if (packageName == context.packageName || isExcluded(packageName)) continue

            // --- DEBUG ---
            android.util.Log.d("AppUsageHelper", "EVENT #$eventCount: pkg=$packageName type=${event.eventType} time=${event.timeStamp}")
            // -------------

            val isFirstEventForPackage = hasSeenEvent.add(packageName)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    startTimes[packageName] = event.timeStamp
                    lastUsedMap[packageName] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val startTime = startTimes.remove(packageName)
                    
                    // If we see a PAUSE as the very first event for this package, 
                    // it means the session started before midnight.
                    val activeStart = if (startTime != null) {
                        startTime
                    } else if (isFirstEventForPackage) {
                        todayStart
                    } else {
                        android.util.Log.d("AppUsageHelper", "IGNORING PAUSE for $packageName (duplicate/no start)")
                        continue // Duplicate PAUSE event, ignore
                    }

                    val duration = event.timeStamp - activeStart
                    android.util.Log.d("AppUsageHelper", "PAUSE for $packageName: duration=$duration")
                    if (duration in 1..86_400_000L) { // Sanity check: > 0 and <= 24 hours
                        usageMap[packageName] = (usageMap[packageName] ?: 0L) + duration
                    } else {
                        android.util.Log.d("AppUsageHelper", "INVALID DURATION for $packageName: duration=$duration")
                    }
                }
            }
        }

        // Add currently running apps
        for ((packageName, startTime) in startTimes) {
            val duration = now - startTime
            if (duration > 0) {
                usageMap[packageName] = (usageMap[packageName] ?: 0L) + duration
                lastUsedMap[packageName] = now
            }
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val appSessions = usageMap.mapNotNull { (packageName, totalTimeMs) ->
            android.util.Log.d("AppUsageHelper", "Package $packageName totalTimeMs=$totalTimeMs")
            if (totalTimeMs < 1000) return@mapNotNull null // Ignore sub-second usage

            val appName = nameMap.getOrPut(packageName) {
                try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
            }

            AppSession(
                appName = appName,
                packageName = packageName,
                totalTimeSeconds = totalTimeMs / 1000,
                date = todayDate,
                lastUsedTimestamp = lastUsedMap[packageName] ?: 0L
            )
        }

        android.util.Log.d("AppUsageHelper", "Returning ${appSessions.size} app sessions (queryEvents)")

        // Sort by most recently used first
        val sortedSessions = appSessions.sortedByDescending { it.lastUsedTimestamp }

        if (sortedSessions.isNotEmpty()) {
            return sortedSessions
        }

        // --- FALLBACK FOR API 35/36 EMULATORS WHERE queryEvents RETURNS EMPTY ---
        android.util.Log.d("AppUsageHelper", "queryEvents returned 0 sessions, falling back to queryUsageStats")
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, todayStart, now)
        val fallbackUsageMap = mutableMapOf<String, Pair<Long, Long>>()

        for (stat in stats ?: emptyList()) {
            val packageName = stat.packageName
            val totalTime = stat.totalTimeInForeground
            val lastUsed = stat.lastTimeUsed

            if (totalTime <= 0 || packageName == context.packageName || isExcluded(packageName)) continue
            if (lastUsed < todayStart) continue

            val cappedTime = minOf(totalTime, 86_400_000L)
            val appName = nameMap.getOrPut(packageName) {
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                } catch (e: Exception) {
                    packageName
                }
            }

            val existing = fallbackUsageMap[packageName]
            if (existing == null || cappedTime > existing.first) {
                fallbackUsageMap[packageName] = Pair(cappedTime, lastUsed)
                nameMap[packageName] = appName
            }
        }

        return fallbackUsageMap.map { (packageName, usage) ->
            AppSession(
                appName = nameMap[packageName] ?: packageName,
                packageName = packageName,
                totalTimeSeconds = usage.first / 1000,
                date = todayDate,
                lastUsedTimestamp = usage.second
            )
        }.sortedByDescending { it.lastUsedTimestamp }
    }

    fun getCurrentForegroundApp(): com.screentime.kids.models.CurrentAppInfo? {
        if (!hasUsageStatsPermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Only need a short lookback window for current foreground app. Using todayStart for consistency.
        val todayStart = getStartOfDay(now)

        val events = usageStatsManager.queryEvents(todayStart, now)
        var currentForegroundPackage: String? = null
        var lastResumeTime = 0L

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName

            if (packageName == context.packageName || isExcluded(packageName)) continue

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPackage = packageName
                lastResumeTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentForegroundPackage == packageName) {
                    currentForegroundPackage = null
                }
            }
        }

        if (currentForegroundPackage != null) {
            return try {
                val appInfo = packageManager.getApplicationInfo(currentForegroundPackage, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                com.screentime.kids.models.CurrentAppInfo(
                    appName = appName,
                    packageName = currentForegroundPackage,
                    startTime = lastResumeTime,
                    durationSeconds = (now - lastResumeTime) / 1000
                )
            } catch (e: Exception) {
                null
            }
        }

        // --- FALLBACK FOR API 35/36 EMULATORS WHERE queryEvents RETURNS EMPTY ---
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, todayStart, now)
        val bestEntry = stats
            ?.filter { stat ->
                stat.lastTimeUsed >= todayStart &&
                stat.packageName != context.packageName &&
                !isExcluded(stat.packageName)
            }
            ?.maxByOrNull { it.lastTimeUsed }
            ?: return null

        return try {
            val appInfo = packageManager.getApplicationInfo(bestEntry.packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            com.screentime.kids.models.CurrentAppInfo(
                appName = appName,
                packageName = bestEntry.packageName,
                startTime = bestEntry.lastTimeUsed,
                durationSeconds = (now - bestEntry.lastTimeUsed) / 1000
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true for packages that should never appear in a screen time report:
     *  - Android home screen launchers (they're "in foreground" between every app open)
     *  - Low-level OS daemons and background services
     *  - Overlay/RRO packages
     */
    private fun isExcluded(packageName: String): Boolean {
        // ── Launchers / home screens ──────────────────────────────────────────────
        // These accumulate enormous foreground time simply by being the home screen.
        val launcherKeywords = listOf(
            "launcher", "nexuslauncher", "trebuchet", "quickstep",
            "home", "zerolauncher", "novaLauncher", "miuihome",
            "com.sec.android.app.launcher", "com.huawei.android.launcher"
        )
        if (launcherKeywords.any { packageName.contains(it, ignoreCase = true) }) return true

        // ── Invisible background process prefixes ─────────────────────────────────
        val bgPrefixes = listOf(
            "android",
            "com.android.systemui",
            "com.android.inputmethod",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.providers",
            "com.android.server",
            "com.android.permissioncontroller",
            "com.android.intentresolver",
            "com.android.wallpaper",
            "com.android.internal",
            "com.google.android.ondevicepersonalization",
            "com.google.android.odad",
            "com.google.android.uwb",
            "com.android.companiondevicemanager",
            "com.android.location",
            "com.android.networkstack",
            "com.android.connectivity",
            "com.android.wifi",
            "com.google.android.wifi",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.vpndialogs",
            "com.android.shell",
            "com.android.devicelockcontroller",
            "com.android.emulation",
            "com.google.android.cellbroadcastreceiver",
            "com.google.android.inputmethod",
            "com.android.wallpaperbackup",
            "com.android.traceur",
            "com.android.storagemanager",
            "com.android.emergency",
            "com.android.bluetoothmidiservice",
            "com.android.userdictionary"
        )

        if (bgPrefixes.any { prefix ->
            packageName == prefix || packageName.startsWith("$prefix.")
        }) return true

        // ── Overlay / RRO packages ────────────────────────────────────────────────
        if (packageName.contains(".auto_generated_")) return true

        return false
    }

    private fun getStartOfDay(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
