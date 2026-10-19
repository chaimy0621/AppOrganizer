package com.apporganizer.launcher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One launchable activity (most apps have exactly one).
 * appKey = "packageName/className"
 */
@Entity(tableName = "apps", indices = [Index("packageName")])
data class AppEntity(
    @PrimaryKey val appKey: String,
    val packageName: String,
    val className: String,
    val label: String,
    /** PackageInfo.firstInstallTime, used for the 7-day "Recently installed" group */
    val installedAt: Long,
    val firstSeenAt: Long,
    /** Classifier result (Category.name). Updated on every rescan. */
    val autoCategory: String,
    /** Folder chosen by the user; when set it always wins over autoCategory and is never overwritten. */
    val userFolderId: Long? = null,
    /** Locked slot in the favourites row (0-based); locked apps are never moved automatically. */
    val lockedSlot: Int? = null,
    /** Dock position (0-based); null = not in the dock. */
    val dockSlot: Int? = null,
    /** true = excluded from automatic favourites ranking. */
    val excludedFromRanking: Boolean = false,
)

/**
 * A folder. Non-null category = smart folder (filled by category); null = user-created folder.
 * Deleting a smart folder only marks it deleted, so a rescan does not recreate it.
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
 * Launch record (raw event, kept for 35 days).
 * source: 0 = system UsageStats, 1 = in-app tap (fallback data when Usage Access is not granted)
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

/** Daily score snapshot; screens read only this, so the order stays stable within a day. */
@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey val appKey: String,
    val score: Double,
    val lastUsedAt: Long?,
    val computedAt: Long,
)
