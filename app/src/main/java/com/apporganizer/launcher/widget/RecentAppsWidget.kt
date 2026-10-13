package com.apporganizer.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.apporganizer.launcher.R
import com.apporganizer.launcher.ui.MainActivity

/** Home-screen widget: every app, most recently used at the top, scrolls down. */
class RecentAppsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.grid)
    }

    companion object {
        @Suppress("DEPRECATION")
        fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_apps)

            val adapter = Intent(context, RecentAppsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.grid, adapter)
            views.setEmptyView(R.id.grid, R.id.empty)

            // Each icon fills in which app to open; LaunchActivity opens it and records the tap.
            val template = PendingIntent.getActivity(
                context, 0, Intent(context, LaunchActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.grid, template)

            val search = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_SEARCH)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.search_button, search)
            views.setOnClickPendingIntent(R.id.title, search)
            return views
        }

        /** Re-sorts every placed widget (after a launch, install, or permission change). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecentAppsWidget::class.java))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.grid)
        }

        fun isPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RecentAppsWidget::class.java))
                .isNotEmpty()
    }
}
