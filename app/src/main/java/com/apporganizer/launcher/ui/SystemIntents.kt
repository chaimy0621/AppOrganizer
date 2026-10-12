package com.apporganizer.launcher.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemIntents {

    /** 打开“使用情况访问权限”设置页（优先直达本应用）。 */
    fun openUsageAccess(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, direct)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** 打开系统“默认主屏幕”选择页 —— 用于切换回 One UI Home。 */
    fun openHomeSettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_HOME_SETTINGS))) {
            tryStart(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    /** RoleManager 请求 ROLE_HOME 的 Intent；设备不支持时返回 null。 */
    fun homeRoleRequest(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    const val RESTORE_ONE_UI_STEPS =
        "设置 → 应用 → 选择默认应用 → 主屏幕应用 → 选择「One UI 主屏幕」。\n" +
            "也可以直接在本应用设置中点「切换回 One UI Home」。卸载本应用后系统也会自动恢复 One UI Home。"
}
