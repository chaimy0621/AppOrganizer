package com.apporganizer.launcher.data.system

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * 只读取“哪个应用在什么时间被切到前台”。
 * 不读取通知、聊天内容、密码、文件或屏幕内容 —— UsageStats API 本身也不提供这些。
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

    /**
     * 返回 (packageName, 时间戳) 列表。一次“启动”= 前台应用从别的包切换到该包；
     * 同一应用内部切换 Activity 不重复计数；锁屏后重新回到同一应用算一次新启动。
     * 系统通常只保留最近数天的事件，所以由每日任务持续写入本地数据库。
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
