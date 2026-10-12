package com.apporganizer.launcher.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class LayoutMode(val frequentCount: Int, val displayName: String, val description: String) {
    BALANCED(8, "平衡模式", "8 个常用应用 + 智能文件夹"),
    FOLDERS_FIRST(4, "文件夹优先", "4 个常用应用，文件夹占主要空间"),
    FREQUENT_FIRST(12, "常用优先", "12 个常用应用，文件夹在下方");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.BALANCED,
    val autoAdjust: Boolean = true,
    val lastRankingAt: Long = 0L,
)

class SettingsStore(context: Context) {
    private val ds = context.applicationContext.dataStore

    private object K {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val LAYOUT = stringPreferencesKey("layout_mode")
        val AUTO = booleanPreferencesKey("auto_adjust")
        val LAST_RANK = longPreferencesKey("last_ranking_at")
        val LAST_INGEST = longPreferencesKey("last_ingest_at")
        val DOCK_INIT = booleanPreferencesKey("dock_initialized")
    }

    private val prefs: Flow<Preferences> = ds.data.catch { emit(emptyPreferences()) }

    val flow: Flow<AppSettings> = prefs.map { p ->
        AppSettings(
            onboardingDone = p[K.ONBOARDING] ?: false,
            layoutMode = LayoutMode.fromName(p[K.LAYOUT]),
            autoAdjust = p[K.AUTO] ?: true,
            lastRankingAt = p[K.LAST_RANK] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setOnboardingDone(v: Boolean) { ds.edit { it[K.ONBOARDING] = v } }
    suspend fun setLayoutMode(m: LayoutMode) { ds.edit { it[K.LAYOUT] = m.name } }
    suspend fun setAutoAdjust(v: Boolean) { ds.edit { it[K.AUTO] = v } }
    suspend fun setLastRankingAt(t: Long) { ds.edit { it[K.LAST_RANK] = t } }

    suspend fun lastIngestAt(): Long = prefs.first()[K.LAST_INGEST] ?: 0L
    suspend fun setLastIngestAt(t: Long) { ds.edit { it[K.LAST_INGEST] = t } }

    suspend fun dockInitialized(): Boolean = prefs.first()[K.DOCK_INIT] ?: false
    suspend fun setDockInitialized() { ds.edit { it[K.DOCK_INIT] = true } }
}
