package com.example.mp3retrowidget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher entry is an activity-alias, so it can be disabled without
 * touching MainActivity: the app keeps working and stays reachable through the
 * widget's "Ajustes" entry or Settings > Apps, but disappears from the drawer.
 */
object LauncherVisibility {

    private const val ALIAS = "com.example.mp3retrowidget.LauncherAlias"

    fun setHidden(context: Context, hidden: Boolean) {
        val manager = context.packageManager
        val component = ComponentName(context.packageName, ALIAS)
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        try {
            manager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
            WidgetPrefs.setLauncherHidden(context, hidden)
        } catch (e: Exception) {
            // Nothing sensible to do; the switch will snap back on resume.
        }
    }

    fun isHidden(context: Context): Boolean = WidgetPrefs.isLauncherHidden(context)
}
