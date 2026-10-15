package com.apporganizer.launcher.widget

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import androidx.core.graphics.drawable.toBitmap

/** Loads app icons as bitmaps for the widget (includes One UI icon themes). */
object WidgetIcons {
    const val PX = 144

    /** Every launchable activity, keyed the same way as AppEntity.appKey ("pkg/class"). */
    fun activities(context: Context): Map<String, LauncherActivityInfo> =
        context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .associateBy { "${it.componentName.packageName}/${it.componentName.className}" }

    fun render(info: LauncherActivityInfo?): Bitmap? =
        runCatching { info?.getBadgedIcon(0)?.toBitmap(PX, PX) }.getOrNull()
}
