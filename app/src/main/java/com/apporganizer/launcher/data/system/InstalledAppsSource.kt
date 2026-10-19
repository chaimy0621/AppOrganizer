package com.apporganizer.launcher.data.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.provider.MediaStore

data class InstalledApp(
    val key: String,
    val packageName: String,
    val className: String,
    val label: String,
    val installedAt: Long,
    val systemCategory: Int,
    val isGame: Boolean,
)

/** Reads the current user's launchable apps via LauncherApps (work profile / Secure Folder not handled yet). */
class InstalledAppsSource(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pm = context.packageManager

    fun loadAll(): List<InstalledApp> {
        val user = Process.myUserHandle()
        return launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val ai: ApplicationInfo = info.applicationInfo
                val pkg = info.componentName.packageName
                @Suppress("DEPRECATION")
                InstalledApp(
                    key = keyOf(pkg, info.componentName.className),
                    packageName = pkg,
                    className = info.componentName.className,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { pkg },
                    installedAt = firstInstallTime(pkg),
                    systemCategory = ai.category,
                    isGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
                )
            }
    }

    private fun firstInstallTime(pkg: String): Long = try {
        pm.getPackageInfo(pkg, 0).firstInstallTime
    } catch (e: PackageManager.NameNotFoundException) {
        System.currentTimeMillis()
    }

    fun launch(packageName: String, className: String, bounds: Rect? = null): Boolean = try {
        launcherApps.startMainActivity(ComponentName(packageName, className), Process.myUserHandle(), bounds, null)
        true
    } catch (e: SecurityException) {
        false
    } catch (e: ActivityNotFoundException) {
        false
    }

    /** Default dock: phone, WhatsApp, camera, browser (resolved to this device's default handlers). */
    fun defaultDockPackages(): List<String> {
        fun resolve(intent: Intent): String? =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" } // "android" = the system chooser, meaning no default app is set

        return listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            "com.whatsapp",
            resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
        ).distinct()
    }

    companion object {
        fun keyOf(pkg: String, cls: String) = "$pkg/$cls"
    }
}
