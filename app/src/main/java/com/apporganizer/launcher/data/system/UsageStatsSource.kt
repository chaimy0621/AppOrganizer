package com.apporganizer.launcher.data.system

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Reads only which app came to the foreground and when.
 * Never reads notifications, messages, passwords, files or screen content; the UsageStats API does not expose them.
 */
class UsageStatsSource(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasAccess(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Last time each app was on screen (package → epoch ms). Empty without Usage Access. */
    fun lastUsedTimes(from: Long, to: Long): Map<String, Long> {
        if (!hasAccess()) return emptyMap()
        return usm.queryAndAggregateUsageStats(from, to)
            .mapValues { it.value.lastTimeVisible }
            .filterValues { it > 0L }
    }

    /**
     * Returns (packageName, timestamp) pairs. One "launch" = the foreground switches to this package from another;
     * switching activities inside one app is not counted again; returning to the same app after a screen lock counts as new.
     * The system keeps only a few days of events, so they are copied into the local database regularly.
     */
    fun readLaunches(from: Long, to: Long): List<Pair<String, Long>> {
        if (!hasAccess()) return emptyList()
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<Pair<String, Long>>()
        val e = UsageEvents.Event()
        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = e.packageName ?: continue
                    if (pkg != lastForeground) {
                        if (pkg != context.packageName) out += pkg to e.timeStamp
                        lastForeground = pkg
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> lastForeground = null
            }
        }
        return out
    }
}
