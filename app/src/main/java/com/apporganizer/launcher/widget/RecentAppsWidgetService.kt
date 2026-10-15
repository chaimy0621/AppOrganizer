package com.apporganizer.launcher.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.apporganizer.launcher.App
import com.apporganizer.launcher.R
import com.apporganizer.launcher.data.db.AppEntity
import kotlinx.coroutines.runBlocking

class RecentAppsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)
}

/**
 * Supplies the scrolling part of the widget (everything except the pinned row).
 * All icons are prepared in onDataSetChanged so scrolling never has to wait — that wait is
 * what showed "Loading…" before.
 */
private class Factory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var apps: List<AppEntity> = emptyList()
    private var icons: Map<String, Bitmap> = emptyMap()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val repo = (context as App).container.repository
        val list = runBlocking {
            repo.syncInstalledApps()
            val pinned = repo.pinnedApps().map { it.appKey }.toSet()
            repo.recentApps().filter { it.appKey !in pinned }
        }
        val activities = WidgetIcons.activities(context)
        val previous = icons
        icons = list.mapNotNull { app ->
            val bmp = previous[app.appKey] ?: WidgetIcons.render(activities[app.appKey])
            bmp?.let { app.appKey to it }
        }.toMap()
        apps = list
    }

    override fun getCount() = apps.size

    override fun getViewAt(position: Int): RemoteViews {
        val app = apps.getOrNull(position) ?: return blank()
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            icons[app.appKey]?.let { setImageViewBitmap(R.id.icon, it) }
            setContentDescription(R.id.icon, app.label)
            setOnClickFillInIntent(
                R.id.item,
                Intent()
                    .putExtra(LaunchActivity.EXTRA_PACKAGE, app.packageName)
                    .putExtra(LaunchActivity.EXTRA_CLASS, app.className),
            )
        }
    }

    /** Shown instead of the system's "Loading…" text if an item is ever not ready. */
    override fun getLoadingView(): RemoteViews = blank()

    private fun blank() = RemoteViews(context.packageName, R.layout.widget_item)

    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) =
        apps.getOrNull(position)?.appKey?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() { icons = emptyMap() }
}
