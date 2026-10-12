package com.apporganizer.launcher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一个可启动的 Activity（多数应用只有一个）。
 * appKey = "packageName/className"
 */
@Entity(tableName = "apps", indices = [Index("packageName")])
data class AppEntity(
    @PrimaryKey val appKey: String,
    val packageName: String,
    val className: String,
    val label: String,
    /** PackageInfo.firstInstallTime，用于“最近安装”7 天分组 */
    val installedAt: Long,
    val firstSeenAt: Long,
    /** Classifier 结果（Category.name）。重新扫描时会更新。 */
    val autoCategory: String,
    /** 用户手动指定的文件夹；非空时永远优先于 autoCategory，且自动分类不会覆盖。 */
    val userFolderId: Long? = null,
    /** 常用区锁定位置（0 起）；非空时不参与自动移动。 */
    val lockedSlot: Int? = null,
    /** Dock 位置（0 起）；null = 不在 Dock。 */
    val dockSlot: Int? = null,
    /** true = 不参与常用区自动排序。 */
    val excludedFromRanking: Boolean = false,
)

/**
 * 文件夹。category 非空 = 智能文件夹（按类别自动填充）；null = 用户自建文件夹。
 * 智能文件夹被删除时只标记 deleted，避免“重新扫描”又把它建回来。
 */
@Entity(tableName = "folders", indices = [Index(value = ["category"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String?,
    val sortOrder: Int,
    val deleted: Boolean = false,
)

/**
 * 启动记录（原始事件，保留 35 天）。
 * source: 0 = 系统 UsageStats，1 = 本 Launcher 内部点击（无 Usage Access 时的降级数据）
 */
@Entity(
    tableName = "launch_events",
    indices = [
        Index(value = ["packageName", "at", "source"], unique = true),
        Index("at"),
    ],
)
data class LaunchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val at: Long,
    val source: Int,
) {
    companion object {
        const val SOURCE_SYSTEM = 0
        const val SOURCE_LAUNCHER = 1
    }
}

/** 每日计算的分数快照；首页只读这里，保证一天内排序稳定。 */
@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey val appKey: String,
    val score: Double,
    val lastUsedAt: Long?,
    val computedAt: Long,
)
