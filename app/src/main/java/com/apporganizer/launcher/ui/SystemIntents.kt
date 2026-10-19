package com.apporganizer.launcher.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemIntents {

    /** Opens the Usage Access settings page (this app's entry directly when possible). */
    fun openUsageAccess(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, direct)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** Opens the system default-home picker, used to switch back to One UI Home. */
    fun openHomeSettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_HOME_SETTINGS))) {
            tryStart(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    /** RoleManager intent requesting ROLE_HOME; null when the device does not support it. */
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
        "Settings → Apps → Choose default apps → Home app → One UI Home.\n" +
            "Or tap “Switch back to One UI Home” in this app’s settings. Uninstalling this app also restores One UI Home."
}
