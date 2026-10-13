package com.apporganizer.launcher.widget

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import com.apporganizer.launcher.App
import com.apporganizer.launcher.R
import com.apporganizer.launcher.data.db.AppEntity
import kotlinx.coroutines.runBlocking

class RecentAppsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)
}

private class Factory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private var apps: List<AppEntity> = emptyList()
    private val icons = HashMap<String, Bitmap>()

    override fun onCreate() = Unit

    /** Runs on a background thread; blocking here is expected by the widget framework. */
    override fun onDataSetChanged() {
        val repo = (context as App).container.repository
        apps = runBlocking {
            repo.syncInstalledApps()
            repo.recentApps()
        }
        val keep = apps.map { it.appKey }.toSet()
        icons.keys.retainAll(keep)
    }

    override fun getCount() = apps.size

    override fun getViewAt(position: Int): RemoteViews? {
        val app = apps.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            iconFor(app)?.let { setImageViewBitmap(R.id.icon, it) }
            setContentDescription(R.id.icon, app.label)
            setOnClickFillInIntent(
                R.id.item,
                Intent()
                    .putExtra(LaunchActivity.EXTRA_PACKAGE, app.packageName)
                    .putExtra(LaunchActivity.EXTRA_CLASS, app.className),
            )
        }
    }

    private fun iconFor(app: AppEntity): Bitmap? = icons[app.appKey] ?: runCatching {
        launcherApps.getActivityList(app.packageName, Process.myUserHandle())
            .firstOrNull { it.componentName.className == app.className }
            ?.getBadgedIcon(0)
            ?.toBitmap(ICON_PX, ICON_PX)
    }.getOrNull()?.also { icons[app.appKey] = it }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = apps.getOrNull(position)?.appKey?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() { icons.clear() }

    private companion object { const val ICON_PX = 120 }
}
