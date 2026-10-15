package com.apporganizer.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.apporganizer.launcher.App
import com.apporganizer.launcher.R
import com.apporganizer.launcher.data.AppRepository
import com.apporganizer.launcher.data.db.AppEntity
import com.apporganizer.launcher.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen widget: a fixed pinned row (up to 5 apps) on top,
 * then every other app, most recently used first, scrolling down.
 */
class RecentAppsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as App).container.appScope.launch {
            try { updateAll(context.applicationContext) } finally { pending.finish() }
        }
    }

    companion object {
        private val PIN_CELLS = intArrayOf(R.id.pin_cell0, R.id.pin_cell1, R.id.pin_cell2, R.id.pin_cell3, R.id.pin_cell4)
        private val PIN_ICONS = intArrayOf(R.id.pin0, R.id.pin1, R.id.pin2, R.id.pin3, R.id.pin4)

        /** Redraws every placed widget: pinned row and the scrolling list. Safe to call from anywhere. */
        fun refresh(context: Context) {
            val app = context.applicationContext as App
            app.container.appScope.launch { updateAll(app) }
        }

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecentAppsWidget::class.java))
            if (ids.isEmpty()) return
            val pinned = (context.applicationContext as App).container.repository.pinnedApps()
            val icons = withContext(Dispatchers.IO) {
                val activities = WidgetIcons.activities(context)
                pinned.map { WidgetIcons.render(activities[it.appKey]) }
            }
            ids.forEach { manager.updateAppWidget(it, build(context, it, pinned, icons)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.grid)
        }

        fun isPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RecentAppsWidget::class.java))
                .isNotEmpty()

        @Suppress("DEPRECATION")
        private fun build(context: Context, widgetId: Int, pinned: List<AppEntity>, icons: List<Bitmap?>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_apps)

            // Scrolling list
            val adapter = Intent(context, RecentAppsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.grid, adapter)
            views.setEmptyView(R.id.grid, R.id.empty)
            views.setPendingIntentTemplate(
                R.id.grid,
                PendingIntent.getActivity(
                    context, 0, Intent(context, LaunchActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )

            // Pinned row
            val hasPins = pinned.isNotEmpty()
            views.setViewVisibility(R.id.pinned_row, if (hasPins) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pinned_divider, if (hasPins) View.VISIBLE else View.GONE)
            for (i in 0 until AppRepository.MAX_PINS) {
                val app = pinned.getOrNull(i)
                if (app == null) {
                    views.setViewVisibility(PIN_CELLS[i], View.INVISIBLE) // keeps columns aligned
                    continue
                }
                views.setViewVisibility(PIN_CELLS[i], View.VISIBLE)
                icons.getOrNull(i)?.let { views.setImageViewBitmap(PIN_ICONS[i], it) }
                views.setContentDescription(PIN_ICONS[i], app.label)
                views.setOnClickPendingIntent(
                    PIN_CELLS[i],
                    PendingIntent.getActivity(
                        context, 100 + i,
                        Intent(context, LaunchActivity::class.java)
                            .putExtra(LaunchActivity.EXTRA_PACKAGE, app.packageName)
                            .putExtra(LaunchActivity.EXTRA_CLASS, app.className),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }

            // Header buttons
            views.setOnClickPendingIntent(R.id.search_button, openMain(context, MainActivity.ACTION_SEARCH, 1))
            views.setOnClickPendingIntent(R.id.title, openMain(context, MainActivity.ACTION_SEARCH, 1))
            views.setOnClickPendingIntent(R.id.edit_button, openMain(context, MainActivity.ACTION_EDIT, 2))
            return views
        }

        private fun openMain(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
