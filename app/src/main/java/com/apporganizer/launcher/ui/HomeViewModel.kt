package com.apporganizer.launcher.ui

import android.app.Application
import android.app.role.RoleManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apporganizer.launcher.App
import com.apporganizer.launcher.data.db.AppEntity
import com.apporganizer.launcher.data.db.FolderEntity
import com.apporganizer.launcher.data.db.ScoreEntity
import com.apporganizer.launcher.data.prefs.AppSettings
import com.apporganizer.launcher.data.prefs.LayoutMode
import com.apporganizer.launcher.data.toItem
import com.apporganizer.launcher.domain.Category
import com.apporganizer.launcher.domain.RankingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UsageMode { SYSTEM, INTERNAL }

data class AppUi(
    val key: String,
    val packageName: String,
    val className: String,
    val label: String,
    val locked: Boolean,
    val excluded: Boolean,
    val inDock: Boolean,
    val userAssigned: Boolean,
    val entity: AppEntity,
)

data class FolderUi(
    val id: Long,
    val name: String,
    val category: Category?,
    val isRecent: Boolean,
    val apps: List<AppUi>,
) {
    val editable get() = !isRecent
    val deletable get() = !isRecent && category != Category.OTHER
}

data class HomeUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val frequent: List<AppUi?> = emptyList(),
    val folders: List<FolderUi> = emptyList(),
    val dock: List<AppUi> = emptyList(),
    val allApps: List<AppUi> = emptyList(),
    /** 可作为“移动到…”目标的文件夹（不含“最近安装”） */
    val moveTargets: List<FolderEntity> = emptyList(),
    val usageMode: UsageMode = UsageMode.INTERNAL,
)

private data class Env(val settings: AppSettings, val now: Long, val usageAccess: Boolean)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as App).container
    private val repo = container.repository
    val iconLoader = container.iconLoader

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val usageAccess = MutableStateFlow(repo.hasUsageAccess())
    private val _roleHeld = MutableStateFlow(isHomeRoleHeld())
    val roleHeld: StateFlow<Boolean> = _roleHeld.asStateFlow()

    /** 每次在首页按 Home 键递增，用于关闭打开的文件夹/菜单。 */
    private val _homeSignal = MutableStateFlow(0)
    val homeSignal: StateFlow<Int> = _homeSignal.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val env = combine(repo.settings.flow, now, usageAccess) { s, t, u -> Env(s, t, u) }

    val state: StateFlow<HomeUiState> =
        combine(repo.apps, repo.folders, repo.scores, env) { apps, folders, scores, env ->
            buildState(apps, folders, scores, env)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch { repo.syncInstalledApps() }
    }

    private fun buildState(
        apps: List<AppEntity>,
        folders: List<FolderEntity>,
        scores: List<ScoreEntity>,
        env: Env,
    ): HomeUiState {
        val scoreMap = scores.associateBy { it.appKey }
        val live = folders.filter { !it.deleted }
        val liveIds = live.map { it.id }.toSet()
        val smartByCat = live.filter { it.category != null }.associateBy { it.category }
        val otherId = smartByCat[Category.OTHER.name]?.id

        fun uiOf(a: AppEntity) = AppUi(
            key = a.appKey, packageName = a.packageName, className = a.className, label = a.label,
            locked = a.lockedSlot != null, excluded = a.excludedFromRanking, inDock = a.dockSlot != null,
            userAssigned = a.userFolderId != null && a.userFolderId in liveIds, entity = a,
        )

        fun folderIdOf(a: AppEntity): Long? = when {
            a.userFolderId != null && a.userFolderId in liveIds -> a.userFolderId
            env.now - a.installedAt in 0..RECENT_WINDOW -> RECENT_FOLDER_ID
            else -> smartByCat[a.autoCategory]?.id ?: otherId
        }

        val byScore = compareByDescending<AppEntity> { scoreMap[it.appKey]?.score ?: 0.0 }
            .thenBy { it.label.lowercase() }
        val grouped = apps.groupBy { folderIdOf(it) }

        val folderUis = buildList {
            grouped[RECENT_FOLDER_ID]?.let { list ->
                add(FolderUi(RECENT_FOLDER_ID, "New", null, true, list.sortedByDescending { it.installedAt }.map(::uiOf)))
            }
            live.forEach { f ->
                val list = grouped[f.id].orEmpty()
                if (list.isNotEmpty()) {
                    add(FolderUi(f.id, f.name, f.category?.let { Category.fromName(it) }, false, list.sortedWith(byScore).map(::uiOf)))
                }
            }
        }

        val byKey = apps.associateBy { it.appKey }
        val frequent = RankingEngine.arrange(
            apps.map { it.toItem(scoreMap[it.appKey]) },
            env.settings.layoutMode.frequentCount,
        ).map { key -> key?.let { byKey[it] }?.let(::uiOf) }

        return HomeUiState(
            loaded = true,
            settings = env.settings,
            frequent = frequent,
            folders = folderUis,
            dock = apps.filter { it.dockSlot != null }.sortedBy { it.dockSlot }.map(::uiOf),
            allApps = apps.map(::uiOf),
            moveTargets = live,
            usageMode = if (env.usageAccess) UsageMode.SYSTEM else UsageMode.INTERNAL,
        )
    }

    // ---------- 生命周期 ----------

    fun onResume() {
        now.value = System.currentTimeMillis()
        usageAccess.value = repo.hasUsageAccess()
        _roleHeld.value = isHomeRoleHeld()
        viewModelScope.launch {
            repo.ingestUsage()
            val s = repo.settings.current()
            // 三星省电策略可能延迟 WorkManager；超过 26 小时未计算则在前台补算一次
            if (s.onboardingDone && s.autoAdjust && now.value - s.lastRankingAt > 26 * RankingEngine.DAY / 24) {
                repo.recomputeScores()
            }
        }
    }

    fun onHomePressed() { _homeSignal.value++ }

    private fun isHomeRoleHeld(): Boolean {
        val rm = getApplication<Application>().getSystemService(RoleManager::class.java)
        return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun consumeMessage() { _message.value = null }

    // ---------- 应用操作 ----------

    fun launch(app: AppUi) {
        if (!repo.launch(app.entity)) _message.value = "Can't open ${app.label}"
    }

    fun lockAt(app: AppUi, slot: Int) = io { repo.lockAt(app.key, slot) }
    fun unlock(app: AppUi) = io { repo.unlock(app.key) }

    /** 从文件夹或 Dock 固定到常用区：放在第一个未锁定的位置。 */
    fun pinToFrequent(app: AppUi) = io {
        val s = state.value
        val lockedSlots = s.allApps.mapNotNull { it.entity.lockedSlot }.toSet()
        val slot = (0 until s.settings.layoutMode.frequentCount).firstOrNull { it !in lockedSlots }
        if (slot == null) _message.value = "All frequent slots are locked" else repo.lockAt(app.key, slot)
    }

    fun setExcluded(app: AppUi, excluded: Boolean) = io { repo.setExcluded(app.key, excluded) }
    fun moveToFolder(app: AppUi, folderId: Long?) = io { repo.moveToFolder(app.key, folderId) }
    fun moveToNewFolder(app: AppUi, name: String) = io {
        if (name.isBlank()) return@io
        repo.moveToFolder(app.key, repo.createFolder(name))
    }
    fun addToDock(app: AppUi) = io { if (!repo.addToDock(app.key)) _message.value = "Dock is full (max 5 apps)" }
    fun removeFromDock(app: AppUi) = io { repo.removeFromDock(app.key) }

    // ---------- 文件夹 ----------

    fun createFolder(name: String) = io { if (name.isNotBlank()) repo.createFolder(name) }
    fun renameFolder(id: Long, name: String) = io { if (name.isNotBlank()) repo.renameFolder(id, name) }
    fun deleteFolder(id: Long) = io { repo.deleteFolder(id) }

    // ---------- 设置 ----------

    fun setLayout(mode: LayoutMode) = io { repo.settings.setLayoutMode(mode) }
    fun setAutoAdjust(v: Boolean) = io { repo.settings.setAutoAdjust(v) }
    fun recomputeNow() = io { repo.ingestUsage(); repo.recomputeScores(); _message.value = "Frequent apps refreshed" }
    fun rescan() = io { repo.rescan(); _message.value = "Apps rescanned" }
    fun resetClassification() = io { repo.resetClassification(); _message.value = "Default categories restored" }

    fun scanForOnboarding() = io { repo.syncInstalledApps() }

    fun finishOnboarding() = io {
        repo.ingestUsage()
        repo.recomputeScores()
        repo.settings.setOnboardingDone(true)
    }

    fun restartOnboarding() = io { repo.settings.setOnboardingDone(false) }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val RECENT_FOLDER_ID = -1L
        const val RECENT_WINDOW = 7 * RankingEngine.DAY
    }
}
