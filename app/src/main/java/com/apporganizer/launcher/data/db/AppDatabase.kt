package com.apporganizer.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppEntity::class, FolderEntity::class, LaunchEventEntity::class, ScoreEntity::class],
    version = 2, // v2: English folder names
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun folderDao(): FolderDao
    abstract fun launchEventDao(): LaunchEventDao
    abstract fun scoreDao(): ScoreDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "organizer.db")
                // 后续版本请改为显式 Migration；MVP 阶段允许破坏性迁移。
                .fallbackToDestructiveMigration()
                .build()
    }
}
