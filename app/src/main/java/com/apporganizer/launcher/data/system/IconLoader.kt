package com.apporganizer.launcher.data.system

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 图标内存缓存（含 One UI 图标主题与工作资料角标）。 */
class IconLoader(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi
    private val cache = LruCache<String, ImageBitmap>(400)

    fun cached(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(packageName: String, className: String, key: String): ImageBitmap? =
        cache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching {
                val info = launcherApps.getActivityList(packageName, Process.myUserHandle())
                    .firstOrNull { it.componentName.className == className }
                    ?: return@runCatching null
                info.getBadgedIcon(density).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }

    fun invalidatePackage(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
    }

    private companion object { const val ICON_PX = 160 }
}
