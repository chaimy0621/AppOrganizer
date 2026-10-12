package com.apporganizer.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM apps ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps")
    suspend fun getAll(): List<AppEntity>

    @Upsert
    suspend fun upsertAll(items: List<AppEntity>)

    @Query("DELETE FROM apps WHERE appKey IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("UPDATE apps SET lockedSlot = NULL WHERE lockedSlot = :slot")
    suspend fun clearLockedSlot(slot: Int)

    @Query("UPDATE apps SET lockedSlot = :slot WHERE appKey = :key")
    suspend fun setLockedSlot(key: String, slot: Int?)

    @Query("UPDATE apps SET userFolderId = :folderId WHERE appKey = :key")
    suspend fun setUserFolder(key: String, folderId: Long?)

    @Query("UPDATE apps SET userFolderId = NULL WHERE userFolderId = :folderId")
    suspend fun clearUserFolder(folderId: Long)

    @Query("UPDATE apps SET userFolderId = NULL")
    suspend fun clearAllUserFolders()

    @Query("UPDATE apps SET excludedFromRanking = :excluded WHERE appKey = :key")
    suspend fun setExcluded(key: String, excluded: Boolean)

    @Query("UPDATE apps SET dockSlot = :slot WHERE appKey = :key")
    suspend fun setDockSlot(key: String, slot: Int?)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET deleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folders WHERE category IS NULL")
    suspend fun deleteUserFolders()

    @Query("UPDATE folders SET deleted = 0, name = :name, sortOrder = :sortOrderValue WHERE category = :category")
    suspend fun restoreSmart(category: String, name: String, sortOrderValue: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM folders")
    suspend fun maxOrder(): Int
}

@Dao
interface LaunchEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<LaunchEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: LaunchEventEntity)

    @Query("SELECT * FROM launch_events WHERE at >= :since AND source = :source")
    suspend fun since(since: Long, source: Int): List<LaunchEventEntity>

    @Query("DELETE FROM launch_events WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores")
    fun observeAll(): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM scores")
    suspend fun getAll(): List<ScoreEntity>

    @Query("DELETE FROM scores")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScoreEntity>)
}
