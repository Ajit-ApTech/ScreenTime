package com.screentime.kids.helpers

import android.app.AppOpsManager
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

        android.util.Log.d("AppUsageHelper", "Querying usage stats from $todayStart to $now")

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            todayStart,
            now
        )

        android.util.Log.d("AppUsageHelper", "Raw stats count: ${stats?.size ?: 0}")

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // packageName -> Pair(totalTimeMs, lastUsedMs)
        val usageMap = mutableMapOf<String, Pair<Long, Long>>()
        val nameMap = mutableMapOf<String, String>()

        for (stat in stats ?: emptyList()) {
            val packageName = stat.packageName
            val totalTime = stat.totalTimeInForeground
            val lastUsed = stat.lastTimeUsed

            // Skip apps with no foreground time today
            if (totalTime <= 0) continue
            // Skip this monitoring app itself
            if (packageName == context.packageName) continue
            // Skip invisible OS daemons and the home screen launcher
            if (isExcluded(packageName)) {
                android.util.Log.d("AppUsageHelper", "Excluded: $packageName (${totalTime}ms)")
                continue
            }

            val appName = nameMap.getOrPut(packageName) {
                try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
            }

            // Keep the entry with the highest usage time (in case of duplicate buckets)
            val existing = usageMap[packageName]
            if (existing == null || totalTime > existing.first) {
                usageMap[packageName] = Pair(totalTime, lastUsed)
                nameMap[packageName] = appName
            }

            android.util.Log.d("AppUsageHelper", "Including: $appName ($packageName) — ${totalTime / 1000}s, lastUsed=$lastUsed")
        }

        val appSessions = usageMap.map { (packageName, usage) ->
            val (totalTime, lastUsed) = usage
            AppSession(
                appName = nameMap[packageName] ?: packageName,
                packageName = packageName,
                totalTimeSeconds = totalTime / 1000,
                date = todayDate,
                lastUsedTimestamp = lastUsed
            )
        }

        android.util.Log.d("AppUsageHelper", "Returning ${appSessions.size} app sessions")

        // Sort by most recently used first (most recent app at top)
        return appSessions.sortedByDescending { it.lastUsedTimestamp }
    }

    fun getCurrentForegroundApp(): com.screentime.kids.models.CurrentAppInfo? {
        if (!hasUsageStatsPermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDay(now)

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            todayStart,
            now
        )

        val bestEntry = stats
            ?.filter { stat ->
                stat.lastTimeUsed > 0 &&
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
