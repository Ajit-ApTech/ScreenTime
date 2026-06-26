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
        val event = UsageEvents.Event()

        // Track precise durations and last used times
        val appUsageTimeMap = mutableMapOf<String, Long>()
        val appLastResumedMap = mutableMapOf<String, Long>()
        val appLastUsedMap = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            
            // We only care about foreground activity transitions
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                appLastResumedMap[pkg] = time
                appLastUsedMap[pkg] = time
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                val lastResumed = appLastResumedMap[pkg]
                if (lastResumed != null) {
                    val duration = time - lastResumed
                    if (duration > 0) {
                        appUsageTimeMap[pkg] = appUsageTimeMap.getOrDefault(pkg, 0L) + duration
                    }
                    appLastResumedMap.remove(pkg) // Consumed
                }
                appLastUsedMap[pkg] = time
            }
        }

        // Add time for apps that are currently resumed right now
        appLastResumedMap.forEach { (pkg, lastResumed) ->
            val duration = now - lastResumed
            if (duration > 0) {
                appUsageTimeMap[pkg] = appUsageTimeMap.getOrDefault(pkg, 0L) + duration
            }
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val nameMap = mutableMapOf<String, String>()
        val appSessions = mutableListOf<AppSession>()

        for ((packageName, totalTimeMs) in appUsageTimeMap) {
            // Skip apps with practically no usage
            if (totalTimeMs < 1000) continue
            // Skip this monitoring app itself
            if (packageName == context.packageName) continue
            // Skip invisible OS daemons and the home screen launcher
            if (isExcluded(packageName)) continue

            val appName = nameMap.getOrPut(packageName) {
                try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
            }

            appSessions.add(
                AppSession(
                    appName = appName,
                    packageName = packageName,
                    totalTimeSeconds = totalTimeMs / 1000,
                    date = todayDate,
                    lastUsedTimestamp = appLastUsedMap[packageName] ?: 0L
                )
            )
        }

        android.util.Log.d("AppUsageHelper", "Returning ${appSessions.size} exact app sessions")

        // Sort by most recently used first
        return appSessions.sortedByDescending { it.lastUsedTimestamp }
    }

    fun getCurrentForegroundApp(): com.screentime.kids.models.CurrentAppInfo? {
        if (!hasUsageStatsPermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)

        val events = usageStatsManager.queryEvents(todayStart, now)
        val event = UsageEvents.Event()
        
        var currentForegroundPkg: String? = null
        var currentForegroundStartTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPkg = event.packageName
                currentForegroundStartTime = event.timeStamp
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                if (currentForegroundPkg == event.packageName) {
                    currentForegroundPkg = null // It was backgrounded
                }
            }
        }

        if (currentForegroundPkg == null) return null
        if (currentForegroundPkg == context.packageName) return null
        if (isExcluded(currentForegroundPkg)) return null

        return try {
            val appInfo = packageManager.getApplicationInfo(currentForegroundPkg, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            com.screentime.kids.models.CurrentAppInfo(
                appName = appName,
                packageName = currentForegroundPkg,
                startTime = currentForegroundStartTime,
                durationSeconds = (now - currentForegroundStartTime) / 1000
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
