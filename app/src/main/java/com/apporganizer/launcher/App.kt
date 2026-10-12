package com.apporganizer.launcher

import android.app.Application
import android.content.Context
import com.apporganizer.launcher.data.AppRepository
import com.apporganizer.launcher.data.db.AppDatabase
import com.apporganizer.launcher.data.prefs.SettingsStore
import com.apporganizer.launcher.data.system.IconLoader
import com.apporganizer.launcher.data.system.InstalledAppsSource
import com.apporganizer.launcher.data.system.UsageStatsSource
import com.apporganizer.launcher.work.DailyWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.repository.startWatchingPackages()
        DailyWorkScheduler.schedule(this)
    }
}

/** 手动依赖注入（MVP 不引入 Hilt，减少构建复杂度）。 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val iconLoader = IconLoader(context)
    val repository = AppRepository(
        context = context,
        db = AppDatabase.build(context),
        settings = SettingsStore(context),
        installed = InstalledAppsSource(context),
        usage = UsageStatsSource(context),
        icons = iconLoader,
        scope = appScope,
    )
}
