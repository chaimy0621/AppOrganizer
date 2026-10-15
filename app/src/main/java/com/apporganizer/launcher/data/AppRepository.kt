package com.apporganizer.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.room.withTransaction
import com.apporganizer.launcher.data.db.AppDatabase
import com.apporganizer.launcher.data.db.AppEntity
import com.apporganizer.launcher.data.db.FolderEntity
import com.apporganizer.launcher.data.db.LaunchEventEntity
import com.apporganizer.launcher.data.db.ScoreEntity
import com.apporganizer.launcher.data.prefs.SettingsStore
import com.apporganizer.launcher.data.system.IconLoader
import com.apporganizer.launcher.data.system.InstalledAppsSource
import com.apporganizer.launcher.data.system.UsageStatsSource
import com.apporganizer.launcher.domain.Category
import com.apporganizer.launcher.domain.Classifier
import com.apporganizer.launcher.domain.RankingEngine
import com.apporganizer.launcher.widget.RecentAppsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 唯一的数据入口：首页常用区、文件夹、Dock 都从这里读取同一份数据。 */
class AppRepository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: SettingsStore,
    private val installed: InstalledAppsSource,
    private val usage: UsageStatsSource,
    private val icons: IconLoader,
    private val scope: CoroutineScope,
) {
    private val appDao = db.appDao()
    private val folderDao = db.folderDao()
    private val eventDao = db.launchEventDao()
    private val scoreDao = db.scoreDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val syncMutex = Mutex()

    val apps = appDao.observeAll()
    val folders = folderDao.observeAll()
    val scores = scoreDao.observeAll()

    fun hasUsageAccess(): Boolean = usage.hasAccess()

    // ---------- 应用安装 / 卸载监听 ----------

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = resync(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = resync(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = resync(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = resync(null)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = resync(null)
    }

    private fun resync(packageName: String?) {
        packageName?.let { icons.invalidatePackage(it) }
        scope.launch {
            syncInstalledApps()
            RecentAppsWidget.refresh(context)
        }
    }

    fun startWatchingPackages() {
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
    }

    // ---------- 扫描与分类 ----------

    /**
     * 同步可启动应用列表。新应用自动分类；已存在的应用保留用户设置。
     * @param reclassify true = 重新计算所有应用的 autoCategory（用户手动分类仍然保留）。
     */
    suspend fun syncInstalledApps(reclassify: Boolean = false) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureSmartFolders()
            val now = System.currentTimeMillis()
            val current = installed.loadAll()
            val existing = appDao.getAll().associateBy { it.appKey }

            val upserts = current.map { a ->
                val old = existing[a.key]
                val auto = if (old == null || reclassify) {
                    Classifier.classify(Classifier.Input(a.packageName, a.label, a.systemCategory, a.isGame)).name
                } else {
                    old.autoCategory
                }
                old?.copy(label = a.label, installedAt = a.installedAt, autoCategory = auto)
                    ?: AppEntity(
                        appKey = a.key,
                        packageName = a.packageName,
                        className = a.className,
                        label = a.label,
                        installedAt = a.installedAt,
                        firstSeenAt = now,
                        autoCategory = auto,
                    )
            }
            val removed = existing.keys - current.map { it.key }.toSet()
            db.withTransaction {
                appDao.upsertAll(upserts)
                if (removed.isNotEmpty()) appDao.deleteByKeys(removed.toList())
            }
            if (!settings.dockInitialized() && upserts.isNotEmpty()) {
                initDefaultDock(upserts)
                settings.setDockInitialized()
            }
        }
    }

    private suspend fun ensureSmartFolders() {
        val have = folderDao.getAll().mapNotNull { it.category }.toSet()
        val missing = Category.entries.filter { it.name !in have }
        if (missing.isNotEmpty()) {
            folderDao.insertAll(missing.map { FolderEntity(name = it.displayName, category = it.name, sortOrder = it.ordinal) })
        }
    }

    private suspend fun initDefaultDock(apps: List<AppEntity>) {
        val byPkg = apps.groupBy { it.packageName }
        installed.defaultDockPackages()
            .mapNotNull { byPkg[it]?.firstOrNull() }
            .take(MAX_DOCK)
            .forEachIndexed { i, app -> appDao.setDockSlot(app.appKey, i) }
    }

    // ---------- 使用记录 ----------

    /** 把系统 UsageStats 事件增量写入本地数据库（没有 Usage Access 时什么都不做）。 */
    suspend fun ingestUsage() = withContext(Dispatchers.IO) {
        if (!usage.hasAccess()) return@withContext
        val now = System.currentTimeMillis()
        val last = settings.lastIngestAt()
        val from = if (last > 0) last - 5 * 60_000L else now - 30 * RankingEngine.DAY
        val launches = usage.readLaunches(from, now)
        if (launches.isNotEmpty()) {
            eventDao.insertAll(launches.map { (pkg, at) ->
                LaunchEventEntity(packageName = pkg, at = at, source = LaunchEventEntity.SOURCE_SYSTEM)
            })
        }
        settings.setLastIngestAt(now)
    }

    /** 本 Launcher 内部点击记录：无 Usage Access 时作为降级学习数据。 */
    fun recordLaunch(packageName: String) {
        scope.launch(Dispatchers.IO) {
            eventDao.insert(
                LaunchEventEntity(packageName = packageName, at = System.currentTimeMillis(), source = LaunchEventEntity.SOURCE_LAUNCHER)
            )
            RecentAppsWidget.refresh(context)
        }
    }

    /** Opens an app from the widget or search and remembers the tap. */
    fun launchComponent(packageName: String, className: String, bounds: android.graphics.Rect? = null): Boolean {
        val ok = installed.launch(packageName, className, bounds)
        if (ok) recordLaunch(packageName)
        return ok
    }

    /** Apps in the widget's pinned row, left to right. */
    suspend fun pinnedApps(): List<AppEntity> = withContext(Dispatchers.IO) {
        appDao.getAll()
            .filter { it.lockedSlot != null && it.lockedSlot in 0 until MAX_PINS }
            .sortedBy { it.lockedSlot }
    }

    /** Replaces the pinned row with [keys] (left to right) and redraws the widget. */
    suspend fun setPins(keys: List<String>) {
        db.withTransaction {
            appDao.clearAllLocks()
            keys.take(MAX_PINS).forEachIndexed { i, key -> appDao.setLockedSlot(key, i) }
        }
        RecentAppsWidget.refresh(context)
    }

    /**
     * All launchable apps, most recently used first.
     * Uses the system's "last visible" time (needs Usage Access) plus taps made through this app;
     * apps never used go to the end in A–Z order.
     */
    suspend fun recentApps(): List<AppEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = HashMap<String, Long>()
        usage.lastUsedTimes(now - 365 * RankingEngine.DAY, now).forEach { (pkg, t) -> last.merge(pkg, t) { a, b -> maxOf(a, b) } }
        eventDao.since(now - 35 * RankingEngine.DAY, LaunchEventEntity.SOURCE_LAUNCHER)
            .forEach { last.merge(it.packageName, it.at) { a, b -> maxOf(a, b) } }
        appDao.getAll().sortedWith(
            compareByDescending<AppEntity> { last[it.packageName] ?: 0L }.thenBy { it.label.lowercase() }
        )
    }

    fun launch(app: AppEntity): Boolean {
        val ok = installed.launch(app.packageName, app.className)
        if (ok) recordLaunch(app.packageName)
        return ok
    }

    // ---------- 每日排序 ----------

    /** 重新计算所有应用分数并保存快照。由 DailyRankingWorker 每天调用一次。 */
    suspend fun recomputeScores() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val source = if (usage.hasAccess()) LaunchEventEntity.SOURCE_SYSTEM else LaunchEventEntity.SOURCE_LAUNCHER
        val byPkg = eventDao.since(now - 30 * RankingEngine.DAY, source)
            .groupBy({ it.packageName }, { it.at })
        val apps = appDao.getAll()
        val oldScores = scoreDao.getAll().associateBy { it.appKey }
        val slotCount = settings.current().layoutMode.frequentCount

        // 昨天在常用区的应用获得稳定性加成
        val previousTop = RankingEngine.arrange(
            apps.map { it.toItem(oldScores[it.appKey]) }, slotCount
        ).filterNotNull().toSet()

        val newScores = apps.map { a ->
            val stats = RankingEngine.statsFrom(byPkg[a.packageName].orEmpty(), now)
            var s = RankingEngine.score(stats, now)
            if (s > 0 && a.appKey in previousTop) s *= RankingEngine.STICKINESS
            ScoreEntity(appKey = a.appKey, score = s, lastUsedAt = stats.lastUsedAt, computedAt = now)
        }
        db.withTransaction {
            scoreDao.clear()
            scoreDao.insertAll(newScores)
        }
        eventDao.deleteOlderThan(now - 35 * RankingEngine.DAY)
        settings.setLastRankingAt(now)
    }

    // ---------- 用户操作 ----------

    suspend fun lockAt(appKey: String, slot: Int) = db.withTransaction {
        appDao.clearLockedSlot(slot)
        appDao.setLockedSlot(appKey, slot)
    }

    suspend fun unlock(appKey: String) = appDao.setLockedSlot(appKey, null)

    suspend fun setExcluded(appKey: String, excluded: Boolean) = appDao.setExcluded(appKey, excluded)

    /** folderId = null → 恢复跟随自动分类 */
    suspend fun moveToFolder(appKey: String, folderId: Long?) = appDao.setUserFolder(appKey, folderId)

    /** @return false = Dock 已满 */
    suspend fun addToDock(appKey: String): Boolean = db.withTransaction {
        val used = appDao.getAll().mapNotNull { it.dockSlot }.toSet()
        val free = (0 until MAX_DOCK).firstOrNull { it !in used } ?: return@withTransaction false
        appDao.setDockSlot(appKey, free)
        true
    }

    suspend fun removeFromDock(appKey: String) = appDao.setDockSlot(appKey, null)

    suspend fun createFolder(name: String): Long =
        folderDao.insert(FolderEntity(name = name.trim(), category = null, sortOrder = folderDao.maxOrder() + 1))

    suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name.trim())

    /** 删除文件夹：其中的应用回到自动分类。“其他”文件夹不可删除（兜底）。 */
    suspend fun deleteFolder(id: Long) = db.withTransaction {
        val f = folderDao.get(id) ?: return@withTransaction
        if (f.category == Category.OTHER.name) return@withTransaction
        appDao.clearUserFolder(id)
        if (f.category != null) folderDao.markDeleted(id) else folderDao.delete(id)
    }

    /** 恢复默认分类：清除所有手动分类和自建文件夹，恢复智能文件夹并重新分类。 */
    suspend fun resetClassification() {
        db.withTransaction {
            appDao.clearAllUserFolders()
            folderDao.deleteUserFolders()
            Category.entries.forEach { folderDao.restoreSmart(it.name, it.displayName, it.ordinal) }
        }
        syncInstalledApps(reclassify = true)
    }

    suspend fun rescan() = syncInstalledApps(reclassify = true)

    companion object {
        const val MAX_DOCK = 5
        const val MAX_PINS = 5
    }
}

internal fun AppEntity.toItem(score: ScoreEntity?) = RankingEngine.Item(
    key = appKey,
    label = label,
    score = score?.score ?: 0.0,
    lastUsedAt = score?.lastUsedAt,
    lockedSlot = lockedSlot,
    excluded = excludedFromRanking,
    inDock = dockSlot != null,
)
